import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { readdir } from "node:fs/promises";

const root = new URL("..", import.meta.url);

async function readJson(path) {
  return JSON.parse(await readFile(new URL(path, root), "utf8"));
}

test("manifests point at the stable script modules and each other", async () => {
  const behavior = await readJson("behavior_pack/manifest.json");
  const resources = await readJson("resource_pack/manifest.json");
  const script = behavior.modules.find((mod) => mod.type === "script");
  assert.equal(script.entry, "scripts/main.js");
  assert.equal(behavior.dependencies.find((dep) => dep.module_name === "@minecraft/server").version, "2.10.0");
  assert.equal(behavior.dependencies.find((dep) => dep.module_name === "@minecraft/server-ui").version, "2.2.0");
  assert.equal(behavior.dependencies.find((dep) => dep.uuid).uuid, resources.header.uuid);
  assert.deepEqual(behavior.header.min_engine_version, [1, 26, 0]);
});

test("the AI Mobs item is a one-press custom component", async () => {
  const item = await readJson("behavior_pack/items/remote.json");
  const components = item["minecraft:item"].components;
  assert.equal(item["minecraft:item"].description.identifier, "aimobs:remote");
  assert.equal(components["minecraft:max_stack_size"], 1);
  assert.equal(components["minecraft:use_modifiers"].use_duration, 0);
  assert.deepEqual(components["aimobs:open"], {});
  assert.equal(components["minecraft:icon"].textures.default, "aimobs_remote");
  const atlas = await readJson("resource_pack/textures/item_texture.json");
  assert.equal(atlas.texture_data.aimobs_remote.textures, "textures/items/aimobs_remote");
  const lang = await readFile(new URL("resource_pack/texts/en_US.lang", root), "utf8");
  assert.match(lang, /item\.aimobs:remote\.name=AI Mobs/);
});

test("possessed mobs lose the vanilla brain group", async () => {
  for (const id of ["cow", "creeper", "zombie", "bat", "squid"]) {
    const entity = await readJson(`behavior_pack/entities/${id}.json`);
    const body = entity["minecraft:entity"];
    const own = Object.keys(body.component_groups["aimobs:own_brain"]);
    const local = Object.keys(body.component_groups["aimobs:local_mind"]);
    assert.deepEqual(local, ["minecraft:behavior.float"]);
    assert.equal(own.includes("minecraft:behavior.melee_attack"), id === "zombie");
    assert.equal(own.includes("minecraft:behavior.swell"), id === "creeper");
    assert.equal(own.includes("minecraft:behavior.random_stroll"), id === "cow" || id === "zombie" || id === "creeper");
    assert.equal(body.events["aimobs:possess"].remove.component_groups[0], "aimobs:own_brain");
    assert.equal(body.events["aimobs:possess"].add.component_groups[0], "aimobs:local_mind");
    assert.equal(body.events["aimobs:release"].add.component_groups[0], "aimobs:own_brain");
    assert.equal(body.events["minecraft:entity_spawned"].filters.value, "aimobs_possessed");
    assert.equal(body.events["aimobs:ensure_brain"].filters.operator, "not");
    assert.equal(body.description.identifier, `minecraft:${id}`);
  }
  const files = await readdir(new URL("behavior_pack/entities", root));
  assert.equal(files.includes("villager_v2.json"), false);
  assert.equal(files.includes("zombie_villager_v2.json"), false);
  assert.ok(files.length >= 70);
});
