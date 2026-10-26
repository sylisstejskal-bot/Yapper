# AI Mobs

A Minecraft Bedrock add-on. It is separate from the Yapper app in this repository.

Select the **AI Mobs** item and the menu opens on this device. Nothing is sent over the network. **Find nearest mobs** lists mobs within 32 blocks. Select one, type a personality, and choose **Give this mob AI**.

The personality is how that mob acts. A grumpy pirate who only talks about treasure walks to a nearby chest and talks about treasure. A cheerful baker who loves fresh bread follows you and talks about bread. A guard who protects the village hunts monsters. A shy poet who loves rain keeps away and still mentions rain.

Giving a mob AI removes that mob's own behavior group (`aimobs:own_brain`) and leaves `aimobs:local_mind`, which only floats. The local mind is the only thing that moves, looks, and attacks. Release puts the mob's own brain back.

Common mobs are overridden with a simpler brain so that switch is possible. Unpossessed cows still wander, and unpossessed zombies still chase players, but extra vanilla jobs are not copied: villagers do not trade, animals do not breed, and skeletons do not shoot. Water mobs can breathe air, so the local mind can lead them ashore. A mob type this pack does not override is slowed and steered each tick; its original goals can still run, and the menu says so.

Needs Minecraft Bedrock 1.26 or newer. The script modules are the stable `@minecraft/server` 2.10.0 and `@minecraft/server-ui` 2.2.0. The Beta APIs experiment stays off.

## Install

Open `AI-Mobs.mcaddon`, or copy `behavior_pack` and `resource_pack` into the world's behavior and resource pack folders. Enable both packs on the world. Join the world and the AI Mobs item is added to your inventory. It is also under Equipment in the creative inventory.

Select the item in the hotbar. Hold Use (right click or the use button) opens it again. If a chest or other screen is open, close it and select the item again.
