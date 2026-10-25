import { system, world } from "@minecraft/server";
import {
  AMBIENT_TICKS_FANCY,
  MOTION_TICKS,
  STEP_DISTANCE,
  WATER_DISTANCE,
  chooseAmbient,
  footstepParticle,
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
    const victim = event.hurtEntity ?? event.entity;
    if (!victim?.isValid) return;
    const damage = typeof event.damage === "number" ? event.damage : 1;
    const attacker = event.damageSource?.damagingEntity;
    const blocked = event.damageSource?.cause === "blocking";
    let sparked = false;
    if (attacker?.typeId === "minecraft:player" && attacker.isValid && attacker.id !== victim.id) {
      if (!isSimpleGraphics(attacker)) {
        burst(attacker, "realistic:spark", chest(victim));
        sparked = true;
      }
    }
    if (victim.typeId !== "minecraft:player") return;
    if (blocked) {
      if (!sparked && !isSimpleGraphics(victim)) burst(victim, "realistic:spark", chest(victim));
      return;
    }
    if (damage < 1) return;
    const spec = hurtShake(damage);
    shake(victim, spec.intensity, spec.duration, "rotational");
    if (!sparked && damage >= 2 && !isSimpleGraphics(victim)) {
      burst(victim, "realistic:spark", chest(victim));
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
      x: player.location.x,
      z: player.location.z,
      travel: 0,
      waterTravel: 0,
      soundWait: 50,
    };
    if (previous.x === undefined) {
      previous.x = player.location.x;
      previous.z = player.location.z;
      previous.travel = 0;
      previous.waterTravel = 0;
      previous.soundWait = 50;
    }
    if (previous.cooldown > 0) previous.cooldown -= MOTION_TICKS;
    if (previous.soundWait > 0) previous.soundWait -= MOTION_TICKS;

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
    const simple = isSimpleGraphics(player);
    if (inWater && previous.inWater === false && !simple) {
      const loc = player.location;
      burst(player, "realistic:splash", { x: loc.x, y: loc.y + 0.15, z: loc.z });
    }
    if (!inWater && previous.inWater === true && !simple) {
      burst(player, "realistic:ripple", feet(player));
    }

    const moved = Math.hypot(player.location.x - previous.x, player.location.z - previous.z);
    const speed = Math.hypot(velocity.x, velocity.z);
    if (moved > 3) {
      previous.travel = 0;
      previous.waterTravel = 0;
    } else if (inWater && speed > 0.04 && !player.isGliding) {
      previous.waterTravel += moved;
      if (previous.waterTravel >= WATER_DISTANCE) {
        previous.waterTravel = 0;
        if (!simple) {
          const loc = player.location;
          burst(player, "realistic:bubble", { x: loc.x, y: loc.y + 0.45, z: loc.z });
          if (onGround) burst(player, "realistic:ripple", feet(player));
        }
      }
      if (!simple && previous.soundWait <= 0) {
        play(player, "realistic.drip", 0.12, 0.85 + Math.random() * 0.3);
        previous.soundWait = 120;
      }
    } else if (onGround && physical && speed > 0.05) {
      previous.travel += moved;
      if (previous.travel >= STEP_DISTANCE) {
        previous.travel = 0;
        const effect = footstepParticle(groundId(player), speed);
        if (effect) burst(player, effect, feet(player));
      }
    }

    previous.onGround = onGround;
    previous.inWater = inWater;
    previous.x = player.location.x;
    previous.z = player.location.z;
    if (onGround) previous.fallSpeed = 0;
    motion.set(player.id, previous);
  }
}

function tickAmbient() {
  if (!effectsEnabled()) return;
  for (const player of world.getPlayers()) {
    if (!player.isValid) continue;
    const simple = isSimpleGraphics(player);
    if (player.isInWater) continue;
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
    if (choice === "realistic:leaf") {
      const state = motion.get(player.id);
      if (state && state.soundWait <= 0) {
        play(player, "realistic.rustle", 0.16, 0.9 + Math.random() * 0.25);
        state.soundWait = 100;
      }
    }
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

function chest(entity) {
  const loc = entity.location;
  return { x: loc.x, y: loc.y + 1.05, z: loc.z };
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
