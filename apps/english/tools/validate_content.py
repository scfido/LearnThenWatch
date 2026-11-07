"""Validate all shipped course records, source mappings, and offline audio.

The optional --decode-audio also decodes every MP3 with ffmpeg; this catches
truncation and format errors but does not claim a human pronunciation review.
"""
import argparse
import concurrent.futures
import csv
import json
from pathlib import Path
import re
import subprocess

APP = Path(__file__).resolve().parents[1]
ASSETS = APP / "assets"


def table(path):
    with path.open(encoding="utf8", newline="") as f:
        return list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--decode-audio", action="store_true")
    args = parser.parse_args()
    rows = table(ASSETS / "curriculum.tsv")
    assert rows and {r["stage"] for r in rows} == {"KET", "PET"}
    ids, audio, natural = set(), set(), set()
    for row in rows:
        assert len(row) == 8 and None not in row, row
        assert all(value and "\n" not in value and "\t" not in value for value in row.values()), row
        assert row["id"] not in ids and re.fullmatch("[a-z0-9_]+", row["id"]), row
        ids.add(row["id"])
        assert re.search(r"[\u4e00-\u9fff]", row["meaning"]), row
        assert row["audio"].startswith("audio/") and ".." not in row["audio"], row
        path = ASSETS / row["audio"]
        assert path.is_file() and path.stat().st_size > 500, row["audio"]
        data = path.read_bytes()
        assert data[:3] == b"ID3" or data[:2] in (b"\xff\xf3", b"\xff\xfb", b"\xff\xf2", b"\xff\xfa"), row["audio"]
        audio.add(path)
        if row["example"].startswith("Meaning: "):
            assert row["example"] == "Meaning: " + row["word"], row
            assert row["translation"].startswith("词义提示："), row
        else:
            assert re.search(r"(?<![A-Za-z])" + re.escape(row["word"]) + r"(?![A-Za-z])", row["example"], re.I), row
            natural.add(row["id"])
    mapping = table(ASSETS / "licenses/reference-coverage.tsv")
    assert all(r["item_id"] in ids for r in mapping), "Source mapping has absent targets"
    missing = table(ASSETS / "licenses/uncovered.tsv")
    assert not missing, missing
    attribution = table(ASSETS / "licenses/sentence-attribution.tsv")
    assert all(r["item_id"] in natural and "CC-BY 2.0 (France) Attribution: tatoeba.org #" in r["attribution"] for r in attribution)
    report = json.loads((ASSETS / "licenses/coverage.json").read_text(encoding="utf8"))
    for stage in ("KET", "PET"):
        assert sum(r["stage"] == stage for r in rows) == report["course_items"][stage]
        assert sum(r["stage"] == stage for r in mapping) == report["reference_headwords"][stage]
        assert sum(r["stage"] == stage and r["id"] in natural for r in rows) == report["natural_examples"][stage]
    assert len(audio) == report["audio_files_expected"]
    if args.decode_audio:
        import imageio_ffmpeg
        executable = imageio_ffmpeg.get_ffmpeg_exe()
        def decode(path):
            subprocess.run([executable, "-v", "error", "-i", str(path), "-f", "null", "-"], check=True, capture_output=True)
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            for count, _ in enumerate(pool.map(decode, sorted(audio)), 1):
                if count % 500 == 0:
                    print(f"Decoded {count} / {len(audio)}", flush=True)
    print(f"PASS: {len(rows)} course items, {len(mapping)} source mappings, {len(natural)} natural examples, {len(audio)} offline MP3 files", flush=True)


if __name__ == "__main__":
    main()
