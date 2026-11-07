"""Check a release tag against version/changelog and collect the signed APK and checksums."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    properties = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
                      if "=" in line and not line.startswith("#"))
    version = properties["appVersionName"]
    if not re.fullmatch(r"\d+\.\d+\.\d+", version) or args.tag != "v" + version:
        raise SystemExit("Release tag must equal v" + version)
    changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    section = re.search(r"^## \[" + re.escape(version) + r"\][^\n]*\n(.*?)(?=^## |\Z)", changelog, re.M | re.S)
    if not section or not section.group(1).strip():
        raise SystemExit("Missing CHANGELOG entry for " + version)
    if args.check_only:
        print("PASS: release version and changelog match " + args.tag)
        return
    output = ROOT / "build/release"
    output.mkdir(parents=True, exist_ok=True)
    apk = output / ("learn-then-watch-" + version + ".apk")
    shutil.copyfile(ROOT / "app/build/outputs/apk/release/app-release.apk", apk)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (output / "SHA256SUMS").write_text(digest + "  " + apk.name + "\n", encoding="utf-8")
    (output / "release-notes.md").write_text(section.group(1).strip() + "\n", encoding="utf-8")
    print("Prepared " + apk.name + " and SHA256SUMS")


if __name__ == "__main__":
    main()
