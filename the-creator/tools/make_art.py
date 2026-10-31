#!/usr/bin/env python3
"""Build The Creator's texture, pack icon, item icon, and geometry.

UV rectangles are the source of truth for both the PNG and the geo file.
"""

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RP = ROOT / "resource_pack"
BP = ROOT / "behavior_pack"
TEX_W = 64
TEX_H = 64

SKIN = (214, 206, 194, 255)
SKIN_SHADOW = (168, 158, 148, 255)
COAT = (16, 16, 20, 255)
COAT_EDGE = (32, 32, 38, 255)
SEAM = (110, 28, 36, 255)
STAIN = (84, 26, 32, 255)
EYE = (6, 6, 8, 255)
HAIR = (22, 22, 26, 255)


CUBES = [
    {
        "bone": "legRight",
        "parent": "root",
        "pivot": [-2, 18, 0],
        "origin": [-3.5, 0, -1.5],
        "size": [2, 18, 3],
        "paint": "coat",
        "uv": {
            "north": [16, 46, 2, 18],
            "south": [18, 46, 2, 18],
            "east": [20, 46, 3, 18],
            "west": [23, 46, 3, 18],
            "up": [16, 43, 2, 3],
            "down": [18, 43, 2, 3],
        },
    },
    {
        "bone": "legLeft",
        "parent": "root",
        "pivot": [2, 18, 0],
        "origin": [1.5, 0, -1.5],
        "size": [2, 18, 3],
        "paint": "coat",
        "uv": {
            "north": [28, 46, 2, 18],
            "south": [30, 46, 2, 18],
            "east": [32, 46, 3, 18],
            "west": [35, 46, 3, 18],
            "up": [28, 43, 2, 3],
            "down": [30, 43, 2, 3],
        },
    },
    {
        "bone": "body",
        "parent": "root",
        "pivot": [0, 33, 0],
        "origin": [-4, 17, -2],
        "size": [8, 16, 4],
        "paint": "body",
        "uv": {
            "north": [40, 20, 8, 16],
            "south": [48, 20, 8, 16],
            "east": [56, 20, 4, 16],
            "west": [60, 20, 4, 16],
            "up": [40, 16, 8, 4],
            "down": [48, 16, 8, 4],
        },
    },
    {
        "bone": "head",
        "parent": "body",
        "pivot": [0, 33, 0],
        "origin": [-3.5, 32, -3.5],
        "size": [7, 11, 7],
        "paint": "head",
        "uv": {
            "north": [0, 20, 7, 11],
            "south": [7, 20, 7, 11],
            "east": [14, 20, 7, 11],
            "west": [21, 20, 7, 11],
            "up": [0, 8, 7, 7],
            "down": [8, 8, 7, 7],
        },
    },
    {
        "bone": "armRight",
        "parent": "body",
        "pivot": [-4, 30, 0],
        "origin": [-6, 8, -1],
        "size": [2, 22, 2],
        "paint": "arm",
        "uv": {
            "north": [0, 42, 2, 22],
            "south": [2, 42, 2, 22],
            "east": [4, 42, 2, 22],
            "west": [6, 42, 2, 22],
            "up": [0, 40, 2, 2],
            "down": [2, 40, 2, 2],
        },
    },
    {
        "bone": "armLeft",
        "parent": "body",
        "pivot": [4, 30, 0],
        "origin": [4, 8, -1],
        "size": [2, 22, 2],
        "paint": "arm",
        "uv": {
            "north": [8, 42, 2, 22],
            "south": [10, 42, 2, 22],
            "east": [12, 42, 2, 22],
            "west": [14, 42, 2, 22],
            "up": [8, 40, 2, 2],
            "down": [10, 40, 2, 2],
        },
    },
]


def rects():
    found = []
    for cube in CUBES:
        for face, (u, v, w, h) in cube["uv"].items():
            found.append((cube["bone"], face, u, v, w, h))
    return found


def assert_uvs():
    used = []
    for bone, face, u, v, w, h in rects():
        if u < 0 or v < 0 or u + w > TEX_W or v + h > TEX_H:
            raise SystemExit(f"UV out of bounds: {bone} {face}")
        box = (u, v, u + w, v + h)
        for other in used:
            if overlaps(box, other[0]):
                raise SystemExit(f"UV overlap: {bone} {face} and {other[1]}")
        used.append((box, f"{bone}.{face}"))
    size = {name: cube["size"] for cube in CUBES for name in [cube["bone"]]}
    expect = {
        "north": lambda s: (s[0], s[1]),
        "south": lambda s: (s[0], s[1]),
        "east": lambda s: (s[2], s[1]),
        "west": lambda s: (s[2], s[1]),
        "up": lambda s: (s[0], s[2]),
        "down": lambda s: (s[0], s[2]),
    }
    for cube in CUBES:
        for face, (u, v, w, h) in cube["uv"].items():
            ew, eh = expect[face](cube["size"])
            if (w, h) != (ew, eh):
                raise SystemExit(f"{cube['bone']} {face} uv {w}x{h} != face {ew}x{eh}")


def overlaps(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def fill(image, u, v, w, h, color):
    draw = ImageDraw.Draw(image)
    draw.rectangle([u, v, u + w - 1, v + h - 1], fill=color)


def face_on(image, u, v, w, h):
    fill(image, u, v, w, h, SKIN)
    # Hairline along the top of every side of the head.
    fill(image, u, v, w, 2, HAIR)
    eye_y = v + 4
    fill(image, u + 1, eye_y, 2, 2, EYE)
    fill(image, u + w - 3, eye_y, 2, 2, EYE)
    fill(image, u + w // 2, v + 1, 1, 6, (48, 42, 40, 255))


def paint(image):
    for cube in CUBES:
        kind = cube["paint"]
        for face, (u, v, w, h) in cube["uv"].items():
            if kind == "head" and face in ("north", "south", "east", "west"):
                face_on(image, u, v, w, h)
            elif kind == "head":
                fill(image, u, v, w, h, HAIR if face == "up" else SKIN_SHADOW)
            elif kind == "arm":
                fill(image, u, v, w, h, SKIN if face not in ("up", "down") else SKIN_SHADOW)
                if face in ("north", "south", "east", "west"):
                    fill(image, u, v + h - 4, w, 4, STAIN)
            elif kind == "body":
                fill(image, u, v, w, h, COAT)
                if face == "north":
                    fill(image, u + w // 2, v + 1, 1, h - 2, SEAM)
                    fill(image, u, v, w, 1, COAT_EDGE)
                if face in ("east", "west", "south"):
                    fill(image, u, v, w, 1, COAT_EDGE)
            else:
                fill(image, u, v, w, h, COAT if face != "up" else COAT_EDGE)


def geometry():
    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    for cube in CUBES:
        bones.append(
            {
                "name": cube["bone"],
                "parent": cube["parent"],
                "pivot": cube["pivot"],
                "cubes": [
                    {
                        "origin": cube["origin"],
                        "size": cube["size"],
                        "uv": {
                            face: {"uv": [u, v], "uv_size": [w, h]}
                            for face, (u, v, w, h) in cube["uv"].items()
                        },
                    }
                ],
            }
        )
    return {
        "format_version": "1.16.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.the_creator",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 3,
                    "visible_bounds_height": 4,
                    "visible_bounds_offset": [0, 1.5, 0],
                },
                "bones": bones,
            }
        ],
    }


def pack_icon():
    image = Image.new("RGBA", (256, 256), (8, 8, 10, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle([118, 78, 138, 168], fill=COAT)
    draw.rectangle([108, 96, 118, 188], fill=SKIN)
    draw.rectangle([138, 96, 148, 188], fill=SKIN)
    draw.rectangle([112, 168, 124, 236], fill=COAT)
    draw.rectangle([132, 168, 144, 236], fill=COAT)
    draw.rectangle([104, 28, 152, 86], fill=SKIN)
    draw.rectangle([104, 28, 152, 40], fill=HAIR)
    draw.rectangle([116, 50, 126, 60], fill=EYE)
    draw.rectangle([134, 50, 144, 60], fill=EYE)
    draw.rectangle([126, 36, 130, 70], fill=(48, 42, 40, 255))
    draw.rectangle([126, 90, 130, 150], fill=SEAM)
    return image


def item_icon():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse([1, 1, 14, 14], fill=(28, 28, 32, 255))
    draw.ellipse([3, 3, 12, 12], fill=SKIN)
    draw.point((6, 7), fill=EYE)
    draw.point((9, 7), fill=EYE)
    draw.point((7, 11), fill=SEAM)
    draw.point((8, 11), fill=SEAM)
    return image


def preview(texture, icon):
    scale = 8
    canvas = Image.new("RGBA", (520, 460), (10, 10, 12, 255))
    # Front orthographic: model X right, model Y up. North-face art on each cube.
    order = ["legRight", "legLeft", "armRight", "armLeft", "body", "head"]
    by_bone = {cube["bone"]: cube for cube in CUBES}
    for name in order:
        cube = by_bone[name]
        ox, oy, oz = cube["origin"]
        sx, sy, sz = cube["size"]
        u, v, w, h = cube["uv"]["north"]
        face = texture.crop((u, v, u + w, v + h)).resize((max(1, int(sx * scale)), max(1, int(sy * scale))), Image.NEAREST)
        px = int((ox + 10) * scale) + 36
        py = int((46 - (oy + sy)) * scale) + 24
        canvas.paste(face, (px, py), face)
    sheet = texture.resize((256, 256), Image.NEAREST)
    canvas.paste(sheet, (250, 24))
    icon_small = icon.resize((96, 96), Image.NEAREST)
    canvas.paste(icon_small, (400, 300))
    return canvas


def main():
    assert_uvs()
    texture = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    paint(texture)
    icon = pack_icon()
    item = item_icon()
    (RP / "textures" / "entity").mkdir(parents=True, exist_ok=True)
    (RP / "textures" / "items").mkdir(parents=True, exist_ok=True)
    (RP / "models" / "entity").mkdir(parents=True, exist_ok=True)
    texture.save(RP / "textures" / "entity" / "the_creator.png")
    item.save(RP / "textures" / "items" / "creator_offering.png")
    icon.save(RP / "pack_icon.png")
    icon.save(BP / "pack_icon.png")
    geo_path = RP / "models" / "entity" / "the_creator.geo.json"
    geo_path.write_text(json.dumps(geometry(), indent=2) + "\n", encoding="utf-8")
    shot = preview(texture, icon)
    shot.save(ROOT / "preview.png")
    print(f"wrote texture, geometry, icons, {shot.size[0]}x{shot.size[1]} preview")


if __name__ == "__main__":
    main()
