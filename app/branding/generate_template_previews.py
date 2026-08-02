from pathlib import Path

from PIL import Image, ImageEnhance


ROOT_DIR = Path(__file__).resolve().parents[2]
SOURCE = ROOT_DIR / "GeoTagImage" / "image.png"
OUTPUT_DIR = ROOT_DIR / "app" / "src" / "main" / "res" / "drawable-nodpi"


def generate_previews() -> None:
    source = Image.open(SOURCE).convert("RGB")
    width, _ = source.size
    crop_height = width // 2
    crop_tops = (30, 170, 300, 410)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    for index, top in enumerate(crop_tops, start=1):
        crop = source.crop((0, top, width, top + crop_height))
        crop = crop.resize((960, 480), Image.Resampling.LANCZOS)
        crop = ImageEnhance.Contrast(crop).enhance(1.04)
        crop.save(
            OUTPUT_DIR / f"template_preview_{index}.webp",
            "WEBP",
            quality=76,
            method=6,
        )


if __name__ == "__main__":
    generate_previews()
