import assert from "node:assert/strict";
import test from "node:test";
import { possess, release, tickBrain, nearestMobs, vanillaBrainShouldRun } from "../behavior_pack/scripts/control.js";

const playerNear = {
  playerKnown: true,
  playerDistance: 4,
  playerName: "Alex",
  hostileKnown: false,
  hostileDistance: Infinity,
  chestKnown: true,
  chestDistance: 6,
  waterKnown: true,
  waterDistance: 5,
  tick: 0,
  salt: 0,
};

test("pirate who only talks about treasure seeks the chest and does not chase the player", () => {
  const taken = possess("a grumpy pirate who only talks about treasure");
  assert.equal(taken.ok, true);
  const tick = tickBrain(taken.state, playerNear);
  assert.equal(tick.controller, "local");
  assert.equal(tick.plan.focus, "chest");
  assert.equal(tick.plan.attack, false);
  assert.match(tick.plan.speech, /treasure/i);
  assert.match(tick.plan.speech, /Arr|Aye/);
  assert.equal(vanillaBrainShouldRun(true), false);
});

test("cheerful baker who loves bread follows and talks about bread", () => {
  const taken = possess("a cheerful baker who loves fresh bread");
  const tick = tickBrain(taken.state, { ...playerNear, chestKnown: false });
  assert.equal(tick.plan.focus, "player");
  assert.equal(tick.plan.intent, "follow");
  assert.equal(tick.plan.attack, false);
  assert.match(tick.plan.speech, /bread/i);
  assert.match(tick.plan.speech, /!/);
});

test("guard personality hunts a nearby monster and strikes when close", () => {
  const taken = possess("a guard who protects the village");
  const far = tickBrain(taken.state, { ...playerNear, hostileKnown: true, hostileDistance: 8 });
  assert.equal(far.plan.focus, "hostile");
  assert.equal(far.plan.attack, false);
  const close = tickBrain(taken.state, { ...playerNear, hostileKnown: true, hostileDistance: 2 });
  assert.equal(close.plan.attack, true);
});

test("shy poet who loves rain keeps away and still mentions rain", () => {
  const taken = possess("a shy poet who loves rain");
  const tick = tickBrain(taken.state, playerNear);
  assert.equal(tick.plan.focus, "away");
  assert.equal(tick.plan.intent, "flee");
  assert.match(tick.plan.speech, /rain/i);
  assert.match(tick.plan.speech, /^Um\./);
});

test("different personalities do not produce the same plan", () => {
  const pirate = tickBrain(possess("a grumpy pirate who only talks about treasure").state, playerNear);
  const baker = tickBrain(possess("a cheerful baker who loves fresh bread").state, { ...playerNear, chestKnown: false });
  assert.notEqual(pirate.plan.focus, baker.plan.focus);
  assert.notEqual(pirate.plan.speech, baker.plan.speech);
});

test("the local mind replaces the vanilla brain instead of sharing the tick", () => {
  const taken = possess("a mean robot who hates music");
  const possessedTick = tickBrain(taken.state, playerNear);
  const releasedTick = tickBrain(release(), playerNear);
  assert.equal(possessedTick.controller, "local");
  assert.equal(releasedTick.controller, "vanilla");
  assert.equal(releasedTick.plan, null);
  assert.match(possessedTick.plan.speech, /music/i);
  assert.match(possessedTick.plan.speech, /Unit online/);
});

test("blank personality is refused", () => {
  const refused = possess("   ");
  assert.equal(refused.ok, false);
  assert.match(refused.reason, /personality/i);
});

test("nearest mobs are the closest ones, capped", () => {
  const ranked = nearestMobs(
    [
      { name: "Zombie", distance: 12 },
      { name: "Cow", distance: 3 },
      { name: "Bat", distance: 30 },
      { name: "Pig", distance: 3 },
      { name: "Spider", distance: 8 },
      { name: "Cat", distance: 1 },
      { name: "Sheep", distance: 20 },
    ],
    3,
  );
  assert.deepEqual(ranked.map((mob) => mob.name), ["Cat", "Cow", "Pig"]);
});
