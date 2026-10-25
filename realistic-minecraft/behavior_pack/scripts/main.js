import { system, world } from "@minecraft/server";
import {
  AMBIENT_TICKS_FANCY,
  MOTION_TICKS,
  chooseAmbient,
  hurtShake,
  landingAction,
  stepSound,
} from "./tuning.js";

const SETTING = "realistic:effects";
const motion = new Map();
let reported = false;

system.runInterval(() => {
  try {
    tickMotion();
  } catch (error) {
    report(error);
  }
}, MOTION_TICKS);

system.runInterval(() => {
  try {
    tickAmbient();
  } catch (error) {
    report(error);
  }
}, AMBIENT_TICKS_FANCY);

system.afterEvents.scriptEventReceive.subscribe((event) => {
  if (event.id !== "realistic:toggle" && event.id !== "realistic:status") return;
  const player = event.sourceEntity;
  if (!player || player.typeId !== "minecraft:player") return;
  try {
    if (event.id === "realistic:toggle") {
      const next = !effectsEnabled();
      world.setDynamicProperty(SETTING, next);
      tell(player, next);
    } else {
      tell(player, effectsEnabled());
    }
  } catch (error) {
    report(error);
  }
});

world.afterEvents.playerLeave.subscribe((event) => {
  if (event.playerId) motion.delete(event.playerId);
});

world.afterEvents.playerBreakBlock.subscribe((event) => {
  try {
    if (!effectsEnabled()) return;
    const player = event.player;
    if (!player?.isValid || isSimpleGraphics(player)) return;
    const loc = event.block.location;
    burst(player, "realistic:puff", { x: loc.x + 0.5, y: loc.y + 0.35, z: loc.z + 0.5 });
  } catch (error) {
    report(error);
  }
});

world.afterEvents.entityHurt.subscribe((event) => {
  try {
    if (!effectsEnabled()) return;
    const player = event.hurtEntity ?? event.entity;
    if (!player || player.typeId !== "minecraft:player" || !player.isValid) return;
    const damage = typeof event.damage === "number" ? event.damage : 1;
    if (damage < 1) return;
    const spec = hurtShake(damage);
    shake(player, spec.intensity, spec.duration, "rotational");
    if (damage >= 2 && !isSimpleGraphics(player)) {
      const loc = player.location;
      burst(player, "realistic:spark", { x: loc.x, y: loc.y + 1.15, z: loc.z });
    }
  } catch (error) {
    report(error);
  }
});

function tickMotion() {
  if (!effectsEnabled()) return;
  for (const player of world.getPlayers()) {
    if (!player.isValid) continue;
    const onGround = player.isOnGround;
    const velocity = player.getVelocity();
    const previous = motion.get(player.id) ?? {
      onGround: true,
      inWater: Boolean(player.isInWater),
      fallSpeed: 0,
      cooldown: 0,
    };
    if (previous.cooldown > 0) previous.cooldown -= MOTION_TICKS;

    const physical = isPhysical(player);
    if (!physical) {
      previous.fallSpeed = 0;
    } else if (!onGround) {
      previous.fallSpeed = Math.max(previous.fallSpeed, -velocity.y);
    }

    if (onGround && previous.onGround === false && physical) {
      const impact = previous.fallSpeed;
      previous.fallSpeed = 0;
      const landing = landingAction(impact);
      if (landing.puff) {
        burst(player, landing.hard ? "realistic:impact" : "realistic:puff", feet(player));
      }
      if (landing.shake && previous.cooldown <= 0) {
        const scale = isSimpleGraphics(player) ? 0.7 : 1;
        shake(player, landing.shake.intensity * scale, landing.shake.duration, "positional");
        previous.cooldown = 10;
        if (landing.hard) {
          play(player, stepSound(groundId(player)), 0.26, 0.8);
        }
      }
    }

    if (!onGround && previous.onGround && velocity.y > 0.18 && physical && !isSimpleGraphics(player)) {
      burst(player, "realistic:puff", feet(player));
    }

    const inWater = Boolean(player.isInWater);
    if (inWater && previous.inWater === false && !isSimpleGraphics(player)) {
      const loc = player.location;
      burst(player, "realistic:splash", { x: loc.x, y: loc.y + 0.15, z: loc.z });
    }

    previous.onGround = onGround;
    previous.inWater = inWater;
    if (onGround) previous.fallSpeed = 0;
    motion.set(player.id, previous);
  }
}

function tickAmbient() {
  if (!effectsEnabled()) return;
  for (const player of world.getPlayers()) {
    if (!player.isValid) continue;
    const simple = isSimpleGraphics(player);
    // The interval stays at the fancy rate so rain and caves can still appear
    // on the same clock. Simple graphics opts out inside chooseAmbient.
    if (simple && Math.random() > 0.35) continue;
    const choice = chooseAmbient({
      dimensionId: player.dimension.id,
      biomeId: readBiome(player),
      skyLight: readSkyLight(player),
      weather: readWeather(player.dimension),
      simple,
    });
    if (!choice) continue;
    const reach = simple ? 1.6 : 3.2;
    const loc = {
      x: player.location.x + (Math.random() - 0.5) * reach * 2,
      y: player.location.y + 1.1 + Math.random() * 1.5,
      z: player.location.z + (Math.random() - 0.5) * reach * 2,
    };
    if (!isAir(player, loc)) continue;
    burst(player, choice, loc);
  }
}

function isPhysical(player) {
  return !player.isSwimming && !player.isGliding && !player.isFlying && !player.isInWater;
}

function effectsEnabled() {
  return world.getDynamicProperty(SETTING) !== false;
}

function feet(player) {
  return { x: player.location.x, y: player.location.y + 0.05, z: player.location.z };
}

function burst(player, effect, location) {
  try {
    player.spawnParticle(effect, location);
  } catch (error) {
    report(error);
  }
}

function shake(player, intensity, seconds, kind) {
  const amount = clamp(intensity, 0.05, 1.2);
  const duration = clamp(seconds, 0.08, 0.45);
  const type = kind === "rotational" ? "rotational" : "positional";
  try {
    player.runCommand(`camerashake add @s ${amount.toFixed(2)} ${duration.toFixed(2)} ${type}`);
  } catch (error) {
    report(error);
  }
}

function play(player, soundId, volume, pitch) {
  try {
    player.playSound(soundId, { volume, pitch });
  } catch {
    // A missing sound id should not stop the rest of the effect.
  }
}

function groundId(player) {
  try {
    const block = player.dimension.getBlock({
      x: player.location.x,
      y: player.location.y - 0.2,
      z: player.location.z,
    });
    return block?.typeId ?? "";
  } catch {
    return "";
  }
}

function isAir(player, location) {
  try {
    const block = player.dimension.getBlock(location);
    if (!block) return false;
    return block.isAir || block.typeId === "minecraft:air" || block.typeId === "minecraft:cave_air";
  } catch {
    return false;
  }
}

function readBiome(player) {
  try {
    return player.dimension.getBiome(player.location)?.id ?? "";
  } catch {
    return "";
  }
}

function readSkyLight(player) {
  try {
    if (typeof player.dimension.getSkyLightLevel !== "function") return null;
    return player.dimension.getSkyLightLevel({
      x: player.location.x,
      y: player.location.y + 1,
      z: player.location.z,
    });
  } catch {
    return null;
  }
}

function readWeather(dimension) {
  if (typeof dimension.getWeather !== "function") return null;
  try {
    const weather = dimension.getWeather();
    return typeof weather === "string" ? weather : String(weather);
  } catch {
    return null;
  }
}

function isSimpleGraphics(player) {
  try {
    return String(player.graphicsMode).toLowerCase() === "simple";
  } catch {
    return false;
  }
}

function tell(player, on) {
  const text = on ? "Realistic effects on" : "Realistic effects off";
  try {
    player.onScreenDisplay.setActionBar(text);
  } catch {
    player.sendMessage(text);
  }
}

function report(error) {
  if (reported) return;
  reported = true;
  console.warn(`[Realistic Minecraft] ${error}`);
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}
