#!/usr/bin/env python3
"""Inventory of the Unreal Engine 3 data of El Chavo Kart (Xbox 360).

Reads only headers (no asset content is copied): UE3 package summaries, fully-compressed package chunk headers,
Coalesced ini blobs, FMOD FSB5 banks, Bink movies and the Xbox360TOC files. Detects the available languages.

Usage: python tools/ue3_inventory.py --game game_xex --out analysis
Writes analysis/ue3/ue3_inventory.json, analysis/ue3/ue3_inventory.md and analysis/localization/languages.json.
"""
import argparse
import collections
import json
import os
import re
import struct

PKG_TAG = 0x9E2A83C1
COMPRESSION = {0: 'none', 1: 'zlib', 2: 'lzo', 4: 'lzx'}
FSB5_FORMATS = {0: 'none', 1: 'PCM8', 2: 'PCM16', 3: 'PCM24', 4: 'PCM32', 5: 'PCMFLOAT', 6: 'GCADPCM', 7: 'IMAADPCM',
                8: 'VAG', 9: 'HEVAG', 10: 'XMA', 11: 'MPEG', 12: 'CELT', 13: 'AT9', 14: 'XWMA', 15: 'VORBIS'}
# UE3 three-letter language codes known from the engine; reported only when files use them.
UE3_LANGS = {'INT': 'English (international)', 'ESN': 'Spanish (Spain)', 'ESM': 'Spanish (Latin America)',
             'PTB': 'Portuguese (Brazil)', 'POR': 'Portuguese', 'FRA': 'French', 'DEU': 'German', 'ITA': 'Italian',
             'JPN': 'Japanese', 'KOR': 'Korean', 'RUS': 'Russian', 'POL': 'Polish', 'CHN': 'Chinese'}


class Reader:
    def __init__(self, data, off=0):
        self.d, self.o = data, off

    def u32(self):
        v = struct.unpack_from('>I', self.d, self.o)[0]
        self.o += 4
        return v

    def i32(self):
        v = struct.unpack_from('>i', self.d, self.o)[0]
        self.o += 4
        return v

    def fstring(self):
        n = self.i32()
        if n == 0:
            return ''
        if n > 0:
            s = self.d[self.o:self.o + n].rstrip(b'\0').decode('latin1')
            self.o += n
        else:
            s = self.d[self.o:self.o - n * 2].decode('utf-16-be').rstrip('\0')
            self.o += -n * 2
        return s


def package_summary(head):
    r = Reader(head)
    if r.u32() != PKG_TAG:
        return None
    second = r.u32()
    # Fully compressed files start with a chunk header: tag, block size, compressed size, uncompressed size.
    if second in (0x20000, 0x10000, 0x8000):
        return {'kind': 'fully-compressed', 'block_size': second, 'compressed_size': r.u32(), 'uncompressed_size': r.u32()}
    version, licensee = second & 0xFFFF, second >> 16
    s = {'kind': 'package', 'file_version': version, 'licensee_version': licensee}
    s['total_header_size'] = r.i32()
    s['folder'] = r.fstring()
    s['package_flags'] = '0x%08X' % r.u32()
    s['name_count'], _ = r.i32(), r.i32()
    s['export_count'], _ = r.i32(), r.i32()
    s['import_count'], _ = r.i32(), r.i32()
    if version >= 415:
        r.i32()  # DependsOffset
    if version >= 623:
        r.i32(); r.i32(); r.i32()  # ImportExportGuidsOffset, ImportGuidsCount, ExportGuidsCount
    if version >= 584:
        r.i32()  # ThumbnailTableOffset
    r.o += 16  # Guid
    gens = r.i32()
    if not 0 <= gens < 1000:
        return s
    r.o += gens * (12 if version >= 322 else 8)
    s['engine_version'] = r.i32()
    s['cooker_version'] = r.i32()
    flags = r.u32()
    s['compression'] = '+'.join(n for b, n in COMPRESSION.items() if b and flags & b) or 'none'
    s['compressed_chunks'] = r.i32()
    return s


def category(name):
    stem = name.split('.')[0]
    for prefix, label in (('CO-', 'track (CO)'), ('CT-', 'track (CT)'), ('EN-', 'track (EN)'), ('PO-', 'track (PO)'),
                          ('KartPawn', 'kart pawn'), ('Pista', 'track (main)')):
        if stem.startswith(prefix):
            return label
    if re.search(r'_LOC_[A-Z]{3}$', stem):
        return 'localized package'
    return 'other'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--game', required=True)
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    game = os.path.realpath(a.game)
    files = []
    for dp, _, fs in os.walk(game):
        for f in fs:
            p = os.path.join(dp, f)
            files.append((os.path.relpath(p, game).replace('\\', '/'), os.path.getsize(p)))
    inv = {'game_root': os.path.basename(a.game.rstrip('\\/')), 'file_count': len(files), 'total_bytes': sum(s for _, s in files),
           'extensions': dict(collections.Counter(os.path.splitext(f)[1].lower() or '(none)' for f, _ in files)),
           'packages': [], 'fsb5': [], 'bink': [], 'coalesced': [], 'toc': {}}
    versions = collections.Counter()
    for rel, size in sorted(files):
        p = os.path.join(game, rel)
        low = rel.lower()
        with open(p, 'rb') as fh:
            head = fh.read(4096)
        if low.endswith(('.xxx', '.upk', '.tfc')):
            s = package_summary(head) or {'kind': 'unknown'}
            s.update({'file': rel, 'size': size, 'category': category(os.path.basename(rel))})
            inv['packages'].append(s)
            if s['kind'] == 'package':
                versions[(s['file_version'], s['licensee_version'], s.get('engine_version'), s.get('cooker_version'))] += 1
        elif low.endswith('.fsbcache') and head[:4] == b'FSB5':
            ver, nsamples, shsize, ntsize, dsize, mode = struct.unpack_from('<6I', head, 4)
            inv['fsb5'].append({'file': rel, 'size': size, 'version': ver, 'samples': nsamples, 'data_size': dsize,
                                'format': FSB5_FORMATS.get(mode, str(mode))})
        elif low.endswith('.bik') and head[:3] in (b'BIK', b'KB2'):
            frames, largest, _, w, h, fps_num, fps_den, flags, tracks = struct.unpack_from('<9I', head, 8)
            inv['bink'].append({'file': rel, 'size': size, 'signature': head[:4].decode('latin1'), 'frames': frames,
                                'width': w, 'height': h, 'fps': round(fps_num / fps_den, 3) if fps_den else None,
                                'audio_tracks': tracks})
        elif re.search(r'coalesced_[a-z]{3}\.bin$', low):
            count = struct.unpack_from('>I', head, 0)[0]
            inv['coalesced'].append({'file': rel, 'size': size, 'ini_count': count,
                                     'language': re.search(r'_([A-Za-z]{3})\.bin$', rel).group(1).upper()})
        elif re.search(r'xbox360toc(_[a-z]{3})?\.txt$', low):
            entries, missing = 0, []
            for line in open(p, encoding='latin1'):
                parts = line.split()
                if len(parts) >= 3:
                    entries += 1
                    target = os.path.normpath(os.path.join(game, 'ChavoKartGame', parts[2].replace('\\', '/')))
                    if not os.path.exists(target):
                        missing.append(parts[2])
            inv['toc'][rel] = {'entries': entries, 'missing_on_disk': missing}
    inv['package_versions'] = [{'file_version': k[0], 'licensee_version': k[1], 'engine_version': k[2],
                                'cooker_version': k[3], 'packages': v} for k, v in versions.most_common()]

    langs = collections.defaultdict(lambda: {'text_packages': [], 'coalesced': [], 'movies': [], 'toc': []})
    for rel, _ in files:
        m = re.search(r'_LOC_([A-Z]{3})\.xxx$', rel) or re.search(r'Coalesced_([A-Z]{3})\.bin$', rel) or \
            re.search(r'_([A-Z]{3})\.BIK$', rel, re.I) or re.search(r'TOC_([A-Z]{3})\.txt$', rel)
        if not m:
            continue
        code = m.group(1).upper()
        key = 'text_packages' if rel.endswith('.xxx') else 'coalesced' if rel.endswith('.bin') else \
            'movies' if rel.lower().endswith('.bik') else 'toc'
        langs[code][key].append(rel)
    languages = {c: dict(v, name=UE3_LANGS.get(c, 'unknown'), default=(c == 'INT')) for c, v in sorted(langs.items())}
    for c in languages:  # INT movies/TOC have no suffix in UE3
        if c == 'INT':
            languages[c]['note'] = 'INT is the base language: unsuffixed movies and Xbox360TOC.txt belong to it.'
    inv['languages'] = languages

    os.makedirs(os.path.join(a.out, 'ue3'), exist_ok=True)
    os.makedirs(os.path.join(a.out, 'localization'), exist_ok=True)
    json.dump(inv, open(os.path.join(a.out, 'ue3', 'ue3_inventory.json'), 'w', encoding='utf-8'), indent=2)
    json.dump(languages, open(os.path.join(a.out, 'localization', 'languages.json'), 'w', encoding='utf-8'), indent=2)

    cats = collections.Counter(p['category'] for p in inv['packages'])
    kinds = collections.Counter(p['kind'] for p in inv['packages'])
    comp = collections.Counter(p.get('compression', p['kind']) for p in inv['packages'])
    L = ['# Inventario UE3 — El Chavo Kart', '', 'Generado por `tools/ue3_inventory.py` (solo cabeceras).', '',
         '- Archivos: %d, %.1f MiB' % (inv['file_count'], inv['total_bytes'] / 1048576),
         '- Extensiones: ' + ', '.join('%s ×%d' % kv for kv in sorted(inv['extensions'].items())), '',
         '## Paquetes UE3', '', '- Tipos: ' + ', '.join('%s ×%d' % kv for kv in kinds.items()),
         '- Categorías: ' + ', '.join('%s ×%d' % kv for kv in sorted(cats.items())),
         '- Compresión: ' + ', '.join('%s ×%d' % kv for kv in comp.items()), '',
         '| FileVersion | Licensee | EngineVersion | CookerVersion | Paquetes |', '|---|---|---|---|---|']
    L += ['| %s | %s | %s | %s | %d |' % (v['file_version'], v['licensee_version'], v['engine_version'],
                                          v['cooker_version'], v['packages']) for v in inv['package_versions']]
    fc = [p for p in inv['packages'] if p['kind'] == 'fully-compressed']
    L += ['', 'Paquetes totalmente comprimidos (cabecera de bloque, acompañados de `.uncompressed_size`):', '']
    L += ['- `%s`: %d → %d bytes' % (p['file'], p['compressed_size'], p['uncompressed_size']) for p in fc]
    L += ['', '## Audio FMOD (FSB5)', '', '| Banco | Muestras | Formato | Datos |', '|---|---|---|---|']
    L += ['| `%s` | %d | %s | %d |' % (f['file'].split('/')[-1], f['samples'], f['format'], f['data_size']) for f in inv['fsb5']]
    L += ['', '## Vídeos Bink', '', '| Vídeo | Firma | Resolución | FPS | Fotogramas | Pistas audio |', '|---|---|---|---|---|---|']
    L += ['| `%s` | %s | %dx%d | %s | %d | %d |' % (b['file'].split('/')[-1], b['signature'], b['width'], b['height'],
                                                  b['fps'], b['frames'], b['audio_tracks']) for b in inv['bink']]
    L += ['', '## Idiomas detectados', '', '| Código | Idioma | Paquetes de texto | Coalesced | Vídeos | TOC |', '|---|---|---|---|---|---|']
    for c, v in languages.items():
        L.append('| %s | %s | %d | %d | %d | %d |' % (c, v['name'], len(v['text_packages']), len(v['coalesced']),
                                                    len(v['movies']), len(v['toc'])))
    L += ['', '## Coherencia de TOC', '']
    L += ['- `%s`: %d entradas, %d ausentes en disco%s' % (k, v['entries'], len(v['missing_on_disk']),
          (': ' + ', '.join('`%s`' % m for m in v['missing_on_disk'][:12])) if v['missing_on_disk'] else '')
          for k, v in inv['toc'].items()]
    open(os.path.join(a.out, 'ue3', 'ue3_inventory.md'), 'w', encoding='utf-8').write('\n'.join(L) + '\n')
    print('[UE3] %d files, %d packages, %d FSB5 banks, %d Bink movies, languages: %s' % (
        inv['file_count'], len(inv['packages']), len(inv['fsb5']), len(inv['bink']), ', '.join(languages)))


if __name__ == '__main__':
    main()
