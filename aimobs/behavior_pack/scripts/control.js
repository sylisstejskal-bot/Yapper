import { plan } from "./mind.js";

export const BRAIN_LOCAL = "local";
export const BRAIN_VANILLA = "vanilla";
export const PERSONALITY_LIMIT = 180;
export const NEAREST_LIMIT = 6;
export const SCAN_RANGE = 32;

export function possess(personality) {
  const text = String(personality ?? "").trim();
  if (!text) return { ok: false, reason: "Type a personality first." };
  if (text.length > PERSONALITY_LIMIT) {
    return { ok: false, reason: `Personality stops at ${PERSONALITY_LIMIT} characters.` };
  }
  return { ok: true, state: { brain: BRAIN_LOCAL, personality: text } };
}

export function release() {
  return { brain: BRAIN_VANILLA, personality: "" };
}

export function tickBrain(state, senses) {
  if (!state || state.brain !== BRAIN_LOCAL) {
    return { controller: BRAIN_VANILLA, plan: null };
  }
  return { controller: BRAIN_LOCAL, plan: plan(state.personality, senses) };
}

export function nearestMobs(entries, limit = NEAREST_LIMIT) {
  return [...entries].sort((a, b) => a.distance - b.distance || a.name.localeCompare(b.name)).slice(0, limit);
}

export function vanillaBrainShouldRun(possessed) {
  return !possessed;
}

export function holdTransition(wasHolding, holding) {
  return holding && !wasHolding;
}
