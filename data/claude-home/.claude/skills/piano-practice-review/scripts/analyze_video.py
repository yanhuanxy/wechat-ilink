#!/usr/bin/env python3
"""
Analyze a children's piano-practice video for the review skill.

What it does (all offline, no network needed):
  1. Probes the video (duration, resolution, whether it has audio).
  2. Extracts ~1 frame/second as JPGs into an output folder.
  3. Ranks frames by sharpness so the reviewer can open the clearest ones
     (best for reading the sheet music and the teacher's red markings).
  4. If there is an audio track, computes approximate, reviewer-friendly
     metrics: tempo / note density per 5s window, rhythm steadiness,
     dynamic range, register movement, and likely hesitation pauses.

Dependencies (all preinstalled in the skill's runtime): ffmpeg, ffprobe,
numpy, scipy, Pillow. No librosa required.

Usage:
    python analyze_video.py /path/to/video.mp4 [--out /path/to/frames_dir]

The script prints a human-readable report AND a machine-readable JSON block
(between BEGIN_JSON / END_JSON) so the model can reliably parse the numbers.
All findings are APPROXIMATE signal-level estimates — the script prints them
as evidence to interpret, not as ground truth.
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile


def run(cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def probe(video):
    r = run([
        "ffprobe", "-v", "error",
        "-show_entries", "format=duration",
        "-show_entries", "stream=codec_type,codec_name,width,height,sample_rate,channels",
        "-of", "json", video,
    ])
    if r.returncode != 0:
        raise RuntimeError(f"ffprobe failed: {r.stderr}")
    data = json.loads(r.stdout)
    info = {"duration": None, "width": None, "height": None, "has_audio": False}
    info["duration"] = float(data.get("format", {}).get("duration", 0) or 0)
    for s in data.get("streams", []):
        if s.get("codec_type") == "video":
            info["width"] = s.get("width")
            info["height"] = s.get("height")
        if s.get("codec_type") == "audio":
            info["has_audio"] = True
    return info


def extract_frames(video, out_dir, fps=1.0):
    os.makedirs(out_dir, exist_ok=True)
    r = run([
        "ffmpeg", "-y", "-i", video,
        "-vf", f"fps={fps}",
        os.path.join(out_dir, "frame_%03d.jpg"),
    ])
    if r.returncode != 0:
        raise RuntimeError(f"frame extraction failed: {r.stderr[-500:]}")
    frames = sorted(
        os.path.join(out_dir, f) for f in os.listdir(out_dir)
        if f.startswith("frame_") and f.endswith(".jpg")
    )
    return frames


def sharpness(path):
    """Variance-of-Laplacian sharpness score (higher = crisper). Pure numpy."""
    import numpy as np
    from PIL import Image
    img = np.asarray(Image.open(path).convert("L"), dtype=np.float64)
    k = np.array([[0, 1, 0], [1, -4, 1], [0, 1, 0]], dtype=np.float64)
    h, w = img.shape
    out = (
        -4 * img[1:-1, 1:-1]
        + img[:-2, 1:-1] + img[2:, 1:-1]
        + img[1:-1, :-2] + img[1:-1, 2:]
    )
    return float(out.var())


def analyze_audio(video):
    import numpy as np
    from scipy.io import wavfile
    from scipy.signal import stft, find_peaks
    from scipy.ndimage import median_filter

    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, "a.wav")
        r = run(["ffmpeg", "-y", "-i", video, "-ac", "1", "-ar", "22050", wav])
        if r.returncode != 0 or not os.path.exists(wav):
            return None
        sr, x = wavfile.read(wav)

    x = x.astype(np.float64)
    if x.ndim > 1:
        x = x.mean(axis=1)
    peak = np.abs(x).max()
    if peak < 1e-6:
        return {"note": "audio track is silent or near-silent"}
    x /= peak
    dur = len(x) / sr

    # --- onset detection via spectral flux ---
    nfft, hop = 2048, 512
    f, t, Z = stft(x, fs=sr, nperseg=nfft, noverlap=nfft - hop)
    mag = np.abs(Z)
    flux = np.maximum(0, np.diff(mag, axis=1)).sum(axis=0)
    flux = flux / (flux.max() + 1e-9)
    times = t[1:]
    thr = flux.mean() + 0.6 * flux.std()
    peaks, _ = find_peaks(flux, height=thr, distance=int(0.08 / (hop / sr)))
    onsets = times[peaks]

    res = {"duration_s": round(dur, 1), "n_onsets": int(len(onsets))}

    if len(onsets) >= 4:
        iois = np.diff(onsets)
        good = iois[iois < 1.0]
        if len(good):
            res["median_ioi_ms"] = int(np.median(good) * 1000)
            res["est_notes_per_min"] = int(60 / np.median(good))
            res["rhythm_cv"] = round(float(good.std() / (good.mean() + 1e-9)), 2)
        gaps = np.diff(onsets)
        pause_idx = [i for i in range(len(gaps)) if gaps[i] > 0.6]
        res["pauses_over_0_6s"] = len(pause_idx)
        res["pause_times_s"] = [round(float(onsets[i]), 1) for i in pause_idx]

    # onset density per 5s window — reveals slowing / hesitation over time
    dens = {}
    lo = 0
    while lo < dur:
        dens[f"{lo}-{lo+5}s"] = int(((onsets >= lo) & (onsets < lo + 5)).sum())
        lo += 5
    res["onset_density_per_5s"] = dens

    # --- dynamics (RMS spread in dB) ---
    fr = int(0.1 * sr)
    rms = np.array([np.sqrt(np.mean(x[i:i + fr] ** 2))
                    for i in range(0, len(x) - fr, fr)])
    rms_db = 20 * np.log10(rms / (rms.max() + 1e-9) + 1e-9)
    res["dynamics_db_std"] = round(float(rms_db.std()), 1)
    res["dynamics_db_min"] = round(float(rms_db.min()), 0)

    # --- register movement (dominant fundamental over thirds of the clip) ---
    nfft2, hop2 = 4096, 1024
    f2, t2, Z2 = stft(x, fs=sr, nperseg=nfft2, noverlap=nfft2 - hop2)
    m2 = np.abs(Z2)
    band = (f2 >= 100) & (f2 <= 2500)
    fb = f2[band]
    if band.sum() and m2.shape[1] > 7:
        dom = fb[np.argmax(m2[band, :], axis=0)]
        dom = median_filter(dom, 7)
        n = len(dom)
        res["register_hz_thirds"] = [
            int(np.median(dom[:n // 3])),
            int(np.median(dom[n // 3:2 * n // 3])),
            int(np.median(dom[2 * n // 3:])),
        ]
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("--out", default=None, help="frame output dir")
    ap.add_argument("--fps", type=float, default=1.0)
    args = ap.parse_args()

    if not os.path.exists(args.video):
        print(f"ERROR: file not found: {args.video}", file=sys.stderr)
        sys.exit(1)

    out_dir = args.out or os.path.join(
        os.path.dirname(os.path.abspath(args.video)) or ".",
        "piano_frames",
    )

    info = probe(args.video)
    frames = extract_frames(args.video, out_dir, fps=args.fps)
    scored = sorted(((sharpness(f), f) for f in frames), reverse=True)
    sharpest = [f for _, f in scored[:6]]

    audio = analyze_audio(args.video) if info["has_audio"] else None

    print("=" * 60)
    print("PIANO PRACTICE VIDEO — ANALYSIS REPORT")
    print("=" * 60)
    print(f"Duration   : {info['duration']:.1f}s")
    print(f"Resolution : {info['width']}x{info['height']}")
    print(f"Has audio  : {info['has_audio']}")
    print(f"Frames     : {len(frames)} extracted -> {out_dir}")
    print("\nSharpest frames (best for reading sheet music / red marks):")
    for f in sharpest:
        print(f"  {f}")
    if audio:
        print("\nAudio metrics (APPROXIMATE — interpret as evidence, not truth):")
        for k, v in audio.items():
            print(f"  {k}: {v}")
    else:
        print("\nNo audio track — base the review on visuals only.")

    payload = {
        "video": os.path.abspath(args.video),
        "info": info,
        "frames_dir": out_dir,
        "all_frames": frames,
        "sharpest_frames": sharpest,
        "audio": audio,
    }
    print("\nBEGIN_JSON")
    print(json.dumps(payload, ensure_ascii=False))
    print("END_JSON")


if __name__ == "__main__":
    main()
