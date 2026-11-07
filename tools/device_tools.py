"""Shared ADB helpers for isolated debug checks; never clear production data."""
from contextlib import contextmanager
import subprocess

PACKAGE = "dev.learnthenwatch.tv.debug"
RUNNER = PACKAGE + ".test/dev.learnthenwatch.tv.LearningTestRunner"
SERVICE = PACKAGE + "/dev.learnthenwatch.tv.PlaybackAccessService"
CONFLICTING_PACKAGES = {PACKAGE, "dev.learnthenwatch.tv", "local.mytv.english"}


def adb(device, *parts, timeout=30):
    return subprocess.run(["adb", "-s", device, *parts], check=True, capture_output=True,
                          encoding="utf-8", errors="replace", timeout=timeout).stdout.strip()


def set_services(device, value):
    adb(device, "shell", "settings", "put", "secure", "enabled_accessibility_services", value or "''")
    adb(device, "shell", "settings", "put", "secure", "accessibility_enabled", "1" if value else "0")


def restore_accessibility(device, original, enabled):
    failures = []
    for name, value in (("enabled_accessibility_services", original), ("accessibility_enabled", enabled)):
        try:
            if value == "null":
                adb(device, "shell", "settings", "delete", "secure", name)
            else:
                adb(device, "shell", "settings", "put", "secure", name, value or "''")
        except Exception as error:
            failures.append(str(error))
    if failures:
        raise RuntimeError("Could not fully restore accessibility settings: " + "; ".join(failures))


@contextmanager
def isolated_services(device):
    if "mWakefulness=Awake" not in adb(device, "shell", "dumpsys", "power"):
        raise RuntimeError("Wake the TV before UI checks or screenshot capture.")
    original = adb(device, "shell", "settings", "get", "secure", "enabled_accessibility_services")
    enabled = adb(device, "shell", "settings", "get", "secure", "accessibility_enabled")
    retained = [s for s in original.split(":") if s and s != "null" and s.split("/")[0] not in CONFLICTING_PACKAGES]
    try:
        set_services(device, ":".join(retained))
        yield
    finally:
        # Leave the player before restoring production monitoring.
        try:
            adb(device, "shell", "input", "keyevent", "KEYCODE_HOME")
        finally:
            restore_accessibility(device, original, enabled)


def require_pass(output):
    if "PASS:" not in output or any(s in output for s in ("FAIL after", "INSTRUMENTATION_FAILED", "Process crashed")):
        raise RuntimeError("Instrumentation did not pass:\n" + output)
