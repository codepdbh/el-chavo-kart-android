// Import logic derived from the-simpsons-game-android LauncherActivity (codepdbh, GPLv3), commit ed0f1ab.
package com.codepdbh.chavokart;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The user's own extracted copy, imported into private storage (files/game) because the runtime needs POSIX paths.
 * Nothing from the game is shipped in the APK.
 */
final class GameFiles {
    /** Executables whose recompiled code exists. A hash match identifies the copy; it does not prove it runs. */
    static final Map<String, String> SUPPORTED = Collections.singletonMap(
            "1DB4E96B413D5629206FFE63E73C0E1CFACA9EEA24BCF6FCED6DB9B135CE120F",
            "El Chavo Kart (Xbox 360) title 475807D7 media 5C2ECDD6 v0.0.0.3");
    static final String[] KNOWN_LANGUAGES = {"INT", "ESN", "ESM", "PTB"};
    private static final int MAX_DEPTH = 32;

    interface Progress {
        void update(long done, long total);
    }

    static File gameDir(Context context) {
        return new File(context.getFilesDir(), "game");
    }

    static boolean installed(Context context) {
        return new File(gameDir(context), "default.xex").isFile() &&
                Prefs.get(context).getBoolean("game_verified", false);
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1 << 20];
        try (InputStream input = new FileInputStream(file)) {
            int n;
            while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder value = new StringBuilder();
        for (byte b : digest.digest()) value.append(String.format(Locale.ROOT, "%02X", b));
        return value.toString();
    }

    static void verify(File folder) throws Exception {
        File xex = new File(folder, "default.xex");
        if (!xex.isFile()) throw new UserError(R.string.game_error_no_xex);
        String sha = sha256(xex);
        Log.i("XEX", "default.xex SHA-256 " + sha);
        if (!SUPPORTED.containsKey(sha)) throw new UserError(R.string.game_error_unsupported, sha);
    }

    /** UE3 text languages present: ChavoKartGame_LOC_<LANG>.xxx plus Coalesced_<LANG>.bin. */
    static List<String> languages(File game) {
        List<String> result = new ArrayList<>();
        File cooked = new File(game, "ChavoKartGame/CookedXbox360");
        for (String code : KNOWN_LANGUAGES) {
            if (new File(cooked, "ChavoKartGame_LOC_" + code + ".xxx").isFile() &&
                    new File(cooked, "Coalesced_" + code + ".bin").isFile()) result.add(code);
        }
        return result;
    }

    private static final class Entry {
        final String documentId, relativePath;
        final boolean directory;
        final long size;

        Entry(String documentId, String relativePath, boolean directory, long size) {
            this.documentId = documentId; this.relativePath = relativePath; this.directory = directory; this.size = size;
        }
    }

    /** Copies the folder that contains default.xex (searched recursively) into files/game, atomically. */
    static void importTree(Context context, Uri tree, AtomicBoolean cancel, Progress progress) throws Exception {
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        String gameRoot = findXexFolder(context, tree, rootId, 0);
        if (gameRoot == null) throw new UserError(R.string.game_error_no_xex);
        List<Entry> entries = new ArrayList<>();
        list(context, tree, gameRoot, "", 0, entries);
        long total = 0;
        for (Entry e : entries) total += e.size;
        long needed = total + 256L * 1024 * 1024;
        if (context.getFilesDir().getUsableSpace() < needed)
            throw new UserError(R.string.game_error_space, android.text.format.Formatter.formatShortFileSize(context, needed));
        File staging = new File(context.getFilesDir(), "game-import");
        File game = gameDir(context);
        File backup = new File(context.getFilesDir(), "game-previous");
        remove(staging);
        if (!staging.mkdirs()) throw new UserError(R.string.error_create_folder);
        String prefix = staging.getCanonicalPath() + File.separator;
        try {
            long done = 0;
            byte[] buffer = new byte[1 << 20];
            for (Entry e : entries) {
                if (cancel.get()) throw new InterruptedIOException("cancelled");
                File target = new File(staging, e.relativePath);
                if (!target.getCanonicalPath().startsWith(prefix)) throw new UserError(R.string.game_error_bad_name);
                if (e.directory) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new UserError(R.string.error_create_folder);
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new UserError(R.string.error_create_folder);
                Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, e.documentId);
                try (InputStream input = context.getContentResolver().openInputStream(document);
                     OutputStream output = new FileOutputStream(target)) {
                    if (input == null) throw new UserError(R.string.game_error_open, e.relativePath);
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (cancel.get()) throw new InterruptedIOException("cancelled");
                        output.write(buffer, 0, n);
                        done += n;
                        progress.update(done, total);
                    }
                }
            }
            verify(staging);
            // Keep the previous installation until the verified replacement is in place.
            remove(backup);
            if (game.exists() && !game.renameTo(backup)) throw new UserError(R.string.game_error_activate);
            if (!staging.renameTo(game)) {
                if (backup.exists()) backup.renameTo(game);
                throw new UserError(R.string.game_error_activate);
            }
            remove(backup);
            Prefs.get(context).edit().putBoolean("game_verified", true).apply();
            Log.i("ANDROID", "Game imported: " + entries.size() + " entries, " + total + " bytes");
        } catch (Exception error) {
            remove(staging);
            throw error;
        }
    }

    private static String findXexFolder(Context context, Uri tree, String id, int depth) throws Exception {
        if (depth > 4) return null;
        List<String[]> dirs = new ArrayList<>();
        try (Cursor c = query(context, tree, id)) {
            while (c.moveToNext()) {
                String childId = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) dirs.add(new String[]{childId, name});
                else if ("default.xex".equalsIgnoreCase(name)) return id;
            }
        }
        for (String[] dir : dirs) {
            String found = findXexFolder(context, tree, dir[0], depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static void list(Context context, Uri tree, String id, String path, int depth, List<Entry> out) throws Exception {
        if (depth > MAX_DEPTH) throw new UserError(R.string.game_error_nesting);
        try (Cursor c = query(context, tree, id)) {
            while (c.moveToNext()) {
                String childId = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") ||
                        name.contains("\\") || name.indexOf('\0') >= 0) throw new UserError(R.string.game_error_bad_name);
                String relative = path.isEmpty() ? name : path + "/" + name;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    out.add(new Entry(childId, relative, true, 0));
                    list(context, tree, childId, relative, depth + 1, out);
                } else {
                    out.add(new Entry(childId, relative, false, c.isNull(3) ? 0 : c.getLong(3)));
                }
            }
        }
    }

    private static Cursor query(Context context, Uri tree, String id) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE};
        Cursor cursor = context.getContentResolver().query(children, columns, null, null, null);
        if (cursor == null) throw new UserError(R.string.game_error_enumerate);
        return cursor;
    }

    /** Only app-owned folders (game-import, game-previous, cache) are passed here. */
    static long remove(File file) {
        long freed = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) freed += remove(child);
        if (file.isFile()) freed += file.length();
        if (file.exists()) file.delete();
        return freed;
    }
}
