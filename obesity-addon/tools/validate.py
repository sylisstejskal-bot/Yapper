#!/usr/bin/env python3
"""Check the Obesity add-on packs and build Obesity.mcaddon."""

import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BP = ROOT / "behavior_packs" / "Obesity_BP"
RP = ROOT / "resource_packs" / "Obesity_RP"
MCADDON = ROOT / "Obesity.mcaddon"

BONES = {"root", "body", "belly", "head", "armL", "armR", "legL", "legR"}
SHORT_ANIMS = {"pop", "idle", "look", "walk", "hungry", "eat", "controller"}


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def png(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path} is not a PNG")
    if len(data) < 32:
        raise SystemExit(f"{path} is empty")


def walk_json(folder):
    for path in folder.rglob("*.json"):
        load(path)


def controller_names(controller):
    found = set()
    states = controller["animation_controllers"]["controller.animation.obesity"]["states"]
    for state in states.values():
        for item in state["animations"]:
            if isinstance(item, str):
                found.add(item)
            else:
                found.update(item.keys())
    return found


def main():
    walk_json(BP)
    walk_json(RP)
    bp = load(BP / "manifest.json")
    rp = load(RP / "manifest.json")
    if bp["dependencies"][0]["uuid"] != rp["header"]["uuid"]:
        raise SystemExit("behavior pack does not depend on the resource pack")
    if bp["header"]["min_engine_version"] != [1, 21, 90]:
        raise SystemExit("unexpected engine version")
    script = next(item for item in bp["dependencies"] if item.get("module_name") == "@minecraft/server")
    if script["version"] != "2.0.0":
        raise SystemExit("script module must be stable 2.0.0 so Android does not need the beta experiment")
    if not (BP / "scripts" / "main.js").exists() or not (BP / "scripts" / "logic.js").exists():
        raise SystemExit("scripts missing")
    main_js = (BP / "scripts" / "main.js").read_text(encoding="utf-8")
    if 'from "./logic.js"' not in main_js:
        raise SystemExit("main.js does not import logic.js")
    if "Beta APIs" in json.dumps(bp) or "beta" in script["version"]:
        raise SystemExit("pack requires a beta API")

    geo = load(RP / "models" / "entity" / "obesity.geo.json")
    bones = {bone["name"] for bone in geo["minecraft:geometry"][0]["bones"]}
    if bones != BONES:
        raise SystemExit(f"unexpected bones: {bones}")
    box = load(RP / "models" / "blocks" / "obesity_box.geo.json")
    if box["minecraft:geometry"][0]["description"]["identifier"] != "geometry.obesity.box":
        raise SystemExit("box geometry id mismatch")

    client = load(RP / "entity" / "obesity.entity.json")["minecraft:client_entity"]["description"]
    if set(client["animations"]) != SHORT_ANIMS:
        raise SystemExit("client entity animation map drifted")
    controller = load(RP / "animation_controllers" / "obesity.animation_controllers.json")
    used = controller_names(controller)
    missing = used - set(client["animations"])
    if missing:
        raise SystemExit(f"animation controller uses unknown clips: {missing}")
    anims = load(RP / "animations" / "obesity.animation.json")["animations"]
    for short, full in client["animations"].items():
        if short == "controller":
            continue
        if full not in anims:
            raise SystemExit(f"missing animation {full}")
        for bone in anims[full]["bones"]:
            if bone not in BONES:
                raise SystemExit(f"{full} animates unknown bone {bone}")

    for path in (
        BP / "pack_icon.png",
        RP / "pack_icon.png",
        RP / "textures" / "entity" / "obesity.png",
        RP / "textures" / "blocks" / "obesity_box.png",
    ):
        png(path)

    if MCADDON.exists():
        MCADDON.unlink()
    with zipfile.ZipFile(MCADDON, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        for folder, prefix in ((BP, "Obesity_BP"), (RP, "Obesity_RP")):
            for path in sorted(folder.rglob("*")):
                if path.is_file():
                    bundle.write(path, f"{prefix}/{path.relative_to(folder).as_posix()}")
    with zipfile.ZipFile(MCADDON) as bundle:
        names = set(bundle.namelist())
    for required in (
        "Obesity_BP/manifest.json",
        "Obesity_BP/scripts/main.js",
        "Obesity_BP/scripts/logic.js",
        "Obesity_BP/entities/obesity.json",
        "Obesity_BP/blocks/obesity_box.json",
        "Obesity_RP/manifest.json",
        "Obesity_RP/entity/obesity.entity.json",
        "Obesity_RP/textures/entity/obesity.png",
        "Obesity_RP/pack_icon.png",
    ):
        if required not in names:
            raise SystemExit(f"mcaddon missing {required}")
    print(f"validated {MCADDON.name} ({MCADDON.stat().st_size} bytes, {len(names)} files)")


if __name__ == "__main__":
    main()
