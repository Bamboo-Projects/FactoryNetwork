"""Regenerate the terminal/drive assets without touching other authored assets.

Requires Pillow. Run from any directory:
    python tools/device_assets.py --preview
    python tools/device_assets.py --check

The preview uses Minecraft model boxes and real textures, not a concept image.
Lighting, live block-entity overlays and occlusion still need an in-game check.
"""
import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw

import assets
import modellblick
import textures


ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/factorynetwork"
TEXTURES = {
    "block/terminal_front": textures.terminal_front,
    "block/terminal_top": textures.terminal_top,
    "block/terminal_keyboard": textures.terminal_keyboard,
    "block/drive_front": textures.drive_front,
    "misc/drive_bays": textures.drive_bays,
}


def check():
    for name, factory in TEXTURES.items():
        path = ASSETS / "textures" / (name + ".png")
        actual = Image.open(path).convert("RGBA")
        expected = factory().convert("RGBA")
        if actual.size != expected.size or actual.tobytes() != expected.tobytes():
            raise ValueError(f"Stale generated texture: {path}")

    terminal = json.loads((ASSETS / "models/block/terminal.json").read_text())
    generated = assets.machine_elements(assets.terminal_boxes())
    if terminal["elements"] != generated:
        raise ValueError("Terminal geometry/UVs differ from the generator")

    for model in ("terminal", "controller", "drive"):
        data = json.loads((ASSETS / "models/block" / (model + ".json")).read_text())
        for texture in data["textures"].values():
            if not texture.startswith("factorynetwork:"):
                continue
            path = ASSETS / "textures" / (texture.split(":", 1)[1] + ".png")
            if not path.is_file():
                raise ValueError(f"Missing texture: {path}")
        for element in data["elements"]:
            for face in element["faces"].values():
                if face["texture"][1:] not in data["textures"]:
                    raise ValueError(f"Unbound texture in {model}: {face}")
                if any(v < 0 or v > 16 for v in face.get("uv", [])):
                    raise ValueError(f"Out-of-bounds UV in {model}: {face}")
    print("Device assets match their generators; model textures and UVs resolve.")


def preview():
    size, gap = 360, 20
    models = (("CONTROLLER", "controller"), ("CODE TERMINAL", "terminal"),
              ("STORAGE DRIVE", "drive"))
    page = Image.new("RGB", (3 * size + 4 * gap, 640), (18, 23, 21))
    pen = ImageDraw.Draw(page)
    pen.text((gap, 18), "FACTORY NETWORK / DEVICE ASSETS", fill=(192, 218, 200))
    pen.text((gap, 40), "Model preview - static textures, no live world lighting",
             fill=(129, 150, 137))
    for i, (label, model) in enumerate(models):
        x = gap + i * (size + gap)
        page.paste(modellblick.render(["block/" + model], size, scale=size / 44), (x, 65))
        pen.text((x, 438), label, fill=(192, 218, 200))

    # The actual atlas is stretched with nearest-neighbour sampling, so all
    # eight states can be reviewed without inventing an in-game screenshot.
    strip = Image.open(ASSETS / "textures/misc/drive_bays.png").convert("RGB")
    tile_w = strip.width // 8
    labels = ("EMPTY", "1K", "4K", "16K", "64K", "FLUID", "ENERGY", "CHEMICAL")
    pen.text((gap, 490), "DRIVE BAY OVERLAY / INSERTED CELL TYPE", fill=(192, 218, 200))
    for i, label in enumerate(labels):
        x = gap + i * 142
        tile = strip.crop((i * tile_w, 0, (i + 1) * tile_w, strip.height))
        page.paste(tile.resize((120, 36), Image.Resampling.NEAREST), (x, 520))
        pen.text((x, 572), label, fill=(148, 169, 155))
    output = ROOT / "docs/previews/devices.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    page.save(output)
    print(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Validate without writing assets")
    parser.add_argument("--preview", action="store_true", help="Render a review sheet")
    args = parser.parse_args()
    if not args.check:
        for name, factory in TEXTURES.items():
            folder, filename = name.split("/")
            textures.save(factory(), folder, filename)
        assets.terminal_model()
    check()
    if args.preview:
        preview()


if __name__ == "__main__":
    main()
