"""Capture five real TV screens using disposable demonstration records, then restore services."""
import argparse
from pathlib import Path
import subprocess
import threading
import time
from device_tools import RUNNER, isolated_services, require_pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    screens = ("home", "lesson", "map", "progress", "parent-settings")
    parser.add_argument("--screens", nargs="+", choices=screens, default=screens)
    args = parser.parse_args()
    destination = Path(__file__).resolve().parents[1] / "docs/screenshots"
    destination.mkdir(parents=True, exist_ok=True)
    with isolated_services(args.device):
        for screen in args.screens:
            process = subprocess.Popen(["adb", "-s", args.device, "shell", "am", "instrument", "-w",
                                        "-e", "screen", screen, RUNNER], stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
            timer = threading.Timer(60, process.kill)
            timer.start()
            output, captured = [], False
            try:
                for line in process.stdout:
                    output.append(line)
                    if "PREVIEW_READY:" in line and not captured:
                        time.sleep(1)
                        path = destination / (screen + ".png")
                        with path.open("wb") as stream:
                            subprocess.run(["adb", "-s", args.device, "exec-out", "screencap", "-p"],
                                           stdout=stream, stderr=subprocess.PIPE, check=True, timeout=20)
                        if path.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
                            raise RuntimeError("Invalid screenshot: " + str(path))
                        captured = True
                process.wait(timeout=20)
                require_pass("".join(output))
                if not captured:
                    raise RuntimeError("No preview-ready event: " + "".join(output))
                print("Captured " + screen, flush=True)
            finally:
                timer.cancel()
                if process.poll() is None:
                    process.kill()
                    process.wait()


if __name__ == "__main__":
    main()
