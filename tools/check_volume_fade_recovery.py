"""Kill only the installed debug process during a fade; verify the alarm restores real volume."""
import argparse
import re
import time
from device_tools import adb, isolated_services, PACKAGE


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    args = parser.parse_args()

    def volume():
        result = adb(args.device, "shell", "cmd", "media_session", "volume", "--stream", "3", "--get")
        match = re.search(r"volume is (\d+)", result)
        if not match:
            raise RuntimeError("Could not read media volume: " + result)
        return int(match.group(1))

    original = volume()
    stream = adb(args.device, "shell", "dumpsys", "audio").split("- STREAM_MUSIC:", 1)[1].split("- STREAM_", 1)[0]
    if "Muted: true" in stream or original < 2:
        raise RuntimeError("Process-death fade test requires unmuted media volume >= 2")
    probe = PACKAGE + "/scfido.learnthenwatch.tv.VolumeFadeProbeActivity"
    with isolated_services(args.device):
        try:
            adb(args.device, "shell", "am", "start", "-n", probe, "--ez", "fade", "true")
            faded = volume()
            ready_deadline = time.monotonic() + 2
            while faded == original and time.monotonic() < ready_deadline:
                time.sleep(.1)
                faded = volume()
            if faded <= 0 or faded >= original:
                raise RuntimeError("Debug fade did not reduce the actual stream")
            pid = adb(args.device, "shell", "pidof", PACKAGE).strip()
            if not re.fullmatch(r"\d+", pid):
                raise RuntimeError("Expected one debug process")
            adb(args.device, "shell", "run-as", PACKAGE, "kill", "-9", pid)
            started = time.monotonic()
            while volume() != original:
                if time.monotonic() - started > 10:
                    raise RuntimeError("Volume was not restored after debug process death")
                time.sleep(.2)
            restarted = adb(args.device, "shell", "pidof", PACKAGE).strip()
            if not restarted or restarted == pid:
                raise RuntimeError("Recovery did not start a replacement debug process")
            print("PASS: actual debug process death; alarm restarted app and restored media volume in %.1fs" %
                  (time.monotonic() - started), flush=True)
        finally:
            try:
                adb(args.device, "shell", "am", "start", "-n", probe, "--ez", "fade", "false")
            finally:
                if volume() != original:
                    adb(args.device, "shell", "cmd", "media_session", "volume", "--stream", "3", "--set", str(original))
                adb(args.device, "shell", "input", "keyevent", "KEYCODE_HOME")


if __name__ == "__main__":
    main()
