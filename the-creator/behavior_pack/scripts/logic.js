// Pure rules for The Creator. No Minecraft imports, so node can test them.

export const SPAN = 7;
export const TYPE_ID = "creator:the_creator";
export const OFFERING_ID = "creator:offering";
export const GIFT_PREFIX = "§8Gift:";

export const DRIFT = 0.12;
export const NIGHT_DRIFT = 0.08;

export const DELTA = {
  attack: 12,
  discard: 8,
  sleep: 6,
  offering: -12,
  stare: -5,
  tribute: -8,
};

export const PHASES = [
  { name: "The Witness", color: "§7", line: "It is still trying to help." },
  { name: "The Debt", color: "§6", line: "The gifts have a cost." },
  { name: "The Grudge", color: "§c", line: "It stopped pretending." },
  { name: "The Reckoning", color: "§4", line: "There is no eighth day." },
];

export const WHISPERS = [
  [
    "You looked tired. I left something.",
    "The night is louder if you listen.",
    "I kept a light where your hands would go.",
    "You do not have to thank me. You will wish you had.",
  ],
  [
    "You took it. You did not look up.",
    "A gift is a debt with cleaner hands.",
    "Count the torches. Then count them again.",
    "I am still where you decided not to look.",
  ],
  [
    "Ungrateful.",
    "I learned your route. It is a small life.",
    "Stop turning around.",
    "The storm is mine. So is the quiet after it.",
  ],
  [
    "Day seven.",
    "I made the margin of this world. You made me wait in it.",
    "Run. I already measured the distance.",
    "There is no eighth day.",
  ],
];

const EVENT_TABLE = [
  ["whisper", "cave", "flicker"],
  ["whisper", "thunder", "torches", "flicker", "cave"],
  ["thunder", "lightning", "torches", "darkness", "knock", "thunder"],
  ["thunder", "lightning", "darkness", "rush", "knock", "lightning"],
];

const EVENT_CHANCE = [0.06, 0.18, 0.34, 0.52];

export function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

export function applyMood(current, delta) {
  return clamp(current + delta, 0, 100);
}

export function countdown(startDay, worldDay, span = SPAN) {
  const elapsed = Math.max(0, worldDay - startDay);
  return {
    elapsed,
    daysLeft: Math.max(0, span - elapsed),
    dayNumber: Math.min(span, elapsed + 1),
    ended: elapsed >= span,
  };
}

export function calendarPhase(elapsed) {
  const day = Math.max(0, elapsed);
  if (day >= 6) return 3;
  if (day >= 4) return 2;
  if (day >= 2) return 1;
  return 0;
}

export function moodPhase(mood) {
  if (mood >= 75) return 3;
  if (mood >= 50) return 2;
  if (mood >= 25) return 1;
  return 0;
}

export function resolvePhase(elapsed, mood) {
  return Math.max(calendarPhase(elapsed), moodPhase(mood));
}

export function isNight(timeOfDay) {
  return timeOfDay >= 12500 && timeOfDay <= 23500;
}

export function stalkDistance(phase) {
  return [34, 22, 13, 6][clamp(phase, 0, 3)];
}

export function fleeDistance(phase) {
  return [14, 9, 0, 0][clamp(phase, 0, 3)];
}

export function blinkGapTicks(phase) {
  return [90, 60, 28, 12][clamp(phase, 0, 3)];
}

export function hurtRange(phase) {
  return [0, 0, 4.5, 9][clamp(phase, 0, 3)];
}

export function hurtAmount(phase) {
  return [0, 0, 4, 6][clamp(phase, 0, 3)];
}

export function hurtCooldownTicks(phase) {
  return [100000, 100000, 160, 100][clamp(phase, 0, 3)];
}

export function eventChance(phase, night) {
  const base = EVENT_CHANCE[clamp(phase, 0, 3)];
  return night ? Math.min(0.9, base + 0.14) : base;
}

export function pickEvent(phase, roll) {
  const table = EVENT_TABLE[clamp(phase, 0, 3)];
  const index = clamp(Math.floor(roll * table.length), 0, table.length - 1);
  return table[index];
}

export function pickWhisper(phase, roll) {
  const lines = WHISPERS[clamp(phase, 0, 3)];
  const index = clamp(Math.floor(roll * lines.length), 0, lines.length - 1);
  return lines[index];
}

export function distance(a, b) {
  return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
}

export function horizontal(view) {
  const len = Math.hypot(view.x, view.z) || 1;
  return { x: view.x / len, z: view.z / len };
}

// Positive side steps to the player's right. Distance is how far behind them.
export function offsetBehind(origin, view, distance, side) {
  const forward = horizontal(view);
  const rightX = -forward.z;
  const rightZ = forward.x;
  return {
    x: origin.x - forward.x * distance + rightX * side,
    z: origin.z - forward.z * distance + rightZ * side,
  };
}

export function offsetAhead(origin, view, distance) {
  const forward = horizontal(view);
  return {
    x: origin.x + forward.x * distance,
    z: origin.z + forward.z * distance,
  };
}

export function lookDot(view, from, to) {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const dz = to.z - from.z;
  const len = Math.hypot(dx, dy, dz) || 1;
  const vlen = Math.hypot(view.x, view.y, view.z) || 1;
  return (view.x * dx + view.y * dy + view.z * dz) / (len * vlen);
}

export function isLookingAt(view, from, to, minDot = 0.82) {
  return lookDot(view, from, to) >= minDot;
}

export function isGiftName(name) {
  return typeof name === "string" && name.startsWith(GIFT_PREFIX);
}

export function actionBarText(clock, phase, mood) {
  const info = PHASES[clamp(phase, 0, 3)];
  const pct = clamp(Math.round(mood), 0, 100);
  if (clock.ended) {
    return `§4Day 7 is over  §8|  ${info.color}${info.name}  §8|  §c${pct}% ungrateful`;
  }
  return `§f${clock.daysLeft} §7days left  §8|  §fDay ${clock.dayNumber}§7/${SPAN}  §8|  ${info.color}${info.name}  §8|  §c${pct}%`;
}

export function giftFor(phase, roll) {
  if (phase <= 0) {
    if (roll < 0.45) return { id: "minecraft:bread", amount: 3, label: "bread" };
    if (roll < 0.8) return { id: "minecraft:torch", amount: 8, label: "torches" };
    return { id: "minecraft:cooked_beef", amount: 2, label: "food" };
  }
  if (roll < 0.6) return { id: "minecraft:rotten_flesh", amount: 2, label: "a reminder" };
  return { id: "minecraft:spider_eye", amount: 1, label: "look at this" };
}
