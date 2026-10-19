#!/usr/bin/env python3
"""Repair only REX_FATAL branch placeholders whose destination label exists in the SAME function.

Adapted from NFS-CARBON-360-ANDROID-RECOMP tools/fix_local_branches.py (commit c3ee962, BSD 3-Clause).

ReXGlue emits `REX_FATAL("Unresolved branch ...")` when it classifies a branch target as Unknown, even if that
target is a basic block of the same function (it then still emits `loc_<target>:`). Replacing the placeholder with
`goto loc_<target>` keeps the original condition and PPC destination. Jump-table cases are repaired the same way.
Placeholders without a local label stay fatal and are reported, so unknown game code is never skipped silently.

Usage: python tools/fix_local_branches.py [--generated generated/default] [--prefix chavokart]
Exit code 1 when placeholders remain (they are listed in analysis/functions/unresolved_functions.json).
"""
import argparse
import re
import sys
from pathlib import Path

FUNCTION = re.compile(r"(?m)^DEFINE_REX_FUNC\(")
FATAL = re.compile(
    r'(?m)^(\tif \([^\n]+?\)) REX_FATAL\("Unresolved branch from '
    r'0x([0-9A-F]{8}) to 0x([0-9A-F]{8})"\);$')
SWITCH = re.compile(
    r'(?m)^(\t+)REX_FATAL\("Jump target 0x([0-9A-F]{8}) unresolved at bctr '
    r'0x[0-9A-F]{8}"\);$')


def main():
    ap = argparse.ArgumentParser()
    root = Path(__file__).resolve().parents[1]
    ap.add_argument('--generated', default=str(root / 'generated' / 'default'))
    ap.add_argument('--prefix', default='chavokart')
    ap.add_argument('--dead', default=str(root / 'config' / 'recomp_dead_placeholders.txt'),
                    help='documented unreachable placeholders (<source> <target> per line)')
    a = ap.parse_args()
    dead = set()
    if Path(a.dead).is_file():
        for line in Path(a.dead).read_text(encoding='utf-8').splitlines():
            parts = line.split('#', 1)[0].split()
            if len(parts) == 2:
                dead.add((parts[0][2:].upper(), parts[1][2:].upper()))
    fixed, missing, changed, known_dead = 0, 0, 0, 0
    for path in sorted(Path(a.generated).glob(f"{a.prefix}_recomp.*.cpp")):
        original = path.read_text(encoding="utf-8")
        starts = [match.start() for match in FUNCTION.finditer(original)]
        if not starts:
            continue
        starts.append(len(original))
        output = [original[:starts[0]]]
        for first, end in zip(starts, starts[1:]):
            body = original[first:end]
            labels = set(re.findall(r"(?m)^loc_([0-9A-F]{8}):$", body))

            def repair(match):
                nonlocal fixed, missing
                nonlocal known_dead
                condition, source, target = match.groups()
                if target not in labels:
                    if (source, target) in dead:
                        known_dead += 1
                    else:
                        missing += 1
                    return match[0]
                fixed += 1
                return f"{condition} goto loc_{target};"

            def repair_case(match):
                nonlocal fixed, missing
                indent, target = match.groups()
                if target not in labels:
                    missing += 1
                    return match[0]
                fixed += 1
                return f"{indent}goto loc_{target};"

            output.append(SWITCH.sub(repair_case, FATAL.sub(repair, body)))
        result = "".join(output)
        if result != original:
            path.write_text(result, encoding="utf-8")
            changed += 1
    print(f"[RECOMP] Repaired {fixed} original local branches in {changed} files; "
          f"{missing} unresolved branches kept fatal for further analysis; "
          f"{known_dead} documented unreachable placeholders left as is")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
