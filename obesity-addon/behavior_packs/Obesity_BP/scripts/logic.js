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

export const COHERE_PLACEHOLDER = "YOUR_COHERE_API_KEY";
export const LOCAL_PLACEHOLDER = "LOCAL_AI_SERVER_PLACEHOLDER_HERE";
export const COHERE_MODEL = "command-r7b-12-2024";
export const OLLAMA_FALLBACK_MODEL = "llama3.2";

const LOCAL_PLACEHOLDER_COMPACT = "LOCALAISERVERPLACEHOLDERHERE";

export function aiGuideLines() {
  return [
    "Obesity can talk once you connect an AI. Pick one.",
    "Local AI with Ollama: on a computer, install Ollama from ollama.com. In a terminal run: ollama pull llama3.2. Leave Ollama open, and allow its port on your Wi-Fi.",
    "/connect to local ai server LOCAL_AI_SERVER_PLACEHOLDER_HERE",
    "Replace LOCAL_AI_SERVER_PLACEHOLDER_HERE with that computer's address, including http and the port. The placeholder is not a server.",
    "Or go to cohere.com, create an API key, and run this:",
    "/connect to YOUR_COHERE_API_KEY",
    "Replace YOUR_COHERE_API_KEY with your own key. The pack does not include a key, and the placeholder will not connect.",
    "Stand near Obesity and talk in chat. If a slash is an unknown command, type the same words without the slash. Your key is not shown to other players.",
    "A phone or single-player world cannot open Cohere or Ollama. Minecraft blocks that call. A Bedrock dedicated server with network scripts allowed can use what you saved.",
  ];
}

export function isLocalPlaceholder(value) {
  if (typeof value !== "string") return false;
  return value.replace(/[\s_]/g, "").toUpperCase() === LOCAL_PLACEHOLDER_COMPACT;
}

export function normalizeServer(input) {
  if (typeof input !== "string") return undefined;
  const text = input.trim();
  if (!/^https?:\/\//i.test(text)) return undefined;
  let parsed;
  try {
    parsed = new URL(text);
  } catch (error) {
    return undefined;
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") return undefined;
  if (!parsed.hostname) return undefined;
  return text.replace(/\/+$/, "");
}

export function parseConnectCommand(raw) {
  if (typeof raw !== "string") return { kind: "ignore" };
  let text = raw.trim();
  if (text.startsWith("/")) text = text.slice(1).trim();
  const lower = text.toLowerCase();
  const prefix = "connect to ";
  if (!lower.startsWith(prefix)) return { kind: "ignore" };
  const rest = text.slice(prefix.length).trim();
  if (!rest) return { kind: "error", reason: "empty" };
  const local = rest.match(/^local\s+ai\s+server\s+(.+)$/i);
  if (local) {
    const server = local[1].trim();
    if (!server || isLocalPlaceholder(server)) return { kind: "error", reason: "local-placeholder" };
    const normalized = normalizeServer(server);
    if (!normalized) return { kind: "error", reason: "bad-server" };
    return { kind: "local", server: normalized };
  }
  if (rest.toUpperCase() === COHERE_PLACEHOLDER || rest.toLowerCase() === "your_cohere_api_key") {
    return { kind: "error", reason: "cohere-placeholder" };
  }
  if (/\s/.test(rest)) return { kind: "error", reason: "bad-key" };
  return { kind: "cohere", key: rest };
}

export function connectFeedback(result) {
  if (!result || result.kind === "ignore") return [];
  if (result.reason === "cohere-placeholder") {
    return ["YOUR_COHERE_API_KEY is only a placeholder. Create a key at cohere.com, then put that key after /connect to"];
  }
  if (result.reason === "local-placeholder") {
    return ["LOCAL_AI_SERVER_PLACEHOLDER_HERE is only a placeholder. Put your Ollama address there instead."];
  }
  if (result.reason === "bad-server") {
    return ["The local address needs to start with http:// or https://. Replace the placeholder with that address."];
  }
  if (result.reason === "bad-key" || result.reason === "empty") {
    return [
      "For Ollama: /connect to local ai server LOCAL_AI_SERVER_PLACEHOLDER_HERE",
      "For Cohere: /connect to YOUR_COHERE_API_KEY",
      "Replace the placeholder. Do not leave it in the command.",
    ];
  }
  if (result.kind === "cohere") {
    return ["Saved. Obesity will use Cohere. Your key was not repeated here."];
  }
  if (result.kind === "local") {
    return ["Saved. Obesity will use your local AI server. The address was not repeated here."];
  }
  return [];
}

export function systemPrompt(hunger) {
  const base =
    "You are Obesity, a round yellow creature in Minecraft with a huge belly and a big smile. You love food. Answer in two short sentences.";
  if (typeof hunger !== "number") return base;
  return `${base} Your hunger is ${hunger} out of 20.`;
}

function cohereHeaders(key) {
  return [
    ["Content-Type", "application/json"],
    ["Authorization", `Bearer ${key}`],
    ["Accept", "application/json"],
  ];
}

export function cohereRequest(key, turns, hunger) {
  return {
    url: "https://api.cohere.com/v2/chat",
    headers: cohereHeaders(key),
    body: {
      model: COHERE_MODEL,
      stream: false,
      max_tokens: 80,
      messages: [{ role: "system", content: systemPrompt(hunger) }, ...turns],
    },
  };
}

export function cohereV1Request(key, turns, hunger) {
  const chatHistory = [];
  let message = "";
  for (const turn of turns || []) {
    if (!turn || typeof turn.content !== "string") continue;
    if (turn.role === "assistant") {
      if (message) {
        chatHistory.push({ role: "USER", message });
        message = "";
      }
      chatHistory.push({ role: "CHATBOT", message: turn.content });
      continue;
    }
    if (message) chatHistory.push({ role: "USER", message });
    message = turn.content;
  }
  return {
    url: "https://api.cohere.com/v1/chat",
    headers: cohereHeaders(key),
    body: {
      model: COHERE_MODEL,
      message,
      chat_history: chatHistory,
      preamble: systemPrompt(hunger),
      stream: false,
      max_tokens: 80,
    },
  };
}

export function failureKind(status, errorText) {
  if (status === 401 || status === 403) return "denied";
  const text = String(errorText || "");
  const dropped = /connection closed|econnreset|socket hang up/i.test(text);
  if (!status && dropped) return "blocked";
  if (status === 0 || dropped || /timed out|timeout/i.test(text)) return "closed";
  if (status) return "http";
  return "failed";
}

export function ollamaTagsUrl(server) {
  const base = String(server).replace(/\/+$/, "");
  if (base.endsWith("/api/chat")) return base.replace(/\/api\/chat$/, "/api/tags");
  return `${base}/api/tags`;
}

export function ollamaChatUrl(server) {
  const base = String(server).replace(/\/+$/, "");
  if (base.endsWith("/api/chat")) return base;
  return `${base}/api/chat`;
}

export function ollamaChatRequest(server, model, turns, hunger) {
  return {
    url: ollamaChatUrl(server),
    headers: [["Content-Type", "application/json"]],
    body: {
      model: model || OLLAMA_FALLBACK_MODEL,
      stream: false,
      messages: [{ role: "system", content: systemPrompt(hunger) }, ...turns],
    },
  };
}

export function readCohereReply(body) {
  const data = typeof body === "string" ? JSON.parse(body) : body;
  const content = data && data.message ? data.message.content : undefined;
  if (typeof content === "string") return content.trim();
  if (Array.isArray(content)) {
    return content
      .map((part) => (part && (part.text || (typeof part.content === "string" ? part.content : ""))) || "")
      .filter(Boolean)
      .join(" ")
      .trim();
  }
  if (data && typeof data.text === "string") return data.text.trim();
  return "";
}

export function readOllamaReply(body) {
  const data = typeof body === "string" ? JSON.parse(body) : body;
  const content = data && data.message ? data.message.content : undefined;
  return typeof content === "string" ? content.trim() : "";
}

export function readOllamaModel(body) {
  const data = typeof body === "string" ? JSON.parse(body) : body;
  const models = data && Array.isArray(data.models) ? data.models : [];
  const names = models.map((model) => model && (model.name || model.model)).filter(Boolean);
  if (names.length === 0) return undefined;
  return names.find((name) => String(name).startsWith("llama3.2")) || names[0];
}

export function clipReply(text) {
  const clean = String(text || "")
    .replace(/§./g, "")
    .replace(/[\r\n]+/g, " ")
    .trim();
  if (!clean) return "";
  return clean.length > 180 ? `${clean.slice(0, 177)}...` : clean;
}

export function boxSpots(x, y, z, yaw) {
  const rad = (Number(yaw) * Math.PI) / 180;
  const fx = -Math.sin(rad);
  const fz = Math.cos(rad);
  const baseY = Math.floor(y);
  const spots = [];
  for (const dist of [2, 3, 4]) {
    const bx = Math.floor(x + fx * dist);
    const bz = Math.floor(z + fz * dist);
    for (let dy = 2; dy >= -4; dy--) spots.push({ x: bx, y: baseY + dy, z: bz });
  }
  return spots;
}

export function hungriest(records) {
  let best;
  for (const record of records) {
    if (!best || record.hunger < best.hunger) best = record;
  }
  return best;
}
