"""Run installed debug instrumentation with isolated DB/preferences and restore services."""
import argparse
from device_tools import RUNNER, adb, isolated_services, require_pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    parser.add_argument("--suite", choices=("all", "sqlite", "ui", "remote"), default="all")
    args = parser.parse_args()
    suites = ("sqlite", "ui", "remote") if args.suite == "all" else (args.suite,)
    with isolated_services(args.device):
        for suite in suites:
            parameters = [] if suite == "sqlite" else ["-e", suite, "true"]
            output = adb(args.device, "shell", "am", "instrument", "-w", *parameters, RUNNER, timeout=240)
            print(output, flush=True)
            require_pass(output)


if __name__ == "__main__":
    main()
