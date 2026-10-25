#!/usr/bin/env python3
"""Simulate the player limb blend used by the resource-pack Molang."""

from __future__ import annotations


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


class Pose:
    def __init__(self) -> None:
        self.stride = 0.0
        self.landing = 0.0
        self.was_air = 0.0
        self.air_time = 0.0
        self.air_start = 0.0
        self.rise = 0.0
        self.fall = 0.0
        self.rise_clock = 0.0
        self.rise_start = 0.0
        self.rise_was = 0.0
        self.crouch = 0.0
        self.leg_l = 0.0
        self.leg_r = 0.0
        self.arm_l = 0.0
        self.arm_r = 0.0
        self.just_landed = False


def step(
    pose: Pose,
    *,
    tcos: float,
    speed: float,
    on_ground: bool,
    dy: float,
    jumping: bool,
    move: bool,
    sprint: bool,
    sneak: bool,
    blocked: bool,
    life: float,
) -> None:
    scale = 1.2 if sprint else (0.58 if sneak else 1.0)
    pose.stride = lerp(pose.stride, tcos * scale, 0.55)
    pose.just_landed = on_ground and pose.was_air > 0.5 and pose.air_time > 0.12 and move
    pose.landing = 1.0 if pose.just_landed else lerp(pose.landing, 0.0, 0.2 if speed > 0.12 else 0.1)
    pose.air_start = life if (not on_ground and move and not (pose.was_air > 0.5)) else pose.air_start
    pose.air_time = (life - pose.air_start) if (not on_ground and move) else 0.0
    pose.was_air = 0.0 if (on_ground or not move) else 1.0
    pose.rise = lerp(pose.rise, 1.0 if (move and not on_ground and dy > 0.0) else 0.0, 0.22)
    pose.fall = lerp(pose.fall, 1.0 if (move and not on_ground and dy <= 0.0) else 0.0, 0.16)
    pose.rise_clock = (life - pose.rise_start) if (pose.rise > 0.35 and pose.rise_was > 0.35) else 0.0
    if pose.rise > 0.35 and not (pose.rise_was > 0.35):
        pose.rise_start = life
    pose.rise_was = pose.rise
    takeoff = clamp(1.0 - (pose.rise_clock / 0.14), 0.0, 1.0) * pose.rise
    rise_mix = 0.0 if pose.rise < 0.001 else clamp(takeoff / pose.rise, 0.0, 1.0)
    pose.crouch = lerp(
        pose.crouch,
        1.0 if (move and on_ground and jumping and pose.landing < 0.35) else 0.0,
        0.4,
    )
    pose.leg_l = lerp(
        lerp(lerp(lerp(pose.stride * -1.4, lerp(-32.0, -18.0, rise_mix), pose.rise), 12.0, pose.fall), -8.0, pose.landing),
        -9.0,
        pose.crouch,
    )
    pose.leg_r = lerp(
        lerp(lerp(lerp(pose.stride * 1.4, lerp(16.0, 10.0, rise_mix), pose.rise), -8.0, pose.fall), -6.0, pose.landing),
        -7.0,
        pose.crouch,
    )
    pose.arm_l = lerp(
        lerp(lerp(lerp(pose.stride, lerp(-44.0, 14.0, rise_mix), pose.rise), -14.0, pose.fall), -4.0, pose.landing),
        8.0,
        pose.crouch,
    )
    pose.arm_r = lerp(
        lerp(lerp(lerp(-pose.stride, lerp(-36.0, 8.0, rise_mix), pose.rise), -10.0, pose.fall), -3.0, pose.landing),
        6.0,
        pose.crouch,
    )
    if not move:
        pose.leg_l = tcos * -1.4
        pose.leg_r = tcos * 1.4
    if (not move) or blocked:
        pose.arm_l = tcos
        pose.arm_r = -tcos


def run_frames(count: int, **query) -> Pose:
    pose = Pose()
    life = 0.0
    for _ in range(count):
        life += 0.05
        step(pose, life=life, **query)
    return pose


def expect(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> None:
    still = run_frames(12, tcos=0.0, speed=0.0, on_ground=True, dy=0.0, jumping=False, move=True, sprint=False, sneak=False, blocked=False)
    expect(abs(still.leg_l) < 0.01 and abs(still.leg_r) < 0.01, "standing still must hold both legs at rest")
    expect(abs(still.arm_l) < 0.01 and abs(still.arm_r) < 0.01, "standing still must hold both arms at rest")

    walk = Pose()
    life = 0.0
    samples = []
    for tcos in (25.0, -25.0, 25.0, -25.0):
        for _ in range(8):
            life += 0.05
            step(walk, tcos=tcos, speed=0.7, on_ground=True, dy=0.0, jumping=False, move=True, sprint=False, sneak=False, blocked=False, life=life)
            samples.append((walk.leg_l, walk.leg_r, walk.arm_l, walk.arm_r))
    forward = [sample for sample in samples if sample[0] < -8.0]
    back = [sample for sample in samples if sample[0] > 8.0]
    expect(forward and back, "walk must swing the left leg both forward and back")
    expect(all(sample[0] * sample[1] < 0 for sample in forward + back), "walk legs must alternate")
    expect(all(sample[0] * sample[2] < 0 for sample in forward + back), "the same-side arm must oppose the leg")
    expect(all(sample[2] * sample[3] < 0 for sample in forward + back), "arms must oppose each other")

    stopped = walk
    for _ in range(18):
        life += 0.05
        step(stopped, tcos=0.0, speed=0.0, on_ground=True, dy=0.0, jumping=False, move=True, sprint=False, sneak=False, blocked=False, life=life)
    expect(abs(stopped.leg_l) < 1.0 and abs(stopped.leg_r) < 1.0, "walk must ease back to rest after stopping")

    rising = Pose()
    life = 0.0
    step(rising, tcos=0.0, speed=0.2, on_ground=False, dy=0.4, jumping=True, move=True, sprint=False, sneak=False, blocked=False, life=life + 0.05)
    life += 0.05
    expect(rising.leg_l > -12.0, "takeoff must not snap to the full rising tuck")
    expect(rising.arm_l > 0.0, "takeoff arms start back")
    for _ in range(18):
        life += 0.05
        step(rising, tcos=0.0, speed=0.2, on_ground=False, dy=0.2, jumping=True, move=True, sprint=False, sneak=False, blocked=False, life=life)
    expect(rising.leg_l < -28.0 and rising.leg_r > 12.0, "rising pose tucks one leg forward and trails the other")
    expect(rising.arm_l < -30.0 and rising.arm_r < -24.0, "rising arms lift")
    risen = (rising.leg_l, rising.leg_r, rising.arm_l, rising.arm_r)

    falling = rising
    for _ in range(20):
        life += 0.05
        step(falling, tcos=0.0, speed=0.2, on_ground=False, dy=-0.45, jumping=False, move=True, sprint=False, sneak=False, blocked=False, life=life)
    expect(falling.leg_l > 8.0 and falling.leg_r < -4.0, "falling pose must differ from the rising pose")
    expect(falling.leg_l * risen[0] < 0, "falling lead leg is not the rising tuck")
    fallen = (falling.leg_l, falling.leg_r)

    landing = falling
    landed = False
    for _ in range(2):
        life += 0.05
        step(landing, tcos=0.0, speed=0.0, on_ground=True, dy=0.0, jumping=False, move=True, sprint=False, sneak=False, blocked=False, life=life)
        landed = landed or landing.just_landed
    expect(landed, "a real jump must enter the landing blend")
    expect(-12.0 < landing.leg_l < -4.0 and -10.0 < landing.leg_r < -3.0, "landing bends both legs a little, then can recover")

    hop = Pose()
    life = 0.0
    step(hop, tcos=0.0, speed=0.1, on_ground=False, dy=0.2, jumping=True, move=True, sprint=False, sneak=False, blocked=False, life=0.05)
    step(hop, tcos=0.0, speed=0.1, on_ground=True, dy=0.0, jumping=False, move=True, sprint=False, sneak=False, blocked=False, life=0.1)
    expect(not hop.just_landed, "a one-frame step must not play the landing bend")

    riding = run_frames(6, tcos=12.0, speed=0.4, on_ground=True, dy=0.0, jumping=False, move=False, sprint=False, sneak=False, blocked=True)
    expect(abs(riding.leg_l - (12.0 * -1.4)) < 0.01, "riding, swimming, and gliding keep the vanilla leg channel")

    crouch = run_frames(10, tcos=0.0, speed=0.0, on_ground=True, dy=0.0, jumping=True, move=True, sprint=False, sneak=False, blocked=False)
    expect(crouch.leg_l < -6.0 and crouch.leg_r < -4.0, "a grounded jump input may crouch the legs")
    expect(crouch.arm_l > 4.0, "the grounded crouch brings the arms back")

    print("motion blend ok")
    print(f"still=({still.leg_l:.2f},{still.leg_r:.2f})")
    print(f"rise=({risen[0]:.2f},{risen[1]:.2f}) arms=({risen[2]:.2f},{risen[3]:.2f})")
    print(f"fall=({fallen[0]:.2f},{fallen[1]:.2f})")
    print(f"land=({landing.leg_l:.2f},{landing.leg_r:.2f}) just={landed}")
    print(f"crouch=({crouch.leg_l:.2f},{crouch.leg_r:.2f},{crouch.arm_l:.2f})")


if __name__ == "__main__":
    main()
