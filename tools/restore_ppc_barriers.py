"""Restores PPC data barriers (sync/lwsync/eieio) that ReXGlue emits only as comments.

x86 hides the omission (TSO); ARM64 reorders loads/stores across them, which corrupts lock-free guest
queues such as the renderer's command/vertex rings. Each barrier comment gets a sequentially consistent
fence (ARM64 dmb ish) on the next line. Idempotent; run after codegen (scripts/generate-code.ps1).
Same fix as the-simpsons-game-android scripts/prepare-arm64-ordering.py.
"""
import argparse
import re
from pathlib import Path

FENCE = "__atomic_thread_fence(__ATOMIC_SEQ_CST);"
BARRIER = re.compile(r"(?m)^([ \t]*)// (?:sync|lwsync|eieio)[ \t]*\r?\n(?![ \t]*" + re.escape(FENCE) + ")")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generated", type=Path,
                        default=Path(__file__).resolve().parent.parent / "generated" / "default")
    args = parser.parse_args()
    total = files = 0
    for path in sorted(args.generated.glob("*.cpp")):
        text = path.read_text(encoding="utf-8")
        fixed, count = BARRIER.subn(lambda m: m.group(0) + m.group(1) + FENCE + "\n", text)
        if count:
            path.write_text(fixed, encoding="utf-8", newline="")
            total += count
            files += 1
    fences = sum(p.read_text(encoding="utf-8").count(FENCE) for p in args.generated.glob("*.cpp"))
    print(f"[RECOMP] PPC barriers restored: {total} new in {files} files ({fences} fences total)")


if __name__ == "__main__":
    main()
