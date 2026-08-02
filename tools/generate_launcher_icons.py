"""Generate Android launcher icon densities from a square raster master."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw


ICON_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("resource_root", type=Path)
    args = parser.parse_args()

    image = Image.open(args.source).convert("RGBA")
    if image.width != image.height:
        raise ValueError("Launcher icon source must be square")

    master = image.resize((1024, 1024), Image.Resampling.LANCZOS)
    master_dir = args.resource_root / "drawable-nodpi"
    master_dir.mkdir(parents=True, exist_ok=True)
    master.save(master_dir / "gdad_launcher_master.png", optimize=True)

    for density, size in ICON_SIZES.items():
        output_dir = args.resource_root / f"mipmap-{density}"
        output_dir.mkdir(parents=True, exist_ok=True)

        icon = master.resize((size, size), Image.Resampling.LANCZOS)
        icon.save(output_dir / "ic_launcher.png", optimize=True)

        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        round_icon = icon.copy()
        round_icon.putalpha(mask)
        round_icon.save(output_dir / "ic_launcher_round.png", optimize=True)


if __name__ == "__main__":
    main()
