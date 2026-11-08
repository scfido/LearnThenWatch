"""Seed the Gradle Wrapper cache, verifying its pinned SHA-256 before using a mirror."""
import hashlib
import os
from pathlib import Path
import re
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    properties = dict(line.split("=", 1) for line in
                      (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8").splitlines()
                      if "=" in line and not line.startswith("#"))
    url = properties["distributionUrl"].replace("\\:", ":")
    expected = properties["distributionSha256Sum"]
    filename = Path(urllib.parse.urlsplit(url).path).name
    if not url.startswith("https://services.gradle.org/distributions/") or not re.fullmatch(r"gradle-\d+(?:\.\d+)+-bin\.zip", filename):
        raise SystemExit("Unsupported Gradle distribution URL")
    if not re.fullmatch(r"[a-f0-9]{64}", expected):
        raise SystemExit("A pinned Gradle distribution SHA-256 is required")
    if properties["distributionBase"] != "GRADLE_USER_HOME" or properties["zipStoreBase"] != "GRADLE_USER_HOME":
        raise SystemExit("Expected the standard Gradle user-home cache")

    # Match Gradle 8.11.1 PathAssembler: the MD5/base36 value names a cache
    # directory only. Archive integrity is checked separately with SHA-256.
    number = int.from_bytes(hashlib.md5(url.encode("utf-8"), usedforsecurity=False).digest(), "big")
    alphabet, key = "0123456789abcdefghijklmnopqrstuvwxyz", ""
    while number:
        number, remainder = divmod(number, 36)
        key = alphabet[remainder] + key
    user_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))).resolve()
    directory = (user_home / properties["zipStorePath"] / filename[:-4] / key).resolve()
    if not directory.is_relative_to(user_home):
        raise SystemExit("Wrapper cache must stay within GRADLE_USER_HOME")
    directory.mkdir(parents=True, exist_ok=True)
    archive = directory / filename
    partial = directory / (filename + ".bootstrap-part")
    if archive.is_file() and sha256(archive) == expected:
        print("PASS: verified Gradle distribution is already cached", flush=True)
        return

    sources = [url, "https://mirrors.huaweicloud.com/gradle/" + filename,
               "https://mirrors.cloud.tencent.com/gradle/" + filename]
    for source in sources:
        host = urllib.parse.urlsplit(source).hostname
        try:
            with urllib.request.urlopen(source, timeout=60) as response, partial.open("wb") as stream:
                while chunk := response.read(1024 * 1024):
                    stream.write(chunk)
            if sha256(partial) != expected:
                raise ValueError("distribution SHA-256 does not match the pinned official checksum")
            partial.replace(archive)
            print("PASS: cached Gradle distribution from " + host + "; official SHA-256 verified", flush=True)
            return
        except (OSError, ValueError) as error:
            print("Gradle download failed from " + host + ": " + str(error), flush=True)
        finally:
            partial.unlink(missing_ok=True)
    raise SystemExit("No source supplied a Gradle distribution matching the pinned SHA-256")


if __name__ == "__main__":
    main()
