// Phone-friendly knobs. Speeds are blocks per tick, which is what getVelocity() returns.
// A normal jump lands around 0.4 to 0.5. A fall of a few blocks lands past 0.85.

export const MOTION_TICKS = 2;
export const AMBIENT_TICKS_FANCY = 40;
export const AMBIENT_TICKS_SIMPLE = 90;

const SOFT_LAND = 0.62;
const HARD_LAND = 0.85;

export function landingAction(impact) {
  if (impact < SOFT_LAND) {
    return { puff: false, hard: false, shake: null };
  }
  const hard = impact >= HARD_LAND;
  let shake = null;
  if (hard) {
    const intensity = Math.min(0.42, (impact - 0.7) * 0.26);
    const duration = Math.min(0.32, 0.1 + impact * 0.05);
    if (intensity > 0.04) {
      shake = {
        intensity: round(intensity),
        duration: round(duration),
      };
    }
  }
  return { puff: true, hard, shake };
}

export function hurtShake(damage) {
  const amount = Math.max(0, damage);
  return {
    intensity: round(Math.min(0.48, 0.08 + amount * 0.06)),
    duration: round(Math.min(0.28, 0.1 + amount * 0.02)),
  };
}

export function stepSound(blockId) {
  const id = String(blockId || "");
  if (id.includes("sand") || id.includes("gravel")) return "step.sand";
  if (id.includes("snow")) return "step.snow";
  if (id.includes("wood") || id.includes("log") || id.includes("plank") || id.includes("stem")) {
    return "step.wood";
  }
  if (
    id.includes("grass") ||
    id.includes("dirt") ||
    id.includes("mud") ||
    id.includes("moss") ||
    id.includes("podzol") ||
    id.includes("mycelium")
  ) {
    return "step.grass";
  }
  return "step.stone";
}

// Returns a particle id, or null when this graphics mode should stay quiet.
export function chooseAmbient(info) {
  const dimensionId = info.dimensionId || "";
  const biomeId = info.biomeId || "";
  const simple = Boolean(info.simple);
  const weather = info.weather;
  const skyLight = info.skyLight;

  if (dimensionId === "minecraft:nether") return "realistic:ash";
  if (dimensionId === "minecraft:the_end") return simple ? null : "realistic:mote";
  if (weather === "Rain" || weather === "Thunder") {
    return simple ? null : "realistic:mist";
  }
  if (typeof skyLight === "number" && skyLight <= 4) {
    return simple ? null : "realistic:dust";
  }
  if (simple) return null;
  if (/desert|beach|badlands/.test(biomeId)) return "realistic:sand";
  if (/forest|taiga|jungle|swamp|grove|cherry|pale_garden|mangrove|meadow/.test(biomeId)) {
    return "realistic:leaf";
  }
  return "realistic:mote";
}

function round(value) {
  return Math.round(value * 100) / 100;
}
