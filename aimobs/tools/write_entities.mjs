import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "behavior_pack", "entities");

const mobs = [
  ["cow", 0.9, 1.4, 10, "walk"],
  ["pig", 0.9, 0.9, 10, "walk"],
  ["sheep", 0.9, 1.3, 8, "walk"],
  ["chicken", 0.4, 0.7, 4, "walk"],
  ["wolf", 0.6, 0.85, 8, "walk"],
  ["cat", 0.6, 0.7, 10, "walk"],
  ["villager", 0.6, 1.95, 20, "walk"],
  ["horse", 1.4, 1.6, 20, "walk"],
  ["donkey", 1.4, 1.6, 20, "walk"],
  ["mule", 1.4, 1.6, 20, "walk"],
  ["rabbit", 0.4, 0.5, 3, "walk"],
  ["fox", 0.6, 0.7, 10, "walk"],
  ["llama", 0.9, 1.87, 15, "walk"],
  ["trader_llama", 0.9, 1.87, 15, "walk"],
  ["wandering_trader", 0.6, 1.95, 20, "walk"],
  ["snow_golem", 0.7, 1.9, 4, "walk"],
  ["frog", 0.5, 0.5, 10, "walk"],
  ["goat", 0.9, 1.3, 10, "walk"],
  ["panda", 1.3, 1.25, 20, "walk"],
  ["turtle", 1.2, 0.4, 30, "walk"],
  ["mooshroom", 0.9, 1.4, 10, "walk"],
  ["strider", 0.9, 1.7, 20, "walk", { fireImmune: true }],
  ["armadillo", 0.7, 0.65, 12, "walk"],
  ["camel", 1.7, 2.375, 32, "walk"],
  ["sniffer", 1.9, 1.75, 14, "walk"],
  ["iron_golem", 1.4, 2.7, 100, "walk"],
  ["ocelot", 0.6, 0.7, 10, "walk"],
  ["zombie", 0.6, 1.95, 20, "walk", { hostile: true, burn: true }],
  ["husk", 0.6, 1.95, 20, "walk", { hostile: true }],
  ["drowned", 0.6, 1.95, 20, "walk", { hostile: true }],
  ["zombie_villager", 0.6, 1.95, 20, "walk", { hostile: true, burn: true }],
  ["skeleton", 0.6, 1.99, 20, "walk", { hostile: true, burn: true }],
  ["stray", 0.6, 1.99, 20, "walk", { hostile: true, burn: true }],
  ["bogged", 0.6, 1.99, 20, "walk", { hostile: true, burn: true }],
  ["wither_skeleton", 0.7, 2.4, 20, "walk", { hostile: true, fireImmune: true }],
  ["spider", 1.4, 0.9, 16, "walk", { hostile: true }],
  ["cave_spider", 0.7, 0.5, 12, "walk", { hostile: true }],
  ["creeper", 0.6, 1.7, 20, "walk", { hostile: true, explode: true }],
  ["enderman", 0.6, 2.9, 40, "walk", { hostile: true }],
  ["pillager", 0.6, 1.95, 24, "walk", { hostile: true }],
  ["vindicator", 0.6, 1.95, 24, "walk", { hostile: true }],
  ["evoker", 0.6, 1.95, 24, "walk", { hostile: true }],
  ["witch", 0.6, 1.95, 26, "walk", { hostile: true }],
  ["ravager", 1.95, 2.2, 100, "walk", { hostile: true }],
  ["piglin", 0.6, 1.95, 16, "walk", { hostile: true, fireImmune: true }],
  ["piglin_brute", 0.6, 1.95, 50, "walk", { hostile: true, fireImmune: true }],
  ["zombified_piglin", 0.6, 1.95, 20, "walk", { hostile: true, fireImmune: true }],
  ["hoglin", 1.4, 1.4, 40, "walk", { hostile: true }],
  ["zoglin", 1.4, 1.4, 40, "walk", { hostile: true }],
  ["slime", 2.04, 2.04, 16, "walk", { hostile: true }],
  ["magma_cube", 2.04, 2.04, 16, "walk", { hostile: true, fireImmune: true }],
  ["silverfish", 0.4, 0.3, 8, "walk", { hostile: true }],
  ["breeze", 0.6, 1.77, 30, "walk", { hostile: true }],
  ["creaking", 0.9, 2.7, 100, "walk", { hostile: true }],
  ["polar_bear", 1.4, 1.4, 30, "walk", { hostile: true }],
  ["bat", 0.5, 0.9, 6, "fly"],
  ["parrot", 0.5, 0.9, 6, "fly"],
  ["allay", 0.6, 0.6, 20, "fly"],
  ["bee", 0.7, 0.6, 10, "fly"],
  ["happy_ghast", 4, 4, 20, "fly", { fireImmune: true }],
  ["blaze", 0.6, 1.8, 20, "fly", { hostile: true, fireImmune: true }],
  ["ghast", 4, 4, 10, "fly", { hostile: true, fireImmune: true }],
  ["phantom", 0.9, 0.5, 20, "fly", { hostile: true, burn: true }],
  ["vex", 0.4, 0.8, 14, "fly", { hostile: true }],
  ["squid", 0.8, 0.8, 10, "swim"],
  ["glow_squid", 0.8, 0.8, 10, "swim"],
  ["salmon", 0.7, 0.4, 3, "swim"],
  ["cod", 0.5, 0.3, 3, "swim"],
  ["tropicalfish", 0.5, 0.4, 3, "swim"],
  ["pufferfish", 0.7, 0.7, 3, "swim"],
  ["dolphin", 0.9, 0.6, 10, "swim"],
  ["axolotl", 0.75, 0.42, 14, "swim"],
  ["tadpole", 0.4, 0.3, 6, "swim"],
  ["guardian", 0.85, 0.85, 30, "swim", { hostile: true }],
  ["elder_guardian", 1.99, 1.99, 80, "swim", { hostile: true }],
];

await mkdir(root, { recursive: true });
for (const [id, width, height, health, kind, flags = {}] of mobs) {
  const file = join(root, `${id}.json`);
  await writeFile(file, `${JSON.stringify(entity(id, width, height, health, kind, flags), null, 2)}\n`);
}

function entity(id, width, height, health, kind, flags) {
  const families = ["mob", "aimobs", id];
  if (flags.hostile) families.push("monster");
  if (flags.burn) families.push("undead");
  const components = {
    "minecraft:type_family": { family: families },
    "minecraft:health": { value: health, max: health },
    "minecraft:collision_box": { width, height },
    "minecraft:movement": { value: kind === "fly" ? 0.25 : 0.22 },
    "minecraft:physics": { has_collision: true, has_gravity: kind !== "fly" },
    "minecraft:pushable": { is_pushable: true, is_pushable_by_piston: true },
    "minecraft:nameable": {},
    "minecraft:experience_reward": { on_death: "query.last_hit_by_player ? 5 : 0" },
    "minecraft:timer": {
      looping: false,
      time: 0.05,
      time_down_event: { event: "aimobs:ensure_brain", target: "self" },
    },
  };
  if (kind === "walk") {
    components["minecraft:movement.basic"] = {};
    components["minecraft:navigation.walk"] = {
      can_path_over_water: true,
      avoid_water: true,
      avoid_damage_blocks: true,
    };
    components["minecraft:jump.static"] = {};
    components["minecraft:breathable"] = { total_supply: 15, suffocate_time: 0 };
  }
  if (kind === "fly") {
    components["minecraft:movement.fly"] = {};
    components["minecraft:navigation.fly"] = { can_path_over_water: true };
    components["minecraft:can_fly"] = {};
    components["minecraft:breathable"] = { total_supply: 15, suffocate_time: 0 };
  }
  if (kind === "swim") {
    components["minecraft:movement.basic"] = {};
    components["minecraft:underwater_movement"] = { value: 0.12 };
    components["minecraft:navigation.generic"] = { can_swim: true, can_walk: false, can_sink: false };
    components["minecraft:breathable"] = {
      breathes_water: true,
      breathes_air: true,
      generates_bubbles: false,
      total_supply: 15,
      suffocate_time: 0,
    };
  }
  if (flags.burn) components["minecraft:burns_in_daylight"] = {};
  if (flags.fireImmune) components["minecraft:fire_immune"] = {};
  if (flags.hostile) {
    components["minecraft:attack"] = { damage: flags.explode ? 0 : 3 };
    components["minecraft:follow_range"] = { value: 24, max: 24 };
  }
  if (flags.explode) {
    components["minecraft:explode"] = { fuse_length: 1.5, power: 3, fuse_lit: false, causes_fire: false };
  }
  return {
    format_version: "1.21.0",
    "minecraft:entity": {
      description: {
        identifier: `minecraft:${id}`,
        is_spawnable: true,
        is_summonable: true,
        is_experimental: false,
      },
      components,
      component_groups: {
        "aimobs:own_brain": ownBrain(kind, flags),
        "aimobs:local_mind": {
          "minecraft:behavior.float": { priority: 0 },
        },
      },
      events: {
        "minecraft:entity_spawned": {
          filters: {
            test: "has_tag",
            subject: "self",
            operator: "not",
            value: "aimobs_possessed",
          },
          add: { component_groups: ["aimobs:own_brain"] },
        },
        "aimobs:ensure_brain": {
          filters: {
            test: "has_tag",
            subject: "self",
            operator: "not",
            value: "aimobs_possessed",
          },
          add: { component_groups: ["aimobs:own_brain"] },
        },
        "aimobs:possess": {
          remove: { component_groups: ["aimobs:own_brain"] },
          add: { component_groups: ["aimobs:local_mind"] },
        },
        "aimobs:release": {
          remove: { component_groups: ["aimobs:local_mind"] },
          add: { component_groups: ["aimobs:own_brain"] },
        },
      },
    },
  };
}

function ownBrain(kind, flags) {
  const brain = {
    "minecraft:behavior.float": { priority: 0 },
  };
  if (kind === "walk") {
    brain["minecraft:behavior.random_stroll"] = { priority: 6, speed_multiplier: 1, xz_dist: 8, y_dist: 2 };
  }
  if (kind === "fly") {
    brain["minecraft:behavior.random_fly"] = { priority: 6, speed_multiplier: 1, xz_dist: 8, y_dist: 4 };
  }
  if (kind === "swim") {
    brain["minecraft:behavior.random_swim"] = { priority: 6, speed_multiplier: 1 };
  }
  brain["minecraft:behavior.look_at_player"] = { priority: 7, look_distance: 8 };
  brain["minecraft:behavior.random_look_around"] = { priority: 8 };
  if (!flags.hostile) {
    brain["minecraft:behavior.panic"] = { priority: 1, speed_multiplier: 1.25 };
  } else if (flags.explode) {
    brain["minecraft:behavior.nearest_attackable_target"] = targetPlayers();
    brain["minecraft:behavior.swell"] = { priority: 2, start_distance: 2.5, stop_distance: 4 };
  } else {
    brain["minecraft:behavior.hurt_by_target"] = { priority: 1 };
    brain["minecraft:behavior.nearest_attackable_target"] = targetPlayers();
    brain["minecraft:behavior.melee_attack"] = { priority: 3, speed_multiplier: 1, track_target: true };
  }
  return brain;
}

function targetPlayers() {
  return {
    priority: 2,
    must_see: true,
    reselect_targets: true,
    within_radius: 16,
    entity_types: [
      {
        max_dist: 16,
        filters: { test: "is_family", subject: "other", value: "player" },
      },
    ],
  };
}
