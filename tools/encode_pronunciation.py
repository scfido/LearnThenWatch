"""Encode the build-only WAV files as small Android-compatible MP3 assets.

Requires imageio-ffmpeg (build machine only). Audio is synthesized offline by
Export-Pronunciation.ps1; the app never contacts any speech service.
"""
import concurrent.futures
import csv
from pathlib import Path
import subprocess

import imageio_ffmpeg

APP = Path(__file__).resolve().parents[1]
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()


def encode(relative):
    destination = APP / "app/src/main/assets" / relative
    source = APP / "build/content-source/wav" / (destination.stem + ".wav")
    if destination.exists() and destination.stat().st_size > 500:
        return
    if not source.exists():
        raise RuntimeError("Missing synthesized speech: " + str(source))
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([FFMPEG, "-v", "error", "-y", "-i", str(source), "-af", "silenceremove=start_periods=1:start_duration=0.03:start_threshold=-55dB,apad=pad_dur=0.12", "-ar", "22050", "-ac", "1", "-b:a", "32k", "-write_xing", "0", "-map_metadata", "-1", str(destination)], check=True, capture_output=True)


def main():
    rows = list(csv.DictReader((APP / "app/src/main/assets/curriculum.tsv").open(encoding="utf8"), delimiter="\t"))
    files = sorted({row["audio"] for row in rows})
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        for count, _ in enumerate(executor.map(encode, files), 1):
            if count % 250 == 0:
                print(f"Encoded {count} / {len(files)}", flush=True)
    total = sum((APP / "app/src/main/assets" / name).stat().st_size for name in files)
    print(f"Encoded all {len(files)} audio files; {total / 1024 / 1024:.1f} MiB", flush=True)


if __name__ == "__main__":
    main()
