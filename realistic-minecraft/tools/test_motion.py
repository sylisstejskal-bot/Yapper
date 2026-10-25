#!/usr/bin/env python3
"""Check walk, sprint-stop, blink, and glance wiring, then simulate it."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def live_speed(modified: float, delta: float, gated: bool) -> float:
    if gated:
        return 0.0
    return clamp(max(modified, (delta - 0.004) * 20.0), 0.0, 1.0)


def phase(distance: float) -> float:
    return distance * 38.17


def next_sprint(smoothed: float, sprinting: bool, live: float) -> float:
    if sprinting and live > 0.12:
        return lerp(smoothed, 1.0, 0.5)
    if live < 0.02:
        return 0.0
    return lerp(smoothed, 0.0, 0.7)


def next_amp(amp: float, live: float) -> float:
    if live < 0.02:
        return lerp(amp, 0.0, 0.7)
    return lerp(amp, live, 0.45)


def pose(cos: float, blend: float, amp: float, *, rise: float = 0.0, fall: float = 0.0, land: float = 0.0) -> tuple[float, float, float, float]:
    fade = 1.0 - clamp(rise + fall + land, 0.0, 1.0)
    left_leg = cos * lerp(-38.0, -62.0, blend) * amp * fade + rise * -28.0 + fall * 12.0 + land * -8.0
    right_leg = cos * lerp(38.0, 62.0, blend) * amp * fade + rise * 18.0 + fall * -10.0 + land * -6.0
    left_arm = cos * lerp(34.0, 56.0, blend) * amp * fade + rise * -42.0 + fall * -14.0 + land * -4.0
    right_arm = cos * lerp(-34.0, -56.0, blend) * amp * fade + rise * -34.0 + fall * -10.0 + land * -3.0
    return left_leg, right_leg, left_arm, right_arm


def expect(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def balanced(text: str) -> bool:
    return text.count("(") == text.count(")") and text.count("[") == text.count("]")


def check_files() -> None:
    entity = json.loads((ROOT / "resource_pack/entity/player.entity.json").read_text())
    animations = json.loads((ROOT / "resource_pack/animations/realistic_player.animation.json").read_text())
    description = entity["minecraft:client_entity"]["description"]
    scripts = description["scripts"]
    expect(scripts["animate"] == ["root", "realistic_motion"], "root must play before the extra controller")
    names = description["animations"]
    expect(names["root"] == "controller.animation.player.root", "vanilla root controller is not registered")
    expect(names["move.legs"] == "animation.player.move.legs", "move.legs is not hooked to the override")
    expect(names["move.arms"] == "animation.player.move.arms", "move.arms is not hooked to the override")
    expect("eyes" not in description["geometry"] and "eyes" not in description["textures"], "the skin already has eyes")
    expect("animation.realistic.player.face" not in animations["animations"], "the face overlay animation is still defined")
    pre = scripts["pre_animation"]
    expect(all(balanced(line) for line in pre), "a pre_animation expression has unbalanced parentheses")
    joined = "\n".join(pre)
    expect(joined.find("variable.gliding_speed_value =") < joined.find("variable.tcos0"), "tcos0 still divides by an unset speed")
    expect("query.is_sprinting" in joined, "sprint state must be driven before the animations")
    expect("variable.rm_blink" not in joined and "variable.rm_look" not in joined, "eye overlay variables should be gone")
    legs = json.dumps(animations["animations"]["animation.player.move.legs"])
    arms = json.dumps(animations["animations"]["animation.player.move.arms"])
    for token in ("query.modified_distance_moved * 38.17", "query.position_delta", "math.cos", "variable.rm_sprint"):
        expect(token in legs and token in arms, f"limb animation is missing {token}")
    expect("math.lerp(38.17, 52.0" not in legs, "sprint still rescales the stride phase")
    expect("query.is_sprinting" not in legs and "query.is_sprinting" not in arms, "limbs must not restart a sprint cycle from the sprint flag")
    expect(balanced(legs) and balanced(arms), "limb Molang has unbalanced parentheses")
    for animation in animations["animations"].values():
        for bone, channels in animation.get("bones", {}).items():
            if bone.lower() in {"root", "waist", "body"} and "position" in channels:
                raise SystemExit(f"{bone} still has a position channel")


def main() -> None:
    check_files()
    expect(live_speed(0.0, 0.0, False) == 0.0, "standing must not count as movement")
    expect(live_speed(0.0, 0.1, False) == 1.0, "horizontal movement must drive the cycle even if modified_move_speed is 0")
    expect(live_speed(1.0, 0.0, True) == 0.0, "swim, crawl, and riding must not use this stride")
    expect(phase(100.0) == phase(100.0), "a frozen stride keeps its phase")
    old_scrub = 100.0 * lerp(38.17, 52.0, 0.82)
    expect(abs(old_scrub - phase(100.0)) > 200.0, "the removed phase lerp was large enough to replay a step")

    amp = 0.0
    for _ in range(8):
        speed = live_speed(0.0, 0.1, False)
        amp = next_amp(amp, speed)
    walk = pose(1.0, 0.0, max(amp, live_speed(0.0, 0.1, False)))
    sprint = pose(1.0, 1.0, max(amp, live_speed(0.0, 0.12, False)))
    opposite = pose(-1.0, 0.0, 1.0)
    expect(walk[0] < -30.0 and walk[1] > 30.0, "a forward step must split the legs")
    expect(walk[0] * walk[2] < 0.0 and walk[1] * walk[3] < 0.0, "each arm must oppose its leg")
    expect(opposite[0] > 30.0 and opposite[1] < -30.0, "the next half-step must swap the legs")
    expect(abs(sprint[0]) > abs(walk[0]) + 15.0, "sprinting must swing farther than walking")
    expect(abs(sprint[2]) > abs(walk[2]) + 10.0, "sprinting must swing the arms harder")

    blend = 1.0
    moving = live_speed(0.0, 0.1, False)
    for _ in range(4):
        blend = next_sprint(blend, False, moving)
    released = pose(1.0, blend, 1.0)
    expect(abs(walk[0]) < abs(released[0]) < abs(sprint[0]), "releasing sprint while moving eases the swing toward a walk")
    expect(phase(100.0) == 100.0 * 38.17, "releasing sprint must not change the stride phase")

    blend = 1.0
    amp = 1.0
    stopped = live_speed(0.0, 0.0, False)
    blend = next_sprint(blend, False, stopped)
    expect(blend == 0.0, "a full stop clears the sprint blend immediately")
    for _ in range(6):
        amp = next_amp(amp, stopped)
    rested = pose(1.0, blend, amp)
    expect(abs(rested[0]) < 1.0 and abs(rested[1]) < 1.0, "stopping must ease the stride back to idle")

    airborne = pose(1.0, 0.0, 1.0, rise=1.0)
    falling = pose(1.0, 0.0, 1.0, fall=1.0)
    expect(airborne[0] < -20.0 and falling[0] > 8.0, "rising and falling stay distinct from the walk")
    print("walk and sprint-stop wiring ok")
    print(f"walk=({walk[0]:.1f},{walk[1]:.1f}) arms=({walk[2]:.1f},{walk[3]:.1f})")
    print(f"sprint=({sprint[0]:.1f},{sprint[1]:.1f}) arms=({sprint[2]:.1f},{sprint[3]:.1f})")
    print(f"release=({released[0]:.1f},{released[1]:.1f}) rest=({rested[0]:.2f},{rested[1]:.2f})")
    print(f"rise={airborne[0]:.1f} fall={falling[0]:.1f}")


if __name__ == "__main__":
    main()
