#!/usr/bin/env python3
"""Generate deterministic Android raster resources from the checked-in masters."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = PROJECT_ROOT / "docs" / "assets"
RESOURCE_ROOT = PROJECT_ROOT / "app" / "src" / "main" / "res"

NAVY = (7, 26, 43)
LEGACY_ICON_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def alpha_crop(image: Image.Image, padding_ratio: float = 0.04) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha_bounds = rgba.getchannel("A").getbbox()
    if alpha_bounds is None:
        raise ValueError("Source image has no visible pixels")
    cropped = rgba.crop(alpha_bounds)
    padding = max(1, round(max(cropped.size) * padding_ratio))
    return ImageOps.expand(cropped, border=padding, fill=(0, 0, 0, 0))


def contain(image: Image.Image, maximum_size: tuple[int, int]) -> Image.Image:
    output = image.copy()
    output.thumbnail(maximum_size, Image.Resampling.LANCZOS)
    return output


def generate_legacy_icons() -> None:
    master = Image.open(SOURCE_ROOT / "app-icon-master.png").convert("RGBA")
    for density, size in LEGACY_ICON_SIZES.items():
        destination = RESOURCE_ROOT / f"mipmap-{density}"
        destination.mkdir(parents=True, exist_ok=True)
        icon = master.resize((size, size), Image.Resampling.LANCZOS)
        icon.save(destination / "ic_launcher.png", optimize=True, compress_level=9)

        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(icon, (0, 0), mask)
        round_icon.save(
            destination / "ic_launcher_round.png",
            optimize=True,
            compress_level=9,
        )


def generate_tv_banner() -> None:
    logo = contain(
        alpha_crop(Image.open(SOURCE_ROOT / "logo-mark-transparent.png")),
        (118, 118),
    )
    banner = Image.new("RGB", (320, 180), NAVY)
    banner.paste(
        logo,
        ((banner.width - logo.width) // 2, (banner.height - logo.height) // 2),
        logo,
    )
    destination = RESOURCE_ROOT / "drawable-nodpi"
    destination.mkdir(parents=True, exist_ok=True)
    banner.save(destination / "tv_banner.png", optimize=True, compress_level=9)


def generate_state_artwork() -> None:
    destination = RESOURCE_ROOT / "drawable-nodpi"
    destination.mkdir(parents=True, exist_ok=True)
    sources = {
        "offline-state-transparent.png": "offline_state.webp",
        "load-error-transparent.png": "load_error_state.webp",
    }
    for source_name, target_name in sources.items():
        artwork = contain(
            alpha_crop(Image.open(SOURCE_ROOT / source_name)),
            (512, 512),
        )
        artwork.save(
            destination / target_name,
            format="WEBP",
            lossless=True,
            method=6,
            exact=True,
        )


def main() -> None:
    generate_legacy_icons()
    generate_tv_banner()
    generate_state_artwork()


if __name__ == "__main__":
    main()
