#!/usr/bin/env python3
"""Disassembles the evidence for 'UnresolvedCall' codegen errors in the rebuilt El Chavo Kart image.

For each `b <target> from <source>` reported by `rexglue codegen`, prints the instructions around the source and
the target, and classifies the target:
  entry-after-boundary : the instruction before the target ends control flow (blr / b / bctr / padding),
                         so the target is the start of a separate routine reached by a tail branch.
  inside-flow          : the target follows a fall-through instruction; declaring it a function would
                         split an existing routine and needs manual review.

Usage: python tools/inspect_branches.py --image out/analysis/chavokart-82000000.bin --log out/logs/codegen-1.stdout
       [--json analysis/functions/unresolved_branches.json]
"""
import argparse
import json
import re
import struct

from capstone import CS_ARCH_PPC, CS_MODE_32, CS_MODE_BIG_ENDIAN, Cs

BASE = 0x82000000
ENDS_FLOW = {0x4E800020, 0x4E800420, 0x00000000}  # blr, bctr, zero padding


def word(image, addr):
    return struct.unpack_from('>I', image, addr - BASE)[0]


def disasm(md, image, start, count):
    out = []
    for addr in range(start, start + count * 4, 4):
        raw = image[addr - BASE:addr - BASE + 4]
        ins = next(md.disasm(raw, addr), None)
        out.append('0x%08X  %08X  %s' % (addr, word(image, addr), '%s %s' % (ins.mnemonic, ins.op_str) if ins else '.long'))
    return out


def classify(image, target):
    prev = word(image, target - 4)
    unconditional_b = (prev & 0xFC000003) == 0x48000000
    if prev in ENDS_FLOW or unconditional_b:
        return 'entry-after-boundary'
    return 'inside-flow'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--image', required=True)
    ap.add_argument('--log', required=True)
    ap.add_argument('--json')
    a = ap.parse_args()
    image = open(a.image, 'rb').read()
    md = Cs(CS_ARCH_PPC, CS_MODE_32 | CS_MODE_BIG_ENDIAN)
    pairs = []
    for m in re.finditer(r'b 0x([0-9A-F]{8}) from 0x([0-9A-F]{8}) - target not in any function',
                         open(a.log, encoding='utf-8', errors='replace').read()):
        pairs.append((int(m.group(1), 16), int(m.group(2), 16)))
    results = []
    for target, source in pairs:
        w = word(image, source)
        # Verify the reported instruction really is an unconditional relative branch to the target.
        li = w & 0x03FFFFFC
        if li & 0x02000000:
            li -= 0x04000000
        verified = (w & 0xFC000003) == 0x48000000 and source + li == target
        kind = classify(image, target)
        results.append({'target': '0x%08X' % target, 'source': '0x%08X' % source, 'branch_verified': verified,
                        'classification': kind, 'distance': target - source})
        print('=' * 72)
        print('b 0x%08X from 0x%08X  verified=%s  %s  distance=%+d' % (target, source, verified, kind, target - source))
        print('  -- source --')
        print('\n'.join('  ' + l for l in disasm(md, image, source - 8, 4)))
        print('  -- target --')
        print('\n'.join('  ' + l for l in disasm(md, image, target - 12, 9)))
    if a.json:
        with open(a.json, 'w', encoding='utf-8') as f:
            json.dump(results, f, indent=2)


if __name__ == '__main__':
    main()
