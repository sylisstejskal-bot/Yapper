import { system, world, GameMode, ItemStack, CommandPermissionLevel, CustomCommandParamType, CustomCommandStatus } from "@minecraft/server";
import { askObesity } from "./aiNet.js";
import {
  DRAIN_INTERVAL_SECONDS,
  START_HUNGER,
  aiGuideLines,
  applyFeed,
  boxSpots,
  clampHunger,
  actionBarText,
  clipReply,
  connectFeedback,
  connectPhrase,
  crossedWarning,
  localReply,
  distanceSquared,
  drainHunger,
  feedMessage,
  foodPoints,
  hungerDisplay,
  leftoverFor,
  isFreshPlacement,
  parseConnectCommand,
  sameOwner,
  shouldCancelBoxUse,
  shouldTeleport,
} from "./logic.js";

const BOX_ID = "obesity:box";
const PET_ID = "obesity:obesity";
const DIMS = ["overworld", "nether", "the_end"];

const hudState = new Map();
const busyBoxes = new Set();
const fedAt = new Map();
const placedTick = new Map();
const talkHistory = new Map();
const talkBusy = new Set();
const toldOffline = new Set();
const showedGuide = new Set();
const TALK_DISTANCE = 8;

function warn(message) {
  console.warn(`[Obesity] ${message}`);
}

function boxKey(dim, loc) {
  return `${dim.id}:${loc.x},${loc.y},${loc.z}`;
}

function nutritionOf(item) {
  try {
    const food = item.getComponent("minecraft:food");
    if (food && typeof food.nutrition === "number") return food.nutrition;
  } catch (error) {
    // Not every item exposes a food component.
  }
  return undefined;
}

function moveOffBox(player) {
  try {
    const inventory = player.getComponent("minecraft:inventory");
    const container = inventory && inventory.container;
    if (!container) return;
    const selected = container.getItem(player.selectedSlotIndex);
    if (!selected || selected.typeId !== BOX_ID) return;
    const hotbar = Math.min(9, container.size);
    for (let slot = 0; slot < hotbar; slot++) {
      if (slot === player.selectedSlotIndex) continue;
      if (!container.getItem(slot)) {
        player.selectedSlotIndex = slot;
        return;
      }
    }
  } catch (error) {
    // Staying on the box is fine. The next tap still opens it.
  }
}

function sneaking(player) {
  try {
    return Boolean(player.isSneaking);
  } catch (error) {
    return false;
  }
}

function giveOrDrop(player, typeId, amount) {
  const stack = new ItemStack(typeId, amount || 1);
  try {
    const inventory = player.getComponent("minecraft:inventory");
    if (inventory && inventory.container) {
      const leftover = inventory.container.addItem(stack);
      if (!leftover) return;
      player.dimension.spawnItem(leftover, player.location);
      return;
    }
  } catch (error) {
    warn(`could not give ${typeId}: ${error}`);
  }
  try {
    player.dimension.spawnItem(stack, player.location);
  } catch (error) {
    warn(`could not drop ${typeId}: ${error}`);
  }
}

function takeOne(player, typeId) {
  const mode = player.getGameMode();
  if (mode === GameMode.creative || mode === GameMode.spectator) return true;
  const inventory = player.getComponent("minecraft:inventory");
  if (!inventory || !inventory.container) return false;
  const slot = player.selectedSlotIndex;
  const stack = inventory.container.getItem(slot);
  if (!stack || stack.typeId !== typeId) return false;
  const leftoverId = leftoverFor(typeId);
  if (stack.amount > 1) {
    stack.amount -= 1;
    inventory.container.setItem(slot, stack);
    if (leftoverId) giveOrDrop(player, leftoverId, 1);
  } else if (leftoverId) {
    inventory.container.setItem(slot, new ItemStack(leftoverId, 1));
  } else {
    inventory.container.setItem(slot, undefined);
  }
  return true;
}

function playAt(dim, sound, loc, pitch, volume) {
  try {
    dim.playSound(sound, loc, { pitch: pitch, volume: volume });
  } catch (error) {
    // Vanilla sound ids differ slightly between versions; the gameplay still works.
  }
}

function burst(dim, particle, loc, count, spread, lift) {
  for (let i = 0; i < count; i++) {
    try {
      dim.spawnParticle(particle, {
        x: loc.x + (Math.random() - 0.5) * spread,
        y: loc.y + lift + Math.random() * spread,
        z: loc.z + (Math.random() - 0.5) * spread,
      });
    } catch (error) {
      return;
    }
  }
}

function rememberOwner(entity, player) {
  try {
    entity.setDynamicProperty("obesity:owner_id", player.id);
    entity.setDynamicProperty("obesity:owner_name", player.name);
  } catch (error) {
    warn(`could not remember owner: ${error}`);
  }
  try {
    const tame = entity.getComponent("minecraft:tameable");
    if (tame && !tame.isTamed) tame.tame(player);
  } catch (error) {
    warn(`could not tame Obesity: ${error}`);
  }
}

function findPlayer(ownerId, ownerName) {
  for (const player of world.getPlayers()) {
    if (sameOwner({ id: ownerId, name: ownerName }, player)) return player;
  }
  return undefined;
}

function showHud(player, title, subtitle) {
  const now = system.currentTick;
  const prev = hudState.get(player.id);
  if (prev && prev.title === title && prev.subtitle === subtitle && now - prev.at < 80) return;
  player.onScreenDisplay.setTitle(title, {
    subtitle,
    fadeInDuration: 0,
    stayDuration: 140,
    fadeOutDuration: 0,
  });
  hudState.set(player.id, { title, subtitle, at: now });
}

function clearHud(player) {
  const prev = hudState.get(player.id);
  if (!prev) return;
  if (prev.holdUntil && system.currentTick < prev.holdUntil) return;
  hudState.delete(player.id);
  try {
    player.onScreenDisplay.setTitle("", {
      subtitle: "",
      fadeInDuration: 0,
      stayDuration: 0,
      fadeOutDuration: 0,
    });
  } catch (error) {
    warn(`could not clear hunger bar: ${error}`);
  }
}

function showNotice(player, lines) {
  const list = (lines || []).filter((line) => typeof line === "string" && line.trim());
  if (!player || list.length === 0) return;
  for (const line of list) {
    try {
      player.sendMessage(`§e${line}`);
    } catch (error) {
      // Chat can be closed. The action bar still shows the first line.
    }
  }
  try {
    player.onScreenDisplay.setActionBar(`§e${actionBarText(list[0])}`);
  } catch (error) {
    warn(`could not show a notice: ${error}`);
  }
}

function showSpeech(player, reply) {
  const clean = clipReply(reply) || "Obesity is here.";
  try {
    player.sendMessage(`§eObesity: §f${clean}`);
  } catch (error) {
    // The action bar carries the same reply.
  }
  try {
    player.onScreenDisplay.setActionBar(`§eObesity: ${actionBarText(clean)}`);
  } catch (error) {
    warn(`could not show Obesity's reply: ${error}`);
  }
}

function playerToken(player) {
  try {
    return player ? { id: player.id, name: player.name } : undefined;
  } catch (error) {
    return undefined;
  }
}

function playerFromToken(token, fallback) {
  if (!token) return fallback;
  try {
    const players = world.getPlayers();
    return (
      players.find((entry) => entry.id === token.id) ||
      players.find((entry) => token.name && entry.name === token.name) ||
      fallback
    );
  } catch (error) {
    return fallback;
  }
}

function showAiGuide(player) {
  try {
    const lines = showedGuide.has(player.id)
      ? [
          "/obesity:connect to YOUR_COHERE_API_KEY",
          "/obesity:connect to local ai server LOCAL_AI_SERVER_PLACEHOLDER_HERE",
          "Replace the placeholder. Do not type /connect. That one is Minecraft's and it closes.",
        ]
      : aiGuideLines();
    showedGuide.add(player.id);
    showNotice(player, lines);
    player.onScreenDisplay.setActionBar("§e/obesity:connect to YOUR_COHERE_API_KEY");
  } catch (error) {
    warn(`could not show the AI steps: ${error}`);
  }
}

function placeBoxNear(player) {
  let yaw = 0;
  try {
    yaw = player.getRotation().y;
  } catch (error) {
    yaw = 0;
  }
  const spots = boxSpots(player.location.x, player.location.y, player.location.z, yaw);
  const dim = player.dimension;
  for (const spot of spots) {
    try {
      const below = dim.getBlock({ x: spot.x, y: spot.y - 1, z: spot.z });
      const here = dim.getBlock(spot);
      const above = dim.getBlock({ x: spot.x, y: spot.y + 1, z: spot.z });
      if (!below || !here || !above) continue;
      if (!below.isSolid || below.isLiquid) continue;
      if (!here.isAir || !above.isAir) continue;
      dim.setBlockType(spot, BOX_ID);
      return true;
    } catch (error) {
      // That spot is outside the loaded area. Try the next one.
    }
  }
  return false;
}

function welcome(player) {
  try {
    if (player.hasTag("obesity_box_spawned")) return;
    const placed = placeBoxNear(player);
    player.addTag("obesity_box_spawned");
    if (placed) {
      player.sendMessage("§6Obesity§f dropped a sealed box in front of you. Tap it.");
    } else {
      giveOrDrop(player, BOX_ID, 1);
      player.sendMessage("§6Obesity§f could not find open ground, so the box is in your inventory. Place it, then tap it.");
    }
    showAiGuide(player);
  } catch (error) {
    warn(`welcome failed: ${error}`);
  }
}

function saveConnection(player, result) {
  try {
    player.setDynamicProperty("obesity:ai_kind", result.kind);
    player.setDynamicProperty("obesity:ai_secret", result.kind === "cohere" ? result.key : result.server);
  } catch (error) {
    warn(`could not save the AI connection: ${error}`);
    return false;
  }
  return true;
}

function applyConnect(player, raw) {
  const result = parseConnectCommand(raw);
  if (result.kind === "ignore") return;
  if ((result.kind === "cohere" || result.kind === "local") && !saveConnection(player, result)) {
    showNotice(player, ["Obesity could not save that connection."]);
    return;
  }
  showNotice(player, connectFeedback(result));
}

function nearbyPet(player) {
  let list;
  try {
    list = player.dimension.getEntities({
      type: PET_ID,
      location: player.location,
      maxDistance: TALK_DISTANCE,
    });
  } catch (error) {
    return undefined;
  }
  let best;
  let bestDistance = TALK_DISTANCE * TALK_DISTANCE + 1;
  for (const entity of list) {
    let distance;
    try {
      distance = distanceSquared(entity.location, player.location);
    } catch (error) {
      continue;
    }
    let owned = false;
    try {
      owned = sameOwner(
        {
          id: entity.getDynamicProperty("obesity:owner_id"),
          name: entity.getDynamicProperty("obesity:owner_name"),
        },
        player
      );
    } catch (error) {
      owned = false;
    }
    if (owned) return entity;
    if (distance < bestDistance) {
      best = entity;
      bestDistance = distance;
    }
  }
  return best;
}

function rememberTurn(player, userText, reply) {
  const prior = talkHistory.get(player.id) || [];
  const history = prior.concat([
    { role: "user", content: userText },
    { role: "assistant", content: reply },
  ]);
  while (history.length > 8) history.shift();
  talkHistory.set(player.id, history);
}

function hearChat(player, text) {
  const pet = nearbyPet(player);
  if (!pet) {
    showNotice(player, ["Stand next to Obesity, then talk again."]);
    return;
  }
  const kind = player.getDynamicProperty("obesity:ai_kind");
  const secret = player.getDynamicProperty("obesity:ai_secret");
  if ((kind !== "cohere" && kind !== "local") || typeof secret !== "string" || !secret) {
    showAiGuide(player);
    return;
  }
  let hunger;
  try {
    hunger = pet.getProperty("obesity:hunger");
  } catch (error) {
    hunger = undefined;
  }
  const prior = talkHistory.get(player.id) || [];
  const reply = localReply(text, hunger);
  rememberTurn(player, text, reply);
  showSpeech(player, reply);
  if (talkBusy.has(player.id)) return;
  talkBusy.add(player.id);
  const turns = prior.concat([{ role: "user", content: text }]);
  let finished = false;
  const timeout = system.runTimeout(() => {
    finished = true;
    talkBusy.delete(player.id);
  }, 40);
  askObesity(kind, secret, turns, hunger)
    .then((result) => {
      if (finished) return;
      finished = true;
      try {
        system.clearRun(timeout);
      } catch (error) {
        // The timer already finished.
      }
      if (result && result.ok && result.text && result.text !== reply) {
        rememberTurn(player, text, result.text);
        showSpeech(player, result.text);
        return;
      }
      if (result && (result.reason === "denied" || result.status === 401 || result.status === 403) && !toldOffline.has(player.id)) {
        toldOffline.add(player.id);
        showNotice(player, ["Cohere refused that key. Create a new one at cohere.com, then run /obesity:connect to your key."]);
      }
    })
    .catch((error) => {
      warn(`talk failed: ${error}`);
    })
    .finally(() => {
      talkBusy.delete(player.id);
    });
}

function openBoxNow(player, dim, loc) {
  const key = boxKey(dim, loc);
  if (busyBoxes.has(key)) return;
  if (isFreshPlacement(placedTick.get(key), system.currentTick)) {
    showNotice(player, ["Tap the box again."]);
    return;
  }
  let block;
  try {
    block = dim.getBlock(loc);
  } catch (error) {
    showNotice(player, ["Move closer to the box and tap it."]);
    return;
  }
  if (!block || block.typeId !== BOX_ID) return;
  busyBoxes.add(key);
  system.runTimeout(() => busyBoxes.delete(key), 20);

  const pos = { x: loc.x + 0.5, y: loc.y, z: loc.z + 0.5 };
  try {
    dim.setBlockType(loc, "minecraft:air");
  } catch (error) {
    busyBoxes.delete(key);
    warn(`could not open box: ${error}`);
    showNotice(player, ["The box did not open. Tap it again."]);
    return;
  }

  let entity;
  try {
    entity = dim.spawnEntity(PET_ID, pos);
  } catch (error) {
    try {
      dim.setBlockType(loc, BOX_ID);
    } catch (restoreError) {
      warn(`could not restore box: ${restoreError}`);
    }
    busyBoxes.delete(key);
    warn(`could not spawn Obesity: ${error}`);
    showNotice(player, ["The box rattles, but Obesity will not come out. Try again."]);
    return;
  }

  try {
    entity.teleport(pos, { facingLocation: player.location });
  } catch (error) {
    // Facing is cosmetic. The pet still exists.
  }
  try {
    entity.nameTag = "§eObesity";
    entity.setProperty("obesity:hunger", START_HUNGER);
    entity.setProperty("obesity:eating", false);
    entity.setProperty("obesity:fresh", true);
  } catch (error) {
    warn(`could not set up Obesity: ${error}`);
  }
  rememberOwner(entity, player);
  system.runTimeout(() => {
    try {
      entity.setProperty("obesity:fresh", false);
    } catch (error) {
      // The pet was removed during the pop animation.
    }
  }, 16);

  playAt(dim, "random.chestopen", pos, 0.9, 1);
  playAt(dim, "random.pop", pos, 1.25, 0.8);
  burst(dim, "minecraft:heart_particle", pos, 8, 0.8, 0.3);
  try {
    showNotice(player, ["Obesity tumbles out. Hold food and tap them.", "To let them talk: /obesity:connect to YOUR_COHERE_API_KEY"]);
    player.onScreenDisplay.setActionBar("§eHold food and tap Obesity");
    const display = hungerDisplay(START_HUNGER, 1);
    showHud(player, display.title, display.subtitle);
  } catch (error) {
    warn(`could not announce Obesity: ${error}`);
  }
}

function scheduleOpen(player, dimId, loc) {
  const spot = { x: Number(loc.x), y: Number(loc.y), z: Number(loc.z) };
  const token = playerToken(player);
  const savedId = String(dimId || "overworld").replace(/^minecraft:/, "");
  system.run(() => {
    const current = playerFromToken(token, player);
    if (!current) return;
    let dim;
    try {
      dim = world.getDimension(savedId);
    } catch (error) {
      try {
        dim = current.dimension;
      } catch (dimensionError) {
        dim = undefined;
      }
    }
    if (!dim) {
      showNotice(current, ["Move closer to the box and tap it."]);
      return;
    }
    try {
      openBoxNow(current, dim, spot);
    } catch (error) {
      warn(`open failed: ${error}`);
      showNotice(current, ["The box did not open. Tap it again."]);
    }
  });
}

function dimensionIdOf(source) {
  try {
    if (source && source.dimension && source.dimension.id) return source.dimension.id;
    if (source && source.id) return source.id;
  } catch (error) {
    // Fall back to the overworld. The player is standing at the box.
  }
  return "overworld";
}

function listen(register) {
  try {
    register();
  } catch (error) {
    warn(`could not listen for the box: ${error}`);
  }
}

function selectedFood(player) {
  const inventory = player.getComponent("minecraft:inventory");
  if (!inventory || !inventory.container) return undefined;
  const stack = inventory.container.getItem(player.selectedSlotIndex);
  if (!stack) return undefined;
  const points = foodPoints(stack.typeId, nutritionOf(stack));
  if (points <= 0) return undefined;
  return { stack, points };
}

function feedOrHint(entity, player) {
  const ownerId = entity.getDynamicProperty("obesity:owner_id");
  if (!ownerId) rememberOwner(entity, player);
  const last = fedAt.get(entity.id) || 0;
  if (system.currentTick - last < 8) return;

  let hunger = clampHunger(entity.getProperty("obesity:hunger"));
  const food = selectedFood(player);
  if (!food) {
    const hint =
      hunger <= 0
        ? "§cObesity is starving. Hold food and tap them."
        : hunger >= 20
          ? "§aObesity is stuffed."
          : "§eHold food and tap Obesity.";
    player.onScreenDisplay.setActionBar(hint);
    playAt(entity.dimension, "random.burp", entity.location, hunger <= 6 ? 0.55 : 0.8, 0.35);
    return;
  }

  const result = applyFeed(hunger, food.points);
  if (result.status === "stuffed") {
    player.onScreenDisplay.setActionBar(feedMessage(result, food.stack.typeId));
    return;
  }
  if (!takeOne(player, food.stack.typeId)) return;
  fedAt.set(entity.id, system.currentTick);
  entity.setProperty("obesity:hunger", result.hunger);
  entity.setProperty("obesity:eating", true);
  system.runTimeout(() => {
    try {
      entity.setProperty("obesity:eating", false);
    } catch (error) {
      // Pet unloaded or died mid-bite.
    }
  }, 16);

  const tag = result.hunger <= 0 ? "§cObesity" : result.hunger <= 6 ? "§6Obesity" : "§eObesity";
  entity.nameTag = tag;
  if (result.hunger > 0) {
    try {
      entity.removeEffect("slowness");
    } catch (error) {
      // Effect was already gone.
    }
  }
  playAt(entity.dimension, "random.eat", entity.location, 0.85 + Math.random() * 0.3, 1);
  if (result.status === "stuffed_now") playAt(entity.dimension, "random.burp", entity.location, 0.7, 1);
  burst(entity.dimension, "minecraft:heart_particle", entity.location, 4, 0.6, 1);
  player.onScreenDisplay.setActionBar(feedMessage(result, food.stack.typeId));
  player.sendMessage(feedMessage(result, food.stack.typeId));
  const display = hungerDisplay(result.hunger, 1);
  showHud(player, display.title, display.subtitle);
}

function eachPet(fn) {
  for (const name of DIMS) {
    let dim;
    try {
      dim = world.getDimension(name);
    } catch (error) {
      continue;
    }
    let list;
    try {
      list = dim.getEntities({ type: PET_ID });
    } catch (error) {
      continue;
    }
    for (const entity of list) fn(entity);
  }
}

function tickPet(entity, drain) {
  let hunger = clampHunger(entity.getProperty("obesity:hunger"));
  const before = hunger;
  if (drain && hunger > 0) {
    hunger = drainHunger(hunger);
    entity.setProperty("obesity:hunger", hunger);
  }
  const tag = hunger <= 0 ? "§cObesity" : hunger <= 6 ? "§6Obesity" : "§eObesity";
  if (entity.nameTag !== tag) entity.nameTag = tag;
  if (hunger <= 0) {
    try {
      entity.addEffect("slowness", 50, { amplifier: 3, showParticles: false });
    } catch (error) {
      // Effect API can reject an unknown id on older engines.
    }
  } else {
    try {
      entity.removeEffect("slowness");
    } catch (error) {
      // Nothing to clear.
    }
  }
  const ownerId = entity.getDynamicProperty("obesity:owner_id");
  const ownerName = entity.getDynamicProperty("obesity:owner_name");
  const owner = findPlayer(ownerId, ownerName);
  if (owner) {
    const sameDim = entity.dimension.id === owner.dimension.id;
    if (!sameDim || shouldTeleport(entity.location, owner.location)) {
      try {
        entity.teleport(
          { x: owner.location.x + 1, y: owner.location.y, z: owner.location.z },
          { dimension: owner.dimension, facingLocation: owner.location }
        );
      } catch (error) {
        // Cross-dimension teleports are not available on every engine.
      }
    }
    if (drain) {
      for (const warning of crossedWarning(before, hunger)) {
        if (warning === "starving") owner.sendMessage("§cObesity is starving and can barely move. Feed them any food.");
        if (warning === "hungry") owner.sendMessage("§6Obesity's tummy is rumbling.");
      }
    }
  }
  return { hunger, ownerId, ownerName };
}

let pulse = 0;
let started = false;

function startLoop() {
  if (started) return;
  started = true;
  system.runInterval(() => {
    try {
      pulse += 1;
      const drain = pulse % DRAIN_INTERVAL_SECONDS === 0;
      const records = [];
      eachPet((entity) => {
        try {
          const record = tickPet(entity, drain);
          if (record) records.push(record);
          if (record && record.hunger <= 6 && record.hunger > 0 && pulse % 5 === 0 && Math.random() < 0.35) {
            playAt(entity.dimension, "random.burp", entity.location, 0.55, 0.3);
          }
        } catch (error) {
          warn(`pet tick failed: ${error}`);
        }
      });
      for (const player of world.getPlayers()) {
        try {
          if (player.getGameMode() === GameMode.spectator) continue;
          const mine = records.filter((record) => sameOwner({ id: record.ownerId, name: record.ownerName }, player));
          if (mine.length === 0) {
            clearHud(player);
            continue;
          }
          let lowest = mine[0].hunger;
          for (const record of mine) if (record.hunger < lowest) lowest = record.hunger;
          const display = hungerDisplay(lowest, mine.length);
          showHud(player, display.title, display.subtitle);
        } catch (error) {
          warn(`hud tick failed: ${error}`);
        }
      }
    } catch (error) {
      warn(`loop failed: ${error}`);
    }
  }, 20);
}

function registerConnect(init) {
  const registry = init.customCommandRegistry;
  if (!registry) return;
  registry.registerEnum("obesity:connect_to", ["to"]);
  const words = ["w1", "w2", "w3", "w4", "w5", "w6"].map((name) => ({
    name,
    type: CustomCommandParamType.String,
  }));
  registry.registerCommand(
    {
      name: "obesity:connect",
      description: "Save a Cohere key or local AI address for Obesity",
      permissionLevel: CommandPermissionLevel.Any,
      cheatsRequired: false,
      mandatoryParameters: [{ name: "obesity:connect_to", type: CustomCommandParamType.Enum }],
      optionalParameters: words,
    },
    (origin, ...params) => {
      const raw = connectPhrase(params);
      const result = parseConnectCommand(raw);
      const lines = connectFeedback(result);
      const token = playerToken(origin && (origin.sourceEntity || origin.initiator));
      system.runTimeout(() => {
        const player = playerFromToken(token);
        if (!player) return;
        try {
          applyConnect(player, raw);
        } catch (error) {
          warn(`connect failed: ${error}`);
        }
      }, 5);
      return {
        status: CustomCommandStatus.Success,
        message: lines.join(" ") || "Obesity is listening.",
      };
    }
  );
}

system.beforeEvents.startup.subscribe((init) => {
  try {
    init.blockComponentRegistry.registerCustomComponent("obesity:open_box", {
      onPlayerInteract(event) {
        const player = event.player;
        const block = event.block;
        if (!player || !block) return;
        scheduleOpen(player, dimensionIdOf(event.dimension || block), block.location);
      },
    });
  } catch (error) {
    warn(`could not register the box: ${error}`);
  }
  try {
    registerConnect(init);
  } catch (error) {
    warn(`could not register /obesity:connect: ${error}`);
  }
});

function rememberPlacement(block) {
  const key = boxKey(block.dimension, block.location);
  placedTick.set(key, system.currentTick);
  system.runTimeout(() => placedTick.delete(key), 5);
}

listen(() => {
  world.beforeEvents.playerInteractWithBlock.subscribe((event) => {
    const block = event.block;
    const player = event.player;
    if (!player || !block || block.typeId !== BOX_ID) return;
    let held;
    try {
      held = event.itemStack && event.itemStack.typeId;
    } catch (error) {
      held = undefined;
    }
    // Cancel only when the player would place an item. An empty-hand tap stays a real interaction.
    if (shouldCancelBoxUse(held)) event.cancel = true;
    scheduleOpen(player, dimensionIdOf(block), block.location);
  });
});

listen(() => {
  world.beforeEvents.playerBreakBlock.subscribe((event) => {
    const block = event.block;
    const player = event.player;
    if (!player || !block || block.typeId !== BOX_ID) return;
    if (sneaking(player)) return;
    event.cancel = true;
    scheduleOpen(player, dimensionIdOf(block), block.location);
  });
});

listen(() => {
  world.afterEvents.entityHitBlock.subscribe((event) => {
    const player = event.damagingEntity;
    const block = event.hitBlock;
    if (!player || player.typeId !== "minecraft:player" || !block || block.typeId !== BOX_ID) return;
    if (sneaking(player)) return;
    scheduleOpen(player, dimensionIdOf(block), block.location);
  });
});

world.afterEvents.playerInteractWithBlock.subscribe((event) => {
  const block = event.block;
  const player = event.player;
  if (!player || !block || block.typeId !== BOX_ID) return;
  scheduleOpen(player, dimensionIdOf(block), block.location);
});

world.afterEvents.playerPlaceBlock.subscribe((event) => {
  if (!event.block || event.block.typeId !== BOX_ID || !event.player) return;
  const player = event.player;
  rememberPlacement(event.block);
  system.run(() => {
    try {
      moveOffBox(player);
      player.onScreenDisplay.setActionBar("§eTap the box to let Obesity out");
      player.sendMessage("§eTap the box to let Obesity out.");
    } catch (error) {
      // Hint only.
    }
  });
});

world.afterEvents.playerInteractWithEntity.subscribe((event) => {
  const target = event.target;
  const player = event.player;
  if (!player || !target || target.typeId !== PET_ID) return;
  system.run(() => {
    try {
      feedOrHint(target, player);
    } catch (error) {
      warn(`feed failed: ${error}`);
    }
  });
});

world.beforeEvents.chatSend.subscribe((event) => {
  const player = event.sender;
  const message = event.message;
  if (!player || typeof message !== "string") return;
  const parsed = parseConnectCommand(message);
  if (parsed.kind !== "ignore") {
    event.cancel = true;
    const token = playerToken(player);
    system.runTimeout(() => {
      const current = playerFromToken(token, player);
      if (!current) return;
      try {
        applyConnect(current, message);
      } catch (error) {
        warn(`connect failed: ${error}`);
      }
    }, 5);
    return;
  }
  const text = message.trim();
  if (!text || text.startsWith("/")) return;
  const token = playerToken(player);
  system.runTimeout(() => {
    const current = playerFromToken(token, player);
    if (!current) return;
    try {
      hearChat(current, text);
    } catch (error) {
      warn(`talk failed: ${error}`);
    }
  }, 5);
});

world.afterEvents.playerSpawn.subscribe((event) => {
  if (!event.initialSpawn) return;
  const player = event.player;
  system.run(() => welcome(player));
});

world.afterEvents.entitySpawn.subscribe((event) => {
  const entity = event.entity;
  if (!entity || entity.typeId !== PET_ID) return;
  system.run(() => {
    try {
      if (!entity.nameTag) entity.nameTag = "§eObesity";
      const hunger = entity.getProperty("obesity:hunger");
      if (typeof hunger !== "number") entity.setProperty("obesity:hunger", START_HUNGER);
    } catch (error) {
      // Summon can race the property sync. The next tick fills it in.
    }
  });
});

world.afterEvents.entityDie.subscribe((event) => {
  const dead = event.deadEntity;
  if (!dead || dead.typeId !== PET_ID) return;
  let ownerName;
  let loc;
  let dim;
  try {
    ownerName = dead.getDynamicProperty("obesity:owner_name");
    loc = { x: dead.location.x, y: dead.location.y, z: dead.location.z };
    dim = dead.dimension;
  } catch (error) {
    return;
  }
  if (dim && loc) playAt(dim, "random.burp", loc, 0.45, 1);
  if (!ownerName) return;
  for (const player of world.getPlayers()) {
    if (player.name !== ownerName) continue;
    try {
      const placed = placeBoxNear(player);
      if (!placed) giveOrDrop(player, BOX_ID, 1);
      player.sendMessage(placed ? "§cObesity collapsed. A new box dropped in front of you." : "§cObesity collapsed. A new box is in your inventory.");
      player.onScreenDisplay.setTitle("§cObesity collapsed", {
        subtitle: placed ? "§7A new box dropped" : "§7A new box is in your inventory",
        fadeInDuration: 0,
        stayDuration: 70,
        fadeOutDuration: 10,
      });
      hudState.set(player.id, {
        title: "collapsed",
        subtitle: "",
        at: system.currentTick,
        holdUntil: system.currentTick + 80,
      });
    } catch (error) {
      warn(`death message failed: ${error}`);
    }
  }
});

world.afterEvents.worldLoad.subscribe(() => startLoop());
system.runTimeout(() => startLoop(), 40);
