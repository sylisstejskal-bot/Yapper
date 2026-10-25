#!/usr/bin/env python3
"""Check the addon, then write dist/Realistic-Minecraft.mcaddon."""

from __future__ import annotations

import json
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RP = ROOT / "resource_pack"
BP = ROOT / "behavior_pack"
DIST = ROOT / "dist" / "Realistic-Minecraft.mcaddon"

RP_UUID = "cd02cbfd-373b-4365-b17c-95839de81131"
BP_UUID = "fb54b911-f8e3-41ec-8397-f7cc5b838423"
ERRORS: list[str] = []


def fail(message: str) -> None:
    ERRORS.append(message)


def load_json(path: Path):
    try:
        return json.loads(path.read_text())
    except json.JSONDecodeError as error:
        fail(f"{path.relative_to(ROOT)} is not valid JSON: {error}")
        return None


def check_manifests() -> None:
    rp = load_json(RP / "manifest.json")
    bp = load_json(BP / "manifest.json")
    if not rp or not bp:
        return
    if rp["format_version"] != 2 or bp["format_version"] != 2:
        fail("manifest format_version must be 2")
    if rp["header"]["uuid"] != RP_UUID:
        fail("resource pack uuid changed")
    if bp["header"]["uuid"] != BP_UUID:
        fail("behavior pack uuid changed")
    if rp["header"]["min_engine_version"] != [1, 26, 0]:
        fail("resource pack min_engine_version must be 1.26.0")
    if bp["header"]["min_engine_version"] != [1, 26, 0]:
        fail("behavior pack min_engine_version must be 1.26.0")
    if "pbr" not in rp.get("capabilities", []):
        fail("resource pack is missing the pbr capability")
    modules = {module["type"]: module for module in bp["modules"]}
    if modules.get("script", {}).get("entry") != "scripts/main.js":
        fail("script entry must be scripts/main.js")
    deps = bp["dependencies"]
    server = next(item for item in deps if "module_name" in item)
    if server["module_name"] != "@minecraft/server" or server["version"] != "2.6.0":
        fail("script dependency must be @minecraft/server 2.6.0")
    linked = next(item for item in deps if "uuid" in item)
    if linked["uuid"] != RP_UUID:
        fail("behavior pack must depend on the resource pack uuid")
    uuids = [
        rp["header"]["uuid"],
        rp["modules"][0]["uuid"],
        bp["header"]["uuid"],
        *[module["uuid"] for module in bp["modules"]],
    ]
    if len(uuids) != len(set(uuids)):
        fail("pack uuids are not unique")


def check_player() -> None:
    entity = load_json(RP / "entity" / "player.entity.json")
    animations = load_json(RP / "animations" / "realistic_player.animation.json")
    controller = load_json(RP / "animation_controllers" / "realistic_player.animation_controllers.json")
    if not entity or not animations or not controller:
        return
    description = entity["minecraft:client_entity"]["description"]
    if description.get("identifier") != "minecraft:player":
        fail("player entity identifier changed")
    if description.get("min_engine_version") != "1.8.0":
        fail("player entity is missing min_engine_version 1.8.0")
    if "realistic_motion" not in description["scripts"]["animate"]:
        fail("player animate list is missing realistic_motion")
    if description["scripts"]["animate"][0] != "root":
        fail("vanilla root animation must stay first")
    names = description["animations"]
    for key in ("root", "move.legs", "move.arms", "third_person_melee_spear_controller", "realistic_motion"):
        if key not in names:
            fail(f"player animations map is missing {key}")
    short_names = {
        "realistic_breathe",
        "realistic_lean",
        "realistic_jump",
        "realistic_fall",
        "realistic_land",
    }
    if not short_names <= set(names):
        fail("player entity is missing a realistic animation short name")
    defined = set(animations["animations"])
    for short in short_names:
        target = names[short]
        if target not in defined:
            fail(f"{short} points at {target}, which is not defined")
    for required in (
        "animation.player.move.legs",
        "animation.player.move.arms",
        "animation.player.first_person.walk",
    ):
        if required not in defined:
            fail(f"missing override {required}")
    controllers = controller["animation_controllers"]
    if list(controllers) != ["controller.animation.realistic.player.motion"]:
        fail("player motion must stay on one animation controller")
    states = controllers["controller.animation.realistic.player.motion"]["states"]
    for state in ("ground", "jump", "fall", "land"):
        if state not in states:
            fail(f"motion controller is missing {state}")
    pre_animation = "\n".join(description["scripts"]["pre_animation"])
    for token in ("variable.rm_stride", "variable.rm_leg_l", "variable.rm_landing", "variable.tcos0"):
        if token not in pre_animation:
            fail(f"player pre_animation is missing {token}")
    if description["scripts"]["animate"] != ["root", "realistic_motion"]:
        fail("player animate list must stay root, then realistic_motion")
    grounded = {"root", "waist", "body", "leftleg", "rightleg", "leftarm", "rightarm"}
    for animation_name, animation in animations["animations"].items():
        for bone, channels in animation.get("bones", {}).items():
            if "position" not in channels:
                continue
            if animation_name == "animation.player.first_person.walk" and bone.lower() in {"leftarm", "rightarm"}:
                continue
            if bone.lower() in grounded:
                fail(f"{animation_name} moves {bone}, which lifts or sinks the player")
    leg_rotation = json.dumps(animations["animations"]["animation.player.move.legs"])
    if "variable.rm_leg_l" not in leg_rotation or "variable.rm_leg_r" not in leg_rotation:
        fail("walk cycle must drive both legs from the smoothed stride")
    land_bones = animations["animations"]["animation.realistic.player.land"]["bones"]
    if set(land_bones) != {"waist"}:
        fail("landing may only pitch the waist")
    if (RP / "textures" / "terrain_texture.json").exists():
        fail("do not replace terrain_texture.json; it is not a merge")
    if (RP / "textures" / "flipbook_textures.json").exists():
        fail("do not replace flipbook_textures.json")


def check_images() -> None:
    hud = {
        "heart.png": (9, 9),
        "heart_half.png": (9, 9),
        "hunger_full.png": (9, 9),
        "hotbar_0.png": (20, 22),
        "hotbar_8.png": (20, 22),
        "hotbar_start_cap.png": (1, 22),
        "selected_hotbar_slot.png": (24, 24),
        "experiencebarempty.png": (13, 5),
        "experiencebarfull.png": (13, 5),
    }
    for name, size in hud.items():
        path = RP / "textures" / "ui" / name
        if not path.exists():
            fail(f"missing HUD texture {name}")
            continue
        with Image.open(path) as image:
            if image.size != size:
                fail(f"{name} is {image.size}, expected {size}")
    for path in (RP / "textures").rglob("*"):
        if path.suffix.lower() not in {".png", ".tga"}:
            continue
        if "_h.png" in path.name or "particle" in path.parts or "ui" in path.parts or "items" in path.parts:
            continue
        with Image.open(path) as image:
            if image.size != (32, 32):
                fail(f"{path.relative_to(RP)} is {image.size}, expected 32x32")
    for path in (RP / "textures" / "items").glob("*.png"):
        with Image.open(path) as image:
            if image.size != (32, 32):
                fail(f"item {path.name} is {image.size}")
    for path in (RP / "textures" / "particle").glob("*.png"):
        with Image.open(path) as image:
            if image.size != (16, 16):
                fail(f"particle texture {path.name} is {image.size}")
    for path in RP.rglob("*.texture_set.json"):
        data = load_json(path)
        if not data:
            continue
        body = data.get("minecraft:texture_set", {})
        if body.get("format_version", data.get("format_version")) != "1.21.30" and data.get("format_version") != "1.21.30":
            fail(f"{path.name} format_version is not 1.21.30")
        stem = path.name.replace(".texture_set.json", "")
        color = next((path.parent / f"{stem}{ext}" for ext in (".png", ".tga") if (path.parent / f"{stem}{ext}").exists()), None)
        height = path.parent / f"{stem}_h.png"
        if color is None:
            fail(f"{path.name} is missing its color texture")
        if not height.exists():
            fail(f"{path.name} is missing {stem}_h.png")
        mers = body.get("metalness_emissive_roughness_subsurface")
        if not (isinstance(mers, list) and len(mers) == 4):
            fail(f"{path.name} needs a 4-channel MERS value")
        elif mers[0] > 0 and mers[3] > 0:
            fail(f"{path.name} sets metal and subsurface together")


def check_particles() -> None:
    ids = set()
    for path in (RP / "particles").glob("*.particle.json"):
        data = load_json(path)
        if not data:
            continue
        effect = data["particle_effect"]
        identifier = effect["description"]["identifier"]
        if identifier in ids:
            fail(f"duplicate particle {identifier}")
        ids.add(identifier)
        if not identifier.startswith("realistic:"):
            fail(f"{identifier} must use the realistic namespace")
        texture = effect["description"]["basic_render_parameters"]["texture"]
        png = RP / f"{texture}.png"
        if not png.exists():
            fail(f"{identifier} texture is missing: {texture}")
        count = effect["components"]["minecraft:emitter_rate_instant"]["num_particles"]
        if count > 12:
            fail(f"{identifier} spawns {count} particles; keep bursts small for phones")
    for required in ("realistic:puff", "realistic:impact", "realistic:leaf", "realistic:spark"):
        if required not in ids:
            fail(f"missing particle {required}")


def check_scripts() -> None:
    main = (BP / "scripts" / "main.js").read_text()
    tuning = (BP / "scripts" / "tuning.js").read_text()
    if "camera.addShake" in main or "addShake" in main:
        fail("do not call Camera.addShake; it is not in the 2.6.0 API this pack depends on")
    if "setFov" in main:
        fail("do not lock FOV; it replaces the player's setting")
    if "camerashake add" not in main:
        fail("impact shake should use the camerashake command")
    if "from \"./tuning.js\"" not in main:
        fail("main.js should keep the editable numbers in tuning.js")
    if "export function landingAction" not in tuning:
        fail("tuning.js is missing landingAction")


def package() -> None:
    DIST.parent.mkdir(parents=True, exist_ok=True)
    if DIST.exists():
        DIST.unlink()
    with zipfile.ZipFile(DIST, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for folder in (RP, BP):
            for path in folder.rglob("*"):
                if path.is_file():
                    archive.write(path, path.relative_to(ROOT).as_posix())
    with zipfile.ZipFile(DIST) as archive:
        names = set(archive.namelist())
    for required in (
        "resource_pack/manifest.json",
        "behavior_pack/manifest.json",
        "behavior_pack/scripts/main.js",
        "resource_pack/entity/player.entity.json",
        "resource_pack/pack_icon.png",
        "behavior_pack/pack_icon.png",
    ):
        if required not in names:
            fail(f"mcaddon is missing {required}")
    if any(name.startswith("tools/") or name.endswith("package.json") for name in names):
        fail("mcaddon should contain only the two packs")


def main() -> None:
    check_manifests()
    check_player()
    check_images()
    check_particles()
    check_scripts()
    for path in list(RP.rglob("*.json")) + list(BP.rglob("*.json")):
        load_json(path)
    if ERRORS:
        for message in ERRORS:
            print(f"ERROR {message}")
        raise SystemExit(1)
    package()
    print(f"ok {DIST} ({DIST.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
