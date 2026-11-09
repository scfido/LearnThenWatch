"""Disposable-install recovery acceptance. Refuses existing debug app installations.

Reboot and standby are opt-in; run sequentially on an awake, explicitly selected TV.
Never reads or modifies the production learning database or PIN.
"""
import argparse
from pathlib import Path
import re
import subprocess
import time
from device_tools import PACKAGE, RUNNER, SERVICE, adb, require_pass, restore_accessibility, set_services, suspended_recovery

ROOT = Path(__file__).resolve().parents[1]
LISTENER = PACKAGE + "/scfido.learnthenwatch.tv.MonitorRevivalService"


def wait_for(predicate, timeout=140):
    start = time.monotonic()
    while time.monotonic() - start < timeout:
        try:
            if predicate():
                return round(time.monotonic() - start, 1)
        except (subprocess.SubprocessError, OSError):
            pass
        time.sleep(2)
    raise RuntimeError("Timed out waiting for recovery; inspect LearnThenWatch logs locally")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    parser.add_argument("--reboot", action="store_true")
    parser.add_argument("--standby", action="store_true")
    args = parser.parse_args()
    device = args.device
    if "mWakefulness=Awake" not in adb(device, "shell", "dumpsys", "power"):
        raise RuntimeError("Wake the TV before running recovery checks")
    test_package = PACKAGE + ".test"
    if "package:" + PACKAGE in adb(device, "shell", "pm", "list", "packages", PACKAGE).splitlines():
        raise RuntimeError("Recovery acceptance requires an absent debug app; existing installs are preserved")
    test_backup = None
    if "package:" + test_package in adb(device, "shell", "pm", "list", "packages", test_package).splitlines():
        # The instrumentation APK has no learning records; preserve its previous binary too.
        import tempfile
        directory = ROOT / "build" / "recovery-check"
        directory.mkdir(parents=True, exist_ok=True)
        test_backup = Path(tempfile.mkdtemp(dir=directory)) / "original-test.apk"
        path = adb(device, "shell", "pm", "path", test_package)
        if not path.startswith("package:") or "\n" in path:
            raise RuntimeError("Cannot safely back up the existing instrumentation APK")
        adb(device, "pull", path.removeprefix("package:"), str(test_backup))
    original = adb(device, "shell", "settings", "get", "secure", "enabled_accessibility_services")
    enabled = adb(device, "shell", "settings", "get", "secure", "accessibility_enabled")
    original_listeners = adb(device, "shell", "settings", "get", "secure", "enabled_notification_listeners")
    retained = [s for s in original.split(":") if s and s != "null" and s.split("/")[0] != "scfido.learnthenwatch.tv"]
    installed = []
    listener_allowed = False

    def connected():
        return "connected=true" in adb(device, "shell", "dumpsys", "activity", "service", SERVICE)

    def focus():
        window = adb(device, "shell", "dumpsys", "window")
        lines = [line.strip() for line in window.splitlines() if "mCurrentFocus=" in line]
        return re.sub(r"Window\{\S+", "Window{ID", " ".join(lines))

    def check_background(label, expected):
        seconds = wait_for(connected)
        current = focus()
        if not current or PACKAGE in current or current != expected:
            raise AssertionError(label + ": foreground changed during recovery")
        actual = adb(device, "shell", "settings", "get", "secure", "enabled_accessibility_services").split(":")
        if not all(s in actual for s in retained):
            raise AssertionError("Unrelated accessibility service was lost")
        print(f"PASS: {label}; observed connected after {seconds}s (includes ADB wait); foreground and unrelated services preserved", flush=True)

    try:
        # Explicitly install only LearnThenWatch debug/test APKs from this checkout.
        for package, path in ((PACKAGE, "app/build/outputs/apk/debug/app-debug.apk"),
                              (test_package, "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")):
            if package != test_package or test_backup is None:
                installed.append(package)
            output = adb(device, "install", "-r", str(ROOT / path), timeout=90)
            if "Success" not in output:
                raise RuntimeError("Disposable APK installation failed")
        require_pass(adb(device, "shell", "am", "instrument", "-w", "-e", "recoveryFixture", "true", RUNNER, timeout=120))
        # Pause any production recovery only while testing. Its previous grant is restored.
        with suspended_recovery(device):
            set_services(device, ":".join(retained))
            adb(device, "shell", "input", "keyevent", "KEYCODE_HOME")
            time.sleep(2)
            home = focus()
            if not home:
                raise RuntimeError("Could not identify the TV home foreground")
            adb(device, "shell", "pm", "grant", PACKAGE, "android.permission.WRITE_SECURE_SETTINGS")
            listener_allowed = True
            adb(device, "shell", "cmd", "notification", "allow_listener", LISTENER)
            check_background("listener binding restores authorization", home)
            # Firmwares can leave the component enabled but unbound after an app update.
            output = adb(device, "install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"), timeout=90)
            if "Success" not in output:
                raise RuntimeError("Disposable APK update failed")
            check_background("package update", home)
            if args.reboot:
                adb(device, "reboot")
                time.sleep(5)
                def booted():
                    subprocess.run(["adb", "connect", device], capture_output=True, timeout=10, check=False)
                    return adb(device, "shell", "getprop", "sys.boot_completed") == "1"
                wait_for(booted, 180)
                wait_for(lambda: bool(focus()), 60)
                check_background("full reboot", home)
            if args.standby:
                # The firmware may ignore KEYCODE_SLEEP; verify before using a power toggle.
                adb(device, "shell", "input", "keyevent", "KEYCODE_SLEEP")
                time.sleep(2)
                if "mWakefulness=Awake" in adb(device, "shell", "dumpsys", "power"):
                    adb(device, "shell", "input", "keyevent", "KEYCODE_POWER")
                wait_for(lambda: "mWakefulness=Awake" not in adb(device, "shell", "dumpsys", "power"), 20)
                time.sleep(10)
                adb(device, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
                time.sleep(2)
                if "mWakefulness=Awake" not in adb(device, "shell", "dumpsys", "power"):
                    adb(device, "shell", "input", "keyevent", "KEYCODE_POWER")
                wait_for(lambda: "mWakefulness=Awake" in adb(device, "shell", "dumpsys", "power"), 30)
                check_background("standby wake", home)
    finally:
        failures = []
        if listener_allowed:
            try:
                adb(device, "shell", "pm", "revoke", PACKAGE, "android.permission.WRITE_SECURE_SETTINGS")
                adb(device, "shell", "cmd", "notification", "disallow_listener", LISTENER)
                # Preserve a previously absent key, without overwriting concurrent additions.
                if original_listeners == "null" and not adb(device, "shell", "settings", "get", "secure", "enabled_notification_listeners"):
                    adb(device, "shell", "settings", "delete", "secure", "enabled_notification_listeners")
            except Exception as error:
                failures.append(str(error))
        # Remove only the disposable packages created by this invocation, then restore settings.
        for package in reversed(installed):
            try:
                if "Success" not in adb(device, "uninstall", package, timeout=60):
                    raise RuntimeError("Could not remove disposable package " + package)
            except Exception as error:
                failures.append(str(error))
        if test_backup is not None:
            try:
                if "Success" not in adb(device, "install", "-r", str(test_backup), timeout=90):
                    raise RuntimeError("Could not restore original instrumentation APK")
            except Exception as error:
                failures.append(str(error))
        try:
            restore_accessibility(device, original, enabled)
        except Exception as error:
            failures.append(str(error))
        if failures:
            raise RuntimeError("Recovery check cleanup incomplete: " + "; ".join(failures))
        print("Restored original accessibility settings and removed disposable installations.", flush=True)


if __name__ == "__main__":
    main()
