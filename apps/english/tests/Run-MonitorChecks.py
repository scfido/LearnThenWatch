"""Actual-TV monitor checks. Requires an installed test APK and a resumable target video.

Uses adb rather than UiAutomation, which disconnects enabled accessibility services.
The signed instrumentation owns only isolated database/preferences and reports each checkpoint.
"""
import argparse
import re
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--device", default="192.168.1.132:5555")
parser.add_argument("--skip-standby", action="store_true", help="Explicitly exclude firmware standby that force-stops third-party apps")
args = parser.parse_args()
prefix = ["adb", "-s", args.device]
english = "local.mytv.english/.LearningService"


def adb(*parts):
    return subprocess.run(prefix + list(parts), check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, encoding="utf-8", errors="replace", timeout=30).stdout.strip()


original = adb("shell", "settings", "get", "secure", "enabled_accessibility_services")
original_enabled = adb("shell", "settings", "get", "secure", "accessibility_enabled")
services = [s for s in original.split(":") if s and s != "null"
            and not s.startswith("local.mytv.english/")]


def set_services(value):
    # A single adb argument must survive the device shell even when the list is empty.
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services", value or "''")
    adb("shell", "settings", "put", "secure", "accessibility_enabled", "1" if value else "0")


def command(value):
    adb("shell", "am", "broadcast", "-a", "local.mytv.english.test.MONITOR_COMMAND", "--es", "command", value)


def key(value):
    adb("shell", "input", "keyevent", value)


def power(awake):
    def is_awake():
        return "mWakefulness=Awake" in adb("shell", "dumpsys", "power")
    if is_awake() == awake:
        return
    key("KEYCODE_WAKEUP" if awake else "KEYCODE_SLEEP")
    time.sleep(1)
    if is_awake() != awake:
        # This MiTV firmware ignores the standard wake key; toggle only after checking state.
        key("KEYCODE_POWER")
        time.sleep(1)
    if is_awake() != awake:
        raise RuntimeError("TV did not enter the requested power state")


def player(resume=False):
    top = adb("shell", "dumpsys", "activity", "activities")
    in_player = any("topResumedActivity=" in line and "com.xiaodianshi.tv.yst" in line
                    and any(name in line for name in ("VideoPlayActivity", "CtsActivity", "PlayerActivity"))
                    for line in top.splitlines())
    if not in_player:
        adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
            "-f", "0x10000000", "-n", "com.xiaodianshi.tv.yst/.ui.main.MainActivity")
        time.sleep(1)
        top = adb("shell", "dumpsys", "activity", "activities")
    if any("topResumedActivity=" in line and "/.ui.main.MainActivity" in line for line in top.splitlines()):
        # Use verified video containers on the currently selected tab; the homepage is cloud-driven.
        for _ in range(6):
            command("open_video")
            time.sleep(1)
            top = adb("shell", "dumpsys", "activity", "activities")
            if any("topResumedActivity=" in line and any(name in line for name in
                   ("VideoPlayActivity", "CtsActivity", "PlayerActivity")) for line in top.splitlines()):
                break
    if resume:
        audio = adb("shell", "dumpsys", "audio")
        playing = any("AudioPlaybackConfiguration" in line and "com.xiaodianshi.tv.yst" in line
                      and "state:started" in line for line in audio.splitlines())
        if not playing and "gate=true" not in adb("shell", "dumpsys", "activity", "service", english):
            key("KEYCODE_DPAD_CENTER")


process = None
passed = False
try:
    power(True)
    set_services(":".join(services))
    time.sleep(1)
    process = subprocess.Popen(prefix + ["shell", "am", "instrument", "-w", "-e", "screen", "monitor", "-e", "standby", "false" if args.skip_standby else "true",
                                        "local.mytv.english.tests/local.mytv.english.RepositoryInstrumentation"],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                               encoding="utf-8", errors="replace", bufsize=1)
    for line in process.stdout:
        print(line, end="", flush=True)
        phase = re.search(r"MONITOR_([A-Z_]+):", line)
        if phase:
            event = phase.group(1)
            if event == "READY":
                set_services(":".join(services + [english]))
                player()
            elif event == "PAUSE":
                key("KEYCODE_DPAD_CENTER"); command("paused")
            elif event == "SLEEP":
                power(False); command("asleep")
            elif event == "WAKE":
                power(True); player(resume=True); command("awake")
            elif event == "RESUME":
                player(resume=True); command("resumed")
            elif event == "BACK":
                key("KEYCODE_BACK"); command("back")
            elif event in ("REENTER", "REENTER_AGAIN"):
                player(); command("reentered")
            elif event == "HOME":
                key("KEYCODE_HOME"); command("home")
            elif event == "REBIND":
                set_services(":".join(services)); time.sleep(1)
                set_services(":".join(services + [english])); time.sleep(2)
                print(adb("shell", "dumpsys", "activity", "service", english), flush=True)
                command("rebound")
        if "PASS:" in line:
            passed = True
    process.wait(timeout=10)
    if not passed:
        raise RuntimeError("Monitor checks failed; ensure a real video is resumable in the target app.")
finally:
    power(True)
    set_services(":".join(services))
    # Leave the player before reconnecting the production repository, so test teardown cannot charge it.
    key("KEYCODE_HOME")
    if process is not None and process.poll() is None:
        adb("shell", "am", "force-stop", "local.mytv.english")
        process.terminate()
    set_services("" if original == "null" else original)
    adb("shell", "settings", "put", "secure", "accessibility_enabled", original_enabled)
    print("Restored original accessibility services.", flush=True)
