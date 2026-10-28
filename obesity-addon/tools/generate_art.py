#!/usr/bin/env python3
"""Build Obesity's textures, block and body geometry, pack icons, and previews."""

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RP = ROOT / "resource_packs" / "Obesity_RP"
BP = ROOT / "behavior_packs" / "Obesity_BP"
PREVIEW = ROOT / "preview"
ARTIFACTS = Path("/opt/cursor/artifacts")

PALETTE = {
    "yellow": (0, 0),
    "belly": (8, 0),
    "shadow": (16, 0),
    "black": (24, 0),
    "white": (32, 0),
    "pink": (40, 0),
    "brown": (48, 0),
    "gold": (56, 0),
}
FILL = {
    "yellow": (255, 214, 61, 255),
    "belly": (245, 186, 43, 255),
    "shadow": (214, 150, 32, 255),
    "black": (22, 18, 16, 255),
    "white": (255, 250, 236, 255),
    "pink": (255, 146, 176, 255),
    "brown": (106, 58, 22, 255),
    "gold": (255, 196, 48, 255),
}
BORDER = {
    "yellow": (196, 138, 16, 255),
    "belly": (184, 122, 18, 255),
    "shadow": (150, 96, 16, 255),
    "black": (8, 8, 8, 255),
    "white": (255, 255, 255, 255),
    "pink": (214, 96, 128, 255),
    "brown": (62, 32, 12, 255),
    "gold": (196, 130, 16, 255),
}
SIDES = ("north", "south", "east", "west", "up", "down")
SHADE = {"north": 1.05, "south": 0.72, "east": 0.9, "west": 0.82, "up": 1.16, "down": 0.58}


def paint_cell(img, name):
    u, v = PALETTE[name]
    fill = FILL[name]
    border = BORDER[name]
    for dy in range(8):
        for dx in range(8):
            edge = dx in (0, 7) or dy in (0, 7)
            img.putpixel((u + dx, v + dy), border if edge and name != "white" else fill)


def entity_texture():
    img = Image.new("RGBA", (64, 64), FILL["yellow"])
    for name in PALETTE:
        paint_cell(img, name)
    return img


def paint_cardboard(px, ox, oy, size, base, light, speck, edge, slot=False):
    for y in range(size):
        for x in range(size):
            if x in (0, size - 1) or y in (0, size - 1):
                color = edge
            else:
                n = (x * 17 + y * 13) % 29
                if n == 0:
                    color = speck
                elif n == 1:
                    color = light
                else:
                    color = base
            px[ox + x, oy + y] = color + (255,)
    if not slot:
        return
    width = int(size * 0.52)
    height = max(3, size // 8)
    x0 = (size - width) // 2
    y0 = (size - height) // 2
    for y in range(y0 - 1, y0 + height + 1):
        for x in range(x0 - 1, x0 + width + 1):
            if 0 <= x < size and 0 <= y < size:
                px[ox + x, oy + y] = (70, 48, 30, 255)
    for y in range(y0, y0 + height):
        for x in range(x0, x0 + width):
            px[ox + x, oy + y] = (18, 14, 12, 255)


def box_texture():
    img = Image.new("RGBA", (64, 64), (150, 110, 70, 255))
    px = img.load()
    # Every side shows the dark slot, so the cube matches the reference from any angle.
    paint_cardboard(px, 0, 0, 32, (196, 148, 96), (214, 170, 116), (158, 114, 70), (104, 72, 44), slot=True)
    paint_cardboard(px, 32, 0, 32, (208, 164, 112), (224, 184, 132), (176, 132, 86), (120, 86, 54), slot=False)
    paint_cardboard(px, 0, 32, 32, (146, 104, 66), (164, 120, 78), (120, 84, 52), (88, 60, 36), slot=False)
    return img


def uv_for(name):
    u, v = PALETTE[name]
    return {"uv": [u, v], "uv_size": [8, 8]}


def cube(origin, size, color):
    if isinstance(color, str):
        faces = {side: color for side in SIDES}
    else:
        default = color.get("*", "yellow")
        faces = {side: color.get(side, default) for side in SIDES}
    return {
        "origin": [round(float(n), 3) for n in origin],
        "size": [round(float(n), 3) for n in size],
        "uv": {side: uv_for(faces[side]) for side in SIDES},
    }


def face_uv(u, v, w, h):
    return {"uv": [u, v], "uv_size": [w, h]}


def character_bones():
    yellow = {"down": "shadow", "*": "yellow"}
    return [
        {"name": "root", "pivot": [0, 0, 0]},
        {
            "name": "body",
            "parent": "root",
            "pivot": [0, 5, 0],
            "cubes": [
                cube([-7, 5, -6], [14, 10, 12], {"north": "belly", "south": "shadow", "down": "shadow", "*": "yellow"}),
                cube([-6.2, 4.2, -7.4], [12.4, 3.4, 8], "belly"),
            ],
        },
        {
            "name": "belly",
            "parent": "body",
            "pivot": [0, 7, -6],
            "cubes": [
                cube([-6.6, 6.2, -10.2], [13.2, 7.6, 4.6], {"north": "gold", "down": "shadow", "*": "belly"}),
                cube([-1.3, 8.4, -11.1], [2.6, 2.5, 1.1], "brown"),
            ],
        },
        {
            "name": "head",
            "parent": "body",
            "pivot": [0, 14, 0],
            "cubes": [
                cube([-5.6, 14, -5.6], [11.2, 9.2, 10.2], yellow),
                cube([-4.2, 12.8, -8.1], [8.4, 3.3, 3.2], "yellow"),
                cube([-4.2, 18.1, -8.4], [2.5, 2.5, 1.1], "black"),
                cube([1.7, 18.1, -8.4], [2.5, 2.5, 1.1], "black"),
                cube([-3.6, 19.3, -8.8], [1, 1, 0.7], "white"),
                cube([2.3, 19.3, -8.8], [1, 1, 0.7], "white"),
                cube([-2.7, 15.5, -8.8], [1.7, 1.15, 0.9], "brown"),
                cube([-0.85, 15.05, -8.9], [1.7, 1.15, 0.9], "brown"),
                cube([1.0, 15.5, -8.8], [1.7, 1.15, 0.9], "brown"),
                cube([-7.3, 16.5, -7.4], [2.3, 2.5, 2.3], "pink"),
                cube([5.0, 16.5, -7.4], [2.3, 2.5, 2.3], "pink"),
                cube([-6.8, 21.6, -2.2], [2.1, 2.8, 2.5], "yellow"),
                cube([4.7, 21.6, -2.2], [2.1, 2.8, 2.5], "yellow"),
            ],
        },
        {
            "name": "armL",
            "parent": "body",
            "pivot": [-7, 14, 0],
            "cubes": [cube([-11.4, 7.1, -2.5], [4.6, 6.8, 4.8], yellow)],
        },
        {
            "name": "armR",
            "parent": "body",
            "pivot": [7, 14, 0],
            "cubes": [cube([6.8, 7.1, -2.5], [4.6, 6.8, 4.8], yellow)],
        },
        {
            "name": "legL",
            "parent": "root",
            "pivot": [-3.5, 5, 0],
            "cubes": [
                cube([-6.3, 2.0, -2.5], [4.8, 3.2, 4.8], "yellow"),
                cube([-6.7, 0, -2.9], [5.6, 2.3, 5.6], "brown"),
            ],
        },
        {
            "name": "legR",
            "parent": "root",
            "pivot": [3.5, 5, 0],
            "cubes": [
                cube([1.5, 2.0, -2.5], [4.8, 3.2, 4.8], "yellow"),
                cube([1.1, 0, -2.9], [5.6, 2.3, 5.6], "brown"),
            ],
        },
    ]


def box_bones():
    side = face_uv(0, 0, 32, 32)
    top = face_uv(32, 0, 32, 32)
    bottom = face_uv(0, 32, 32, 32)
    return [
        {
            "name": "box",
            "pivot": [0, 0, 0],
            "cubes": [
                {
                    "origin": [-8, 0, -8],
                    "size": [16, 16, 16],
                    "uv": {
                        "north": side,
                        "south": side,
                        "east": side,
                        "west": side,
                        "up": top,
                        "down": bottom,
                    },
                }
            ],
        }
    ]


def geometry(identifier, bones, bounds):
    return {
        "format_version": "1.16.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": identifier,
                    "texture_width": 64,
                    "texture_height": 64,
                    "visible_bounds_width": bounds[0],
                    "visible_bounds_height": bounds[1],
                    "visible_bounds_offset": [0, 0.75, 0],
                },
                "bones": bones,
            }
        ],
    }


def iter_cubes(bones):
    for bone in bones:
        for cube_def in bone.get("cubes", []):
            yield bone["name"], cube_def


def face_corners(origin, size, face):
    x, y, z = origin
    w, h, d = size
    if face == "north":
        return [(x, y, z), (x + w, y, z), (x + w, y + h, z), (x, y + h, z)]
    if face == "south":
        return [(x + w, y, z + d), (x, y, z + d), (x, y + h, z + d), (x + w, y + h, z + d)]
    if face == "east":
        return [(x + w, y, z), (x + w, y, z + d), (x + w, y + h, z + d), (x + w, y + h, z)]
    if face == "west":
        return [(x, y, z + d), (x, y, z), (x, y + h, z), (x, y + h, z + d)]
    if face == "up":
        return [(x, y + h, z + d), (x + w, y + h, z + d), (x + w, y + h, z), (x, y + h, z)]
    return [(x, y, z), (x + w, y, z), (x + w, y, z + d), (x, y, z + d)]


def shade(color, face):
    gain = SHADE[face]
    return tuple(max(0, min(255, int(channel * gain))) for channel in color[:3]) + (255,)


def sample(texture, u, v):
    w, h = texture.size
    x = min(w - 1, max(0, int(u)))
    y = min(h - 1, max(0, int(v)))
    return texture.getpixel((x, y))


def project_front(point, scale, origin):
    x, y, _z = point
    return (origin[0] + x * scale, origin[1] - y * scale)


def project_iso(point, scale, origin):
    x, y, z = point
    sx = (x - z) * 0.86
    sy = y + (x + z) * 0.5
    return (origin[0] + sx * scale, origin[1] - sy * scale)


def draw_model(texture, bones, mode, size, ground=True):
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    raw = []
    for _name, cube_def in iter_cubes(bones):
        for face in SIDES:
            raw.extend(face_corners(cube_def["origin"], cube_def["size"], face))
    project_unit = project_front if mode == "front" else project_iso
    unit = [project_unit(p, 1, (0, 0)) for p in raw]
    min_x = min(p[0] for p in unit)
    max_x = max(p[0] for p in unit)
    min_y = min(p[1] for p in unit)
    max_y = max(p[1] for p in unit)
    scale = min((size[0] - 80) / max(max_x - min_x, 1), (size[1] - 120) / max(max_y - min_y, 1))
    origin = (
        size[0] / 2 - (min_x + max_x) * 0.5 * scale,
        size[1] / 2 - (min_y + max_y) * 0.5 * scale,
    )
    project = project_unit

    if ground:
        xs = [p[0] for p in raw]
        zs = [p[2] for p in raw]
        gx0, gy0 = project((min(xs), 0, min(zs)), scale, origin)
        gx1, gy1 = project((max(xs), 0, max(zs)), scale, origin)
        ellipse = [min(gx0, gx1) - 18, min(gy0, gy1) - 8, max(gx0, gx1) + 18, max(gy0, gy1) + 16]
        draw.ellipse(ellipse, fill=(90, 140, 70, 90))

    visible = {"north"} if mode == "front" else {"north", "west", "up"}
    polys = []
    for _name, cube_def in iter_cubes(bones):
        for face in SIDES:
            if face not in visible:
                continue
            corners = face_corners(cube_def["origin"], cube_def["size"], face)
            uv = cube_def["uv"][face]
            u0, v0 = uv["uv"]
            uw, uh = uv["uv_size"]
            depth = sum(p[2] for p in corners) / 4 if mode == "front" else sum(p[0] + p[2] for p in corners) / 4
            polys.append((depth, face, corners, u0, v0, uw, uh))
    # Farther faces (larger depth) paint first.
    polys.sort(key=lambda item: -item[0])

    steps = 16
    for _depth, face, corners, u0, v0, uw, uh in polys:
        for iy in range(steps):
            for ix in range(steps):
                def mix(a, b, t):
                    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))

                def at(fx, fy):
                    # corners: bl, br, tr, tl in face space. fy 0 is bottom.
                    bottom = mix(corners[0], corners[1], fx)
                    top = mix(corners[3], corners[2], fx)
                    return mix(bottom, top, fy)

                quad = [at(ix / steps, iy / steps), at((ix + 1) / steps, iy / steps), at((ix + 1) / steps, (iy + 1) / steps), at(ix / steps, (iy + 1) / steps)]
                screen = [project(p, scale, origin) for p in quad]
                if mode == "front":
                    area = 0
                    for i, p in enumerate(screen):
                        q = screen[(i + 1) % 4]
                        area += p[0] * q[1] - q[0] * p[1]
                    # Screen Y grows downward, so the front faces come out negative.
                    if area >= 0:
                        continue
                fu = u0 + (ix + 0.5) / steps * uw
                fv = v0 + uh - (iy + 0.5) / steps * uh
                color = shade(sample(texture, fu, fv), face)
                draw.polygon(screen, fill=color)
    return img


def font(size, bold=False):
    paths = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for path in paths:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


MC = {
    "0": (0, 0, 0),
    "1": (0, 0, 170),
    "2": (0, 170, 0),
    "3": (0, 170, 170),
    "4": (170, 0, 0),
    "5": (170, 0, 170),
    "6": (255, 170, 0),
    "7": (170, 170, 170),
    "8": (85, 85, 85),
    "9": (85, 85, 255),
    "a": (85, 255, 85),
    "b": (85, 255, 255),
    "c": (255, 85, 85),
    "d": (255, 85, 255),
    "e": (255, 255, 85),
    "f": (255, 255, 255),
}


def draw_mc(draw, xy, text, size, anchor="lt"):
    face = font(size, bold=True)
    color = MC["f"]
    parts = []
    i = 0
    while i < len(text):
        if text[i] == "§" and i + 1 < len(text):
            color = MC.get(text[i + 1], color)
            i += 2
            continue
        parts.append((text[i], color))
        i += 1
    widths = [draw.textlength(ch, font=face) for ch, _color in parts]
    total = sum(widths)
    x, y = xy
    if anchor == "mt":
        x -= total / 2
    for (ch, col), width in zip(parts, widths):
        draw.text((x + 2, y + 2), ch, font=face, fill=(0, 0, 0))
        draw.text((x, y), ch, font=face, fill=col)
        x += width
    return total


def pack_icon(character):
    icon = Image.new("RGBA", (128, 128), (196, 154, 108, 255))
    draw = ImageDraw.Draw(icon)
    draw.rounded_rectangle((6, 6, 121, 121), 18, fill=(232, 184, 64, 255), outline=(120, 78, 24, 255), width=6)
    fitted = character.resize((96, 96), Image.Resampling.NEAREST)
    icon.alpha_composite(fitted, (16, 22))
    return icon


def hud_mock():
    img = Image.new("RGBA", (900, 520), (126, 186, 232, 255))
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 360, 900, 520), fill=(86, 168, 72, 255))
    draw.rectangle((0, 430, 900, 520), fill=(112, 78, 48, 255))
    title = "§eObesity §f[§e#######§8---§f] §f14/20"
    sub = "§7Hold food and tap Obesity"
    draw_mc(draw, (450, 28), title, 36, anchor="mt")
    draw_mc(draw, (450, 78), sub, 22, anchor="mt")
    # The player's own hunger stays above the hotbar, so Obesity's bar is separate.
    draw.rounded_rectangle((250, 448, 650, 500), 6, fill=(40, 40, 40, 255))
    for i in range(9):
        draw.rectangle((262 + i * 42, 456, 298 + i * 42, 492), fill=(90, 90, 90, 255))
    draw_mc(draw, (760, 400), "§c❤❤❤❤❤❤❤❤❤❤", 18, anchor="lt")
    return img


def storyboard(character, box):
    canvas = Image.new("RGBA", (1320, 560), (24, 28, 34, 255))
    draw = ImageDraw.Draw(canvas)
    panels = [
        (30, "1. Place the box", box, "A cardboard cube with a dark slot."),
        (450, "2. Tap it open", character, "Obesity pops out, chubby and yellow."),
        (870, "3. Feed them", hud_mock().resize((390, 226), Image.Resampling.LANCZOS), "Their hunger stays at the top."),
    ]
    face = font(28, bold=True)
    small = font(18, bold=False)
    for x, title, picture, caption in panels:
        draw.rounded_rectangle((x, 24, x + 400, 530), 16, fill=(250, 246, 236, 255))
        draw.text((x + 24, 40), title, font=face, fill=(40, 32, 24, 255))
        shot = picture.resize((340, 300), Image.Resampling.NEAREST)
        canvas.alpha_composite(shot, (x + 30, 100))
        draw.text((x + 24, 430), caption, font=small, fill=(70, 58, 40, 255))
    return canvas


def assert_character(image):
    yellow = black = pink = brown = 0
    for r, g, b, a in image.getdata():
        if a < 16:
            continue
        if r > 160 and g > 110 and b < 140 and r > b + 40:
            yellow += 1
        if r < 45 and g < 45 and b < 45:
            black += 1
        if r > 190 and 90 < g < 190 and b > 120:
            pink += 1
        if 50 < r < 150 and g < 100 and b < 70:
            brown += 1
    if yellow < 400 or black < 20 or pink < 12 or brown < 30:
        raise SystemExit(
            f"character preview is missing features: yellow={yellow} black={black} pink={pink} brown={brown}"
        )


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    body_tex = entity_texture()
    carton_tex = box_texture()
    body = character_bones()
    carton = box_bones()
    body_tex.save(RP / "textures" / "entity" / "obesity.png")
    carton_tex.save(RP / "textures" / "blocks" / "obesity_box.png")
    write_json(
        RP / "models" / "entity" / "obesity.geo.json",
        geometry("geometry.obesity.character", body, (3, 3)),
    )
    write_json(
        RP / "models" / "blocks" / "obesity_box.geo.json",
        geometry("geometry.obesity.box", carton, (2, 2)),
    )
    front = draw_model(body_tex, body, "front", (640, 720))
    iso = draw_model(body_tex, body, "iso", (720, 720))
    box_front = draw_model(carton_tex, carton, "iso", (640, 640))
    assert_character(front)
    icon = pack_icon(front)
    icon.save(BP / "pack_icon.png")
    icon.save(RP / "pack_icon.png")
    board = storyboard(front, box_front)
    hud = hud_mock()
    PREVIEW.mkdir(parents=True, exist_ok=True)
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    for folder in (PREVIEW, ARTIFACTS):
        front.save(folder / "obesity-character.png")
        iso.save(folder / "obesity-character-iso.png")
        box_front.save(folder / "obesity-box.png")
        board.save(folder / "obesity-storyboard.png")
        hud.save(folder / "obesity-hud.png")
        icon.save(folder / "obesity-pack-icon.png")
    print("generated Obesity art")


if __name__ == "__main__":
    main()
