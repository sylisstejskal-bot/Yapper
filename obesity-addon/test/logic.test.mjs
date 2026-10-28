import assert from "node:assert/strict";
import test from "node:test";
import {
  HUNGER_MAX,
  START_HUNGER,
  applyFeed,
  barGlyphs,
  clampHunger,
  crossedWarning,
  drainHunger,
  feedMessage,
  foodPoints,
  COHERE_PLACEHOLDER,
  LOCAL_PLACEHOLDER,
  aiGuideLines,
  localReply,
  boxSpots,
  clipReply,
  cohereRequest,
  cohereV1Request,
  failureKind,
  connectFeedback,
  hungerDisplay,
  hungriest,
  isFreshPlacement,
  ollamaChatRequest,
  ollamaTagsUrl,
  parseConnectCommand,
  readCohereReply,
  readOllamaModel,
  readOllamaReply,
  itemLabel,
  leftoverFor,
  sameOwner,
  shouldTeleport,
  stripCodes,
} from "../behavior_packs/Obesity_BP/scripts/logic.js";

test("hunger clamps into 0..20 and treats junk as the starting value", () => {
  assert.equal(clampHunger(25), 20);
  assert.equal(clampHunger(-4), 0);
  assert.equal(clampHunger(14.4), 14);
  assert.equal(clampHunger(Number.NaN), START_HUNGER);
  assert.equal(clampHunger(undefined), START_HUNGER);
});

test("the bar is always ten marks", () => {
  assert.deepEqual(barGlyphs(20), { full: 10, half: 0, empty: 0 });
  assert.deepEqual(barGlyphs(19), { full: 9, half: 1, empty: 0 });
  assert.deepEqual(barGlyphs(14), { full: 7, half: 0, empty: 3 });
  assert.deepEqual(barGlyphs(1), { full: 0, half: 1, empty: 9 });
  assert.deepEqual(barGlyphs(0), { full: 0, half: 0, empty: 10 });
});

test("the top bar names Obesity and shows the fraction", () => {
  const full = hungerDisplay(14, 1);
  const plain = stripCodes(full.title);
  assert.match(plain, /Obesity/);
  assert.match(plain, /\[#######---\]/);
  assert.match(plain, /14\/20/);
  assert.match(stripCodes(full.subtitle), /Hold food and tap Obesity/);

  const starving = hungerDisplay(0, 2);
  assert.match(stripCodes(starving.title), /Obesity/);
  assert.match(stripCodes(starving.title), /\[----------\]/);
  assert.match(stripCodes(starving.title), /0\/20/);
  assert.match(stripCodes(starving.title), /x2/);
  assert.match(stripCodes(starving.subtitle), /starving/);

  const half = stripCodes(hungerDisplay(19, 1).title);
  assert.match(half, /\[#########\+\]/);
  assert.match(half, /19\/20/);
});

test("food restores hunger and special apples are worth more", () => {
  assert.equal(foodPoints("minecraft:bread", undefined), 5);
  assert.equal(foodPoints("minecraft:bread", 5), 5);
  assert.equal(foodPoints("minecraft:cooked_beef", undefined), 8);
  assert.equal(foodPoints("minecraft:golden_apple", 4), 10);
  assert.equal(foodPoints("minecraft:enchanted_golden_apple", 4), HUNGER_MAX);
  assert.equal(foodPoints("minecraft:stick", undefined), 0);
  assert.equal(foodPoints("minecraft:stick", 0), 0);
});

test("feeding clamps at full and will not eat when already stuffed", () => {
  assert.deepEqual(applyFeed(10, 0), { hunger: 10, status: "not_food", gained: 0 });
  assert.deepEqual(applyFeed(20, 8), { hunger: 20, status: "stuffed", gained: 0 });
  assert.deepEqual(applyFeed(18, 8), { hunger: 20, status: "stuffed_now", gained: 2 });
  assert.deepEqual(applyFeed(10, 4), { hunger: 14, status: "ate", gained: 4 });
  assert.match(feedMessage(applyFeed(10, 5), "minecraft:bread"), /bread/);
  assert.match(feedMessage(applyFeed(20, 5), "minecraft:bread"), /stuffed/);
});

test("stews leave a bowl and honey leaves a bottle", () => {
  assert.equal(leftoverFor("minecraft:mushroom_stew"), "minecraft:bowl");
  assert.equal(leftoverFor("minecraft:rabbit_stew"), "minecraft:bowl");
  assert.equal(leftoverFor("minecraft:honey_bottle"), "minecraft:glass_bottle");
  assert.equal(leftoverFor("minecraft:bread"), undefined);
  assert.equal(itemLabel("minecraft:cooked_beef"), "steak");
  assert.equal(itemLabel("minecraft:pumpkin_pie"), "pumpkin pie");
});

test("hunger drains one point and warnings fire on the way down", () => {
  assert.equal(drainHunger(14), 13);
  assert.equal(drainHunger(0), 0);
  assert.deepEqual(crossedWarning(7, 6), ["hungry"]);
  assert.deepEqual(crossedWarning(1, 0), ["starving"]);
  assert.deepEqual(crossedWarning(14, 13), []);
  assert.deepEqual(crossedWarning(6, 5), []);
});

test("the tap that places the box does not open it", () => {
  assert.equal(isFreshPlacement(undefined, 10), false);
  assert.equal(isFreshPlacement(10, 10), true);
  assert.equal(isFreshPlacement(10, 11), true);
  assert.equal(isFreshPlacement(10, 12), false);
});

test("connect rejects the placeholders and keeps a real key private in the reply", () => {
  const coherePlaceholder = parseConnectCommand("/connect to YOUR_COHERE_API_KEY");
  assert.equal(coherePlaceholder.reason, "cohere-placeholder");
  assert.equal(connectFeedback(coherePlaceholder).some((line) => line.includes("cohere.com")), true);

  const localPlaceholder = parseConnectCommand("/connect to local ai server LOCAL_AI_SERVER_PLACEHOLDER_HERE");
  assert.equal(localPlaceholder.reason, "local-placeholder");
  const spaced = parseConnectCommand("/connect to local ai server LOCAL_AI SERVER_PLACEHOLDER_HERE");
  assert.equal(spaced.reason, "local-placeholder");

  const local = parseConnectCommand("connect to local ai server http://ollama.local:11434");
  assert.equal(local.kind, "local");
  assert.equal(local.server, "http://ollama.local:11434");
  assert.equal(connectFeedback(local).join(" ").includes("http://ollama.local"), false);

  const cohere = parseConnectCommand("/connect to test-key");
  assert.deepEqual(cohere, { kind: "cohere", key: "test-key" });
  assert.equal(connectFeedback(cohere).join(" ").includes("test-key"), false);
  const namespaced = parseConnectCommand("/obesity:connect to test-key");
  assert.deepEqual(namespaced, { kind: "cohere", key: "test-key" });
  assert.equal(parseConnectCommand("hello there").kind, "ignore");
});

test("the guide shows the placeholder commands and no real secret", () => {
  const guide = aiGuideLines().join("\n");
  assert.equal(guide.includes("/obesity:connect to YOUR_COHERE_API_KEY"), true);
  assert.equal(guide.includes("/obesity:connect to local ai server LOCAL_AI_SERVER_PLACEHOLDER_HERE"), true);
  assert.equal(guide.includes("Do not type /connect"), true);
  assert.equal(guide.includes(COHERE_PLACEHOLDER), true);
  assert.equal(guide.includes(LOCAL_PLACEHOLDER), true);
  assert.equal(guide.includes("Bearer "), false);
});

test("AI requests use the supplied connection and read the replies", () => {
  const request = cohereRequest("test-key", [{ role: "user", content: "hi" }], 14);
  assert.equal(request.url, "https://api.cohere.com/v2/chat");
  assert.equal(request.body.stream, false);
  assert.equal(request.headers[1][1], "Bearer test-key");
  assert.equal(request.headers.some((header) => header[0] === "Accept"), true);
  assert.equal(JSON.stringify(request).includes(COHERE_PLACEHOLDER), false);
  const older = cohereV1Request("test-key", [
    { role: "user", content: "hi" },
    { role: "assistant", content: "hello" },
    { role: "user", content: "hungry?" },
  ]);
  assert.equal(older.url, "https://api.cohere.com/v1/chat");
  assert.equal(older.body.message, "hungry?");
  assert.equal(older.body.stream, false);
  assert.deepEqual(older.body.chat_history, [
    { role: "USER", message: "hi" },
    { role: "CHATBOT", message: "hello" },
  ]);
  assert.equal(failureKind(0, "Connection closed"), "blocked");
  assert.equal(failureKind(502, "connection closed"), "closed");
  assert.equal(failureKind(401, ""), "denied");
  assert.equal(readCohereReply(JSON.stringify({ message: { content: [{ type: "text", text: "feed me" }] } })), "feed me");

  const ollama = ollamaChatRequest("http://ollama.local:11434", "llama3.2", [{ role: "user", content: "hi" }]);
  assert.equal(ollama.url, "http://ollama.local:11434/api/chat");
  assert.equal(ollamaTagsUrl("http://ollama.local:11434/api/chat"), "http://ollama.local:11434/api/tags");
  assert.equal(readOllamaReply({ message: { content: "hello" } }), "hello");
  assert.equal(readOllamaModel({ models: [{ name: "mistral" }, { name: "llama3.2:latest" }] }), "llama3.2:latest");
  assert.equal(clipReply("a\nb§c"), "a b");
  assert.equal(localReply("hello", 4).includes("Hello"), true);
  assert.equal(localReply("I am hungry", 4).includes("4 out of 20"), true);
  assert.equal(localReply("what is outside?", 14).includes("14 out of 20"), true);
});

test("the box drops in front of the player", () => {
  const spots = boxSpots(0.2, 64.9, 0.2, 0);
  assert.equal(spots[0].x, 0);
  assert.equal(spots[0].z, 2);
  assert.equal(spots[0].y, 66);
  const west = boxSpots(10.1, 70, 10.1, 90);
  assert.equal(west[0].x, 8);
  assert.equal(west[0].z, 10);
});

test("ownership and follow distance", () => {
  const player = { id: "p1", name: "Ada" };
  assert.equal(sameOwner({ id: "p1", name: "other" }, player), true);
  assert.equal(sameOwner({ id: "nope", name: "Ada" }, player), true);
  assert.equal(sameOwner({ id: "nope", name: "Bea" }, player), false);
  assert.equal(shouldTeleport({ x: 0, y: 0, z: 0 }, { x: 10, y: 0, z: 0 }), false);
  assert.equal(shouldTeleport({ x: 0, y: 0, z: 0 }, { x: 30, y: 0, z: 0 }), true);
  assert.equal(hungriest([{ hunger: 12 }, { hunger: 3 }, { hunger: 9 }]).hunger, 3);
});
