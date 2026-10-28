# Obesity

A Minecraft Bedrock add-on for Android, Windows, and consoles. You start with a sealed box. Open it and a chubby yellow character named Obesity pops out. A hunger bar at the top of the screen is Obesity's hunger. Feed them food or they starve.

Requires **Minecraft 1.21.90 or newer**. It uses the stable script API, so you do not turn on the Beta APIs experiment. Adding a behavior pack turns off achievements for that world.

## Install on Android

1. Copy `Obesity.mcaddon` onto the phone.
2. Tap the file and open it with Minecraft.
3. Minecraft imports **Obesity** and **Obesity Resources**.
4. Create a world, or edit one. Under Behavior Packs, activate **Obesity**. Accept the prompt that turns on the resource pack.
5. Enter the world. A box is in your inventory.

The same file works on Windows and consoles. On Windows you can also double-click it.

## How to play

1. Place the **Obesity Box**.
2. Switch to an empty hand and tap the box. Obesity tumbles out.
3. Look at the top of the screen. The bar is Obesity's hunger, from 0 to 20. Your own hunger bar stays above the hotbar.
4. Hold any food and tap Obesity. The food is eaten (bowls and bottles are given back). Steak fills more than bread. A golden apple is a big meal, and an enchanted golden apple fills the bar.
5. Hunger drops by 1 every 12 seconds. At 0, Obesity can barely move until you feed them. They follow you, and they walk toward food you hold.

Breaking the box before you open it drops the box. You can craft another one: a chest in the middle, with yellow wool on the four corners.

```
Y . Y
Y C Y
Y . Y
```

`Y` is yellow wool and `C` is a chest.

## Rebuild

From `obesity-addon/`:

```bash
python3 tools/generate_art.py
node --test test/logic.test.mjs
python3 tools/validate.py
```

`validate.py` writes `Obesity.mcaddon`.
