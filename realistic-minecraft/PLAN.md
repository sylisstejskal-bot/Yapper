# Plan

Checked against Minecraft's public creator docs and the Mojang `bedrock-samples` repository on October 25, 2026. The Play Store game that day is Bedrock 26.52 (1.26.52). Preview 26.70.21 is out, and 26.60 is not stable yet.

The pack targets **1.26.0 / script API 2.6.0**, which 26.52 already includes. It does not require a beta experiment.

## What the game can do

- A resource pack replaces textures by using the same file path as vanilla. `terrain_texture.json` and `flipbook_textures.json` do not merge, so this pack does not ship its own copies.
- Grass tops and leaves are grayscale in vanilla because the biome tint colors them. The new grass and leaf textures stay grayscale, with more shape. Fast graphics uses `leaves_*_opaque.png`. Fancy leaves are the `.tga` files.
- Block texture sets (`format_version` 1.21.30) are how Vibrant Visuals gets a height map and a metal/emissive/roughness/subsurface value. Classic graphics still uses the color image. The manifest `pbr` capability is the documented switch for those sets. Metal and subsurface are not combined on the same texture.
- Animations overwrite by identifier. Replacing `animation.player.move.legs`, `animation.player.move.arms`, and `animation.player.first_person.walk` changes the stride and the hand without copying every vanilla animation.
- New poses (breathe, lean, jump, fall, land) need a new controller, and the controller's short names have to be listed on the player. `entity/player.entity.json` is a full replace. The base file is the current `bedrock-samples` player, including the spear animations, plus `min_engine_version` `1.8.0`. That version line is the known way to add player animations without resetting character-creator skins and capes to Steve.
- Swim, crawl, elytra, riding, and sleep keep the vanilla controllers. The waist lean turns off for those states. Arm and leg poses stay inside the vanilla move animations, which swimming and crawling already override. Nothing in those poses translates the root, waist, or body.
- Particles are JSON in the resource pack, spawned with `player.spawnParticle`, which only that player sees.
- `player.camera.addShake` was added in `@minecraft/server` 2.10.0, alongside the 1.26.70 preview docs. It is not part of 2.6.0. The built-in command `camerashake add` is the supported shake on 26.52.
- `dimension.getWeather()` is in the 2.13 preview module. The script calls it only when the function exists.
- `getVelocity`, `isOnGround`, `isInWater`, `isGliding`, `isFlying`, `isSwimming`, `getBiome`, `getSkyLightLevel`, `graphicsMode`, `entityHurt`, and `playerBreakBlock` are all in 2.6.0 or earlier.

## What was built

1. **Resource pack.** 32×32 color textures for the common overworld, deepslate, nether, and end surfaces, plus wood types through cherry, pale oak, and mangrove. Ores are clusters, not single pixels. Items in the hotbar (gems, ingots, tools, food, bow, arrow) are redrawn at 32×32. HUD icons keep vanilla's exact pixel sizes.
2. **Motion.** The vanilla player root controller already plays `move.legs` and `move.arms`. Those two overrides are the walk and sprint. The cycle is a cosine of `modified_distance_moved * 38.17`, with amplitude from horizontal movement, so it repeats while moving and rests while standing. That phase constant does not change with the sprint blend. An earlier version lerped the phase from 38.17 to 52 while sprint eased off, which scrubbed the cosine through an extra fast step after sprint ended. Sprint is now a wider swing and a deeper waist lean, driven only by `variable.rm_sprint`. Letting go while still moving eases that blend toward a walk. A full stop clears it immediately and the amplitude settles to idle. The old stride multiplied `variable.tcos0`, which divides by `variable.gliding_speed_value`. That variable was never set, the division was zero, and the limbs stayed still. Jump, fall, and landing still only rotate bones. Nothing translates the root, waist, or body.
3. **Face.** The player eyes stay the pixels on the skin. Bedrock cannot blink those pixels, and a second eye mesh reads as another pair of eyes, so none is drawn.
4. **World particles.** Leaves drift on a slow sideways curve in forests. Snowy biomes get one flake. Swamps and mangroves get mist. Footsteps spawn one small particle from the block under the player, about every 0.85 blocks of travel, and only while moving. Stone chips appear while sprinting. Swimming spawns one bubble about every 1.15 blocks, plus a splash on entry and a smaller ripple on exit. There is no block-wind animation: vanilla foliage has no bones.
5. **Combat.** A hit the player lands sparks once on the target and does not shake the camera. Damage, reach, and attack speed stay vanilla. A hit that lands on the player still shakes the view. A blocking cause does not.
6. **Animals.** `animation.quadruped.walk` keeps one distance phase and opposite legs, with a small body and head sway. Pigs, cows, sheep, and mooshrooms already use that animation.
7. **Sound.** Two short original Vorbis clips, a leaf rustle and an underwater drip, play at low volume with a cooldown. Vanilla footstep and hit sounds are left to the game so they are not doubled.
8. **Impact.** Soft landings puff dust. Landings past about 0.85 blocks/tick also shake the camera and play a quiet vanilla step sound. Simple graphics softens the shake and skips most ambient particles.
9. **Behavior pack.** One script entry, `scripts/main.js`, with the numbers in `scripts/tuning.js`. `@minecraft/server` 2.6.0. The behavior pack depends on the resource pack so they import together. Pack version 1.1.0.

## What was left alone on purpose

- Water, lava, and fire frames. Also the sky, sun, moon, and biome color files. A partial biome file would wipe vanilla colors, and this script API cannot ask if it is raining.
- Block wind on grass, flowers, leaves, kelp, and seagrass. Those blocks have no bones to animate.
- Torch, door, ladder, and iron-bar textures. Those images are silhouettes, and a filled square would break the model.
- FOV changes.
- A copied vanilla player animation controller. Adding a second controller avoids dropping spear, crawl, and swim states the next time that file is compared.
- Custom blocks, items, and recipes.

## Phone editing

Change landing strength in `behavior_pack/scripts/tuning.js`. Change a pose in `resource_pack/animations/realistic_player.animation.json`. The generator is for a computer rebuild of the images, not for day-to-day edits.
