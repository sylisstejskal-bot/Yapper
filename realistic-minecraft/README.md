# Realistic Minecraft

A Minecraft Bedrock addon you can install on a phone. Blocks and items are sharper, the player leans and lands with a little more weight, and hard falls kick up dust. Mining, damage, mobs, and recipes stay the same.

Needs Minecraft **26.0 or newer** (the 2026 versions, also called 1.26). The Play Store build **26.52** can run it.

## Download

Tap this link on your phone:

https://github.com/sylisstejskal-bot/Yapper/raw/refs/heads/cursor/realistic-minecraft-addon-1c54/realistic-minecraft/dist/Realistic-Minecraft.mcaddon

The file inside this project is `dist/Realistic-Minecraft.mcaddon`.

## Install on Android

1. Tap the link and let the download finish.
2. Open the Files app, or tap the finished download in Chrome.
3. Tap `Realistic-Minecraft.mcaddon`.
4. Open it with Minecraft.
5. Wait until Minecraft says the import worked.

The file holds two packs: **Realistic Minecraft** (textures and motion) and **Realistic Minecraft Behavior** (dust and camera shake).

If Android will not open the file, update Minecraft from the Play Store first.

## Turn the packs on

1. In Minecraft, create a world or edit one you already play.
2. Open **Resource Packs**.
3. Under My Packs, tap **Realistic Minecraft**, then Activate.
4. Open **Behavior Packs**.
5. Tap **Realistic Minecraft Behavior**, then Activate.
6. If Minecraft asks to also turn on the resource pack, tap Yes.
7. Save or create the world.

Cheats can stay off. The effects start with the behavior pack.

## What you should see

Try these in order:

1. Look at grass, wood, stone, and ore. Textures are 32×32, so edges are clearer than vanilla and the style still matches across blocks.
2. Tap the perspective button until you see your body. Stand still: the feet stay on the ground and the limbs rest. Walk, then stop. The legs and opposite arms swing, then ease back to rest. Sprint swings a little farther and the waist leans from the hips.
3. Jump. The arms trail, then lift, and the legs move into a rising pose. Falling is a different pose. Landing eases back to standing without dropping the body into the ground. First person only nudges the hand. Bedrock does not draw your legs there.
4. Walk off a one-block step. You get no shake.
5. Drop about four blocks. You get a dust burst and a short camera shake.
6. Get hit. The view kicks once, lightly.
7. Stand in a forest, a desert, a cave, and the Nether. One slow particle appears every couple of seconds. Simple graphics skips most of these.
8. Hearts, hunger, the hotbar, and the experience bar use a darker set of icons.

## Turn dust and shake off

Textures and the walk poses stay. This switch only stops particles, the extra landing sound, and camera shake.

1. Turn cheats on for that world.
2. Open chat and send:

```
/scriptevent realistic:toggle
```

3. Send it again to turn them back on.
4. This shows the current state:

```
/scriptevent realistic:status
```

The world remembers the choice.

## Folders

```
realistic-minecraft/
  dist/Realistic-Minecraft.mcaddon
  preview.png
  resource_pack/          what you see
  behavior_pack/          dust, shake, particles
  tools/                  rebuild and checks
```

Files worth opening on a phone:

- `behavior_pack/scripts/tuning.js` — how hard a landing must be before dust or shake
- `resource_pack/animations/realistic_player.animation.json` — the poses
- `resource_pack/animation_controllers/realistic_player.animation_controllers.json` — when each pose plays
- `PLAN.md` — what the game actually supports

## Rebuild

On a computer, from the `realistic-minecraft` folder:

```
python3 tools/generate_textures.py
node tools/test_tuning.mjs
python3 tools/validate.py
```

`generate_textures.py` rewrites textures, HUD icons, and particle files. It does not rewrite animations or scripts. `validate.py` rewrites `dist/Realistic-Minecraft.mcaddon` after the checks pass.

You need Python 3 with Pillow, and Node, only if you rebuild. Installing the `.mcaddon` does not need either.

## What stays vanilla

Breaking speed, reach, damage, hunger, recipes, mobs, and world generation are unchanged. Water, lava, and fire keep Minecraft's own animation. Remove both packs and the world looks and plays like vanilla again.

## Vibrant Visuals

Each block has a texture set: the color image, a height map, and a simple metal/roughness value. Classic graphics uses only the color. If your phone has **Vibrant Visuals** in video settings, turn it on to see the height. The pack does not force ray tracing.

## Limits

These are real limits of Bedrock, not missing files:

- The player skeleton has no knees or elbows. This pack replaces the two vanilla stride animations, `animation.player.move.legs` and `animation.player.move.arms`, and pitches the waist for lean, jump, fall, and landing. It does not move the root, waist, or body position, so the feet stay on Minecraft's ground line. Swim and crawl still override the limb animations.
- There is no animation event before the jump leaves the ground. A small crouch plays only if Bedrock reports `query.is_jumping` while the player is still standing. The visible takeoff is the short blend at the start of the jump. Jump height and speed are unchanged.
- First person can only move the hand. The walk bob stays, with a small rise, fall, and crouch offset.
- Overriding the player needs a full copy of `player.entity.json`. This copy is Mojang's current samples file plus the new motion, with `min_engine_version` set to `1.8.0` so character-creator skins and capes keep working. A later Minecraft update that changes the player will not merge into this copy until the pack is updated.
- `Camera.addShake` exists in `@minecraft/server` 2.10.0. That module is documented against 1.26.70 preview, not the 26.52 Play Store build. This pack depends on 2.6.0, which 26.0 and newer already include, and shakes the camera with the built-in `camerashake` command.
- Script FOV is not used. Setting it would replace your FOV slider and the vanilla sprint FOV.
- Rain mist calls `dimension.getWeather()`. That function is in the 2.13 preview API, not in 2.6.0. On 26.52 the call is skipped. Forest, desert, cave, Nether, and End particles do not need it.
- The HUD edits are the classic hotbar, hearts, hunger, and experience icons, at the original pixel sizes so the layout stays put. The newer Ore UI main menu is not replaced.
- Animated water, lava, and fire are untouched. Replacing them means replacing the whole flipbook list, which would break every other vanilla animation.
- This project was checked as files: JSON, image sizes, script syntax, and the `.mcaddon` layout. It was not launched inside the Minecraft app, because that app is not in this environment. Use the Android steps above for the first in-game pass.
