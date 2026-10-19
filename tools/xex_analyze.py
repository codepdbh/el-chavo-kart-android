#!/usr/bin/env python3
"""Analyzes an Xbox 360 XEX2 executable for the El Chavo Kart port.

Parses the XEX2 headers, decrypts and rebuilds the PE image (basic or no compression), resolves the kernel/XAM
imports by ordinal and classifies each one against the pinned ReXGlue SDK fork (implemented / stub / missing).

The retail/devkit AES keys are not stored in this project: they are read from the SDK source
(src/system/xex_module.cpp), the same public Xenia constants the runtime uses.

Usage:
  python tools/xex_analyze.py --xex <default.xex> --sdk <nfsmw-android/sdk> --out analysis [--image out/image.bin]

Writes analysis/xex/xex_report.json, analysis/xex/xex_report.md and analysis/imports/imports_report.md.
The rebuilt image is copyrighted game data: --image must point to an ignored folder (out/).
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import struct
import sys

try:
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
except ImportError:  # pragma: no cover
    sys.exit('[XEX] Python package "cryptography" is required (pip install cryptography)')


def be32(b, o):
    return struct.unpack_from('>I', b, o)[0]


def be16(b, o):
    return struct.unpack_from('>H', b, o)[0]


def le32(b, o):
    return struct.unpack_from('<I', b, o)[0]


def le16(b, o):
    return struct.unpack_from('<H', b, o)[0]


OPT_NAMES = {
    0x000002FF: 'RESOURCE_INFO', 0x000003FF: 'FILE_FORMAT_INFO', 0x00000405: 'BASE_REFERENCE',
    0x000005FF: 'DELTA_PATCH_DESCRIPTOR', 0x000080FF: 'BOUNDING_PATH', 0x00008105: 'DEVICE_ID',
    0x00010001: 'ORIGINAL_BASE_ADDRESS', 0x00010100: 'ENTRY_POINT', 0x00010201: 'IMAGE_BASE_ADDRESS',
    0x000103FF: 'IMPORT_LIBRARIES', 0x00018002: 'CHECKSUM_TIMESTAMP', 0x00018102: 'ENABLED_FOR_CALLCAP',
    0x00018200: 'ENABLED_FOR_FASTCAP', 0x000183FF: 'ORIGINAL_PE_NAME', 0x000200FF: 'STATIC_LIBRARIES',
    0x00020104: 'TLS_INFO', 0x00020200: 'DEFAULT_STACK_SIZE', 0x00020301: 'DEFAULT_FILESYSTEM_CACHE_SIZE',
    0x00020401: 'DEFAULT_HEAP_SIZE', 0x00028002: 'PAGE_HEAP_SIZE_AND_FLAGS', 0x00030000: 'SYSTEM_FLAGS',
    0x00040006: 'EXECUTION_INFO', 0x000401FF: 'TITLE_WORKSPACE_SIZE', 0x00040201: 'GAME_RATINGS?',
    0x00040310: 'GAME_RATINGS', 0x00040404: 'LAN_KEY', 0x000405FF: 'XBOX360_LOGO',
    0x000406FF: 'MULTIDISC_MEDIA_IDS', 0x000407FF: 'ALTERNATE_TITLE_IDS', 0x00040801: 'ADDITIONAL_TITLE_MEMORY',
    0x00E10402: 'EXPORTS_BY_NAME',
}
REGIONS = [(0x000000FF, 'NTSC-U'), (0x0000FD00, 'NTSC-J (Japan)'), (0x00000100, 'NTSC-J (China)'),
           (0x00FE0000, 'PAL (excl. AU/NZ)'), (0x00010000, 'PAL (AU/NZ)')]
SECTION_TYPES = {1: 'code', 2: 'data', 3: 'readonly'}
# Strings that reveal engine/middleware; each is only reported when found in the image.
MIDDLEWARE_PATTERNS = [
    ('Unreal Engine 3', rb'Unreal ?Engine|UnrealScript|UObject|ULinkerLoad|FName|Engine\.GameEngine'),
    ('FMOD', rb'FMOD|fmod_|\.fsb'),
    ('Bink video', rb'Bink|BINK|\.bik|\.BIK'),
    ('Scaleform GFx', rb'Scaleform|GFx|\.gfx|SWF'),
    ('PhysX / NovodeX', rb'PhysX|NxPhysics|Novodex|NovodeX|AGEIA'),
    ('XAudio / XACT', rb'XAudio|XACT|X3DAudio|XMA'),
    ('Havok', rb'Havok|hkBase|hkp'),
    ('SpeedTree', rb'SpeedTree'),
    ('Lua', rb'lua_|LUA_'),
    ('Xbox Live / XUI', rb'XUI|XamShowSigninUI|XLive'),
]


def read_sdk_keys(sdk):
    src = open(os.path.join(sdk, 'src', 'system', 'xex_module.cpp'), encoding='utf-8', errors='replace').read()
    keys = {}
    for name in ('retail', 'devkit'):
        m = re.search(r'xe_xex2_%s_key\[16\]\s*=\s*\{([^}]*)\}' % name, src)
        if m:
            keys[name] = bytes(int(x, 16) for x in re.findall(r'0x[0-9A-Fa-f]{2}', m.group(1)))
    if 'retail' not in keys:
        raise SystemExit('[XEX] could not read the XEX2 keys from %s' % sdk)
    return keys


def read_sdk_exports(sdk):
    """Ordinal tables (xboxkrnl/xam) and the export implementation status in the SDK."""
    tables = {}
    for module, path in (('xboxkrnl.exe', 'src/kernel/xboxkrnl/export_table.inc'),
                         ('xam.xex', 'src/kernel/xam/export_table.inc')):
        table = {}
        text = open(os.path.join(sdk, path), encoding='utf-8', errors='replace').read()
        for m in re.finditer(r'XE_EXPORT\(\s*\w+\s*,\s*(0x[0-9A-Fa-f]+)\s*,\s*(\w+)\s*,\s*(\w+)\s*\)', text):
            table[int(m.group(1), 16)] = (m.group(2), m.group(3))
        tables[module] = table
    status = {}
    for dirpath, _, files in os.walk(os.path.join(sdk, 'src')):
        for f in files:
            if not f.endswith('.cpp'):
                continue
            rel = os.path.relpath(os.path.join(dirpath, f), sdk).replace('\\', '/')
            text = open(os.path.join(dirpath, f), encoding='utf-8', errors='replace').read()
            # REX_EXPORT / REX_HOOK / REX_HOOK_RAW define a real body; REX_EXPORT_STUB* only logs a warning.
            for m in re.finditer(r'REX_(?:EXPORT|HOOK)(_RAW|_STUB_RETURN|_STUB)?\(\s*__imp__(\w+)', text):
                kind = 'stub' if m.group(1) in ('_STUB', '_STUB_RETURN') else 'implemented'
                # An implementation anywhere wins over a stub elsewhere.
                if status.get(m.group(2), ('', ''))[0] != 'implemented':
                    status[m.group(2)] = (kind, rel)
    return tables, status


def decode_version(v):
    return '%d.%d.%d.%d' % (v >> 28, (v >> 24) & 0xF, (v >> 8) & 0xFFFF, v & 0xFF)


def aes_ecb_decrypt(key, data):
    d = Cipher(algorithms.AES(key), modes.ECB()).decryptor()
    return d.update(data) + d.finalize()


def aes_cbc_decrypt(key, data):
    d = Cipher(algorithms.AES(key), modes.CBC(b'\0' * 16)).decryptor()
    n = len(data) & ~15
    return d.update(data[:n]) + d.finalize() + data[n:]


def build_image(xex, pe_offset, fmt_off, session_key, encrypted, image_size):
    info_size = be32(xex, fmt_off)
    compression = be16(xex, fmt_off + 6)
    payload = xex[pe_offset:]
    if encrypted:
        payload = aes_cbc_decrypt(session_key, payload)
    if compression == 0:
        image = bytearray(payload[:image_size])
    elif compression == 1:
        image = bytearray()
        src = 0
        for i in range((info_size - 8) // 8):
            data_size = be32(xex, fmt_off + 8 + i * 8)
            zero_size = be32(xex, fmt_off + 12 + i * 8)
            image += payload[src:src + data_size]
            image += b'\0' * zero_size
            src += data_size
    else:
        raise ValueError('compression %d (LZX/delta) is not handled by this tool' % compression)
    if len(image) < image_size:
        image += b'\0' * (image_size - len(image))
    return bytes(image[:image_size]), compression


def parse_pe(image, base):
    if image[:2] != b'MZ':
        return None
    nt = le32(image, 0x3C)
    if image[nt:nt + 4] != b'PE\0\0':
        return None
    machine = le16(image, nt + 4)
    nsec = le16(image, nt + 6)
    timestamp = le32(image, nt + 8)
    opt_size = le16(image, nt + 20)
    opt = nt + 24
    sec = opt + opt_size
    sections = []
    for i in range(nsec):
        o = sec + i * 40
        name = image[o:o + 8].split(b'\0')[0].decode('latin1')
        vsize, va, rsize, raddr = le32(image, o + 8), le32(image, o + 12), le32(image, o + 16), le32(image, o + 20)
        chars = le32(image, o + 36)
        sections.append({
            'name': name, 'address': '0x%08X' % (base + va), 'virtual_size': vsize, 'raw_size': rsize,
            'characteristics': '0x%08X' % chars, 'executable': bool(chars & 0x20000000),
            'writable': bool(chars & 0x80000000), 'readable': bool(chars & 0x40000000)})
    return {'machine': '0x%04X' % machine, 'timestamp': timestamp,
            'timestamp_utc': datetime.datetime.fromtimestamp(timestamp, datetime.timezone.utc).isoformat(),
            'sections': sections}


def count_code(image, base, sections):
    """Rough instruction statistics for executable sections (prologue/blr patterns only)."""
    stats = {'instructions': 0, 'blr': 0, 'mflr_r12': 0, 'bctr': 0, 'bl': 0}
    for s in sections:
        if not s['executable']:
            continue
        start = int(s['address'], 16) - base
        end = start + s['virtual_size']
        for o in range(start, min(end, len(image)) - 3, 4):
            w = be32(image, o)
            stats['instructions'] += 1
            if w == 0x4E800020:
                stats['blr'] += 1
            elif w == 0x7D8802A6:
                stats['mflr_r12'] += 1
            elif w == 0x4E800420:
                stats['bctr'] += 1
            elif (w & 0xFC000003) == 0x48000001:
                stats['bl'] += 1
    return stats


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--xex', required=True)
    ap.add_argument('--sdk', required=True)
    ap.add_argument('--out', required=True, help='analysis/ folder of the project')
    ap.add_argument('--image', help='write the rebuilt PE image here (ignored folder)')
    a = ap.parse_args()

    xex = open(a.xex, 'rb').read()
    if xex[:4] != b'XEX2':
        raise SystemExit('[XEX] %s is not an XEX2 file' % a.xex)
    report = {'file': os.path.basename(a.xex), 'size': len(xex), 'sha256': hashlib.sha256(xex).hexdigest(),
              'format': 'XEX2'}
    module_flags, pe_offset, sec_off, opt_count = be32(xex, 4), be32(xex, 8), be32(xex, 0x10), be32(xex, 0x14)
    report['module_flags'] = '0x%08X' % module_flags
    report['module_type'] = [n for bit, n in ((1, 'title'), (2, 'exports_to_title'), (4, 'system_debugger'),
                                              (8, 'dll'), (0x10, 'module_patch'), (0x20, 'patch_full'),
                                              (0x40, 'patch_delta'), (0x80, 'user_mode')) if module_flags & bit]
    opts = {}
    report['optional_headers'] = []
    for i in range(opt_count):
        k, v = be32(xex, 0x18 + i * 8), be32(xex, 0x1C + i * 8)
        opts[k] = v
        report['optional_headers'].append({'key': '0x%08X' % k, 'name': OPT_NAMES.get(k, 'UNKNOWN'),
                                           'value': '0x%08X' % v})

    base = opts.get(0x00010201, 0)
    report['image_base'] = '0x%08X' % base
    report['entry_point'] = '0x%08X' % opts.get(0x00010100, 0)
    report['default_stack_size'] = opts.get(0x00020200)
    report['system_flags'] = '0x%08X' % opts.get(0x00030000, 0)
    if 0x000183FF in opts:
        o = opts[0x000183FF]
        report['original_pe_name'] = xex[o + 4:o + be32(xex, o)].split(b'\0')[0].decode('latin1')
    if 0x00040006 in opts:
        o = opts[0x00040006]
        report['execution_info'] = {
            'media_id': '%08X' % be32(xex, o), 'version': decode_version(be32(xex, o + 4)),
            'base_version': decode_version(be32(xex, o + 8)), 'title_id': '%08X' % be32(xex, o + 12),
            'publisher_code': xex[o + 12:o + 14].decode('latin1', 'replace'),
            'game_number': be16(xex, o + 14), 'platform': xex[o + 16], 'executable_type': xex[o + 17],
            'disc_number': xex[o + 18], 'disc_count': xex[o + 19], 'savegame_id': '%08X' % be32(xex, o + 20)}
    if 0x00020104 in opts:
        o = opts[0x00020104]
        report['tls'] = {'slot_count': be32(xex, o), 'raw_data_address': '0x%08X' % be32(xex, o + 4),
                         'data_size': be32(xex, o + 8), 'raw_data_size': be32(xex, o + 12)}
    if 0x00018002 in opts:
        o = opts[0x00018002]
        report['checksum'] = '0x%08X' % be32(xex, o)
        ts = be32(xex, o + 4)
        report['link_timestamp_utc'] = datetime.datetime.fromtimestamp(ts, datetime.timezone.utc).isoformat()
    if 0x000200FF in opts:
        o = opts[0x000200FF]
        size = be32(xex, o)
        libs = []
        for p in range(o + 4, o + size, 16):
            name = xex[p:p + 8].split(b'\0')[0].decode('latin1')
            major, minor, build, flags = be16(xex, p + 8), be16(xex, p + 10), be16(xex, p + 12), be16(xex, p + 14)
            approval = {0: 'unapproved', 1: 'possible', 2: 'approved', 3: 'expired'}.get(flags >> 13 & 3, '?')
            libs.append({'name': name, 'version': '%d.%d.%d.%d' % (major, minor, build, flags & 0xFF),
                         'approval': approval})
        report['static_libraries'] = libs
    if 0x000002FF in opts:
        o = opts[0x000002FF]
        res = []
        for p in range(o + 4, o + be32(xex, o), 16):
            res.append({'name': xex[p:p + 8].split(b'\0')[0].decode('latin1'),
                        'address': '0x%08X' % be32(xex, p + 8), 'size': be32(xex, p + 12)})
        report['resources'] = res

    # Security info
    s = sec_off
    image_size = be32(xex, s + 4)
    image_flags = be32(xex, s + 0x10C)
    region = be32(xex, s + 0x178)
    media = be32(xex, s + 0x17C)
    page_size = 0x1000 if image_flags & 0x10000000 else 0x10000
    report['security'] = {
        'image_size': image_size, 'image_flags': '0x%08X' % image_flags, 'load_address': '0x%08X' % be32(xex, s + 0x110),
        'region_mask': '0x%08X' % region,
        'regions': ['ALL'] if region == 0xFFFFFFFF else [n for m, n in REGIONS if region & m],
        'allowed_media_mask': '0x%08X' % media, 'page_size': page_size,
        'import_table_count': be32(xex, s + 0x128)}
    pages = []
    addr = be32(xex, s + 0x110)
    for i in range(be32(xex, s + 0x180)):
        v = be32(xex, s + 0x184 + i * 24)
        npages, info = v >> 4, v & 0xF
        if pages and pages[-1]['type'] == SECTION_TYPES.get(info, str(info)):
            pages[-1]['size'] += npages * page_size
        else:
            pages.append({'address': addr, 'size': npages * page_size, 'type': SECTION_TYPES.get(info, str(info))})
        addr += npages * page_size
    report['security']['page_ranges'] = [{'start': '0x%08X' % p['address'], 'end': '0x%08X' % (p['address'] + p['size']),
                                          'size': p['size'], 'type': p['type']} for p in pages]

    # Decrypt + rebuild
    fmt = opts[0x000003FF]
    encrypted = be16(xex, fmt + 4) == 1
    keys = read_sdk_keys(a.sdk)
    image = pe = None
    for key_name in ('retail', 'devkit'):
        session = aes_ecb_decrypt(keys[key_name], xex[s + 0x150:s + 0x160]) if encrypted else None
        image, compression = build_image(xex, pe_offset, fmt, session, encrypted, image_size)
        pe = parse_pe(image, base)
        if pe:
            report['key'] = key_name if encrypted else 'none'
            break
    report['encryption'] = 'AES-128 (%s key)' % report.get('key') if encrypted else 'none'
    report['compression'] = {0: 'none', 1: 'basic (zero-fill blocks)', 2: 'normal (LZX)', 3: 'delta'}[compression]
    if not pe:
        raise SystemExit('[XEX] image did not decrypt to a valid PE with the SDK keys')
    report['image_sha256'] = hashlib.sha256(image).hexdigest()
    report['pe'] = pe
    report['code_statistics'] = count_code(image, base, pe['sections'])
    if a.image:
        os.makedirs(os.path.dirname(os.path.abspath(a.image)), exist_ok=True)
        with open(a.image, 'wb') as f:
            f.write(image)

    # Imports
    tables, status = read_sdk_exports(a.sdk)
    o = opts[0x000103FF]
    name_size, lib_count = be32(xex, o + 4), be32(xex, o + 8)
    names = [n.decode('latin1') for n in xex[o + 12:o + 12 + name_size].split(b'\0') if n]
    p = o + 12 + name_size
    libraries = []
    for _ in range(lib_count):
        rec_size = be32(xex, p)
        version, version_min = be32(xex, p + 0x1C), be32(xex, p + 0x20)
        lib_name = names[be16(xex, p + 0x24)]
        count = be16(xex, p + 0x26)
        entries = {}
        for i in range(count):
            rec_addr = be32(xex, p + 0x28 + i * 4)
            value = be32(image, rec_addr - base)
            kind, ordinal = value >> 24, value & 0xFFFF
            e = entries.setdefault(ordinal, {'ordinal': ordinal, 'variable_address': None, 'thunk_address': None})
            if kind == 0:
                e['variable_address'] = '0x%08X' % rec_addr
            else:
                e['thunk_address'] = '0x%08X' % rec_addr
        table = tables.get(lib_name, {})
        for e in entries.values():
            name, etype = table.get(e['ordinal'], ('ordinal_%d' % e['ordinal'], 'unknown'))
            e['name'], e['export_type'] = name, etype
            st, where = status.get(name, ('missing', ''))
            if etype == 'kVariable':
                st, where = 'variable (kernel data)', ''
            e['sdk_status'], e['sdk_source'] = st, where
        libraries.append({'name': lib_name, 'version': decode_version(version), 'min_version': decode_version(version_min),
                          'imports': sorted(entries.values(), key=lambda e: e['ordinal'])})
        p += rec_size
    report['imports'] = libraries

    # Middleware evidence (only counts of matches; no game text is copied into reports)
    mw = []
    for label, pat in MIDDLEWARE_PATTERNS:
        n = len(re.findall(pat, image))
        if n:
            mw.append({'component': label, 'matches': n})
    report['middleware_string_evidence'] = mw
    m = re.search(rb'ENGINE_VERSION|EngineVersion', image)
    report['ue3_engine_version_symbol_present'] = bool(m)

    os.makedirs(os.path.join(a.out, 'xex'), exist_ok=True)
    os.makedirs(os.path.join(a.out, 'imports'), exist_ok=True)
    with open(os.path.join(a.out, 'xex', 'xex_report.json'), 'w', encoding='utf-8') as f:
        json.dump(report, f, indent=2)
    write_markdown(report, a.out)
    print('[XEX] %s sha256=%s title=%s version=%s entry=%s' % (
        os.path.basename(a.xex), report['sha256'], report.get('execution_info', {}).get('title_id'),
        report.get('execution_info', {}).get('version'), report['entry_point']))


def write_markdown(r, out):
    ei = r.get('execution_info', {})
    L = ['# Informe XEX — El Chavo Kart (Xbox 360)', '',
         'Generado por `tools/xex_analyze.py` el %s. Datos extraídos del ejecutable real; no editar a mano.' %
         datetime.datetime.now().strftime('%Y-%m-%d %H:%M'), '',
         '| Campo | Valor |', '|---|---|']
    rows = [('Archivo', os.path.basename(r['file'])), ('Tamaño', '%d bytes' % r['size']), ('SHA-256 (XEX)', r['sha256']),
            ('SHA-256 (imagen PE reconstruida)', r['image_sha256']), ('Formato', r['format']),
            ('Tipo de módulo', ', '.join(r['module_type'])), ('Nombre PE original', r.get('original_pe_name', '')),
            ('Title ID', ei.get('title_id', '')), ('Media ID', ei.get('media_id', '')), ('Versión', ei.get('version', '')),
            ('Versión base', ei.get('base_version', '')), ('Disco', '%s/%s' % (ei.get('disc_number'), ei.get('disc_count'))),
            ('Savegame ID', ei.get('savegame_id', '')), ('Regiones', ', '.join(r['security']['regions']) +
                                                          ' (`%s`)' % r['security']['region_mask']),
            ('Cifrado', r['encryption']), ('Compresión', r['compression']), ('Image base', r['image_base']),
            ('Entry point', r['entry_point']), ('Tamaño de imagen', '0x%X (%.1f MiB)' % (r['security']['image_size'],
                                                                                      r['security']['image_size'] / 1048576)),
            ('Tamaño de página XEX', '0x%X' % r['security']['page_size']), ('Pila por defecto', r.get('default_stack_size')),
            ('System flags', r['system_flags']), ('Fecha de enlace', r.get('link_timestamp_utc', '')),
            ('TLS', json.dumps(r.get('tls')))]
    L += ['| %s | %s |' % (k, v) for k, v in rows]
    L += ['', '## Secciones PE', '', '| Nombre | Dirección | Tamaño virtual | Ejecutable | Escritura |', '|---|---|---|---|---|']
    for s in r['pe']['sections']:
        L.append('| `%s` | %s | 0x%X | %s | %s |' % (s['name'], s['address'], s['virtual_size'],
                                                   'sí' if s['executable'] else 'no', 'sí' if s['writable'] else 'no'))
    L += ['', '## Rangos de páginas (descriptor de seguridad)', '', '| Inicio | Fin | Tipo |', '|---|---|---|']
    L += ['| %s | %s | %s |' % (p['start'], p['end'], p['type']) for p in r['security']['page_ranges']]
    L += ['', '## Bibliotecas estáticas enlazadas (XDK)', '', '| Biblioteca | Versión | Aprobación |', '|---|---|---|']
    L += ['| %s | %s | %s |' % (l['name'], l['version'], l['approval']) for l in r.get('static_libraries', [])]
    cs = r['code_statistics']
    L += ['', '## Estadísticas del código (patrones exactos de instrucción)', '',
          '- Palabras en secciones ejecutables: %d' % cs['instructions'],
          '- `blr`: %d · `bctr` (saltos indirectos/tablas): %d · `bl`: %d · `mflr r12` (prólogos): %d' % (
              cs['blr'], cs['bctr'], cs['bl'], cs['mflr_r12']), '',
          'El número real de funciones lo determina el análisis de ReXGlue (`analysis/functions/`).', '',
          '## Indicios de middleware en la imagen', '', '| Componente | Coincidencias |', '|---|---|']
    L += ['| %s | %d |' % (m['component'], m['matches']) for m in r['middleware_string_evidence']]
    L += ['', 'Los indicios por cadenas son orientativos; cada componente se confirma en `docs/ue3-analysis.md`.', '']
    with open(os.path.join(out, 'xex', 'xex_report.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(L))

    I = ['# Imports del ejecutable — El Chavo Kart', '',
         'Clasificación frente al SDK fijado (`references/nfsmw-android/sdk`): **implemented** = `REX_EXPORT` con '
         'implementación; **stub** = `REX_EXPORT_STUB` (registra aviso y devuelve sin hacer el trabajo); '
         '**missing** = sin registrar en el SDK.', '']
    for lib in r['imports']:
        counts = {}
        for e in lib['imports']:
            counts[e['sdk_status']] = counts.get(e['sdk_status'], 0) + 1
        I += ['## %s (versión %s, mínima %s) — %d imports' % (lib['name'], lib['version'], lib['min_version'],
                                                              len(lib['imports'])), '',
              'Resumen: ' + ', '.join('%s: %d' % kv for kv in sorted(counts.items())), '',
              '| Ordinal | Nombre | Tipo | Estado SDK | Fuente SDK |', '|---|---|---|---|---|']
        for e in lib['imports']:
            I.append('| 0x%03X | %s | %s | %s | %s |' % (e['ordinal'], e['name'], e['export_type'], e['sdk_status'],
                                                         e['sdk_source']))
        I.append('')
    with open(os.path.join(out, 'imports', 'imports_report.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(I))


if __name__ == '__main__':
    main()
