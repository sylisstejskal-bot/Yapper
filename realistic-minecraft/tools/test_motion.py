#!/usr/bin/env python3
"""Check that the walk and sprint cycle is wired into the player, then simulate it."""

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


def sprint_blend(smoothed: float, sprinting: bool) -> float:
    return clamp(smoothed + (1.0 if sprinting else 0.0), 0.0, 1.0)


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
    pre = scripts["pre_animation"]
    expect(all(balanced(line) for line in pre), "a pre_animation expression has unbalanced parentheses")
    joined = "\n".join(pre)
    expect(joined.find("variable.gliding_speed_value =") < joined.find("variable.tcos0"), "tcos0 still divides by an unset speed")
    legs = json.dumps(animations["animations"]["animation.player.move.legs"])
    arms = json.dumps(animations["animations"]["animation.player.move.arms"])
    for token in ("query.modified_distance_moved", "query.position_delta", "query.is_sprinting", "math.cos"):
        expect(token in legs and token in arms, f"limb animation is missing {token}")
    expect("variable.rm_leg_l" not in legs, "legs still read the old zeroed variable")
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

    amp = 0.0
    for _ in range(8):
        speed = live_speed(0.0, 0.1, False)
        amp = lerp(amp, speed, 0.45 if speed > amp else 0.12)
    walk = pose(1.0, sprint_blend(0.0, False), max(amp, live_speed(0.0, 0.1, False)))
    sprint = pose(1.0, sprint_blend(0.0, True), max(amp, live_speed(0.0, 0.12, False)))
    opposite = pose(-1.0, sprint_blend(0.0, False), 1.0)
    expect(walk[0] < -30.0 and walk[1] > 30.0, "a forward step must split the legs")
    expect(walk[0] * walk[2] < 0.0 and walk[1] * walk[3] < 0.0, "each arm must oppose its leg")
    expect(opposite[0] > 30.0 and opposite[1] < -30.0, "the next half-step must swap the legs")
    expect(abs(sprint[0]) > abs(walk[0]) + 15.0, "sprinting must swing farther than walking")
    expect(abs(sprint[2]) > abs(walk[2]) + 10.0, "sprinting must swing the arms harder")

    amp = 1.0
    for _ in range(24):
        amp = lerp(amp, 0.0, 0.12)
    rested = pose(1.0, 0.0, amp)
    expect(abs(rested[0]) < 3.0 and abs(rested[1]) < 3.0, "stopping must ease the stride back to rest")

    airborne = pose(1.0, 0.0, 1.0, rise=1.0)
    falling = pose(1.0, 0.0, 1.0, fall=1.0)
    expect(airborne[0] < -20.0 and falling[0] > 8.0, "rising and falling stay distinct from the walk")
    print("walk and sprint wiring ok")
    print(f"walk=({walk[0]:.1f},{walk[1]:.1f}) arms=({walk[2]:.1f},{walk[3]:.1f})")
    print(f"sprint=({sprint[0]:.1f},{sprint[1]:.1f}) arms=({sprint[2]:.1f},{sprint[3]:.1f})")
    print(f"rest=({rested[0]:.2f},{rested[1]:.2f}) rise={airborne[0]:.1f} fall={falling[0]:.1f}")


if __name__ == "__main__":
    main()
