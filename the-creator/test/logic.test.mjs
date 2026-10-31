import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

import {
  DELTA,
  DRIFT,
  PHASES,
  SPAN,
  WHISPERS,
  actionBarText,
  applyMood,
  blinkGapTicks,
  calendarPhase,
  countdown,
  eventChance,
  fleeDistance,
  giftFor,
  hurtAmount,
  isGiftName,
  isLookingAt,
  isNight,
  lookDot,
  moodPhase,
  offsetAhead,
  offsetBehind,
  pickEvent,
  pickWhisper,
  resolvePhase,
  stalkDistance,
} from "../behavior_pack/scripts/logic.js";

test("countdown runs seven in-game days from the day the pack starts", () => {
  const start = countdown(10, 10);
  assert.equal(start.daysLeft, 7);
  assert.equal(start.dayNumber, 1);
  assert.equal(start.ended, false);

  const last = countdown(10, 16);
  assert.equal(last.elapsed, 6);
  assert.equal(last.daysLeft, 1);
  assert.equal(last.dayNumber, 7);
  assert.equal(last.ended, false);

  const over = countdown(10, 17);
  assert.equal(over.daysLeft, 0);
  assert.equal(over.ended, true);
  assert.equal(over.dayNumber, SPAN);
});

test("calendar moves witness, debt, grudge, reckoning across the week", () => {
  assert.deepEqual([0, 1, 2, 3, 4, 5, 6, 7].map(calendarPhase), [0, 0, 1, 1, 2, 2, 3, 3]);
});

test("ungratefulness can pull a phase forward and cannot hold a phase back", () => {
  assert.equal(moodPhase(0), 0);
  assert.equal(moodPhase(24), 0);
  assert.equal(moodPhase(25), 1);
  assert.equal(moodPhase(50), 2);
  assert.equal(moodPhase(75), 3);
  assert.equal(resolvePhase(0, 0), 0);
  assert.equal(resolvePhase(0, 80), 3);
  assert.equal(resolvePhase(5, 0), 2);
  assert.equal(resolvePhase(6, 0), 3);
  assert.equal(resolvePhase(4, 10), 2);
});

test("neglect over a week raises mood while gratitude and the clock still meet", () => {
  let mood = 0;
  for (let day = 0; day < 7; day++) {
    for (let halfMinute = 0; halfMinute < 40; halfMinute++) mood = applyMood(mood, DRIFT);
    if (day === 1) mood = applyMood(mood, DELTA.offering);
  }
  assert.ok(mood > 20 && mood < 50, `neglect mood landed at ${mood}`);
  assert.equal(resolvePhase(6, mood), 3);
  assert.equal(resolvePhase(0, applyMood(0, DELTA.attack)), 0);
  assert.equal(resolvePhase(0, applyMood(20, DELTA.attack)), 1);
  assert.equal(applyMood(0, DELTA.tribute), 0);
  assert.equal(applyMood(96, DELTA.attack), 100);
});

test("stalk range closes as the phase turns hostile", () => {
  assert.deepEqual([0, 1, 2, 3].map(stalkDistance), [34, 22, 13, 6]);
  assert.deepEqual([0, 1, 2, 3].map(fleeDistance), [14, 9, 0, 0]);
  assert.ok(blinkGapTicks(0) > blinkGapTicks(3));
  assert.equal(hurtAmount(0), 0);
  assert.equal(hurtAmount(1), 0);
  assert.ok(hurtAmount(3) > hurtAmount(2));
});

test("stalk spot stays behind the look direction and the rush spot stays ahead", () => {
  const origin = { x: 0, y: 64, z: 0 };
  const lookingSouth = { x: 0, y: 0, z: 1 };
  const behind = offsetBehind(origin, lookingSouth, 30, 0);
  assert.ok(Math.abs(behind.x) < 0.001);
  assert.ok(behind.z < -29);
  const right = offsetBehind(origin, lookingSouth, 30, 4);
  assert.ok(right.x < -3);
  assert.ok(right.z < -29);
  const ahead = offsetAhead(origin, lookingSouth, 5);
  assert.ok(ahead.z > 4);
});

test("looking straight at the figure counts and looking away does not", () => {
  const from = { x: 0, y: 1.6, z: 0 };
  const view = { x: 0, y: 0, z: 1 };
  const ahead = { x: 0, y: 2, z: 20 };
  const behind = { x: 0, y: 2, z: -20 };
  assert.ok(lookDot(view, from, ahead) > 0.9);
  assert.equal(isLookingAt(view, from, ahead), true);
  assert.equal(isLookingAt(view, from, behind), false);
});

test("paranormal events get more likely and harsher as the mood breaks", () => {
  assert.ok(eventChance(0, false) < eventChance(1, false));
  assert.ok(eventChance(2, false) < eventChance(3, false));
  assert.ok(eventChance(1, true) > eventChance(1, false));
  assert.equal(pickEvent(0, 0), "whisper");
  assert.equal(pickEvent(3, 0.99), "lightning");
  assert.equal(pickEvent(2, 0), "thunder");
  assert.equal(isNight(0), false);
  assert.equal(isNight(18000), true);
  assert.equal(isNight(23000), true);
  assert.equal(isNight(24000), false);
});

test("gifts start useful and turn sour, and only marked drops count as discarded", () => {
  assert.equal(giftFor(0, 0).id, "minecraft:bread");
  assert.equal(giftFor(0, 0.5).id, "minecraft:torch");
  assert.equal(giftFor(1, 0).id, "minecraft:rotten_flesh");
  assert.equal(isGiftName("§8Gift: bread"), true);
  assert.equal(isGiftName("bread"), false);
  assert.equal(pickWhisper(0, 0).includes("left something"), true);
  assert.equal(WHISPERS[3].some((line) => line.includes("eighth")), true);
});

test("action bar names the day, the days left, and the phase", () => {
  const early = actionBarText(countdown(0, 0), 0, 4);
  assert.match(early, /7/);
  assert.match(early, /Day 1/);
  assert.match(early, /The Witness/);
  assert.match(early, /4%/);
  const ended = actionBarText(countdown(0, 8), 3, 90);
  assert.match(ended, /Day 7 is over/);
  assert.match(ended, /The Reckoning/);
  assert.equal(PHASES.length, 4);
});

test("geometry bones cover the idle animation and stay inside the texture", () => {
  const geo = JSON.parse(
    fs.readFileSync(new URL("../resource_pack/models/entity/the_creator.geo.json", import.meta.url), "utf8"),
  );
  const anim = JSON.parse(
    fs.readFileSync(new URL("../resource_pack/animations/the_creator.animation.json", import.meta.url), "utf8"),
  );
  const bones = new Set(geo["minecraft:geometry"][0].bones.map((bone) => bone.name));
  const animated = Object.keys(anim.animations["animation.the_creator.idle"].bones);
  for (const name of animated) assert.ok(bones.has(name), name);
  for (const bone of geo["minecraft:geometry"][0].bones) {
    for (const cube of bone.cubes ?? []) {
      for (const face of Object.values(cube.uv)) {
        const [u, v] = face.uv;
        const [w, h] = face.uv_size;
        assert.ok(u >= 0 && v >= 0 && u + w <= 64 && v + h <= 64);
      }
    }
  }
});
