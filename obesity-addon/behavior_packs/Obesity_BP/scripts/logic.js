// Pure hunger rules for Obesity. No Minecraft imports, so this can be tested on its own.

export const HUNGER_MAX = 20;
export const START_HUNGER = 14;
export const DRAIN_INTERVAL_SECONDS = 12;
export const TELEPORT_DISTANCE = 24;

// Vanilla nutrition, used when the food component is missing.
// Golden foods are overridden in foodPoints.
export const FOOD = {
  "minecraft:apple": 4,
  "minecraft:baked_potato": 5,
  "minecraft:beef": 3,
  "minecraft:beetroot": 1,
  "minecraft:beetroot_soup": 6,
  "minecraft:bread": 5,
  "minecraft:carrot": 3,
  "minecraft:chicken": 2,
  "minecraft:chorus_fruit": 4,
  "minecraft:cod": 2,
  "minecraft:cooked_beef": 8,
  "minecraft:cooked_chicken": 6,
  "minecraft:cooked_cod": 5,
  "minecraft:cooked_mutton": 6,
  "minecraft:cooked_porkchop": 8,
  "minecraft:cooked_rabbit": 5,
  "minecraft:cooked_salmon": 6,
  "minecraft:cookie": 2,
  "minecraft:dried_kelp": 1,
  "minecraft:enchanted_golden_apple": 4,
  "minecraft:glow_berries": 2,
  "minecraft:golden_apple": 4,
  "minecraft:golden_carrot": 6,
  "minecraft:honey_bottle": 6,
  "minecraft:melon_slice": 2,
  "minecraft:mushroom_stew": 6,
  "minecraft:mutton": 2,
  "minecraft:poisonous_potato": 2,
  "minecraft:porkchop": 3,
  "minecraft:potato": 1,
  "minecraft:pufferfish": 1,
  "minecraft:pumpkin_pie": 8,
  "minecraft:rabbit": 3,
  "minecraft:rabbit_stew": 10,
  "minecraft:rotten_flesh": 4,
  "minecraft:salmon": 2,
  "minecraft:spider_eye": 2,
  "minecraft:suspicious_stew": 6,
  "minecraft:sweet_berries": 2,
  "minecraft:tropical_fish": 1,
};

const NAMES = {
  "minecraft:apple": "apple",
  "minecraft:baked_potato": "baked potato",
  "minecraft:beef": "raw beef",
  "minecraft:beetroot": "beetroot",
  "minecraft:beetroot_soup": "beetroot soup",
  "minecraft:bread": "bread",
  "minecraft:carrot": "carrot",
  "minecraft:chicken": "raw chicken",
  "minecraft:chorus_fruit": "chorus fruit",
  "minecraft:cod": "raw cod",
  "minecraft:cooked_beef": "steak",
  "minecraft:cooked_chicken": "cooked chicken",
  "minecraft:cooked_cod": "cooked cod",
  "minecraft:cooked_mutton": "cooked mutton",
  "minecraft:cooked_porkchop": "cooked porkchop",
  "minecraft:cooked_rabbit": "cooked rabbit",
  "minecraft:cooked_salmon": "cooked salmon",
  "minecraft:cookie": "cookie",
  "minecraft:dried_kelp": "dried kelp",
  "minecraft:enchanted_golden_apple": "enchanted golden apple",
  "minecraft:glow_berries": "glow berries",
  "minecraft:golden_apple": "golden apple",
  "minecraft:golden_carrot": "golden carrot",
  "minecraft:honey_bottle": "honey bottle",
  "minecraft:melon_slice": "melon slice",
  "minecraft:mushroom_stew": "mushroom stew",
  "minecraft:mutton": "raw mutton",
  "minecraft:poisonous_potato": "poisonous potato",
  "minecraft:porkchop": "raw porkchop",
  "minecraft:potato": "potato",
  "minecraft:pufferfish": "pufferfish",
  "minecraft:pumpkin_pie": "pumpkin pie",
  "minecraft:rabbit": "raw rabbit",
  "minecraft:rabbit_stew": "rabbit stew",
  "minecraft:rotten_flesh": "rotten flesh",
  "minecraft:salmon": "raw salmon",
  "minecraft:spider_eye": "spider eye",
  "minecraft:suspicious_stew": "suspicious stew",
  "minecraft:sweet_berries": "sweet berries",
  "minecraft:tropical_fish": "tropical fish",
};

const STEWS = new Set([
  "minecraft:mushroom_stew",
  "minecraft:beetroot_soup",
  "minecraft:rabbit_stew",
  "minecraft:suspicious_stew",
]);

export function clampHunger(value) {
  if (typeof value !== "number" || Number.isNaN(value)) return START_HUNGER;
  return Math.max(0, Math.min(HUNGER_MAX, Math.round(value)));
}

export function foodPoints(typeId, componentNutrition) {
  if (typeId === "minecraft:enchanted_golden_apple") return HUNGER_MAX;
  if (typeId === "minecraft:golden_apple") return 10;
  if (typeof componentNutrition === "number" && componentNutrition > 0) return componentNutrition;
  return FOOD[typeId] || 0;
}

export function applyFeed(current, points) {
  const now = clampHunger(current);
  if (!points || points <= 0) return { hunger: now, status: "not_food", gained: 0 };
  if (now >= HUNGER_MAX) return { hunger: now, status: "stuffed", gained: 0 };
  const hunger = clampHunger(now + points);
  const gained = hunger - now;
  return { hunger, gained, status: hunger >= HUNGER_MAX ? "stuffed_now" : "ate" };
}

export function leftoverFor(typeId) {
  if (typeId === "minecraft:honey_bottle") return "minecraft:glass_bottle";
  if (STEWS.has(typeId)) return "minecraft:bowl";
  return undefined;
}

export function itemLabel(typeId) {
  if (NAMES[typeId]) return NAMES[typeId];
  const raw = String(typeId || "food").split(":").pop();
  return raw.replace(/_/g, " ");
}

export function barGlyphs(hunger) {
  const h = clampHunger(hunger);
  let full = 0;
  let half = 0;
  let empty = 0;
  for (let i = 0; i < 10; i++) {
    const fill = h - i * 2;
    if (fill >= 2) full += 1;
    else if (fill === 1) half += 1;
    else empty += 1;
  }
  return { full, half, empty };
}

export function stripCodes(text) {
  return String(text).replace(/§./g, "");
}

export function hungerDisplay(hunger, count) {
  const h = clampHunger(hunger);
  const glyphs = barGlyphs(h);
  const color = h <= 4 ? "§c" : h <= 8 ? "§6" : "§e";
  const halfColor = h <= 4 ? "§c" : "§6";
  const bar =
    color +
    "#".repeat(glyphs.full) +
    (glyphs.half ? halfColor + "+" : "") +
    "§8" +
    "-".repeat(glyphs.empty);
  const extra = count > 1 ? ` §7x${count}` : "";
  const name = h <= 0 ? "§4Obesity" : h <= 6 ? "§6Obesity" : "§eObesity";
  const title = `${name} §f[${bar}§f] §f${h}/${HUNGER_MAX}${extra}`;
  let subtitle = "§7Hold food and tap Obesity";
  if (h <= 0) subtitle = "§cObesity is starving. Hold food and tap them";
  else if (h >= HUNGER_MAX) subtitle = "§aObesity is stuffed";
  else if (h <= 6) subtitle = "§6That tummy is rumbling. Hold food and tap Obesity";
  return { title, subtitle, hunger: h };
}

export function feedMessage(result, typeId) {
  const label = itemLabel(typeId);
  if (result.status === "not_food") return "§eHold food and tap Obesity.";
  if (result.status === "stuffed") return "§eObesity is stuffed and pushes the food away.";
  if (result.status === "stuffed_now") return `§eObesity finishes the ${label} and lets out a huge burp.`;
  if (typeId === "minecraft:rotten_flesh" || typeId === "minecraft:spider_eye" || typeId === "minecraft:poisonous_potato") {
    return `§eObesity eats the ${label} anyway. §a+${result.gained} §7(${result.hunger}/${HUNGER_MAX})`;
  }
  return `§eObesity eats the ${label}. §a+${result.gained} §7(${result.hunger}/${HUNGER_MAX})`;
}

export function crossedWarning(before, after) {
  const warnings = [];
  if (before > 6 && after <= 6) warnings.push("hungry");
  if (before > 0 && after <= 0) warnings.push("starving");
  return warnings;
}

export function drainHunger(current) {
  return clampHunger(clampHunger(current) - 1);
}

export function distanceSquared(a, b) {
  const dx = a.x - b.x;
  const dy = a.y - b.y;
  const dz = a.z - b.z;
  return dx * dx + dy * dy + dz * dz;
}

export function shouldTeleport(from, to) {
  return distanceSquared(from, to) > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
}

export function sameOwner(record, player) {
  if (!record || !player) return false;
  if (record.id && player.id && record.id === player.id) return true;
  if (record.name && player.name && record.name === player.name) return true;
  return false;
}

// The placement tap can also hit the new box. Ignore that tap, then open on the next one.
export function isFreshPlacement(placedTick, nowTick) {
  if (placedTick == null || nowTick == null) return false;
  return nowTick - placedTick <= 1;
}

export function hungriest(records) {
  let best;
  for (const record of records) {
    if (!best || record.hunger < best.hunger) best = record;
  }
  return best;
}
