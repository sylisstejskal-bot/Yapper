# Obesity

A Minecraft Bedrock add-on for Android, Windows, and consoles. A sealed box appears in front of you. Open it and Obesity pops out: a round yellow character with a big smile, tiny arms, and no legs. A hunger bar at the top of the screen is Obesity's hunger. Feed them food or they starve. Obesity can also talk through Ollama on a computer, or through a Cohere key you create.

Requires **Minecraft 1.21.100 or newer**. It uses the stable script API, so you do not turn on the Beta APIs experiment. Adding a behavior pack turns off achievements for that world.

## Install on Android

1. Copy `Obesity.mcaddon` onto the phone.
2. Tap the file and open it with Minecraft.
3. Minecraft imports **Obesity** and **Obesity Resources**.
4. Create a world, or edit one. Under Behavior Packs, activate **Obesity**. Accept the prompt that turns on the resource pack.
5. Enter the world. A box appears in front of you. Tap it.

The same file works on Windows and consoles. On Windows you can also double-click it.

## How to play

1. Join the world. The **Obesity Box** appears in front of you. You do not place the first one.
2. Tap the box. Obesity tumbles out. Tapping it while you are still holding a box opens it. Crouch and hold if you want to mine the box and pick it back up.
3. Look at the top of the screen. The bar is Obesity's hunger, from 0 to 20. Your own hunger bar stays above the hotbar.
4. Connect an AI, then tap Obesity with an empty hand or type to him in chat. The line above his head is the AI's reply. He does not use pre-made lines.
5. Hold any food and tap Obesity to feed him. The food is eaten (bowls and bottles are given back). Steak fills more than bread. A golden apple is a big meal, and an enchanted golden apple fills the bar.
6. Hunger drops by 1 every 12 seconds. At 0, Obesity can barely move until you feed them. He stays beside you and comes back if he gets a few blocks away.

Breaking the box before you open it drops the box. You can craft another one: a chest in the middle, with yellow wool on the four corners. If Obesity collapses, a new box drops in front of you.

## Let Obesity talk

The chat guide uses placeholders. Replace them. The pack does not contain a Cohere key or a server address.

**Local AI (Ollama)**

1. On a computer, install Ollama from [ollama.com](https://ollama.com).
2. In a terminal on that computer, run `ollama pull llama3.2`.
3. Leave Ollama open, and allow its port through the computer's firewall so the Minecraft world can reach it.
4. In Minecraft, run:

```
/obesity:connect to local ai server LOCAL_AI_SERVER_PLACEHOLDER_HERE
```

Replace `LOCAL_AI_SERVER_PLACEHOLDER_HERE` with that computer's address, including `http://` and the port. The placeholder is not a server, and the pack will refuse it.

**Cohere**

1. Go to [cohere.com](https://cohere.com), create an account, and make an API key.
2. In a single-player world, run:

```
/obesity:connect to YOUR_COHERE_API_KEY
```

Replace `YOUR_COHERE_API_KEY` with your key. Do not leave the placeholder in the command. The key is not printed back to chat.

Do not type `/connect`. That command is Minecraft's websocket connection. In a single-player world it closes immediately, which is the "connection closed" message. `/obesity:connect` stays in the world and saves the key. You can also type `connect to` and the key in chat with no slash.

Stand near Obesity and talk. The reply appears in chat and on the line above the hotbar. Obesity asks Cohere when the world can open it, and still answers in the world when it cannot.

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
