from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


APP_DIR = Path(__file__).resolve().parents[1]
RES_DIR = APP_DIR / "src" / "main" / "res"


def blend(start: tuple[int, int, int], end: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    return tuple(round(a + (b - a) * amount) for a, b in zip(start, end))


def create_master(size: int = 1024) -> Image.Image:
    image = Image.new("RGB", (size, size))
    pixels = image.load()
    top = (105, 80, 220)
    bottom = (65, 43, 155)
    for y in range(size):
        color = blend(top, bottom, y / max(1, size - 1))
        for x in range(size):
            pixels[x, y] = color

    decoration = Image.new("RGBA", image.size, (0, 0, 0, 0))
    deco = ImageDraw.Draw(decoration)
    deco.ellipse((680, -210, 1260, 370), fill=(167, 139, 250, 85))
    deco.ellipse((-270, 650, 600, 1520), fill=(40, 24, 115, 105))
    deco.ellipse((70, 80, 420, 430), fill=(255, 255, 255, 18))
    decoration = decoration.filter(ImageFilter.GaussianBlur(18))
    image = Image.alpha_composite(image.convert("RGBA"), decoration)

    shadow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle((174, 350, 850, 774), radius=112, fill=(20, 10, 70, 105))
    shadow_draw.rounded_rectangle((332, 270, 670, 435), radius=62, fill=(20, 10, 70, 105))
    shadow = shadow.filter(ImageFilter.GaussianBlur(34))
    image = Image.alpha_composite(image, shadow)

    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((170, 324, 854, 748), radius=108, fill=(255, 255, 255, 255))
    draw.rounded_rectangle((315, 246, 694, 432), radius=70, fill=(255, 255, 255, 255))
    draw.rounded_rectangle((720, 382, 814, 448), radius=24, fill=(103, 80, 164, 255))

    coral = (255, 180, 171, 255)
    draw.polygon(((512, 755), (350, 538), (674, 538)), fill=coral)
    draw.ellipse((350, 350, 674, 674), fill=coral)
    draw.ellipse((428, 428, 596, 596), fill=(79, 55, 139, 255))
    draw.ellipse((463, 456, 513, 506), fill=(255, 255, 255, 178))
    return image


def save_assets() -> None:
    master = create_master()
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        target_dir = RES_DIR / folder
        target_dir.mkdir(parents=True, exist_ok=True)
        resized = master.resize((size, size), Image.Resampling.LANCZOS).convert("RGB")
        resized.save(target_dir / "ic_launcher.webp", "WEBP", quality=100, lossless=True, method=6)

        round_icon = resized.convert("RGBA")
        round_mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(round_mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        round_icon.putalpha(round_mask)
        round_icon.save(
            target_dir / "ic_launcher_round.webp",
            "WEBP",
            quality=100,
            lossless=True,
            method=6,
        )

    master.resize((512, 512), Image.Resampling.LANCZOS).convert("RGB").save(
        APP_DIR / "src" / "main" / "ic_launcher-playstore.png",
        "PNG",
        optimize=True,
    )
    master.save(APP_DIR / "branding" / "geo-tag-photo-icon-master.png", "PNG", optimize=True)


if __name__ == "__main__":
    save_assets()
