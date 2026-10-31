import {
  DisplaySlotId,
  EntityDamageCause,
  GameMode,
  ItemStack,
  WeatherType,
  system,
  world,
} from "@minecraft/server";

import {
  DELTA,
  DRIFT,
  GIFT_PREFIX,
  NIGHT_DRIFT,
  OFFERING_ID,
  PHASES,
  TYPE_ID,
  actionBarText,
  applyMood,
  blinkGapTicks,
  countdown,
  distance,
  eventChance,
  fleeDistance,
  giftFor,
  hurtAmount,
  hurtCooldownTicks,
  hurtRange,
  isGiftName,
  isLookingAt,
  isNight,
  offsetAhead,
  offsetBehind,
  pickEvent,
  pickWhisper,
  resolvePhase,
  stalkDistance,
} from "./logic.js";

const KEY = {
  startDay: "creator:startDay",
  mood: "creator:ungratefulness",
  seenDay: "creator:seenDay",
  phase: "creator:phase",
  sleepDay: "creator:sleepDay",
  torchDay: "creator:torchDay",
  torchCount: "creator:torchCount",
  welcomed: "creator:welcomed",
};

const state = {
  startDay: 0,
  mood: 0,
  seenDay: undefined,
  phase: undefined,
  ready: false,
  side: 1,
  nextBlink: 0,
  holdRespawn: 0,
};

const watchTicks = new Map();
const wasSleeping = new Map();
const ackAt = new Map();
const giftAt = new Map();
const hurtAt = new Map();
const thankAt = new Map();
const whisperAt = new Map();
const seenErrors = new Set();
const welcoming = new Set();
let step = 0;

function valid(entity) {
  if (!entity) return false;
  try {
    if (typeof entity.isValid === "function") return entity.isValid();
    return entity.isValid !== false;
  } catch {
    return false;
  }
}

function num(key, fallback) {
  const value = world.getDynamicProperty(key);
  return typeof value === "number" ? value : fallback;
}

function saveMood(next) {
  state.mood = applyMood(next, 0);
  world.setDynamicProperty(KEY.mood, state.mood);
}

function addMood(delta) {
  saveMood(state.mood + delta);
}

function load() {
  if (state.ready) return;
  const start = world.getDynamicProperty(KEY.startDay);
  if (typeof start !== "number") {
    state.startDay = world.getDay();
    world.setDynamicProperty(KEY.startDay, state.startDay);
  } else {
    state.startDay = start;
  }
  state.mood = num(KEY.mood, 0);
  const seen = world.getDynamicProperty(KEY.seenDay);
  state.seenDay = typeof seen === "number" ? seen : undefined;
  const phase = world.getDynamicProperty(KEY.phase);
  state.phase = typeof phase === "number" ? phase : undefined;
  state.ready = true;
}

function livingPlayers() {
  const found = [];
  for (const player of world.getPlayers()) {
    if (!valid(player)) continue;
    try {
      if (player.getGameMode() === GameMode.spectator) continue;
    } catch {
      continue;
    }
    found.push(player);
  }
  return found;
}

function clock() {
  return countdown(state.startDay, world.getDay());
}

function phaseNow() {
  return resolvePhase(clock().elapsed, state.mood);
}

function say(player, text) {
  try {
    player.sendMessage(text);
  } catch {
    // The player left between the check and the send.
  }
}

function whisper(player, line) {
  const now = system.currentTick;
  const last = whisperAt.get(player.id) ?? 0;
  if (now - last < 80) return;
  whisperAt.set(player.id, now);
  say(player, `§8§oThe Creator whispers: ${line}`);
  try {
    player.playSound("mob.endermen.idle", { volume: 0.35, pitch: 0.5 });
  } catch {
    // Sound ids differ slightly across releases. The line still lands.
  }
}

function title(player, text, subtitle) {
  try {
    player.onScreenDisplay.setTitle(text, {
      stayDuration: 70,
      fadeInDuration: 8,
      fadeOutDuration: 16,
      subtitle,
    });
  } catch {
    say(player, `${text} ${subtitle}`);
  }
}

function jolt(player, intensity, seconds) {
  try {
    player.camera.addShake({ intensity, duration: seconds, type: "positional" });
  } catch {
    // Camera shake options are easy to miss on an older stable build.
  }
  try {
    player.camera.fade({
      fadeColor: { red: 0.02, green: 0, blue: 0 },
      fadeTime: { fadeInTime: 0.05, holdTime: 0.08, fadeOutTime: 0.28 },
    });
  } catch {
    // Fade is optional flavor.
  }
}

function updateBoard(info) {
  let objective = world.scoreboard.getObjective("the_creator");
  if (!objective) objective = world.scoreboard.addObjective("the_creator", "The Creator");
  objective.setScore("Days left", info.daysLeft);
  objective.setScore("Day", info.dayNumber);
  objective.setScore("Ungrateful", Math.round(state.mood));
  world.scoreboard.setObjectiveAtDisplaySlot(DisplaySlotId.Sidebar, { objective });
}

function showHud(players, info, phase) {
  const bar = actionBarText(info, phase, state.mood);
  for (const player of players) {
    try {
      player.onScreenDisplay.setActionBar(bar);
    } catch {
      // Display can throw for a player who is mid-respawn.
    }
  }
  try {
    updateBoard(info);
  } catch {
    // A world that already owns this objective name still has the action bar.
  }
}

function announce(players, info, phase) {
  const day = world.getDay();
  if (state.seenDay === undefined) {
    state.seenDay = day;
    world.setDynamicProperty(KEY.seenDay, day);
  } else if (day !== state.seenDay) {
    state.seenDay = day;
    world.setDynamicProperty(KEY.seenDay, day);
    const subtitle = info.ended ? "§4There is no eighth day." : `§7${info.daysLeft} days left`;
    for (const player of players) title(player, `§fDay ${info.dayNumber}`, subtitle);
  }
  if (state.phase === undefined) {
    state.phase = phase;
    world.setDynamicProperty(KEY.phase, phase);
    return;
  }
  if (phase === state.phase) return;
  state.phase = phase;
  world.setDynamicProperty(KEY.phase, phase);
  const infoPhase = PHASES[phase];
  for (const player of players) {
    title(player, `${infoPhase.color}${infoPhase.name}`, `§7${infoPhase.line}`);
    if (phase >= 2) {
      try {
        player.playSound("ambient.cave", { volume: 0.8, pitch: 0.6 });
      } catch {
        // Missing sound is not fatal.
      }
    }
  }
}

function creators(dimension) {
  try {
    return dimension.getEntities({ type: TYPE_ID }).filter(valid);
  } catch {
    return [];
  }
}

function nearestPlayer(origin, players) {
  let best = null;
  let bestDistance = Infinity;
  for (const player of players) {
    if (player.dimension.id !== origin.dimension.id) continue;
    const gap = distance(origin.location, player.location);
    if (gap < bestDistance) {
      best = player;
      bestDistance = gap;
    }
  }
  return best ?? players[0];
}

function standable(dimension, x, y, z) {
  const bx = Math.floor(x);
  const by = Math.floor(y);
  const bz = Math.floor(z);
  let below;
  let here;
  let above;
  try {
    below = dimension.getBlock({ x: bx, y: by - 1, z: bz });
    here = dimension.getBlock({ x: bx, y: by, z: bz });
    above = dimension.getBlock({ x: bx, y: by + 1, z: bz });
  } catch {
    return null;
  }
  let higher;
  try {
    higher = dimension.getBlock({ x: bx, y: by + 2, z: bz });
  } catch {
    return null;
  }
  if (!below || !here || !above || !higher) return null;
  if (below.isAir || below.isLiquid) return null;
  if (!here.isAir || !above.isAir || !higher.isAir) return null;
  return { x: bx + 0.5, y: by, z: bz + 0.5 };
}

function findStand(dimension, x, y, z) {
  const base = Math.floor(y);
  const order = [0, 1, -1, 2, -2, 3, -3, 4, -4, 5, 6, -5, -6];
  for (const delta of order) {
    const spot = standable(dimension, x, base + delta, z);
    if (spot) return spot;
  }
  return { x: Math.floor(x) + 0.5, y: base, z: Math.floor(z) + 0.5 };
}

function cull(extra) {
  try {
    extra.addTag("creator_cull");
  } catch {
    // The tag only exists so a death event can ignore the duplicate.
  }
  try {
    extra.triggerEvent("creator:despawn");
    return;
  } catch {
    // Fall through to a plain remove.
  }
  try {
    extra.remove();
  } catch {
    // Keep the duplicate rather than killing it. A kill would look like the player won.
  }
}

function faceOf(player) {
  try {
    return player.getHeadLocation();
  } catch {
    return player.location;
  }
}

function moveCreator(creator, dimension, spot, face) {
  try {
    creator.teleport(spot, { dimension, facingLocation: face, checkForBlocks: true });
  } catch {
    try {
      creator.teleport(spot, { dimension, facingLocation: face, checkForBlocks: false });
    } catch {
      return false;
    }
  }
  try {
    creator.lookAt(face);
  } catch {
    // Some bodies reject lookAt. The teleport facing is enough.
  }
  return true;
}

function ensureCreator(focus, phase) {
  if (system.currentTick < state.holdRespawn) return null;
  const found = creators(focus.dimension);
  if (found.length > 1) {
    found.sort((a, b) => distance(a.location, focus.location) - distance(b.location, focus.location));
    for (const extra of found.slice(1)) cull(extra);
    return found[0];
  }
  if (found.length === 1) return found[0];
  const behind = offsetBehind(focus.location, focus.getViewDirection(), stalkDistance(phase), state.side * 6);
  const spot = findStand(focus.dimension, behind.x, focus.location.y, behind.z);
  try {
    return focus.dimension.spawnEntity(TYPE_ID, spot);
  } catch {
    return null;
  }
}

function placeTorch(dimension, player) {
  const base = player.location;
  const offsets = [
    [1, 0],
    [-1, 0],
    [0, 1],
    [0, -1],
    [2, 1],
    [-2, -1],
  ];
  for (const [dx, dz] of offsets) {
    const x = Math.floor(base.x + dx);
    const z = Math.floor(base.z + dz);
    const y = Math.floor(base.y);
    const spot = standable(dimension, x, y, z);
    if (!spot) continue;
    try {
      dimension.getBlock({ x: Math.floor(spot.x), y: spot.y, z: Math.floor(spot.z) }).setType("minecraft:torch");
      return true;
    } catch {
      // The block rejected a torch. Try the next square.
    }
  }
  return false;
}

function snuffTorches(dimension, player) {
  const origin = player.location;
  let removed = 0;
  for (let dx = -4; dx <= 4 && removed < 3; dx++) {
    for (let dy = -2; dy <= 2 && removed < 3; dy++) {
      for (let dz = -4; dz <= 4 && removed < 3; dz++) {
        const pos = {
          x: Math.floor(origin.x + dx),
          y: Math.floor(origin.y + dy),
          z: Math.floor(origin.z + dz),
        };
        let block;
        try {
          block = dimension.getBlock(pos);
        } catch {
          continue;
        }
        if (!block) continue;
        if (block.typeId !== "minecraft:torch" && block.typeId !== "minecraft:soul_torch") continue;
        try {
          block.setType("minecraft:air");
          removed += 1;
        } catch {
          // Leave blocks the script is not allowed to edit.
        }
      }
    }
  }
  return removed;
}

function giveGift(player, phase) {
  const spec = giftFor(phase, Math.random());
  let item;
  try {
    item = new ItemStack(spec.id, spec.amount);
  } catch {
    return;
  }
  const marked = `${GIFT_PREFIX} ${spec.label}`;
  try {
    item.nameTag = marked;
  } catch {
    // Older item stacks keep the vanilla name. Lore still marks them.
  }
  try {
    item.setLore([marked]);
  } catch {
    // Lore is a backup mark.
  }
  try {
    player.dimension.spawnItem(item, {
      x: player.location.x + 0.4,
      y: player.location.y,
      z: player.location.z + 0.4,
    });
  } catch {
    return;
  }
  whisper(player, phase <= 0 ? pickWhisper(0, Math.random()) : pickWhisper(1, Math.random()));
}

function maybeHelp(player, phase, night) {
  if (phase > 1) return;
  const now = system.currentTick;
  const last = giftAt.get(player.id) ?? 0;
  if (now - last < 2400) return;
  let health = 20;
  try {
    health = player.getComponent("minecraft:health")?.currentValue ?? 20;
  } catch {
    health = 20;
  }
  if (phase === 0 && night) {
    const day = world.getDay();
    const torchDay = world.getDynamicProperty(KEY.torchDay);
    let count = torchDay === day ? num(KEY.torchCount, 0) : 0;
    let light = 15;
    try {
      light = player.dimension.getLightLevel(player.location);
    } catch {
      light = 15;
    }
    if (light < 7 && count < 6 && placeTorch(player.dimension, player)) {
      count += 1;
      world.setDynamicProperty(KEY.torchDay, day);
      world.setDynamicProperty(KEY.torchCount, count);
      whisper(player, "I kept a light where your hands would go.");
    }
  }
  if (health <= 12 || (phase === 0 && Math.random() < 0.35) || (phase === 1 && Math.random() < 0.4)) {
    giftAt.set(player.id, now);
    giveGift(player, phase);
  }
}

function strikeWeather(dimension, seconds) {
  if (dimension.id !== "minecraft:overworld") return;
  try {
    dimension.setWeather(WeatherType.Thunder, 20 * seconds);
  } catch {
    // Nether and some template worlds reject weather.
  }
}

function lightningNear(player, blocks) {
  const view = player.getViewDirection();
  const spot = offsetBehind(player.location, view, blocks, (Math.random() - 0.5) * 6);
  const stand = findStand(player.dimension, spot.x, player.location.y, spot.z);
  try {
    player.dimension.spawnEntity("minecraft:lightning_bolt", {
      x: stand.x,
      y: stand.y + 1,
      z: stand.z,
    });
  } catch {
    // Lightning can fail in an unloaded chunk.
  }
}

function effect(player, id, ticks, amplifier) {
  try {
    player.addEffect(id, ticks, { amplifier, showParticles: false });
  } catch {
    // Unknown effect ids should not stop the rest of the event.
  }
}

function playAt(player, sound, pitch) {
  try {
    player.dimension.playSound(sound, player.location, { volume: 0.7, pitch });
  } catch {
    try {
      player.playSound(sound, { volume: 0.7, pitch });
    } catch {
      // Keep going if this build renamed the sound.
    }
  }
}

function runEvent(player, creator, phase, name) {
  if (name === "whisper") {
    whisper(player, pickWhisper(phase, Math.random()));
    return;
  }
  if (name === "cave") {
    playAt(player, "ambient.cave", 0.7);
    return;
  }
  if (name === "flicker") {
    effect(player, "minecraft:blindness", 12, 0);
    jolt(player, 0.15, 0.25);
    return;
  }
  if (name === "thunder") {
    strikeWeather(player.dimension, phase >= 3 ? 50 : phase === 2 ? 28 : 14);
    playAt(player, "ambient.weather.thunder", 0.8);
    if (phase >= 2) jolt(player, 0.35, 0.6);
    whisper(player, pickWhisper(phase, Math.random()));
    return;
  }
  if (name === "lightning") {
    strikeWeather(player.dimension, 20);
    lightningNear(player, phase >= 3 && Math.random() < 0.2 ? 4 : 8);
    jolt(player, phase >= 3 ? 0.7 : 0.4, 0.45);
    return;
  }
  if (name === "torches") {
    const removed = snuffTorches(player.dimension, player);
    if (removed > 0) whisper(player, "Count the torches. Then count them again.");
    return;
  }
  if (name === "darkness") {
    effect(player, "minecraft:darkness", phase >= 3 ? 80 : 50, 0);
    effect(player, "minecraft:slowness", 40, phase >= 3 ? 1 : 0);
    jolt(player, 0.3, 0.4);
    return;
  }
  if (name === "knock") {
    const behind = offsetBehind(player.location, player.getViewDirection(), 2, 0);
    try {
      player.dimension.playSound("random.door_open", { x: behind.x, y: player.location.y, z: behind.z }, { volume: 0.8, pitch: 0.6 });
    } catch {
      playAt(player, "mob.zombie.wood", 0.6);
    }
    return;
  }
  if (name === "rush" && creator && valid(creator)) {
    const ahead = offsetAhead(player.location, player.getViewDirection(), 3);
    const spot = findStand(player.dimension, ahead.x, player.location.y, ahead.z);
    moveCreator(creator, player.dimension, spot, faceOf(player));
    effect(player, "minecraft:darkness", 40, 0);
    jolt(player, 0.8, 0.5);
    title(player, "§4It is in front of you", "§7Day seven does not wait.");
    hurtPlayer(player, creator, phase, true);
  }
}

function maybeEvents(players, creator, phase, night) {
  for (const player of players) {
    if (Math.random() > eventChance(phase, night)) continue;
    runEvent(player, creator, phase, pickEvent(phase, Math.random()));
  }
}

function hurtPlayer(player, creator, phase, forced) {
  const amount = forced ? Math.max(4, hurtAmount(phase)) : hurtAmount(phase);
  if (amount <= 0 || !valid(creator)) return;
  const now = system.currentTick;
  const last = hurtAt.get(player.id) ?? 0;
  if (!forced && now - last < hurtCooldownTicks(phase)) return;
  hurtAt.set(player.id, now);
  try {
    player.applyDamage(amount, { cause: EntityDamageCause.entityAttack, damagingEntity: creator });
  } catch {
    return;
  }
  effect(player, "minecraft:darkness", 30, 0);
  if (phase >= 3) effect(player, "minecraft:slowness", 40, 1);
  try {
    player.playSound("mob.endermen.hit", { volume: 0.6, pitch: 0.6 });
  } catch {
    // The damage already landed.
  }
}

function stalk(players, creator, phase) {
  if (!creator || !valid(creator)) return;
  const focus = nearestPlayer(creator, players);
  if (!focus) return;
  const view = focus.getViewDirection();
  const head = faceOf(focus);
  const looked = isLookingAt(view, { x: focus.location.x, y: focus.location.y + 1.6, z: focus.location.z }, {
    x: creator.location.x,
    y: creator.location.y + 1.4,
    z: creator.location.z,
  });
  const watched = looked ? (watchTicks.get(focus.id) ?? 0) + 10 : 0;
  watchTicks.set(focus.id, watched);
  const gap = distance(creator.location, focus.location);
  const name = gap < 18 ? `${PHASES[phase].color}${PHASES[phase].name}` : "";
  try {
    creator.nameTag = name;
  } catch {
    // Name tags are cosmetic.
  }

  if (phase <= 1 && (gap < fleeDistance(phase) || watched >= 50)) {
    if (watched >= 50) {
      const now = system.currentTick;
      if (now >= (ackAt.get(focus.id) ?? 0)) {
        addMood(DELTA.stare);
        ackAt.set(focus.id, now + 20 * 90);
        whisper(focus, "You looked.");
      }
    } else if (gap < fleeDistance(phase)) {
      whisper(focus, "You were not supposed to come closer.");
    }
    state.side = Math.random() < 0.5 ? -1 : 1;
    const away = offsetBehind(focus.location, view, stalkDistance(phase) + 16, state.side * 8);
    const spot = findStand(focus.dimension, away.x, focus.location.y, away.z);
    moveCreator(creator, focus.dimension, spot, head);
    watchTicks.set(focus.id, 0);
    state.nextBlink = system.currentTick + blinkGapTicks(phase);
    try {
      focus.dimension.playSound("mob.endermen.portal", spot, { volume: 0.45, pitch: 0.55 });
    } catch {
      // The teleport is the event.
    }
    return;
  }

  if (phase <= 1 && looked) {
    try {
      creator.lookAt(head);
    } catch {
      // Holding still is enough when lookAt is unavailable.
    }
    return;
  }

  if (system.currentTick < state.nextBlink && creator.dimension.id === focus.dimension.id) {
    try {
      creator.lookAt(head);
    } catch {
      // Keep the previous facing.
    }
    if (gap <= hurtRange(phase)) hurtPlayer(focus, creator, phase, false);
    return;
  }

  state.side = Math.random() < 0.5 ? -1 : 1;
  const desired = phase >= 2 && looked ? Math.max(4, stalkDistance(phase) - 4) : stalkDistance(phase);
  const behind = offsetBehind(focus.location, view, desired, state.side * desired * 0.28);
  const spot = findStand(focus.dimension, behind.x, focus.location.y, behind.z);
  const moved = moveCreator(creator, focus.dimension, spot, head);
  state.nextBlink = system.currentTick + blinkGapTicks(phase);
  if (moved && gap < 40) {
    try {
      focus.dimension.playSound("mob.endermen.portal", spot, { volume: phase >= 2 ? 0.55 : 0.28, pitch: 0.5 });
      focus.spawnParticle("minecraft:basic_smoke_particle", spot);
    } catch {
      // Particles are optional.
    }
  }
  if (distance(spot, focus.location) <= hurtRange(phase)) hurtPlayer(focus, creator, phase, false);
  if (phase >= 2 && gap < 16 && step % 8 === 0) {
    try {
      focus.playSound("mob.warden.heartbeat", { volume: Math.min(0.8, 8 / Math.max(4, gap)), pitch: 0.8 });
    } catch {
      // Heartbeat is flavor.
    }
  }
}

function watchSleep(players) {
  const day = world.getDay();
  for (const player of players) {
    let sleeping = false;
    try {
      sleeping = player.isSleeping === true;
    } catch {
      sleeping = false;
    }
    const before = wasSleeping.get(player.id) === true;
    wasSleeping.set(player.id, sleeping);
    if (!sleeping || before) continue;
    if (world.getDynamicProperty(KEY.sleepDay) === day) continue;
    world.setDynamicProperty(KEY.sleepDay, day);
    addMood(DELTA.sleep);
    whisper(player, "You closed your eyes. I did not.");
  }
}

function stackIsGift(stack) {
  if (!stack) return false;
  if (isGiftName(stack.nameTag)) return true;
  try {
    const lore = stack.getLore();
    return Array.isArray(lore) && lore.some((line) => isGiftName(line));
  } catch {
    return false;
  }
}

function onDrop(event) {
  const dropper = event.entity;
  if (!valid(dropper) || dropper.typeId !== "minecraft:player") return;
  const player = dropper;
  system.run(() => {
    if (!valid(player)) return;
    let discarded = false;
    let tribute = false;
    for (const itemEntity of event.items ?? []) {
      if (!valid(itemEntity)) continue;
      let stack;
      try {
        stack = itemEntity.getComponent("minecraft:item")?.itemStack;
      } catch {
        stack = undefined;
      }
      if (!stack) continue;
      if (stackIsGift(stack)) {
        discarded = true;
        continue;
      }
      const figure = creators(player.dimension)[0];
      if (!figure) continue;
      if (distance(itemEntity.location, figure.location) > 4) continue;
      tribute = true;
      try {
        itemEntity.remove();
      } catch {
        try {
          itemEntity.kill();
        } catch {
          // The item can stay on the ground if it cannot be taken.
        }
      }
    }
    if (discarded) {
      addMood(DELTA.discard);
      whisper(player, "You dropped what I gave you.");
    } else if (tribute) {
      addMood(DELTA.tribute);
      whisper(player, "Set at my feet. I will count it.");
    }
  });
}

function thank(player) {
  if (!valid(player)) return;
  const now = system.currentTick;
  if (now < (thankAt.get(player.id) ?? 0)) return;
  thankAt.set(player.id, now + 20);
  addMood(DELTA.offering);
  const phase = phaseNow();
  if (phase >= 3) whisper(player, "Too late. I kept the ledger.");
  else whisper(player, "You remembered.");
  try {
    player.playSound("random.levelup", { volume: 0.35, pitch: 0.6 });
  } catch {
    // The mood change is the reward.
  }
}

function giveOffering(player) {
  if (!valid(player)) return;
  if (player.getDynamicProperty(KEY.welcomed) === true) return;
  if (welcoming.has(player.id)) return;
  welcoming.add(player.id);
  system.runTimeout(() => {
    welcoming.delete(player.id);
    if (!valid(player)) return;
    if (player.getDynamicProperty(KEY.welcomed) === true) return;
    player.setDynamicProperty(KEY.welcomed, true);
    title(player, "§fIt has noticed you", "§7Seven days.");
    say(player, "§8The Creator is behind you. The action bar is the seven-day count.");
    say(player, "§8Hold an Offering, look at it, or drop an item at its feet, and the mood stays softer.");
    say(player, "§8Attack it, throw its gifts away, or sleep, and it stops pretending to help.");
    try {
      const inventory = player.getComponent("minecraft:inventory");
      const leftover = inventory?.container?.addItem(new ItemStack(OFFERING_ID, 1));
      if (leftover) player.dimension.spawnItem(leftover, player.location);
    } catch {
      try {
        player.dimension.spawnItem(new ItemStack(OFFERING_ID, 1), player.location);
      } catch {
        say(player, "§8The Offering is in the creative items menu if your inventory would not take it.");
      }
    }
  }, 40);
}

function onScript(event) {
  const id = event.id;
  const message = `${event.message ?? ""}`.trim();
  const value = Number(message);
  if (id === "creator:mood" && Number.isFinite(value)) {
    saveMood(value);
  } else if (id === "creator:add" && Number.isFinite(value)) {
    addMood(value);
  } else if (id === "creator:day" && Number.isFinite(value)) {
    state.startDay = world.getDay() - Math.max(0, Math.floor(value));
    world.setDynamicProperty(KEY.startDay, state.startDay);
    state.phase = undefined;
  } else {
    return;
  }
  const player = event.sourceEntity;
  if (valid(player) && player.typeId === "minecraft:player") {
    say(player, `§8The Creator: day ${clock().dayNumber}, mood ${Math.round(state.mood)}%.`);
  }
}

function tick() {
  step += 1;
  const players = livingPlayers();
  if (players.length === 0) return;
  load();
  const info = clock();
  const phase = resolvePhase(info.elapsed, state.mood);
  let night = false;
  try {
    night = isNight(world.getTimeOfDay());
  } catch {
    night = false;
  }
  announce(players, info, phase);
  for (const player of players) giveOffering(player);
  if (step % 2 === 0) showHud(players, info, phase);
  if (step % 60 === 0) addMood(DRIFT + (night ? NIGHT_DRIFT : 0));
  watchSleep(players);
  const focus = players[0];
  let creator = ensureCreator(focus, phase);
  if (creator && valid(creator)) {
    const sameDimension = players.find((player) => player.dimension.id === creator.dimension.id);
    if (!sameDimension) {
      const destination = offsetBehind(focus.location, focus.getViewDirection(), stalkDistance(phase), 0);
      const spot = findStand(focus.dimension, destination.x, focus.location.y, destination.z);
      moveCreator(creator, focus.dimension, spot, faceOf(focus));
    }
  }
  stalk(players, creator, phase);
  if (step % 40 === 0) {
    if (creator && !valid(creator)) creator = null;
    maybeEvents(players, creator, phase, night);
    for (const player of players) maybeHelp(player, phase, night);
    if (phase >= 3) strikeWeather(players[0].dimension, 40);
  }
}

function report(error) {
  const message = String(error && error.stack ? error.stack : error).slice(0, 180);
  console.warn(`The Creator: ${message}`);
  if (seenErrors.has(message) || seenErrors.size >= 4) return;
  seenErrors.add(message);
  try {
    world.sendMessage(`§8The Creator falters: §7${message}`);
  } catch {
    // The content log still has it.
  }
}

function listen(label, subscribe) {
  try {
    subscribe();
  } catch (error) {
    report(`${label}: ${error}`);
  }
}

listen("hurt", () => {
  world.afterEvents.entityHurt.subscribe((event) => {
    try {
      const hurt = event.hurtEntity;
      if (!valid(hurt) || hurt.typeId !== TYPE_ID) return;
      const attacker = event.damageSource?.damagingEntity;
      if (!valid(attacker) || attacker.typeId !== "minecraft:player") return;
      addMood(DELTA.attack);
      whisper(attacker, "That is an answer.");
    } catch (error) {
      report(error);
    }
  });
});

listen("die", () => {
  world.afterEvents.entityDie.subscribe((event) => {
    try {
      const dead = event.deadEntity;
      if (!dead || dead.typeId !== TYPE_ID) return;
      if (typeof dead.hasTag === "function" && dead.hasTag("creator_cull")) return;
      addMood(40);
      state.holdRespawn = system.currentTick + 20 * 12;
      world.sendMessage("§4You cannot unmake it. It will be behind you again.");
    } catch (error) {
      report(error);
    }
  });
});

listen("drop", () => {
  world.afterEvents.entityItemDrop.subscribe(onDrop);
});

listen("offer", () => {
  const finish = (event) => {
    try {
      if (event.itemStack?.typeId !== OFFERING_ID) return;
      thank(event.source);
    } catch (error) {
      report(error);
    }
  };
  if (world.afterEvents.itemCompleteUse) world.afterEvents.itemCompleteUse.subscribe(finish);
  else if (world.afterEvents.itemUse) world.afterEvents.itemUse.subscribe(finish);
});

listen("spawn", () => {
  world.afterEvents.playerSpawn.subscribe((event) => {
    if (!event.initialSpawn) return;
    system.run(() => {
      if (valid(event.player)) giveOffering(event.player);
    });
  });
});

listen("scriptevent", () => {
  system.afterEvents.scriptEventReceive.subscribe((event) => {
    try {
      load();
      onScript(event);
    } catch (error) {
      report(error);
    }
  });
});

listen("chat", () => {
  const signal = world.afterEvents.chatSend;
  if (!signal) return;
  signal.subscribe((event) => {
    const text = `${event.message ?? ""}`.toLowerCase();
    if (!text.includes("thank")) return;
    const player = event.sender ?? event.player;
    if (!valid(player)) return;
    addMood(-8);
    whisper(player, "Words. I will still weigh them.");
  });
});

system.runInterval(() => {
  try {
    tick();
  } catch (error) {
    report(error);
  }
}, 10);

console.warn("The Creator script loaded");
