// Derived from nfsmw-android (codepdbh, GPLv3) com.nfsmw.android.DriverArchive, commit 507a63f.
package com.codepdbh.chavokart;

import java.io.*;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.*;

/** Bounded driver ZIP extraction with path traversal protection and ARM64 ELF validation. */
final class DriverArchive {
    static final long LIMIT = 256L * 1024 * 1024;
    static final int MAX_ENTRIES = 1024;

    static void extract(InputStream input, File root) throws IOException {
        String prefix = root.getCanonicalPath() + File.separator;
        Set<String> seen = new HashSet<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new UserError(R.string.driver_error_too_many_files);
                String name = entry.getName();
                File file = new File(root, name);
                if (name.startsWith("/") || name.contains("\\") || name.contains(":") || name.split("/").length > 32 ||
                        !file.getCanonicalPath().startsWith(prefix) || !seen.add(file.getCanonicalPath()))
                    throw new UserError(R.string.driver_error_bad_path);
                if (entry.isDirectory()) {
                    if (!file.isDirectory() && !file.mkdirs()) throw new UserError(R.string.error_create_folder);
                    continue;
                }
                if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs())
                    throw new UserError(R.string.error_create_folder);
                long size = 0;
                try (FileOutputStream output = new FileOutputStream(file)) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        size += n;
                        total += n;
                        if (size > LIMIT / 2 || total > LIMIT || (file.getName().equals("meta.json") && size > 65536))
                            throw new UserError(R.string.driver_error_too_large);
                        output.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    /** ELF64, little endian, ET_DYN, EM_AARCH64 (183). */
    static void validateLibrary(File library) throws IOException {
        byte[] h = new byte[64];
        try (DataInputStream input = new DataInputStream(new FileInputStream(library))) {
            input.readFully(h);
        } catch (EOFException e) {
            throw new UserError(R.string.driver_error_not_arm64);
        }
        if (h[0] != 127 || h[1] != 'E' || h[2] != 'L' || h[3] != 'F' || h[4] != 2 || h[5] != 1 ||
                h[16] != 3 || h[17] != 0 || (h[18] & 255) != 183 || h[19] != 0)
            throw new UserError(R.string.driver_error_not_arm64);
    }

    static void delete(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) throw new UserError(R.string.driver_error_bad_path);
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (file.exists() && !file.delete()) throw new UserError(R.string.error_delete, file.getName());
    }
}
