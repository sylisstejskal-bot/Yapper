#!/usr/bin/env python3
"""Zip the behavior pack and resource pack into dist/The-Creator.mcaddon."""

import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
OUT = DIST / "The-Creator.mcaddon"
PACKS = (
    ("behavior_pack", "TheCreator_BP"),
    ("resource_pack", "TheCreator_RP"),
)


def main():
    for folder, _name in PACKS:
        manifest = ROOT / folder / "manifest.json"
        json.loads(manifest.read_text(encoding="utf-8"))
    DIST.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for folder, name in PACKS:
            base = ROOT / folder
            for path in base.rglob("*"):
                if not path.is_file():
                    continue
                if path.name.endswith(".py"):
                    continue
                archive.write(path, f"{name}/{path.relative_to(base).as_posix()}")
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
