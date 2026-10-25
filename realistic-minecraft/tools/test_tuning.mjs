import assert from "node:assert/strict";
import {
  AMBIENT_TICKS_FANCY,
  AMBIENT_TICKS_SIMPLE,
  chooseAmbient,
  hurtShake,
  landingAction,
  stepSound,
} from "../behavior_pack/scripts/tuning.js";

assert.equal(landingAction(0.4).puff, false);
assert.equal(landingAction(0.4).shake, null);

const hop = landingAction(0.7);
assert.equal(hop.puff, true);
assert.equal(hop.hard, false);
assert.equal(hop.shake, null);

const drop = landingAction(1.3);
assert.equal(drop.hard, true);
assert.ok(drop.shake.intensity > 0.05);
assert.ok(drop.shake.intensity <= 0.42);
assert.ok(drop.shake.duration <= 0.32);

const hurt = hurtShake(4);
assert.ok(hurt.intensity <= 0.48);
assert.ok(hurt.duration > 0);

assert.equal(stepSound("minecraft:sand"), "step.sand");
assert.equal(stepSound("minecraft:oak_log"), "step.wood");
assert.equal(stepSound("minecraft:grass_block"), "step.grass");
assert.equal(stepSound("minecraft:stone"), "step.stone");

assert.equal(chooseAmbient({ dimensionId: "minecraft:nether", simple: false }), "realistic:ash");
assert.equal(chooseAmbient({ dimensionId: "minecraft:overworld", biomeId: "minecraft:desert", simple: false }), "realistic:sand");
assert.equal(chooseAmbient({ dimensionId: "minecraft:overworld", biomeId: "minecraft:forest", simple: false }), "realistic:leaf");
assert.equal(chooseAmbient({ dimensionId: "minecraft:overworld", biomeId: "minecraft:plains", weather: "Rain", simple: false }), "realistic:mist");
assert.equal(chooseAmbient({ dimensionId: "minecraft:overworld", skyLight: 2, simple: false }), "realistic:dust");
assert.equal(chooseAmbient({ dimensionId: "minecraft:overworld", biomeId: "minecraft:forest", simple: true }), null);
assert.equal(chooseAmbient({ dimensionId: "minecraft:nether", simple: true }), "realistic:ash");
assert.ok(AMBIENT_TICKS_SIMPLE > AMBIENT_TICKS_FANCY);

console.log("tuning checks passed");
