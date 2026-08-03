from io import BytesIO
from pathlib import Path
from urllib.request import Request, urlopen

from PIL import Image, ImageEnhance


ROOT_DIR = Path(__file__).resolve().parents[2]
OUTPUT_DIR = ROOT_DIR / "app" / "src" / "main" / "res" / "drawable-nodpi"
SOURCE_URL = "https://erode-sengunthar.ac.in/wp-content/uploads/2019/03/cse-block.jpg"
OUTPUT = OUTPUT_DIR / "template_preview_cse_block.webp"


def generate_previews() -> None:
    request = Request(SOURCE_URL, headers={"User-Agent": "Geo-Tag-Photo-branding/1.0"})
    with urlopen(request, timeout=30) as response:
        source = Image.open(BytesIO(response.read())).convert("RGB")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    source = ImageEnhance.Contrast(source).enhance(1.03)
    source.save(OUTPUT, "WEBP", quality=82, method=6)


if __name__ == "__main__":
    generate_previews()
