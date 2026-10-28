import assert from "node:assert/strict";
import test from "node:test";
import {
  HUNGER_MAX,
  START_HUNGER,
  applyFeed,
  barGlyphs,
  clampHunger,
  crossedWarning,
  drainHunger,
  feedMessage,
  foodPoints,
  hungerDisplay,
  hungriest,
  itemLabel,
  leftoverFor,
  sameOwner,
  shouldTeleport,
  stripCodes,
} from "../behavior_packs/Obesity_BP/scripts/logic.js";

test("hunger clamps into 0..20 and treats junk as the starting value", () => {
  assert.equal(clampHunger(25), 20);
  assert.equal(clampHunger(-4), 0);
  assert.equal(clampHunger(14.4), 14);
  assert.equal(clampHunger(Number.NaN), START_HUNGER);
  assert.equal(clampHunger(undefined), START_HUNGER);
});

test("the bar is always ten marks", () => {
  assert.deepEqual(barGlyphs(20), { full: 10, half: 0, empty: 0 });
  assert.deepEqual(barGlyphs(19), { full: 9, half: 1, empty: 0 });
  assert.deepEqual(barGlyphs(14), { full: 7, half: 0, empty: 3 });
  assert.deepEqual(barGlyphs(1), { full: 0, half: 1, empty: 9 });
  assert.deepEqual(barGlyphs(0), { full: 0, half: 0, empty: 10 });
});

test("the top bar names Obesity and shows the fraction", () => {
  const full = hungerDisplay(14, 1);
  const plain = stripCodes(full.title);
  assert.match(plain, /Obesity/);
  assert.match(plain, /\[#######---\]/);
  assert.match(plain, /14\/20/);
  assert.match(stripCodes(full.subtitle), /Hold food and tap Obesity/);

  const starving = hungerDisplay(0, 2);
  assert.match(stripCodes(starving.title), /Obesity/);
  assert.match(stripCodes(starving.title), /\[----------\]/);
  assert.match(stripCodes(starving.title), /0\/20/);
  assert.match(stripCodes(starving.title), /x2/);
  assert.match(stripCodes(starving.subtitle), /starving/);

  const half = stripCodes(hungerDisplay(19, 1).title);
  assert.match(half, /\[#########\+\]/);
  assert.match(half, /19\/20/);
});

test("food restores hunger and special apples are worth more", () => {
  assert.equal(foodPoints("minecraft:bread", undefined), 5);
  assert.equal(foodPoints("minecraft:bread", 5), 5);
  assert.equal(foodPoints("minecraft:cooked_beef", undefined), 8);
  assert.equal(foodPoints("minecraft:golden_apple", 4), 10);
  assert.equal(foodPoints("minecraft:enchanted_golden_apple", 4), HUNGER_MAX);
  assert.equal(foodPoints("minecraft:stick", undefined), 0);
  assert.equal(foodPoints("minecraft:stick", 0), 0);
});

test("feeding clamps at full and will not eat when already stuffed", () => {
  assert.deepEqual(applyFeed(10, 0), { hunger: 10, status: "not_food", gained: 0 });
  assert.deepEqual(applyFeed(20, 8), { hunger: 20, status: "stuffed", gained: 0 });
  assert.deepEqual(applyFeed(18, 8), { hunger: 20, status: "stuffed_now", gained: 2 });
  assert.deepEqual(applyFeed(10, 4), { hunger: 14, status: "ate", gained: 4 });
  assert.match(feedMessage(applyFeed(10, 5), "minecraft:bread"), /bread/);
  assert.match(feedMessage(applyFeed(20, 5), "minecraft:bread"), /stuffed/);
});

test("stews leave a bowl and honey leaves a bottle", () => {
  assert.equal(leftoverFor("minecraft:mushroom_stew"), "minecraft:bowl");
  assert.equal(leftoverFor("minecraft:rabbit_stew"), "minecraft:bowl");
  assert.equal(leftoverFor("minecraft:honey_bottle"), "minecraft:glass_bottle");
  assert.equal(leftoverFor("minecraft:bread"), undefined);
  assert.equal(itemLabel("minecraft:cooked_beef"), "steak");
  assert.equal(itemLabel("minecraft:pumpkin_pie"), "pumpkin pie");
});

test("hunger drains one point and warnings fire on the way down", () => {
  assert.equal(drainHunger(14), 13);
  assert.equal(drainHunger(0), 0);
  assert.deepEqual(crossedWarning(7, 6), ["hungry"]);
  assert.deepEqual(crossedWarning(1, 0), ["starving"]);
  assert.deepEqual(crossedWarning(14, 13), []);
  assert.deepEqual(crossedWarning(6, 5), []);
});

test("ownership and follow distance", () => {
  const player = { id: "p1", name: "Ada" };
  assert.equal(sameOwner({ id: "p1", name: "other" }, player), true);
  assert.equal(sameOwner({ id: "nope", name: "Ada" }, player), true);
  assert.equal(sameOwner({ id: "nope", name: "Bea" }, player), false);
  assert.equal(shouldTeleport({ x: 0, y: 0, z: 0 }, { x: 10, y: 0, z: 0 }), false);
  assert.equal(shouldTeleport({ x: 0, y: 0, z: 0 }, { x: 30, y: 0, z: 0 }), true);
  assert.equal(hungriest([{ hunger: 12 }, { hunger: 3 }, { hunger: 9 }]).hunger, 3);
});
