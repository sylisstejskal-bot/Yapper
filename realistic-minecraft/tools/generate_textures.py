#!/usr/bin/env python3
"""Build the Realistic Minecraft images.

Writes 32x32 block and item textures, HUD icons at the vanilla pixel sizes,
particle images, and particle JSON. Safe to run again. Does not touch
animations, scripts, or manifests.

    python3 tools/generate_textures.py
"""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RP = ROOT / "resource_pack"
BP = ROOT / "behavior_pack"
SIZE = 32

# Metal, emissive, roughness, subsurface. 0-255. Metal and subsurface do not
# combine; leaves keep metal at 0 so a little subsurface can show.
STONE = [0, 0, 214, 0]
DIRT = [0, 0, 232, 0]
WOOD = [0, 0, 176, 0]
SAND = [0, 0, 236, 0]
METAL = [214, 0, 58, 0]
GOLD = [232, 0, 42, 0]
GEM = [18, 0, 28, 0]
GLOW = [0, 230, 36, 0]
LEAF = [0, 0, 188, 32]
GLASS = [0, 0, 14, 0]
ICE = [0, 0, 20, 18]
ROUGH = [0, 0, 200, 0]


def clamp(value: float, lo: int = 0, hi: int = 255) -> int:
    return max(lo, min(hi, int(value)))


def hash2(x: int, y: int, seed: int) -> float:
    n = (int(x) * 374761393 + int(y) * 668265263 + int(seed) * 1442695041) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    n = n ^ (n >> 16)
    return (n & 255) / 255.0


def fade(t: float) -> float:
    return t * t * (3.0 - 2.0 * t)


def noise(x: float, y: float, seed: int) -> float:
    x0 = math.floor(x)
    y0 = math.floor(y)
    tx = fade(x - x0)
    ty = fade(y - y0)

    def at(ix: int, iy: int) -> float:
        return hash2(ix, iy, seed)

    top = at(x0, y0) * (1 - tx) + at(x0 + 1, y0) * tx
    bot = at(x0, y0 + 1) * (1 - tx) + at(x0 + 1, y0 + 1) * tx
    return top * (1 - ty) + bot * ty


def fbm(x: float, y: float, seed: int, octaves: int = 4) -> float:
    total = 0.0
    amp = 0.5
    freq = 1.0
    norm = 0.0
    for i in range(octaves):
        total += noise(x * freq, y * freq, seed + i * 19) * amp
        norm += amp
        amp *= 0.5
        freq *= 2.0
    return total / norm


def mix(a, b, t: float):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def shade(color, light: float, alpha: int = 255):
    return tuple(clamp(channel * (1.0 + light)) for channel in color[:3]) + (alpha,)


def blank(size: int = SIZE) -> Image.Image:
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def light_pass(im: Image.Image, strength: float = 0.22) -> Image.Image:
    w, h = im.size
    px = im.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lx = 1.0 - x / max(1, w - 1)
            ly = 1.0 - y / max(1, h - 1)
            light = ((lx * 0.58 + ly * 0.42) - 0.5) * strength * 2.0
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge == 0:
                light -= 0.14
            elif edge == 1:
                light -= 0.06
            px[x, y] = shade((r, g, b), light, a)
    return im


def paint_field(size, base, seed, scale, contrast, specks=0):
    im = blank(size)
    px = im.load()
    dark = mix(base, (0, 0, 0), 0.28)
    light = mix(base, (255, 255, 255), 0.22)
    for y in range(size):
        for x in range(size):
            n = fbm(x / scale, y / scale, seed)
            color = mix(dark, light, n)
            if specks and hash2(x, y, seed + 3) > 0.92:
                color = mix(color, light, 0.65)
            grain = (hash2(x, y, seed + 9) - 0.5) * contrast
            px[x, y] = shade(color, grain / 255.0)
    if scale >= 5:
        for y in range(size):
            for x in range(size):
                n = fbm(x / 3.2, y / 3.2, seed + 4, 2)
                if n > 0.72 and hash2(x, y, seed) > 0.55:
                    r, g, b, a = px[x, y]
                    px[x, y] = shade((r, g, b), -0.18, a)
    return light_pass(im, 0.16)


def paint_planks(size, base, seed):
    im = blank(size)
    px = im.load()
    board = max(4, size // 4)
    for y in range(size):
        by = y // board
        offset = (by % 2) * (board // 2)
        local = (x_seam(y, board))
        for x in range(size):
            seam = (y % board) == 0 or ((x + offset) % (size // 2)) == 0
            n = fbm((x + offset) / 9.0, y / 6.0, seed + by)
            color = mix(mix(base, (0, 0, 0), 0.22), mix(base, (255, 255, 255), 0.16), n)
            if seam:
                color = mix(color, (0, 0, 0), 0.45)
            px[x, y] = shade(color, 0)
    return light_pass(im, 0.12)


def x_seam(y, board):
    return (y // board) * 3


def paint_log_side(size, base, seed):
    im = blank(size)
    px = im.load()
    dark = mix(base, (0, 0, 0), 0.35)
    for y in range(size):
        for x in range(size):
            stripe = math.sin((x + fbm(x / 8, y / 14, seed) * 4) * 0.85)
            color = mix(dark, base, 0.45 + stripe * 0.35)
            if abs((x - size * 0.62)) < 2.2 and abs(y - size * 0.38) < 3.5:
                color = mix(color, dark, 0.55)
            px[x, y] = shade(color, 0)
    return light_pass(im, 0.14)


def paint_log_top(size, base, seed):
    im = blank(size)
    px = im.load()
    cx = (size - 1) / 2
    cy = (size - 1) / 2
    bark = mix(base, (0, 0, 0), 0.38)
    for y in range(size):
        for x in range(size):
            dist = math.hypot(x - cx, y - cy) / (size * 0.5)
            ring = 0.5 + 0.5 * math.sin(dist * 28 + fbm(x / 5, y / 5, seed) * 2)
            if dist > 0.86:
                color = bark
            else:
                color = mix(mix(base, (255, 240, 210), 0.15), mix(base, (0, 0, 0), 0.3), ring)
            px[x, y] = shade(color, 0)
    return light_pass(im, 0.1)


def paint_ore(size, base, gem, seed):
    im = paint_field(size, base, seed, 5.5, 18)
    px = im.load()
    for y in range(2, size - 2):
        for x in range(2, size - 2):
            n = fbm(x / 3.4, y / 3.4, seed + 11)
            if n > 0.7 and hash2(x // 2, y // 2, seed) > 0.42:
                for dy in (0, 1):
                    for dx in (0, 1):
                        if x + dx >= size or y + dy >= size:
                            continue
                        light = 0.3 if dx == 0 and dy == 0 else -0.05
                        px[x + dx, y + dy] = shade(gem, light)
    return im


def paint_bricks(size, brick, mortar, seed):
    im = blank(size)
    px = im.load()
    bh = max(3, size // 4)
    bw = max(6, size // 2)
    for y in range(size):
        row = y // bh
        ox = (bw // 2) if row % 2 else 0
        for x in range(size):
            mortar_line = (y % bh) == 0 or ((x + ox) % bw) == 0
            n = fbm(x / 7, y / 7, seed + row)
            color = mortar if mortar_line else mix(mix(brick, (0, 0, 0), 0.18), brick, n)
            if not mortar_line and hash2(x, y, seed) > 0.96:
                color = mix(color, (0, 0, 0), 0.35)
            px[x, y] = shade(color, 0)
    return light_pass(im, 0.12)


def paint_metal(size, base, seed):
    im = blank(size)
    px = im.load()
    for y in range(size):
        for x in range(size):
            panel = 0.08 if (x < 2 or y < 2) else (-0.1 if x > size - 3 or y > size - 3 else 0.0)
            brush = math.sin((x * 0.7 + y * 0.15) + fbm(x / 6, y / 10, seed) * 3) * 0.06
            px[x, y] = shade(base, panel + brush)
    return im


def paint_leaves(size, gray, seed, holes: bool):
    im = blank(size)
    px = im.load()
    for y in range(size):
        for x in range(size):
            n = fbm(x / 4.2, y / 4.2, seed)
            if holes and (n < 0.38 or hash2(x, y, seed) > 0.9):
                continue
            tone = gray * (0.72 + n * 0.4)
            alpha = 255 if not holes else (210 if n < 0.55 else 255)
            px[x, y] = (clamp(tone), clamp(tone), clamp(tone * 0.98), alpha)
    return light_pass(im, 0.08)


def paint_grass_top(size, seed):
    im = blank(size)
    px = im.load()
    for y in range(size):
        for x in range(size):
            n = fbm(x / 4.0, y / 5.0, seed)
            blade = 1.0 if (hash2(x, y // 2, seed) > 0.62 and y % 3 != 0) else 0.0
            tone = 132 + n * 50 + blade * 28
            px[x, y] = (clamp(tone), clamp(tone), clamp(tone), 255)
    return im


def paint_grass_fringe(size, seed):
    im = blank(size)
    px = im.load()
    for y in range(size):
        reach = 6 + int(hash2(0, y, seed) * 4)
        if y > 12:
            continue
        for x in range(size):
            blade = hash2(x, 1, seed + y) 
            if y > reach and blade < 0.72:
                continue
            if y > 3 and hash2(x, y, seed) < 0.25:
                continue
            tone = 150 + fbm(x / 3, y / 3, seed) * 70
            alpha = 230 if y < reach - 1 else 160
            px[x, y] = (clamp(tone), clamp(tone), clamp(tone), alpha)
    return im


def paint_snow_side(size, seed):
    dirt = paint_field(size, (124, 88, 60), seed, 5, 16)
    px = dirt.load()
    for y in range(size):
        snow_line = 8 + int(math.sin(x_wave(y)) * 0)
        cap = 7 + int(hash2(0, y, seed) > 0.5) + int(2 * math.sin(y * 0.0))
        for x in range(size):
            edge = 6 + int(hash2(x, 2, seed) * 4)
            if y <= edge:
                n = fbm(x / 4, y / 3, seed + 2)
                px[x, y] = shade((236, 240, 244), (n - 0.5) * 0.15)
    return light_pass(dirt, 0.08)


def x_wave(y):
    return y


def paint_glass(size, seed):
    im = blank(size)
    px = im.load()
    for y in range(size):
        for x in range(size):
            streak = abs((x - y) % 11 - 2) < 1
            alpha = 150 if streak else 78
            tone = 214 if streak else 186
            px[x, y] = (tone, tone + 6, tone + 12, alpha)
            if x == 0 or y == 0 or x == size - 1 or y == size - 1:
                px[x, y] = (210, 224, 230, 190)
    return im


def paint_ice(size, seed):
    im = paint_field(size, (198, 224, 232), seed, 7, 10)
    px = im.load()
    for i in range(6):
        x = int(hash2(i, 1, seed) * (size - 1))
        for y in range(size):
            xx = clamp(x + int(math.sin(y / 4 + i) * 2), 0, size - 1)
            r, g, b, a = px[xx, y]
            px[xx, y] = (clamp(r + 30), clamp(g + 30), clamp(b + 20), 230)
    return im


def paint_glow(size, base, hot, seed):
    im = blank(size)
    px = im.load()
    for y in range(size):
        for x in range(size):
            n = fbm(x / 4.5, y / 4.5, seed)
            color = hot if n > 0.58 else mix(base, hot, n)
            px[x, y] = shade(color, 0.05 if n > 0.7 else -0.05)
    return im


def paint_obsidian(size, seed, tears=False):
    im = paint_field(size, (22, 16, 36), seed, 6, 12)
    px = im.load()
    for y in range(size):
        for x in range(size):
            if fbm(x / 2.5, y / 8, seed + 5) > 0.78:
                px[x, y] = shade((70, 48, 110), 0.2)
            if tears and x % 7 == int(hash2(x, 0, seed) * 3) and y > 8 and hash2(x, y, seed) > 0.55:
                px[x, y] = (120, 230, 230, 255)
    return im


def paint_deepslate(size, seed):
    im = blank(size)
    px = im.load()
    for y in range(size):
        band = (y // 4) % 2
        for x in range(size):
            n = fbm(x / 6, y / 3, seed)
            base = (58, 58, 64) if band == 0 else (78, 78, 84)
            px[x, y] = shade(mix(base, (110, 110, 116), n * 0.4), 0)
    return light_pass(im, 0.1)


def paint_sandstone(size, base, seed):
    im = paint_field(size, base, seed, 8, 8)
    px = im.load()
    for y in (6, 7, size - 8, size - 7):
        for x in range(size):
            px[x, y] = shade(mix(base, (0, 0, 0), 0.25), 0)
    return im


def paint_gravel(size, seed):
    im = paint_field(size, (128, 124, 120), seed, 4, 14)
    px = im.load()
    for i in range(18):
        cx = int(hash2(i, 2, seed) * (size - 4)) + 2
        cy = int(hash2(i, 5, seed) * (size - 4)) + 2
        rad = 1 + int(hash2(i, 8, seed) * 2)
        tone = 100 + int(hash2(i, 3, seed) * 80)
        for y in range(cy - rad, cy + rad + 1):
            for x in range(cx - rad, cx + rad + 1):
                if 0 <= x < size and 0 <= y < size and (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad:
                    px[x, y] = (tone, tone, tone - 4, 255)
    return light_pass(im, 0.08)


def paint_basalt(size, seed, top=False):
    im = blank(size)
    px = im.load()
    for y in range(size):
        for x in range(size):
            if top:
                n = fbm(x / 4, y / 4, seed)
                px[x, y] = shade((70, 70, 74), (n - 0.5) * 0.3)
            else:
                stripe = 0.15 if (x + int(fbm(0, y / 5, seed) * 2)) % 5 == 0 else 0
                px[x, y] = shade((64, 64, 68), stripe)
    return im


def paint_blackstone(size, seed, polished=False):
    base = (42, 40, 46) if not polished else (48, 46, 52)
    im = paint_field(size, base, seed, 7 if polished else 4, 10 if polished else 18)
    return im


def paint_farmland(size, wet, seed):
    base = (92, 62, 40) if wet else (122, 86, 54)
    im = paint_field(size, base, seed, 6, 12)
    px = im.load()
    for y in range(4, size, 8):
        for x in range(size):
            px[x, y] = shade((40, 28, 18), 0)
            if y + 1 < size:
                px[x, y + 1] = shade(mix(base, (0, 0, 0), 0.2), 0)
    return im


def paint_path(size, seed):
    im = paint_field(size, (148, 126, 90), seed, 4.5, 16, specks=1)
    return im


def paint_furnace(size, lit, seed):
    im = paint_field(size, (120, 120, 122), seed, 6, 12)
    px = im.load()
    for y in range(size):
        for x in range(size):
            mouth = 8 <= x <= 23 and 6 <= y <= 16
            fire = 12 <= x <= 19 and 18 <= y <= 26
            if mouth:
                px[x, y] = (28, 26, 26, 255)
            elif lit and fire:
                px[x, y] = (230, 120 + (y % 3) * 20, 30, 255) if (x + y) % 2 == 0 else (255, 196, 60, 255)
            elif fire:
                px[x, y] = (48, 42, 40, 255)
    return light_pass(im, 0.08)


def paint_craft_front(size, seed):
    im = paint_planks(size, (176, 140, 88), seed)
    px = im.load()
    for y in range(6, 26):
        for x in range(8, 24):
            grid = (x in (8, 15, 16, 23) or y in (6, 15, 16, 25))
            px[x, y] = (92, 64, 36, 255) if grid else (196, 168, 120, 255)
    return im


def paint_craft_top(size, seed):
    im = paint_planks(size, (186, 150, 96), seed)
    draw = ImageDraw.Draw(im)
    draw.rectangle((6, 6, 25, 25), outline=(110, 78, 42, 255))
    return im


def paint_craft_side(size, seed):
    im = paint_planks(size, (168, 132, 82), seed)
    px = im.load()
    for y in range(8, 24):
        px[size // 2, y] = (90, 62, 36, 255)
        if y == 16:
            for x in range(10, 22):
                px[x, y] = (70, 48, 28, 255)
    return im


def paint_bookshelf(size, seed):
    im = paint_planks(size, (146, 108, 64), seed)
    px = im.load()
    spines = [(92, 48, 42), (64, 78, 58), (120, 96, 60), (72, 58, 48), (138, 122, 90), (80, 64, 70)]
    for index, x0 in enumerate(range(3, size - 3, 4)):
        color = spines[index % len(spines)]
        for y in range(4, size - 4):
            for x in range(x0, min(size - 2, x0 + 3)):
                px[x, y] = shade(color, 0.08 if x == x0 else -0.04)
    return im


def paint_mossy(size, seed, bricks=False):
    im = paint_bricks(size, (132, 132, 128), (90, 90, 86), seed) if bricks else paint_gravel(size, seed)
    px = im.load()
    for y in range(size):
        for x in range(size):
            if fbm(x / 4.6, y / 4.6, seed + 8) > 0.64:
                px[x, y] = shade((62, 112, 48), (hash2(x, y, seed) - 0.5) * 0.25)
    return im


def paint_nylium(size, dirt, top, seed, side=False):
    im = paint_field(size, dirt if side else top, seed, 4.5, 16)
    if not side:
        return im
    px = im.load()
    for y in range(10):
        for x in range(size):
            if y < 6 or hash2(x, y, seed) > 0.4:
                px[x, y] = shade(top, (hash2(x, y, seed + 1) - 0.5) * 0.2)
    return im


def paint_quartz(size, seed, face):
    base = (230, 226, 220)
    im = paint_field(size, base, seed, 10, 6)
    if face == "side":
        px = im.load()
        for y in range(size):
            if y % 16 == 0:
                for x in range(size):
                    px[x, y] = shade(base, -0.08)
    return im


def paint_debris(size, seed, top=False):
    im = paint_field(size, (62, 46, 40), seed, 4, 16)
    px = im.load()
    for y in range(size):
        for x in range(size):
            if fbm(x / 3, y / (6 if not top else 3), seed + 6) > 0.74:
                px[x, y] = shade((150, 92, 58), 0.15)
    return im


def height_from(im: Image.Image) -> Image.Image:
    blurred = im.filter(ImageFilter.SMOOTH)
    src = blurred.convert("RGBA")
    out = Image.new("L", im.size, 128)
    sp = src.load()
    op = out.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = sp[x, y]
            if a < 12:
                op[x, y] = 128
                continue
            lum = 0.3 * r + 0.59 * g + 0.11 * b
            op[x, y] = clamp(80 + lum * 0.6)
    return out


def save_image(im: Image.Image, rel: str, formats: tuple[str, ...]) -> None:
    path = RP / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    stem = path.with_suffix("")
    for fmt in formats:
        target = stem.with_suffix(f".{fmt}")
        im.save(target, format="TGA" if fmt == "tga" else "PNG", optimize=fmt == "png")


def save_set(rel_without_ext: str, mers: list[int]) -> None:
    path = RP / f"{rel_without_ext}.texture_set.json"
    stem = Path(rel_without_ext).name
    # Names are resolved next to this file, so the folder is not part of the id.
    payload = {
        "format_version": "1.21.30",
        "minecraft:texture_set": {
            "color": stem,
            "heightmap": f"{stem}_h",
            "metalness_emissive_roughness_subsurface": mers,
        },
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n")
    color = None
    for ext in (".png", ".tga"):
        candidate = path.parent / f"{stem}{ext}"
        if candidate.exists():
            color = Image.open(candidate)
            break
    if color is None:
        raise FileNotFoundError(f"missing color texture for {rel_without_ext}")
    height_from(color).save(path.parent / f"{stem}_h.png")


def emit_block(rel: str, image: Image.Image, mers: list[int], formats=("png",)) -> None:
    save_image(image, f"textures/{rel}.png", formats)
    save_set(f"textures/{rel}", mers)


def build_blocks() -> list[str]:
    made = []

    def add(rel, image, mers, formats=("png",)):
        emit_block(rel, image, mers, formats)
        made.append(rel)

    s = 32
    add("blocks/stone", paint_field(s, (136, 134, 130), 1, 5.5, 16), STONE)
    add("blocks/cobblestone", paint_gravel(s, 2), STONE)
    add("blocks/cobblestone_mossy", paint_mossy(s, 3), ROUGH)
    add("blocks/stonebrick", paint_bricks(s, (132, 132, 128), (88, 88, 86), 4), STONE)
    add("blocks/stonebrick_cracked", paint_field(s, (128, 126, 122), 5, 4, 22), STONE)
    add("blocks/stonebrick_mossy", paint_mossy(s, 6, bricks=True), ROUGH)
    add("blocks/stonebrick_carved", paint_bricks(s, (140, 140, 136), (96, 96, 94), 7), STONE)
    add("blocks/gravel", paint_gravel(s, 8), STONE)
    add("blocks/dirt", paint_field(s, (124, 88, 58), 9, 4.5, 20), DIRT)
    add("blocks/coarse_dirt", paint_field(s, (112, 82, 54), 10, 3.5, 24, specks=1), DIRT)
    add("blocks/grass_top", paint_grass_top(s, 11), LEAF)
    add("blocks/grass_side", paint_grass_fringe(s, 12), LEAF, ("tga",))
    add("blocks/grass_side_snowed", paint_snow_side(s, 13), ROUGH, ("png", "tga"))
    add("blocks/mycelium_top", paint_field(s, (122, 104, 124), 14, 3.5, 18), ROUGH)
    add("blocks/mycelium_side", paint_nylium(s, (124, 88, 58), (132, 112, 136), 15, True), DIRT)
    add("blocks/dirt_podzol_top", paint_field(s, (96, 72, 42), 16, 4, 18), DIRT)
    add("blocks/dirt_podzol_side", paint_nylium(s, (124, 88, 58), (92, 68, 40), 17, True), DIRT)
    add("blocks/sand", paint_field(s, (216, 204, 158), 18, 2.6, 10, specks=1), SAND)
    add("blocks/red_sand", paint_field(s, (190, 102, 62), 19, 2.6, 12, specks=1), SAND)
    add("blocks/sandstone_top", paint_field(s, (214, 202, 150), 20, 6, 8), SAND)
    add("blocks/sandstone_normal", paint_sandstone(s, (206, 192, 140), 21), SAND)
    add("blocks/sandstone_bottom", paint_field(s, (200, 186, 134), 22, 6, 8), SAND)
    add("blocks/red_sandstone_top", paint_field(s, (184, 98, 58), 23, 6, 8), SAND)
    add("blocks/red_sandstone_normal", paint_sandstone(s, (176, 92, 54), 24), SAND)
    add("blocks/red_sandstone_bottom", paint_field(s, (160, 82, 48), 25, 6, 8), SAND)

    woods = {
        "oak": (168, 132, 82),
        "spruce": (104, 80, 48),
        "birch": (214, 206, 186),
        "big_oak": (78, 58, 34),
        "jungle": (160, 114, 72),
        "acacia": (172, 92, 52),
    }
    names = {
        "oak": ("log_oak", "planks_oak"),
        "spruce": ("log_spruce", "planks_spruce"),
        "birch": ("log_birch", "planks_birch"),
        "big_oak": ("log_big_oak", "planks_big_oak"),
        "jungle": ("log_jungle", "planks_jungle"),
        "acacia": ("log_acacia", "planks_acacia"),
    }
    for index, (key, color) in enumerate(woods.items()):
        log, planks = names[key]
        add(f"blocks/{log}", paint_log_side(s, color, 30 + index), WOOD)
        add(f"blocks/{log}_top", paint_log_top(s, color, 40 + index), WOOD)
        add(f"blocks/{planks}", paint_planks(s, color, 50 + index), WOOD)

    add("blocks/cherry_log_side", paint_log_side(s, (196, 150, 146), 61), WOOD)
    add("blocks/cherry_log_top", paint_log_top(s, (210, 170, 162), 62), WOOD)
    add("blocks/cherry_planks", paint_planks(s, (214, 176, 168), 63), WOOD)
    add("blocks/pale_oak_log_side", paint_log_side(s, (206, 204, 196), 64), WOOD)
    add("blocks/pale_oak_log_top", paint_log_top(s, (214, 212, 204), 65), WOOD)
    add("blocks/pale_oak_planks", paint_planks(s, (220, 216, 206), 66), WOOD)
    add("blocks/mangrove_log_side", paint_log_side(s, (120, 58, 48), 67), WOOD)
    add("blocks/mangrove_log_top", paint_log_top(s, (140, 70, 56), 68), WOOD)
    add("blocks/mangrove_planks", paint_planks(s, (150, 78, 64), 69), WOOD)
    add("blocks/huge_fungus/crimson_log_side", paint_log_side(s, (122, 42, 62), 70), WOOD)
    add("blocks/huge_fungus/crimson_log_top", paint_log_top(s, (148, 58, 72), 71), WOOD)
    add("blocks/huge_fungus/warped_stem_side", paint_log_side(s, (48, 112, 108), 72), WOOD)

    grays = {"oak": 158, "spruce": 118, "birch": 176, "jungle": 132}
    for index, (name, gray) in enumerate(grays.items()):
        add(f"blocks/leaves_{name}", paint_leaves(s, gray, 80 + index, True), LEAF, ("tga",))
        add(f"blocks/leaves_{name}_opaque", paint_leaves(s, gray - 8, 90 + index, False), LEAF)

    ores = {
        "coal_ore": (32, 32, 34),
        "iron_ore": (196, 168, 140),
        "copper_ore": (150, 96, 70),
        "gold_ore": (232, 196, 64),
        "diamond_ore": (92, 220, 214),
        "emerald_ore": (64, 196, 96),
        "redstone_ore": (176, 32, 28),
        "lapis_ore": (48, 78, 176),
    }
    for index, (name, gem) in enumerate(ores.items()):
        add(f"blocks/{name}", paint_ore(s, (128, 126, 122), gem, 100 + index), STONE)
        add(
            f"blocks/deepslate/deepslate_{name}",
            paint_ore(s, (70, 70, 76), gem, 120 + index),
            STONE,
        )
    add("blocks/deepslate/deepslate", paint_deepslate(s, 140), STONE)

    add("blocks/bedrock", paint_field(s, (28, 28, 30), 150, 3.5, 20), ROUGH)
    add("blocks/netherrack", paint_field(s, (112, 54, 52), 151, 3.2, 22), ROUGH)
    add("blocks/nether_brick", paint_bricks(s, (48, 24, 28), (28, 14, 16), 152), ROUGH)
    add("blocks/end_stone", paint_field(s, (220, 224, 168), 153, 5, 10), SAND)
    add("blocks/end_bricks", paint_bricks(s, (214, 220, 160), (160, 164, 110), 154), SAND)
    add("blocks/obsidian", paint_obsidian(s, 155), GEM)
    add("blocks/crying_obsidian", paint_obsidian(s, 156, tears=True), [8, 40, 24, 0])
    add("blocks/glowstone", paint_glow(s, (176, 122, 48), (255, 214, 90), 157), GLOW)
    add("blocks/shroomlight", paint_glow(s, (176, 92, 48), (255, 170, 70), 158), GLOW)
    add("blocks/sea_lantern", paint_glow(s, (170, 200, 190), (230, 255, 245), 159), GLOW)
    add("blocks/magma", paint_glow(s, (92, 28, 16), (230, 90, 24), 160), [20, 160, 50, 0])
    add("blocks/soul_sand", paint_field(s, (82, 64, 50), 161, 3.5, 16), DIRT)
    add("blocks/soul_soil", paint_field(s, (74, 56, 44), 162, 4, 14), DIRT)
    add("blocks/clay", paint_field(s, (160, 166, 178), 163, 6, 8), ROUGH)
    add("blocks/brick", paint_bricks(s, (156, 92, 74), (128, 118, 110), 164), ROUGH)
    add("blocks/snow", paint_field(s, (236, 240, 244), 165, 4, 8, specks=1), [0, 0, 70, 0])
    add("blocks/ice", paint_ice(s, 166), ICE)
    add("blocks/blue_ice", paint_field(s, (120, 168, 214), 167, 7, 8), ICE)
    add("blocks/glass", paint_glass(s, 168), GLASS)
    add("blocks/iron_block", paint_metal(s, (206, 206, 210), 169), METAL)
    add("blocks/gold_block", paint_metal(s, (232, 190, 64), 170), GOLD)
    add("blocks/diamond_block", paint_metal(s, (116, 230, 220), 171), GEM)
    add("blocks/emerald_block", paint_metal(s, (72, 196, 104), 172), GEM)
    add("blocks/copper_block", paint_metal(s, (184, 106, 74), 173), METAL)
    add("blocks/coal_block", paint_metal(s, (28, 28, 30), 174), [40, 0, 90, 0])
    add("blocks/lapis_block", paint_metal(s, (40, 74, 176), 175), GEM)
    add("blocks/redstone_block", paint_metal(s, (168, 28, 24), 176), [30, 20, 40, 0])
    add("blocks/netherite_block", paint_metal(s, (66, 60, 64), 177), [180, 0, 80, 0])
    add("blocks/raw_iron_block", paint_field(s, (168, 142, 120), 178, 4, 16), [80, 0, 140, 0])
    add("blocks/raw_copper_block", paint_field(s, (176, 104, 76), 179, 4, 16), [80, 0, 140, 0])
    add("blocks/raw_gold_block", paint_field(s, (214, 176, 64), 180, 4, 16), [90, 0, 130, 0])
    add("blocks/ancient_debris_side", paint_debris(s, 181), [60, 0, 120, 0])
    add("blocks/ancient_debris_top", paint_debris(s, 182, top=True), [60, 0, 120, 0])
    add("blocks/moss_block", paint_field(s, (78, 112, 52), 183, 3.2, 18), LEAF)
    add("blocks/tuff", paint_field(s, (108, 110, 96), 184, 3.4, 18), STONE)
    add("blocks/tuff_bricks", paint_bricks(s, (112, 114, 100), (78, 80, 70), 185), STONE)
    add("blocks/calcite", paint_field(s, (224, 222, 216), 186, 5, 8), STONE)
    add("blocks/dripstone_block", paint_field(s, (136, 108, 84), 187, 4, 16), ROUGH)
    add("blocks/amethyst_block", paint_metal(s, (150, 112, 196), 188), GEM)
    add("blocks/quartz_block_side", paint_quartz(s, 189, "side"), [0, 0, 40, 0])
    add("blocks/quartz_block_top", paint_quartz(s, 190, "top"), [0, 0, 40, 0])
    add("blocks/quartz_block_bottom", paint_quartz(s, 191, "bottom"), [0, 0, 40, 0])
    add("blocks/basalt_side", paint_basalt(s, 192), STONE)
    add("blocks/basalt_top", paint_basalt(s, 193, top=True), STONE)
    add("blocks/polished_basalt_side", paint_basalt(s, 194), STONE)
    add("blocks/polished_basalt_top", paint_basalt(s, 195, top=True), STONE)
    add("blocks/blackstone", paint_blackstone(s, 196), STONE)
    add("blocks/blackstone_top", paint_blackstone(s, 197, polished=True), STONE)
    add("blocks/polished_blackstone", paint_blackstone(s, 198, polished=True), STONE)
    add("blocks/gilded_blackstone", paint_ore(s, (46, 42, 48), (212, 170, 64), 199), STONE)
    add("blocks/stone_andesite", paint_field(s, (136, 136, 138), 200, 4, 14), STONE)
    add("blocks/stone_diorite", paint_field(s, (220, 220, 222), 201, 4, 12), STONE)
    add("blocks/stone_granite", paint_field(s, (156, 108, 92), 202, 4, 14), STONE)
    add("blocks/stone_slab_top", paint_field(s, (160, 160, 160), 203, 8, 8), STONE)
    add("blocks/stone_slab_side", paint_field(s, (148, 148, 148), 204, 6, 10), STONE)
    add("blocks/grass_path_top", paint_path(s, 205), DIRT)
    add("blocks/grass_path_side", paint_nylium(s, (124, 88, 58), (150, 128, 92), 206, True), DIRT)
    add("blocks/farmland_dry", paint_farmland(s, False, 207), DIRT)
    add("blocks/farmland_wet", paint_farmland(s, True, 208), DIRT)
    add("blocks/crafting_table_top", paint_craft_top(s, 209), WOOD)
    add("blocks/crafting_table_front", paint_craft_front(s, 210), WOOD)
    add("blocks/crafting_table_side", paint_craft_side(s, 211), WOOD)
    add("blocks/furnace_front_off", paint_furnace(s, False, 212), STONE)
    add("blocks/furnace_front_on", paint_furnace(s, True, 213), [0, 40, 180, 0])
    add("blocks/furnace_side", paint_field(s, (118, 118, 120), 214, 6, 12), STONE)
    add("blocks/furnace_top", paint_field(s, (96, 96, 98), 215, 5, 14), STONE)
    add("blocks/bookshelf", paint_bookshelf(s, 216), WOOD)
    add("blocks/redstone_lamp_off", paint_field(s, (120, 78, 58), 217, 6, 10), ROUGH)
    add("blocks/redstone_lamp_on", paint_glow(s, (196, 140, 70), (255, 214, 120), 218), GLOW)
    add("blocks/crimson_nylium_top", paint_nylium(s, (112, 54, 52), (168, 28, 42), 219), ROUGH)
    add("blocks/crimson_nylium_side", paint_nylium(s, (112, 54, 52), (148, 32, 48), 220, True), ROUGH)
    add("blocks/warped_nylium_top", paint_nylium(s, (112, 54, 52), (28, 128, 124), 221), ROUGH)
    add("blocks/warped_nylium_side", paint_nylium(s, (112, 54, 52), (32, 112, 108), 222, True), ROUGH)
    add("blocks/nether_wart_block", paint_field(s, (168, 32, 36), 223, 3.2, 18), ROUGH)
    add("blocks/warped_wart_block", paint_field(s, (24, 128, 118), 224, 3.2, 18), ROUGH)
    add("blocks/mud", paint_field(s, (62, 50, 46), 225, 5, 10), DIRT)
    add("blocks/packed_mud", paint_field(s, (142, 108, 84), 226, 6, 10), DIRT)
    add("blocks/mud_bricks", paint_bricks(s, (140, 104, 80), (96, 74, 58), 227), DIRT)
    add("blocks/dirt_with_roots", paint_nylium(s, (124, 88, 58), (92, 70, 48), 228, True), DIRT)
    return made


def icon(draw_fn, color) -> Image.Image:
    im = blank(32)
    draw_fn(im.load(), color)
    return outline(im)


def outline(im: Image.Image) -> Image.Image:
    src = im.copy()
    sp = src.load()
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if sp[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < w and 0 <= yy < h and sp[xx, yy][3] > 0:
                    px[x, y] = (20, 16, 14, 180)
                    break
    return im


def gem_icon(px, color):
    pts = [(16, 4), (24, 12), (20, 27), (12, 27), (8, 12)]
    fill_poly(px, pts, color)
    fill_poly(px, [(16, 6), (22, 12), (16, 14), (10, 12)], mix(color, (255, 255, 255), 0.45))


def ingot_icon(px, color):
    fill_poly(px, [(6, 18), (10, 12), (24, 12), (26, 18), (22, 24), (8, 24)], color)
    fill_poly(px, [(10, 13), (22, 13), (20, 17), (10, 17)], mix(color, (255, 255, 255), 0.35))


def stick_icon(px, color):
    for y in range(6, 27):
        px[15, y] = color + (255,)
        px[16, y] = shade(color, -0.1)


def coal_icon(px, color):
    fill_poly(px, [(10, 8), (22, 7), (26, 16), (20, 26), (9, 22), (7, 14)], color)


def apple_icon(px, color):
    fill_ellipse(px, 16, 18, 8, 8, color)
    for y in range(6, 12):
        px[16, y] = (92, 58, 28, 255)
    px[19, 8] = (70, 130, 60, 255)
    px[20, 9] = (70, 130, 60, 255)


def bread_icon(px, color):
    fill_ellipse(px, 16, 18, 11, 6, color)
    for x in range(8, 24):
        px[x, 16] = shade(color, -0.2)


def put(px, x, y, color):
    if 0 <= x < 32 and 0 <= y < 32:
        px[x, y] = color if len(color) == 4 else color + (255,)


def sword_icon(px, color):
    for i in range(18):
        x = 23 - i
        y = 4 + i
        put(px, x - 1, y, shade(color, 0.28)[:3])
        put(px, x, y, color)
        put(px, x + 1, y, shade(color, -0.12)[:3])
    for x in range(8, 16):
        put(px, x, 20, (196, 168, 64))
    put(px, 11, 22, (110, 76, 42))
    put(px, 10, 23, (110, 76, 42))
    put(px, 9, 24, (70, 48, 28))


def pick_icon(px, color):
    for i in range(16):
        put(px, 12 + i // 2, 11 + i, (110, 76, 42))
        put(px, 13 + i // 2, 11 + i, (78, 54, 30))
    for x in range(5, 28):
        put(px, x, 8, shade(color, 0.2)[:3])
        put(px, x, 9, color)
        put(px, x, 10, shade(color, -0.18)[:3])
    put(px, 6, 7, color)
    put(px, 26, 7, color)


def axe_icon(px, color):
    for i in range(16):
        px[12 + i // 3, 8 + i] = (110, 76, 42, 255)
    fill_poly(px, [(8, 8), (20, 6), (22, 14), (12, 16)], color)


def bow_icon(px, color):
    for i in range(20):
        y = 6 + i
        x = int(10 + math.sin(i / 19 * math.pi) * 8)
        px[x, y] = color + (255,)
        px[x + 1, y] = shade(color, 0.1)
    for y in range(7, 25):
        px[18, y] = (230, 226, 210, 255)


def arrow_icon(px, color):
    for i in range(16):
        px[8 + i, 22 - i] = (214, 206, 186, 255)
    fill_poly(px, [(20, 6), (26, 12), (18, 12)], (196, 196, 200))
    px[9, 22] = (180, 40, 40, 255)
    px[10, 21] = (180, 40, 40, 255)


def fill_poly(px, pts, color):
    im = blank(32)
    draw = ImageDraw.Draw(im)
    rgba = color + (255,) if len(color) == 3 else color
    draw.polygon(pts, fill=rgba)
    sp = im.load()
    for y in range(32):
        for x in range(32):
            if sp[x, y][3]:
                px[x, y] = sp[x, y]


def fill_ellipse(px, cx, cy, rx, ry, color):
    im = blank(32)
    draw = ImageDraw.Draw(im)
    rgba = color + (255,) if len(color) == 3 else color
    draw.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=rgba)
    sp = im.load()
    for y in range(32):
        for x in range(32):
            if sp[x, y][3]:
                px[x, y] = sp[x, y]


def build_items() -> None:
    specs = [
        ("diamond", gem_icon, (92, 220, 214)),
        ("emerald", gem_icon, (64, 196, 96)),
        ("coal", coal_icon, (36, 36, 38)),
        ("iron_ingot", ingot_icon, (206, 206, 210)),
        ("gold_ingot", ingot_icon, (232, 190, 64)),
        ("copper_ingot", ingot_icon, (184, 106, 74)),
        ("netherite_ingot", ingot_icon, (92, 84, 88)),
        ("stick", stick_icon, (146, 112, 64)),
        ("apple", apple_icon, (176, 40, 36)),
        ("bread", bread_icon, (198, 150, 78)),
        ("diamond_sword", sword_icon, (92, 220, 214)),
        ("diamond_pickaxe", pick_icon, (92, 220, 214)),
        ("diamond_axe", axe_icon, (92, 220, 214)),
        ("iron_sword", sword_icon, (206, 206, 210)),
        ("iron_pickaxe", pick_icon, (206, 206, 210)),
        ("bow_standby", bow_icon, (120, 78, 42)),
        ("arrow", arrow_icon, (214, 206, 186)),
    ]
    for name, fn, color in specs:
        image = icon(lambda px, c=color, f=fn: f(px, c), color)
        path = RP / "textures" / "items" / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path, optimize=True)


HEART = [
    "000101000",
    "001111100",
    "011111110",
    "011111110",
    "001111100",
    "000111000",
    "000010000",
    "000000000",
    "000000000",
]


def heart_image(fill, half=False, empty=False) -> Image.Image:
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(HEART):
        for x, bit in enumerate(row):
            if bit == "0":
                continue
            if empty:
                px[x, y] = (24, 20, 18, 180)
                continue
            if half and x > 4:
                px[x, y] = mix(fill, (24, 20, 18), 0.65) + (220,)
            else:
                highlight = y < 3 and 2 <= x <= 4
                px[x, y] = shade(fill, 0.25 if highlight else 0.0)
    return im


def hunger_image(fill, half=False, empty=False) -> Image.Image:
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = im.load()
    mask = [
        "001110000",
        "011111000",
        "011111100",
        "001111100",
        "000111000",
        "000010000",
        "000001000",
        "000000100",
        "000000000",
    ]
    for y, row in enumerate(mask):
        for x, bit in enumerate(row):
            if bit == "0":
                continue
            if empty:
                px[x, y] = (24, 20, 18, 180)
            elif half and x > 4:
                px[x, y] = (70, 48, 28, 220)
            else:
                px[x, y] = shade(fill, 0.15 if y < 2 else 0)
    return im


def build_hud() -> None:
    ui = RP / "textures" / "ui"
    ui.mkdir(parents=True, exist_ok=True)
    red = (176, 48, 42)
    heart_image(red).save(ui / "heart.png")
    heart_image(shade(red, 0.35)[:3]).save(ui / "heart_blink.png")
    heart_image((255, 236, 220)).save(ui / "heart_flash.png")
    heart_image(red, half=True).save(ui / "heart_half.png")
    heart_image(red, empty=True).save(ui / "heart_background.png")
    heart_image((214, 176, 48)).save(ui / "absorption_heart.png")
    heart_image((214, 176, 48), half=True).save(ui / "absorption_heart_half.png")
    heart_image((92, 150, 48)).save(ui / "poison_heart.png")
    heart_image((48, 42, 46)).save(ui / "wither_heart.png")
    hunger_image((176, 112, 48)).save(ui / "hunger_full.png")
    hunger_image((210, 150, 70)).save(ui / "hunger_blink.png")
    hunger_image((176, 112, 48), half=True).save(ui / "hunger_half.png")
    hunger_image((176, 112, 48), empty=True).save(ui / "hunger_background.png")
    hunger_image((120, 96, 48), empty=True).save(ui / "hunger_effect_background.png")

    slot = Image.new("RGBA", (20, 22), (0, 0, 0, 0))
    sp = slot.load()
    for y in range(22):
        for x in range(20):
            if x == 0 or y == 0 or x == 19 or y == 21:
                continue
            border = x < 2 or y < 2 or x > 17 or y > 19
            if border:
                sp[x, y] = (92, 78, 58, 255) if x < 3 or y < 3 else (28, 22, 18, 255)
            else:
                n = fbm(x / 6, y / 6, 7)
                sp[x, y] = shade((48, 40, 32), (n - 0.5) * 0.15)
    for index in range(9):
        slot.save(ui / f"hotbar_{index}.png")
    cap = Image.new("RGBA", (1, 22), (32, 26, 20, 255))
    cap.save(ui / "hotbar_start_cap.png")
    cap.save(ui / "hotbar_end_cap.png")
    frame = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    fp = frame.load()
    for y in range(24):
        for x in range(24):
            edge = min(x, y, 23 - x, 23 - y)
            if edge <= 2:
                fp[x, y] = (232, 196, 96, 235) if edge == 0 else (120, 86, 36, 220)
    frame.save(ui / "selected_hotbar_slot.png")

    empty = Image.new("RGBA", (13, 5), (0, 0, 0, 0))
    ep = empty.load()
    full = Image.new("RGBA", (13, 5), (0, 0, 0, 0))
    fp = full.load()
    for y in range(5):
        for x in range(13):
            if y == 0 or y == 4 or x == 0 or x == 12:
                ep[x, y] = (20, 16, 12, 220)
                fp[x, y] = (20, 48, 24, 220)
            else:
                ep[x, y] = (36, 30, 24, 255)
                fp[x, y] = (110, 196, 64, 255) if y < 3 else (64, 140, 36, 255)
    empty.save(ui / "experiencebarempty.png")
    full.save(ui / "experiencebarfull.png")


def soft_blob() -> Image.Image:
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            if d >= 1:
                continue
            alpha = int((1 - d * d) * 255)
            px[x, y] = (255, 255, 255, alpha)
    return im


def leaf_tex() -> Image.Image:
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    draw.polygon([(8, 1), (14, 8), (8, 15), (2, 8)], fill=(150, 168, 96, 230))
    draw.line((8, 2, 8, 14), fill=(70, 90, 40, 255))
    return im


def spark_tex() -> Image.Image:
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2:
                px[x, y] = (255, 244, 210, 255)
            elif d < 4:
                px[x, y] = (255, 180, 60, 180)
    return im


def write_particle_images() -> None:
    folder = RP / "textures" / "particle"
    folder.mkdir(parents=True, exist_ok=True)
    soft_blob().save(folder / "realistic_soft.png")
    leaf_tex().save(folder / "realistic_leaf.png")
    spark_tex().save(folder / "realistic_spark.png")


def particle_json(identifier, texture, material, count, life, speed, accel, drag, size, radius, direction, tint):
    r, g, b = tint
    return {
        "format_version": "1.10.0",
        "particle_effect": {
            "description": {
                "identifier": identifier,
                "basic_render_parameters": {
                    "material": material,
                    "texture": texture,
                },
            },
            "components": {
                "minecraft:emitter_rate_instant": {"num_particles": count},
                "minecraft:emitter_lifetime_once": {"active_time": 0.05},
                "minecraft:emitter_shape_sphere": {
                    "radius": radius,
                    "direction": direction,
                },
                "minecraft:particle_initial_speed": speed,
                "minecraft:particle_initial_spin": {
                    "rotation": "Math.random(0, 360)",
                    "rotation_rate": "Math.random(-40, 40)",
                },
                "minecraft:particle_lifetime_expression": {"max_lifetime": life},
                "minecraft:particle_motion_dynamic": {
                    "linear_acceleration": accel,
                    "linear_drag_coefficient": drag,
                },
                "minecraft:particle_appearance_billboard": {
                    "size": [size, size],
                    "facing_camera_mode": "lookat_xyz",
                    "uv": {
                        "texture_width": 16,
                        "texture_height": 16,
                        "uv": [0, 0],
                        "uv_size": [16, 16],
                    },
                },
                "minecraft:particle_appearance_tinting": {
                    "color": [r, g, b, "(1 - variable.particle_age)"],
                },
            },
        },
    }


def build_particles() -> None:
    write_particle_images()
    soft = "textures/particle/realistic_soft"
    specs = [
        ("puff", "realistic:puff", soft, "particles_blend", 4, "Math.random(0.25, 0.45)", "Math.random(0.4, 1.1)", [0, 0.6, 0], 3.0, "0.1", 0.22, "outwards", [0.72, 0.66, 0.55]),
        ("impact", "realistic:impact", soft, "particles_blend", 8, "Math.random(0.35, 0.6)", "Math.random(0.8, 1.8)", [0, 0.2, 0], 2.4, "0.16 * (1 - variable.particle_age)", 0.35, "outwards", [0.62, 0.56, 0.48]),
        ("mote", "realistic:mote", soft, "particles_blend", 1, "Math.random(1.6, 2.4)", "Math.random(0.05, 0.2)", [0, 0.12, 0], 0.4, "0.07", 0.02, "outwards", [0.9, 0.86, 0.72]),
        ("dust", "realistic:dust", soft, "particles_blend", 1, "Math.random(1.2, 2.0)", "Math.random(0.05, 0.16)", [0, 0.05, 0], 0.6, "0.08", 0.04, "outwards", [0.55, 0.52, 0.48]),
        ("sand", "realistic:sand", soft, "particles_blend", 1, "Math.random(1.0, 1.6)", "Math.random(0.1, 0.3)", [0, -0.4, 0], 0.8, "0.09", 0.05, "outwards", [0.86, 0.74, 0.48]),
        ("ash", "realistic:ash", soft, "particles_blend", 1, "Math.random(1.4, 2.2)", "Math.random(0.15, 0.4)", [0, 0.35, 0], 0.5, "0.08", 0.08, "outwards", [0.28, 0.26, 0.25]),
        ("splash", "realistic:splash", soft, "particles_blend", 7, "Math.random(0.25, 0.45)", "Math.random(1.2, 2.2)", [0, -6, 0], 1.5, "0.1", 0.12, [0, 1, 0], [0.75, 0.88, 0.96]),
        ("mist", "realistic:mist", soft, "particles_blend", 1, "Math.random(1.8, 2.6)", "Math.random(0.02, 0.08)", [0, 0.04, 0], 0.3, "0.18", 0.05, "outwards", [0.78, 0.84, 0.9]),
        ("leaf", "realistic:leaf", "textures/particle/realistic_leaf", "particles_blend", 1, "Math.random(1.4, 2.2)", "Math.random(0.15, 0.4)", [0, -0.8, 0], 0.35, "0.14", 0.1, "outwards", [0.85, 0.95, 0.7]),
        ("spark", "realistic:spark", "textures/particle/realistic_spark", "particles_add", 5, "Math.random(0.12, 0.28)", "Math.random(1.4, 2.6)", [0, -3, 0], 1.2, "0.08 * (1 - variable.particle_age)", 0.2, "outwards", [1, 0.86, 0.45]),
    ]
    folder = RP / "particles"
    folder.mkdir(parents=True, exist_ok=True)
    for filename, *rest in specs:
        payload = particle_json(*rest)
        (folder / f"{filename}.particle.json").write_text(json.dumps(payload, indent=2) + "\n")


def build_icon() -> None:
    im = Image.new("RGBA", (128, 128), (28, 24, 20, 255))
    stone = paint_field(128, (120, 116, 110), 3, 18, 16)
    im.paste(stone, (0, 0))
    draw = ImageDraw.Draw(im)
    draw.polygon([(64, 24), (96, 48), (80, 100), (48, 100), (32, 48)], fill=(92, 220, 214, 255))
    draw.polygon([(64, 32), (84, 50), (64, 58), (44, 50)], fill=(210, 255, 250, 255))
    draw.rectangle((4, 4, 123, 123), outline=(212, 176, 84, 255), width=4)
    for pack in (RP, BP):
        im.resize((64, 64), Image.Resampling.BOX).save(pack / "pack_icon.png")


def build_preview(paths: list[str]) -> None:
    thumbs = []
    for rel in paths:
        folder = RP / "textures" / str(Path(rel).parent)
        stem = Path(rel).name
        file = next(folder.glob(stem + ".*"))
        if file.suffix == ".json" or file.name.endswith("_h.png"):
            continue
        thumbs.append((stem, Image.open(file).convert("RGBA")))
        if len(thumbs) >= 48:
            break
    cols = 8
    pad = 4
    cell = 32
    rows = math.ceil(len(thumbs) / cols)
    sheet = Image.new("RGBA", (cols * (cell + pad) + pad, rows * (cell + pad) + pad), (18, 16, 14, 255))
    for index, (name, image) in enumerate(thumbs):
        x = pad + (index % cols) * (cell + pad)
        y = pad + (index // cols) * (cell + pad)
        sheet.paste(image.resize((cell, cell), Image.Resampling.BOX), (x, y))
    preview = sheet.resize((sheet.width * 2, sheet.height * 2), Image.Resampling.NEAREST)
    preview.save(ROOT / "preview.png")
    artifacts = Path("/opt/cursor/artifacts")
    if artifacts.exists():
        preview.save(artifacts / "texture-preview.png")


def main() -> None:
    made = build_blocks()
    build_items()
    build_hud()
    build_particles()
    build_icon()
    build_preview(made)
    print(f"blocks {len(made)}")


if __name__ == "__main__":
    main()
