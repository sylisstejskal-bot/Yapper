import { world, system, ItemStack, EntityDamageCause, EquipmentSlot } from "@minecraft/server";
import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import { plan } from "./mind.js";
import { holdTransition, nearestMobs, possess, SCAN_RANGE } from "./control.js";

const REMOTE = "aimobs:remote";
const TAG = "aimobs_possessed";
const busy = new Set();
const pending = new Set();
const told = new Set();
const wasHolding = new Set();
const chestCache = new Map();
const waterCache = new Map();
const bootErrors = [];
const USE_HINT = "Select the AI Mobs item to open the menu.";

function listen(label, subscribe) {
  try {
    subscribe();
  } catch (error) {
    bootErrors.push(`${label}: ${error}`);
  }
}

listen("startup", () => {
  system.beforeEvents.startup.subscribe((event) => {
    event.itemComponentRegistry.registerCustomComponent("aimobs:open", {
      onUse(useEvent) {
        queueMenu(useEvent.source);
      },
      onUseOn(useEvent) {
        queueMenu(useEvent.source);
      },
      onCompleteUse(useEvent) {
        queueMenu(useEvent.source);
      },
      onConsume(useEvent) {
        const player = useEvent.source;
        queueMenu(player);
        system.run(() => {
          if (player?.isValid()) giveRemote(player);
        });
      },
    });
  });
});

listen("itemStartUse", () => {
  world.afterEvents.itemStartUse.subscribe((event) => {
    if (event.itemStack?.typeId !== REMOTE) return;
    queueMenu(event.source);
  });
});

listen("itemUse", () => {
  world.afterEvents.itemUse.subscribe((event) => {
    if (event.itemStack?.typeId !== REMOTE) return;
    queueMenu(event.source);
  });
});

listen("playerSpawn", () => {
  world.afterEvents.playerSpawn.subscribe((event) => {
    system.run(() => {
      const player = event.player;
      if (!player.isValid()) return;
      giveRemote(player);
      if (event.initialSpawn) {
        told.add(player.id);
        player.sendMessage(USE_HINT);
      }
    });
  });
});

console.warn("AI Mobs script loaded");

world.afterEvents.entityLoad.subscribe((event) => {
  const entity = event.entity;
  if (!entity.isValid()) return;
  if (entity.getDynamicProperty("aimobs:on") !== true) return;
  takeOver(entity);
});

system.runInterval(() => {
  const seen = new Set();
  for (const player of world.getPlayers()) {
    watchHold(player);
    let mobs = [];
    try {
      mobs = player.dimension.getEntities({
        location: player.location,
        maxDistance: 48,
        tags: [TAG],
      });
    } catch {
      continue;
    }
    for (const mob of mobs) {
      if (!mob.isValid() || seen.has(mob.id) || mob.typeId === "minecraft:player") continue;
      seen.add(mob.id);
      drive(mob);
    }
  }
}, 1);

function giveRemote(player) {
  try {
    const container = player.getComponent("minecraft:inventory")?.container;
    if (!container) return;
    for (let slot = 0; slot < container.size; slot++) {
      if (container.getItem(slot)?.typeId === REMOTE) return;
    }
    const leftover = container.addItem(new ItemStack(REMOTE, 1));
    if (leftover) player.sendMessage("The AI Mobs item could not be added. Take it from the creative inventory.");
  } catch {
    player.sendMessage("The AI Mobs item could not be added. Take it from the creative inventory.");
  }
}

function watchHold(player) {
  if (!player.isValid()) return;
  let holding = false;
  try {
    holding = holdingRemote(player);
  } catch {
    return;
  }
  if (holdTransition(wasHolding.has(player.id), holding)) queueMenu(player);
  if (holding) wasHolding.add(player.id);
  else wasHolding.delete(player.id);
  if (holding && !told.has(player.id)) {
    told.add(player.id);
    player.sendMessage(USE_HINT);
    if (bootErrors.length) player.sendMessage(`AI Mobs script warning: ${bootErrors[0]}`);
  }
}

function queueMenu(player) {
  if (!player || player.typeId !== "minecraft:player") return;
  if (pending.has(player.id) || busy.has(player.id)) return;
  pending.add(player.id);
  system.runTimeout(() => {
    pending.delete(player.id);
    openMenu(player);
  }, 20);
}

async function openMenu(player) {
  if (!player.isValid() || busy.has(player.id)) return;
  busy.add(player.id);
  try {
    await menu(player);
  } finally {
    busy.delete(player.id);
  }
}

async function menu(player) {
  while (player.isValid()) {
    const form = new ActionFormData()
      .title("AI Mobs")
      .body("Local mind on this device. Nothing is sent over the network.\n\nGiving a mob AI removes its own brain, so the two of them do not fight for control.")
      .button("Find nearest mobs")
      .button("Close");
    const response = await show(player, form);
    if (!response || response.canceled || response.selection !== 0) return;
    const picked = await pickMob(player);
    if (picked === "back") continue;
    if (!picked) return;
  }
}

async function pickMob(player) {
  const found = nearestMobs(scan(player));
  const form = new ActionFormData().title("Nearest mobs");
  if (found.length === 0) {
    form.body(`No mobs within ${SCAN_RANGE} blocks.`);
    form.button("Back");
    const response = await show(player, form);
    if (!response || response.canceled) return null;
    return "back";
  }
  form.body("Select a mob. The top of the list is the nearest.");
  for (const mob of found) {
    const mark = mob.possessed ? " · AI" : "";
    form.button(`${mob.name} · ${mob.distance} blocks${mark}`);
  }
  form.button("Back");
  const response = await show(player, form);
  if (!response || response.canceled) return null;
  if (response.selection === found.length) return "back";
  const chosen = found[response.selection];
  if (!chosen?.entity.isValid()) {
    player.sendMessage("That mob is gone.");
    return "back";
  }
  await mobActions(player, chosen.entity);
  return "back";
}

async function mobActions(player, entity) {
  const possessed = entity.isValid() && entity.hasTag(TAG);
  if (!possessed) {
    await askPersonality(player, entity, "");
    return;
  }
  const current = String(entity.getDynamicProperty("aimobs:personality") ?? "");
  const form = new ActionFormData()
    .title(mobName(entity))
    .body(`Local mind is in control.\n\nPersonality: ${current}`)
    .button("Change personality")
    .button("Release")
    .button("Back");
  const response = await show(player, form);
  if (!response || response.canceled || !entity.isValid()) return;
  if (response.selection === 0) await askPersonality(player, entity, current);
  if (response.selection === 1) releaseMob(player, entity);
}

async function askPersonality(player, entity, current) {
  if (!entity.isValid()) return;
  const form = new ModalFormData()
    .title("Personality")
    .textField("Personality", "a grumpy pirate who only talks about treasure", { defaultValue: current })
    .submitButton(entity.hasTag(TAG) ? "Update personality" : "Give this mob AI");
  const response = await show(player, form);
  if (!response || response.canceled || !entity.isValid()) return;
  const typed = String(response.formValues?.[0] ?? "");
  const decision = possess(typed);
  if (!decision.ok) {
    player.sendMessage(decision.reason);
    return;
  }
  giveMind(player, entity, decision.state.personality);
}

function giveMind(player, entity, personality) {
  entity.setDynamicProperty("aimobs:on", true);
  entity.setDynamicProperty("aimobs:personality", personality);
  const fallback = takeOver(entity);
  const name = mobName(entity);
  if (fallback) {
    player.sendMessage(`${name} is being steered by the local mind. This mob type still has some of its own goals, so movement is forced each tick.`);
    return;
  }
  player.sendMessage(`${name} is under the local mind. Its own brain is off.`);
}

function releaseMob(player, entity) {
  if (!entity.isValid()) return;
  const name = mobName(entity);
  entity.setDynamicProperty("aimobs:on", false);
  entity.setDynamicProperty("aimobs:personality", "");
  entity.removeTag(TAG);
  try {
    entity.triggerEvent("aimobs:release");
  } catch {
    // This mob type has no pack event. Clearing the tag stops the local mind.
  }
  try {
    entity.removeEffect("slowness");
  } catch {
    // The effect was not there.
  }
  player.sendMessage(`${name} has its own brain again.`);
}

function takeOver(entity) {
  entity.addTag(TAG);
  try {
    entity.triggerEvent("aimobs:possess");
    entity.setDynamicProperty("aimobs:fallback", false);
    return false;
  } catch {
    entity.setDynamicProperty("aimobs:fallback", true);
    return true;
  }
}

function scan(player) {
  let entities = [];
  try {
    entities = player.dimension.getEntities({
      location: player.location,
      maxDistance: SCAN_RANGE,
    });
  } catch {
    return [];
  }
  const rows = [];
  for (const entity of entities) {
    if (!entity.isValid() || entity.id === player.id) continue;
    if (entity.typeId === "minecraft:player") continue;
    if (!entity.getComponent("minecraft:health")) continue;
    rows.push({
      entity,
      name: mobName(entity),
      distance: Math.round(distance(player.location, entity.location)),
      possessed: entity.hasTag(TAG),
    });
  }
  return rows;
}

function drive(entity) {
  const personality = String(entity.getDynamicProperty("aimobs:personality") ?? "");
  if (!personality) return;
  const nearest = nearestPlayer(entity);
  const senses = readSenses(entity, nearest.player, nearest.distance);
  const decision = plan(personality, senses);
  try {
    entity.clearVelocity();
  } catch {
    // Knockback may already be gone.
  }
  if (entity.getDynamicProperty("aimobs:fallback") === true) {
    try {
      entity.addEffect("slowness", 40, { amplifier: 255, showParticles: false });
    } catch {
      // The mob may already be invalid this tick.
    }
  }
  move(entity, decision, senses, nearest.player);
  if (decision.attack) strike(entity, decision, nearest.player);
  if (decision.speech && nearest.player) say(entity, decision.speech);
}

function readSenses(entity, player, playerDistance) {
  const tick = system.currentTick;
  const chest = cachedSpot(chestCache, entity, 40, () => findBlock(entity, 8, isChest));
  const water = cachedSpot(waterCache, entity, 40, () => findBlock(entity, 8, isWater));
  const hostile = nearestHostile(entity);
  return {
    playerKnown: Boolean(player),
    playerDistance: player ? playerDistance : Infinity,
    playerName: player?.name ?? "friend",
    hostileKnown: Boolean(hostile),
    hostileDistance: hostile ? distance(entity.location, hostile.location) : Infinity,
    chestKnown: Boolean(chest),
    chestDistance: chest ? distance(entity.location, center(chest)) : Infinity,
    waterKnown: Boolean(water),
    waterDistance: water ? distance(entity.location, center(water)) : Infinity,
    tick,
    salt: hash(entity.id),
    chest,
    water,
    hostile,
  };
}

function move(entity, decision, senses, player) {
  const here = entity.location;
  let face = player?.location ?? null;
  if (decision.focus === "chest" && senses.chest) face = center(senses.chest);
  if (decision.focus === "water" && senses.water) face = center(senses.water);
  if (decision.focus === "hostile" && senses.hostile?.isValid()) face = senses.hostile.location;
  if (decision.focus === "away" && player) {
    const dx = here.x - player.location.x;
    const dz = here.z - player.location.z;
    const scale = Math.hypot(dx, dz) || 1;
    face = { x: here.x + (dx / scale) * 6, y: here.y, z: here.z + (dz / scale) * 6 };
  }
  if (decision.focus === "wander") {
    const angle = ((hash(entity.id) + Math.floor(system.currentTick / 40)) % 360) * (Math.PI / 180);
    face = { x: here.x + Math.cos(angle) * 6, y: here.y, z: here.z + Math.sin(angle) * 6 };
  }
  if (!face) return;
  if (!decision.speed) {
    try {
      entity.lookAt(face);
    } catch {
      // Looking can fail if the chunk is gone.
    }
    return;
  }
  const dx = face.x - here.x;
  const dy = face.y - here.y;
  const dz = face.z - here.z;
  const flat = Math.hypot(dx, dz) || 1;
  const step = decision.speed;
  let climb = 0;
  if (dy > 0.75) climb = Math.min(0.35, dy);
  else if (!entity.isOnGround) climb = Math.max(-0.35, Math.min(0.35, dy));
  const next = {
    x: here.x + (dx / flat) * step,
    y: here.y + climb,
    z: here.z + (dz / flat) * step,
  };
  let moved = false;
  try {
    moved = entity.tryTeleport(next, { facingLocation: face });
  } catch {
    moved = false;
  }
  if (moved) return;
  try {
    entity.tryTeleport({ x: next.x, y: here.y + 1, z: next.z }, { facingLocation: face });
  } catch {
    // Blocked. The next tick picks a new step.
  }
}

function cachedSpot(cache, entity, interval, find) {
  const key = entity.id;
  const now = system.currentTick;
  const hit = cache.get(key);
  if (hit && now - hit.tick < interval) return hit.pos;
  const pos = find();
  cache.set(key, { tick: now, pos });
  return pos;
}

function strike(entity, decision, player) {
  const next = entity.getDynamicProperty("aimobs:nextAttack") ?? 0;
  if (system.currentTick < next) return;
  let target = null;
  if (decision.focus === "hostile") target = nearestHostile(entity);
  if (decision.focus === "player") target = player;
  if (!target?.isValid()) return;
  if (distance(entity.location, target.location) > 2.6) return;
  try {
    target.applyDamage(4, { cause: EntityDamageCause.entityAttack, damagingEntity: entity });
    entity.setDynamicProperty("aimobs:nextAttack", system.currentTick + 20);
  } catch {
    // Damage can fail if the target is already gone.
  }
}

function say(entity, line) {
  const next = entity.getDynamicProperty("aimobs:nextSay") ?? 0;
  if (system.currentTick < next) return;
  entity.setDynamicProperty("aimobs:nextSay", system.currentTick + 80);
  const text = `<${mobName(entity)}> ${line}`;
  for (const player of world.getPlayers()) {
    if (player.dimension.id !== entity.dimension.id) continue;
    if (distance(player.location, entity.location) > 32) continue;
    player.sendMessage(text);
  }
}

function nearestPlayer(entity) {
  let player = null;
  let best = Infinity;
  for (const candidate of world.getPlayers()) {
    if (candidate.dimension.id !== entity.dimension.id) continue;
    const gap = distance(entity.location, candidate.location);
    if (gap < best) {
      best = gap;
      player = candidate;
    }
  }
  return { player, distance: best };
}

function nearestHostile(entity) {
  let found = [];
  try {
    found = entity.dimension.getEntities({
      location: entity.location,
      maxDistance: 12,
      families: ["monster"],
    });
  } catch {
    return null;
  }
  let best = null;
  let bestGap = Infinity;
  for (const other of found) {
    if (!other.isValid() || other.id === entity.id) continue;
    const gap = distance(entity.location, other.location);
    if (gap < bestGap) {
      bestGap = gap;
      best = other;
    }
  }
  return best;
}

function findBlock(entity, radius, test) {
  const origin = entity.location;
  const baseX = Math.floor(origin.x);
  const baseY = Math.floor(origin.y);
  const baseZ = Math.floor(origin.z);
  let best = null;
  let bestGap = Infinity;
  for (let dx = -radius; dx <= radius; dx++) {
    for (let dy = -3; dy <= 3; dy++) {
      for (let dz = -radius; dz <= radius; dz++) {
        const pos = { x: baseX + dx, y: baseY + dy, z: baseZ + dz };
        let block;
        try {
          block = entity.dimension.getBlock(pos);
        } catch {
          continue;
        }
        if (!block || !test(block)) continue;
        const gap = dx * dx + dy * dy + dz * dz;
        if (gap < bestGap) {
          bestGap = gap;
          best = pos;
        }
      }
    }
  }
  return best;
}

function isChest(block) {
  return block.typeId === "minecraft:chest" || block.typeId === "minecraft:trapped_chest" || block.typeId === "minecraft:barrel";
}

function isWater(block) {
  return block.typeId === "minecraft:water";
}

function center(pos) {
  return { x: pos.x + 0.5, y: pos.y, z: pos.z + 0.5 };
}

function distance(a, b) {
  const dx = a.x - b.x;
  const dy = a.y - b.y;
  const dz = a.z - b.z;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

function mobName(entity) {
  if (entity.nameTag) return entity.nameTag;
  const raw = entity.typeId.split(":")[1] ?? entity.typeId;
  return raw.replace(/_/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function hash(value) {
  let total = 0;
  const text = String(value);
  for (let i = 0; i < text.length; i++) total = (total + text.charCodeAt(i) * (i + 1)) % 997;
  return total;
}

function holdingRemote(player) {
  try {
    return player.getComponent("minecraft:equippable")?.getEquipment(EquipmentSlot.Mainhand)?.typeId === REMOTE;
  } catch {
    return false;
  }
}

function later(ticks) {
  return new Promise((resolve) => system.runTimeout(resolve, ticks));
}

async function show(player, form) {
  for (let attempt = 0; attempt < 6; attempt++) {
    if (!player.isValid()) return null;
    try {
      return await form.show(player);
    } catch {
      await later(4);
    }
  }
  if (player.isValid()) player.sendMessage("Close any other screen, then select the AI Mobs item again.");
  return null;
}
