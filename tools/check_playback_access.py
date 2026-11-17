"""Actual-TV monitor checks. Requires an installed test APK and a resumable target video.

Uses adb rather than UiAutomation, which disconnects enabled accessibility services.
The signed instrumentation owns only isolated database/preferences and reports each checkpoint.
"""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import time
from device_tools import restore_accessibility, suspended_recovery

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--device", required=True)
parser.add_argument("--package", default="scfido.learnthenwatch.tv.debug", choices=["scfido.learnthenwatch.tv.debug"])
parser.add_argument("--skip-standby", action="store_true", help="Explicitly exclude firmware standby that force-stops third-party apps")
parser.add_argument("--screenshots-dir", type=Path, help="Save isolated expiry-gate focus screenshots for visual inspection")
parser.add_argument("--theme", choices=("auto", "light", "dark"), default="auto")
args = parser.parse_args()
prefix = ["adb", "-s", args.device]
monitor = args.package + "/scfido.learnthenwatch.tv.PlaybackAccessService"


def adb(*parts):
    return subprocess.run(prefix + list(parts), check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, encoding="utf-8", errors="replace", timeout=30).stdout.strip()


original = adb("shell", "settings", "get", "secure", "enabled_accessibility_services")
original_enabled = adb("shell", "settings", "get", "secure", "accessibility_enabled")
services = [s for s in original.split(":") if s and s != "null"
            and s.split("/")[0] not in ("scfido.learnthenwatch.tv", args.package)]


def set_services(value):
    # A single adb argument must survive the device shell even when the list is empty.
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services", value or "''")
    adb("shell", "settings", "put", "secure", "accessibility_enabled", "1" if value else "0")


def command(value):
    adb("shell", "am", "broadcast", "-a", args.package + ".test.MONITOR_COMMAND", "--es", "command", value)


def key(value):
    adb("shell", "input", "keyevent", value)


def pause_player():
    # Center may only reveal player controls on some videos. Check playback before
    # using a media-pause fallback so a second toggle cannot accidentally resume it.
    for action in ("KEYCODE_DPAD_CENTER", "KEYCODE_MEDIA_PAUSE", "KEYCODE_DPAD_CENTER"):
        key(action)
        for _ in range(4):
            time.sleep(.4)
            audio = adb("shell", "dumpsys", "audio")
            playing = any("AudioPlaybackConfiguration" in line and "com.xiaodianshi.tv.yst" in line
                          and "state:started" in line for line in audio.splitlines())
            if not playing:
                return
    raise RuntimeError("The target player did not pause after verified remote/media-pause actions")


def capture_focus(name):
    if not args.screenshots_dir:
        return
    time.sleep(.3)
    args.screenshots_dir.mkdir(parents=True, exist_ok=True)
    path = args.screenshots_dir / name
    # The test draws its actual overlay view; protected player surfaces can block screencap.
    command("capture_focus")
    adb("pull", "/sdcard/Android/data/" + args.package + "/cache/monitor-focus.png", str(path))
    if path.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
        raise RuntimeError("Invalid focus screenshot: " + str(path))


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
    # The learning activity may already be above a confirmed player. Relaunching
    # the player's singleTask launcher here would clear its video back stack.
    if "gate=true" in adb("shell", "dumpsys", "activity", "service", monitor):
        return
    top = adb("shell", "dumpsys", "activity", "activities")
    in_player = any("topResumedActivity=" in line and "com.xiaodianshi.tv.yst" in line
                    and any(name in line for name in ("VideoPlayActivity", "CtsActivity", "PlayerActivity"))
                    for line in top.splitlines())
    if not in_player:
        adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
            "-f", "0x10200000", "-n", "com.xiaodianshi.tv.yst/.ui.main.MainActivity")
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
    # On this TV's current home layout, DOWN enters the first video row.
    # Use the normal remote action when cloud-driven cards expose no click action.
    if any("topResumedActivity=" in line and "/.ui.main.MainActivity" in line for line in top.splitlines()):
        key("KEYCODE_DPAD_DOWN")
        key("KEYCODE_DPAD_CENTER")
        time.sleep(1)
    if resume:
        audio = adb("shell", "dumpsys", "audio")
        playing = any("AudioPlaybackConfiguration" in line and "com.xiaodianshi.tv.yst" in line
                      and "state:started" in line for line in audio.splitlines())
        if not playing and "gate=true" not in adb("shell", "dumpsys", "activity", "service", monitor):
            key("KEYCODE_DPAD_CENTER")


process = None
recording = None
passed = False
volume_ui_errors = []
recovery = suspended_recovery(args.device)
recovery.__enter__()
try:
    power(True)
    set_services(":".join(services))
    time.sleep(1)
    process = subprocess.Popen(prefix + ["shell", "am", "instrument", "-w", "-e", "screen", "monitor", "-e", "standby", "false" if args.skip_standby else "true", "-e", "theme", args.theme,
                                        args.package + ".test/scfido.learnthenwatch.tv.LearningTestRunner"],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                               encoding="utf-8", errors="replace", bufsize=1)
    for line in process.stdout:
        print(line, end="", flush=True)
        if "VOLUME_SILENT:" in line:
            window_dump = adb("shell", "dumpsys", "window")
            if any("ty=VOLUME_OVERLAY" in w and "Surface: shown=true" in w
                   for w in re.split(r"(?m)^  Window #", window_dump)[1:]):
                # Continue so instrumentation can restore the pre-test volume in finally.
                volume_ui_errors.append("Automatic volume adjustment displayed system volume UI")
        phase = re.search(r"MONITOR_([A-Z_]+):", line)
        if phase:
            event = phase.group(1)
            if event == "READY":
                set_services(":".join(services + [monitor]))
                player()
            elif event in ("PAUSE", "WARNING_PAUSE"):
                pause_player(); command("paused")
            elif event == "RESULT_CONTINUE":
                time.sleep(.4)
                capture_focus("complete-continue.png")
                key("KEYCODE_DPAD_CENTER"); command("continue")
            elif event == "RESULT_HOME":
                time.sleep(.4)
                key("KEYCODE_DPAD_LEFT")
                capture_focus("complete-home.png")
                key("KEYCODE_DPAD_CENTER"); command("result_home")
            elif event == "COUNTDOWN":
                capture_focus("countdown-strip.png")
                if args.screenshots_dir:
                    recording = subprocess.Popen(prefix + ["shell", "screenrecord", "--time-limit", "22", "--bit-rate", "3000000",
                        "/sdcard/Android/data/" + args.package + "/cache/monitor-transition.mp4"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                command("countdown")
            elif event == "FADE":
                deadline = time.monotonic() + 3.4
                snapshots = []
                while time.monotonic() < deadline:
                    window_dump = adb("shell", "dumpsys", "window")
                    if any("ty=VOLUME_OVERLAY" in w and "Surface: shown=true" in w
                           for w in re.split(r"(?m)^  Window #", window_dump)[1:]):
                        volume_ui_errors.append("Real player fade displayed system volume UI")
                    snapshots.append("no visible system volume window")
                    time.sleep(.15)
                if args.screenshots_dir:
                    (args.screenshots_dir / "silent-fade-windows.txt").write_text("\n".join(snapshots), encoding="utf-8")
            elif event in ("INITIAL_DOWN", "EXPIRED_DOWN"):
                key("KEYCODE_DPAD_DOWN")
                if event == "EXPIRED_DOWN":
                    capture_focus("expired-answer-b.png")
                command("down")
            elif event in ("INITIAL_UP", "EXPIRED_UP"):
                key("KEYCODE_DPAD_UP")
                if event == "EXPIRED_UP":
                    capture_focus("expired-answer-a.png")
                command("up")
            elif event in ("INITIAL_VOLUME", "EXPIRED_VOLUME"):
                key("KEYCODE_VOLUME_UP")
                deadline = time.monotonic() + 2
                volume_windows = []
                while not volume_windows and time.monotonic() < deadline:
                    window_dump = adb("shell", "dumpsys", "window")
                    volume_windows = [w for w in re.split(r"(?m)^  Window #", window_dump)[1:]
                                      if "ty=VOLUME_OVERLAY" in w and "Surface: shown=true" in w]
                    if not volume_windows:
                        time.sleep(.1)
                if args.screenshots_dir:
                    args.screenshots_dir.mkdir(parents=True, exist_ok=True)
                    (args.screenshots_dir / (event.lower() + "-windows.txt")).write_text(window_dump, encoding="utf-8")
                if not volume_windows:
                    raise RuntimeError("TV system volume window is not shown")
                ordered_windows = re.split(r"(?m)^  Window #", window_dump)[1:]
                gate_order = next((i for i, w in enumerate(ordered_windows) if "LearningGateActivity}" in w), None)
                volume_order = next(i for i, w in enumerate(ordered_windows) if w == volume_windows[0])
                if gate_order is None or volume_order >= gate_order:
                    raise RuntimeError("TV system volume is covered by the learning window")
                if args.screenshots_dir:
                    args.screenshots_dir.mkdir(parents=True, exist_ok=True)
                    (args.screenshots_dir / (event.lower() + "-system-window.txt")).write_text(
                        volume_windows[0], encoding="utf-8")
                capture_focus(event.lower() + ".png")
                command("volume")
            elif event == "SLEEP":
                power(False); command("asleep")
            elif event == "WAKE":
                power(True); player(resume=True); command("awake")
            elif event in ("RESUME", "WARNING_RESUME"):
                player(resume=True); command("resumed")
            elif event == "BACK":
                key("KEYCODE_BACK"); command("back")
            elif event in ("REENTER", "REENTER_AGAIN"):
                player(); command("reentered")
            elif event == "HOME":
                key("KEYCODE_HOME"); command("home")
            elif event == "REBIND":
                set_services(":".join(services)); time.sleep(1)
                set_services(":".join(services + [monitor])); time.sleep(2)
                print(adb("shell", "dumpsys", "activity", "service", monitor), flush=True)
                command("rebound")
        if "PASS:" in line:
            passed = True
    process.wait(timeout=10)
    if volume_ui_errors:
        raise RuntimeError("; ".join(sorted(set(volume_ui_errors))))
    if not passed:
        raise RuntimeError("Monitor checks failed; ensure a real video is resumable in the target app.")
finally:
    try:
        try:
            if recording is not None:
                recording.wait(timeout=30)
                remote = "/sdcard/Android/data/" + args.package + "/cache/monitor-transition.mp4"
                adb("pull", remote, str(args.screenshots_dir / "transition.mp4"))
                adb("shell", "rm", remote)
        finally:
            power(True)
            set_services(":".join(services))
            # Leave the player before restoring production monitoring.
            key("KEYCODE_HOME")
            if process is not None and process.poll() is None:
                adb("shell", "am", "force-stop", args.package)
                process.terminate()
    finally:
        try:
            restore_accessibility(args.device, original, original_enabled)
        finally:
            recovery.__exit__(None, None, None)
    print("Restored original accessibility services.", flush=True)
