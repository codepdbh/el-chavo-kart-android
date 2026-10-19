#!/usr/bin/env python3
"""Function map and coverage report from the ReXGlue output of El Chavo Kart.

Every recompiled PPC instruction is emitted with a `// <mnemonic>` comment, so the number of instruction comments
per DEFINE_REX_FUNC is the exact number of guest instructions translated for that function.

Usage: python tools/function_report.py --generated generated/default --xex-report analysis/xex/xex_report.json
         --image out/analysis/chavokart-82000000.bin --out analysis/functions
Writes function_map.json, unresolved_functions.json and coverage.md.
"""
import argparse
import glob
import json
import os
import re
import struct

FUNC = re.compile(r'(?m)^DEFINE_REX_FUNC\(sub_([0-9A-F]{8})\)')
INSN = re.compile(r'(?m)^\t+// [a-z]')
FATAL = re.compile(r'REX_FATAL\("((?:Unresolved branch from 0x([0-9A-F]{8}) to 0x([0-9A-F]{8}))|'
                   r'(?:Jump target 0x([0-9A-F]{8}) unresolved at bctr 0x([0-9A-F]{8})))"\)')
LABEL = re.compile(r'(?m)^loc_([0-9A-F]{8}):')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--generated', required=True)
    ap.add_argument('--xex-report', required=True)
    ap.add_argument('--image', required=True)
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    report = json.load(open(a.xex_report, encoding='utf-8'))
    base = int(report['image_base'], 16)
    image = open(a.image, 'rb').read()
    sections = [s for s in report['pe']['sections'] if s['executable']]

    functions = {}
    unresolved = []
    for path in sorted(glob.glob(os.path.join(a.generated, '*_recomp.*.cpp'))):
        text = open(path, encoding='utf-8').read()
        starts = [(m.start(), int(m.group(1), 16)) for m in FUNC.finditer(text)] + [(len(text), None)]
        for (begin, addr), (end, _) in zip(starts, starts[1:]):
            body = text[begin:end]
            insns = len(INSN.findall(body))
            labels = set(LABEL.findall(body))
            functions[addr] = {'address': '0x%08X' % addr, 'file': os.path.basename(path), 'instructions': insns}
            for m in FATAL.finditer(body):
                if m.group(2):
                    kind, source, target = 'conditional-branch', m.group(2), m.group(3)
                else:
                    kind, target, source = 'jump-table-case', m.group(4), m.group(5)
                unresolved.append({'function': '0x%08X' % addr, 'kind': kind, 'source': '0x' + source,
                                   'target': '0x' + target, 'label_in_same_function': target in labels,
                                   'file': os.path.basename(path)})

    # Coverage: translated instruction bytes vs non-zero words of the executable sections.
    code_words = 0
    per_section = []
    ordered = sorted(functions)
    for s in sections:
        start = int(s['address'], 16)
        end = start + s['virtual_size']
        words = sum(1 for o in range(start - base, end - base, 4) if struct.unpack_from('>I', image, o)[0] != 0)
        covered = sum(functions[f]['instructions'] for f in ordered if start <= f < end)
        count = sum(1 for f in ordered if start <= f < end)
        per_section.append({'section': s['name'], 'nonzero_words': words, 'translated_instructions': covered,
                            'functions': count})
        code_words += words
    translated = sum(f['instructions'] for f in functions.values())

    os.makedirs(a.out, exist_ok=True)
    json.dump({'function_count': len(functions), 'translated_instructions': translated,
               'functions': [functions[f] for f in ordered]},
              open(os.path.join(a.out, 'function_map.json'), 'w', encoding='utf-8'), indent=1)
    json.dump(unresolved, open(os.path.join(a.out, 'unresolved_functions.json'), 'w', encoding='utf-8'), indent=2)
    same = sum(1 for u in unresolved if u['label_in_same_function'])
    owners = sorted({u['function'] for u in unresolved})
    L = ['# Cobertura de la recompilación — El Chavo Kart', '',
         'Generado por `tools/function_report.py` a partir de `generated/default`.', '',
         '- Funciones generadas: **%d**' % len(functions),
         '- Instrucciones PPC traducidas: **%d** de %d palabras no nulas en secciones ejecutables (**%.2f %%**)' % (
             translated, code_words, 100.0 * translated / max(code_words, 1)),
         '- Marcadores `REX_FATAL` en código generado: **%d** (%d con etiqueta en la misma función, %d sin ella) '
         'en %d funciones' % (len(unresolved), same, len(unresolved) - same, len(owners)), '',
         '| Sección | Palabras no nulas | Instrucciones traducidas | Funciones |', '|---|---|---|---|']
    L += ['| `%s` | %d | %d | %d |' % (p['section'], p['nonzero_words'], p['translated_instructions'], p['functions'])
          for p in per_section]
    L += ['', 'Las palabras no nulas incluyen datos incrustados y tablas de salto dentro de `.text`, por lo que el '
          'porcentaje es una cota inferior de la cobertura de código real.', '',
          '## Funciones con marcadores pendientes', '', '| Función | Tipo | Origen | Destino | Etiqueta en la función |',
          '|---|---|---|---|---|']
    L += ['| %s | %s | %s | %s | %s |' % (u['function'], u['kind'], u['source'], u['target'],
                                         'sí' if u['label_in_same_function'] else 'no') for u in unresolved]
    open(os.path.join(a.out, 'coverage.md'), 'w', encoding='utf-8').write('\n'.join(L) + '\n')
    print('[RECOMP] %d functions, %d instructions (%.2f%%), %d REX_FATAL (%d repairable locally)' % (
        len(functions), translated, 100.0 * translated / max(code_words, 1), len(unresolved), same))


if __name__ == '__main__':
    main()
