"""Reproducible renderer A/B test; keeps and restores the developer overrides.

python tools/android_render_test.py depth --seconds 60 -- --depth_float24_convert_in_pixel_shader=true
Requires an unlocked phone with the game already imported. Does not clear caches or saves.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("name")
    parser.add_argument("--seconds", type=int, default=60)
    parser.add_argument("--serial")
    options, cvars = parser.parse_known_args()
    cvars = [arg for arg in cvars if arg != "--"]
    if not re.fullmatch(r"[\w-]+", options.name):
        parser.error("name must contain only letters, digits, underscores or hyphens")
    if options.seconds < 1 or any(not arg.startswith("--") or "=" not in arg for arg in cvars):
        parser.error("use --seconds >= 1 and --cvar=value arguments")
    root = Path(__file__).resolve().parents[1]
    output = root / "out" / "android-tests" / options.name
    output.mkdir(parents=True, exist_ok=True)
    sdk = Path(os.environ.get("ANDROID_HOME", Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
    adb = [str(sdk / "platform-tools/adb.exe")]
    if options.serial:
        adb += ["-s", options.serial]

    def run(*args, check=True):
        return subprocess.run(adb + list(args), capture_output=True, check=check).stdout

    package = "com.codepdbh.chavokart"
    overrides = f"/sdcard/Android/data/{package}/files/extra_args.txt"
    run("get-state")
    present = subprocess.run(adb + ["shell", "test", "-e", overrides], capture_output=True)
    if present.returncode not in (0, 1):
        raise RuntimeError("Cannot check the existing developer overrides")
    # exec-out avoids the shell PTY's CR/LF conversion; the backup must be byte-for-byte exact.
    original = subprocess.run(adb + ["exec-out", "cat", overrides], capture_output=True) if present.returncode == 0 else None
    if original is not None:
        original.check_returncode()
        (output / "original.args").write_bytes(original.stdout)
    args = output / "test.args"
    args.write_text("\n".join(["--log_level=info"] + cvars) + "\n", encoding="ascii")
    old_log = subprocess.run(adb + ["shell", "run-as", package, "cat", "files/logs/game.log"],
                             capture_output=True)
    try:
        run("push", str(args), overrides)
        run("shell", "am", "force-stop", package)
        run("shell", "am", "start", "-n", package + "/.LauncherActivity")
        time.sleep(2)
        run("shell", "uiautomator", "dump", "/sdcard/ck_test_ui.xml")
        ui = ET.fromstring(run("shell", "cat", "/sdcard/ck_test_ui.xml"))
        button = next(node for node in ui.iter("node") if node.get("text") in ("JUGAR", "PLAY", "JOGAR"))
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", button.attrib["bounds"]))
        run("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        print(f"{options.name}: running for {options.seconds}s", flush=True)
        time.sleep(options.seconds)
        (output / "screen.png").write_bytes(run("exec-out", "screencap", "-p"))
        if old_log.returncode == 0:
            log = run("shell", "run-as", package, "cat", "files/logs/game.log")
            if log.startswith(old_log.stdout):
                log = log[len(old_log.stdout):]
        else:
            pid = run("shell", "pidof", package + ":game").decode().strip()
            log = run("logcat", "-d", "--pid=" + pid)
        (output / "game.log").write_bytes(log)
        (output / "crash.log").write_bytes(run("logcat", "-d", "-b", "crash"))
        for line in log.decode("utf-8", errors="replace").splitlines():
            if "[PERFORMANCE]" in line:
                print(line, flush=True)
        print(f"Saved {output}", flush=True)
    finally:
        if original is not None:
            run("push", str(output / "original.args"), overrides)
        else:
            run("shell", "rm", "-f", overrides)


if __name__ == "__main__":
    main()
