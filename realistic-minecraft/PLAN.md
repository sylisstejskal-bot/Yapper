# Plan

Checked against Minecraft's public creator docs and the Mojang `bedrock-samples` repository on October 25, 2026. The Play Store game that day is Bedrock 26.52 (1.26.52). Preview 26.70.21 is out, and 26.60 is not stable yet.

The pack targets **1.26.0 / script API 2.6.0**, which 26.52 already includes. It does not require a beta experiment.

## What the game can do

- A resource pack replaces textures by using the same file path as vanilla. `terrain_texture.json` and `flipbook_textures.json` do not merge, so this pack does not ship its own copies.
- Grass tops and leaves are grayscale in vanilla because the biome tint colors them. The new grass and leaf textures stay grayscale, with more shape. Fast graphics uses `leaves_*_opaque.png`. Fancy leaves are the `.tga` files.
- Block texture sets (`format_version` 1.21.30) are how Vibrant Visuals gets a height map and a metal/emissive/roughness/subsurface value. Classic graphics still uses the color image. The manifest `pbr` capability is the documented switch for those sets. Metal and subsurface are not combined on the same texture.
- Animations overwrite by identifier. Replacing `animation.player.move.legs`, `animation.player.move.arms`, and `animation.player.first_person.walk` changes the stride and the hand without copying every vanilla animation.
- New poses (breathe, lean, jump, fall, land) need a new controller, and the controller's short names have to be listed on the player. `entity/player.entity.json` is a full replace. The base file is the current `bedrock-samples` player, including the spear animations, plus `min_engine_version` `1.8.0`. That version line is the known way to add player animations without resetting character-creator skins and capes to Steve.
- Swim, crawl, elytra, riding, and sleep keep the vanilla controllers. The extra body lean turns off for those states. Arm and leg air poses are inside the vanilla move animations, which swimming already overrides.
- Particles are JSON in the resource pack, spawned with `player.spawnParticle`, which only that player sees.
- `player.camera.addShake` was added in `@minecraft/server` 2.10.0, alongside the 1.26.70 preview docs. It is not part of 2.6.0. The built-in command `camerashake add` is the supported shake on 26.52.
- `dimension.getWeather()` is in the 2.13 preview module. The script calls it only when the function exists.
- `getVelocity`, `isOnGround`, `isInWater`, `isGliding`, `isFlying`, `isSwimming`, `getBiome`, `getSkyLightLevel`, `graphicsMode`, `entityHurt`, and `playerBreakBlock` are all in 2.6.0 or earlier.

## What was built

1. **Resource pack.** 32×32 color textures for the common overworld, deepslate, nether, and end surfaces, plus wood types through cherry, pale oak, and mangrove. Ores are clusters, not single pixels. Items in the hotbar (gems, ingots, tools, food, bow, arrow) are redrawn at 32×32. HUD icons keep vanilla's exact pixel sizes.
2. **Motion.** A longer stride while sprinting, a quieter stride while sneaking, a split-leg jump, open arms while falling, a body lean, idle breathing, a landing dip, and a stronger first-person hand bob.
3. **Impact.** Soft landings puff dust. Landings past about 0.85 blocks/tick also shake the camera and play a quiet step sound. Hits shake the view. Simple graphics softens the shake and skips most ambient particles.
4. **Particles.** One ambient speck every two seconds, chosen from the dimension, biome, and sky light. Jump dust, landing dust, water entry, and block-break dust are separate short bursts. No particle uses more than 8 sprites.
5. **Behavior pack.** One script entry, `scripts/main.js`, with the numbers in `scripts/tuning.js`. `@minecraft/server` 2.6.0. The behavior pack depends on the resource pack so they import together.

## What was left alone on purpose

- Water, lava, and fire frames.
- Torch, door, ladder, and iron-bar textures. Those images are silhouettes, and a filled square would break the model.
- FOV changes.
- A copied vanilla player animation controller. Adding a second controller avoids dropping spear, crawl, and swim states the next time that file is compared.
- Custom blocks, items, and recipes.

## Phone editing

Change landing strength in `behavior_pack/scripts/tuning.js`. Change a pose in `resource_pack/animations/realistic_player.animation.json`. The generator is for a computer rebuild of the images, not for day-to-day edits.
