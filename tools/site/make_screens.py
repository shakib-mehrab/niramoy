"""
Turns raw phone screenshots (1080x2400 PNG from `adb exec-out screencap -p`) into website images:
crops the status bar and the gesture bar, scales to 360 px wide and saves WebP into docs/assets/screens/.

Usage:  python tools/site/make_screens.py <png> <name> [<png> <name> ...]
        e.g. python tools/site/make_screens.py home.png home health.png health
Needs Pillow (pip install pillow).
"""
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "docs", "assets", "screens")
TARGET_W = 360
RATIO = 19.5 / 9  # same frame as the site's device mockup


def convert(src: str, name: str) -> str:
    im = Image.open(src).convert("RGB")
    w, h = im.size
    top = round(h * 0.040)      # status bar
    bottom = round(h * 0.022)   # gesture handle
    # Hide the real status bar (clock, notification icons) behind the app's own top colour, keeping the
    # space so the mockup's camera notch sits over an empty strip instead of the header.
    for x0 in range(0, w, 8):
        c = im.getpixel((min(x0, w - 1), top + 4))
        im.paste(c, (x0, 0, min(x0 + 8, w), top))
    im = im.crop((0, 0, w, h - bottom))
    # Trim the bottom (never the top) so every shot has the device's exact aspect ratio.
    want_h = round(im.width * RATIO)
    if im.height > want_h:
        im = im.crop((0, 0, im.width, want_h))
    im = im.resize((TARGET_W, round(TARGET_W * im.height / im.width)), Image.LANCZOS)
    os.makedirs(OUT, exist_ok=True)
    dest = os.path.join(OUT, name + ".webp")
    im.save(dest, "WEBP", quality=86, method=6)
    return dest


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args or len(args) % 2:
        sys.exit(__doc__)
    for src, name in zip(args[::2], args[1::2]):
        d = convert(src, name)
        print(f"{name}: {os.path.getsize(d) // 1024} KB")
