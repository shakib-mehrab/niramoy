"""
Generates NIRAMOY reminder tones at build time with free, open-source TTS
(Meta MMS-TTS, VITS models: facebook/mms-tts-ben for Bangla, facebook/mms-tts-eng for English).

Output: app/src/main/res/raw/
    bn_<slot>_<timing>.wav, bn_generic.wav   (slot = morning|noon|night, timing = before|after)
    en_<slot>_<timing>.wav, en_generic.wav
    beep.wav                                 (plain two-tone beep, made without any model)

Usage (from the project root):
    python -m venv tools/tts/.venv
    tools/tts/.venv/Scripts/pip install -r tools/tts/requirements.txt      (Windows)
    tools/tts/.venv/Scripts/python tools/tts/generate_tones.py
    # add --beep-only to just make beep.wav (no downloads needed)

Licence note: MMS-TTS weights are CC-BY-NC-4.0 (fine for this free, personal, non-commercial app).
"""
import argparse
import math
import os
import struct
import sys
import wave

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "raw")

# Simple, spoken, family-member style phrases. Keep in sync with "phrase_*" in StringsBn/StringsEn.
PHRASES = {
    "bn": {
        "morning_before": "সকালের খাবারের আগে ওষুধ খান",
        "morning_after": "সকালের খাবারের পরে ওষুধ খান",
        "noon_before": "দুপুরের খাবারের আগে ওষুধ খান",
        "noon_after": "দুপুরের খাবারের পরে ওষুধ খান",
        "night_before": "রাতের খাবারের আগে ওষুধ খান",
        "night_after": "রাতের খাবারের পরে ওষুধ খান",
        "generic": "ওষুধ খাওয়ার সময় হয়েছে",
    },
    "en": {
        "morning_before": "Please take your medicine before breakfast.",
        "morning_after": "Please take your medicine after breakfast.",
        "noon_before": "Please take your medicine before lunch.",
        "noon_after": "Please take your medicine after lunch.",
        "night_before": "Please take your medicine before dinner.",
        "night_after": "Please take your medicine after dinner.",
        "generic": "It is time to take your medicine.",
    },
}
MODELS = {"bn": "facebook/mms-tts-ben", "en": "facebook/mms-tts-eng"}


def write_wav(path, samples, rate):
    """samples: floats in [-1, 1]."""
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))


def make_beep(path, rate=16000):
    """Three soft two-tone beeps (880 Hz / 660 Hz) with short fades, ~1.6 s."""
    out = []
    for _ in range(3):
        for freq in (880, 660):
            n = int(rate * 0.18)
            for i in range(n):
                fade = min(1.0, i / (rate * 0.01), (n - i) / (rate * 0.01))
                out.append(0.6 * fade * math.sin(2 * math.pi * freq * i / rate))
        out.extend([0.0] * int(rate * 0.16))
    write_wav(path, out, rate)


def synth_all(langs):
    import numpy as np
    import torch
    from transformers import AutoTokenizer, VitsModel

    torch.manual_seed(7)  # VITS is stochastic; a fixed seed makes builds repeatable
    for lang in langs:
        model = VitsModel.from_pretrained(MODELS[lang])
        tok = AutoTokenizer.from_pretrained(MODELS[lang])
        model.speaking_rate = 0.85  # a little slower: easier for elderly listeners
        rate = model.config.sampling_rate
        for key, text in PHRASES[lang].items():
            inputs = tok(text, return_tensors="pt")
            with torch.no_grad():
                wav = model(**inputs).waveform[0].numpy()
            peak = float(np.max(np.abs(wav))) or 1.0
            wav = 0.9 * wav / peak                                  # normalise loudness
            pad = np.zeros(int(rate * 0.25), dtype=wav.dtype)       # breathing room
            wav = np.concatenate([pad, wav, pad])
            name = f"{lang}_{key}.wav"
            write_wav(os.path.join(OUT, name), wav.tolist(), rate)
            print("wrote", name, f"{len(wav) / rate:.1f}s")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--beep-only", action="store_true")
    ap.add_argument("--langs", default="bn,en")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    make_beep(os.path.join(OUT, "beep.wav"))
    print("wrote beep.wav")
    if not args.beep_only:
        try:
            synth_all([l for l in args.langs.split(",") if l in MODELS])
        except ImportError as e:
            sys.exit(f"Missing package ({e}). Run: pip install -r tools/tts/requirements.txt")


if __name__ == "__main__":
    main()
