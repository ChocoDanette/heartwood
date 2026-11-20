# Changelog

## 0.13.1

- **Organic trees.** Every overworld tree (vanilla and Heartwood species) now grows from boughs of variable thickness, in the style of Dynamic Trees. Trunks thicken towards the ground and branches thin out to twigs. The trees also have more natural shapes: leaning trunks, forks, and clustered, ragged crowns. Giant 2x2 trees, azaleas and mangroves keep their vanilla shape. New trees only appear in newly generated chunks, or from saplings.
- **Felling bough trees.** Trunk pieces at least half a block thick take the V notch (drawn to their thickness), and the back cut fells the tree as before. Cutting a branch off a standing tree brings down everything it carries. Felling one tree no longer brings down a tree it touches, and where two crowns meet, each tree keeps its own leaves.
- **Bucking by thickness.** Twigs give sticks, half-block poles give a green log one time in two (otherwise sticks), and thicker pieces give a green log. On a fallen tree, thin pieces break by hand with the twigs they carry; thick pieces need an axe once their leaves and thin branches are off. Thick branches of a standing tree need an axe.

## 0.9.3

- **Real tree species.** Dark oak becomes **beech** and jungle becomes **teak** (new names in English and French, new textures). Four new European species grow in the overworld: **ash** (forests, flower forests, meadows, rarely plains), **Scots pine** (taigas, windswept forests and hills, groves), **sweet chestnut** (forests, windswept and dark forests) and **maple** (forests, flower and birch forests, meadows). New species only appear in newly generated chunks, or from saplings.
- **Full wood sets.** Each new species has logs, wood, stripped logs and wood, planks, stairs, slab, fence, fence gate, door, trapdoor, button, pressure plate, sign, hanging sign, boat and boat with chest, leaves and sapling, with the usual recipes and tags.
- **Textures in the vanilla style, in the real colours of each wood.** Bark keeps a light species pattern (ash net, chestnut twist, pine plates, teak fibres, beech scars, maple flakes). Doors and trapdoors are panelled and glazed for beech, ash and maple, and rustic braced boards for teak, Scots pine and chestnut.
- All new species work with directional felling, limbing, bucking and green wood drying.

## 0.4.1

First public release, for Minecraft 1.21.1 (NeoForge). Works on vanilla worlds.

- **Directional felling.** With an axe, the first cut on a standing trunk carves a V notch into the face you hit. The back cut, on the opposite face, fells the tree towards the notch.
- **Realistic fall.** The tree tips over around the stump, slowly then faster (1 to 3 seconds), and hits the ground with an impact sound and debris. Living things under the trunk take 6 damage plus 1 per trunk section (20 at most); under the branches, 2.
- **Limbing and bucking.** The fallen crown becomes branches: cut them with an axe for 1 to 3 sticks and a small chance of a sapling. A trunk section can only be bucked with an axe once no branches touch it.
- **Green and dry wood.** Natural wood always breaks into green logs (8 vanilla woods). A green log only dries as a placed block with no sky above it, in about one in-game day (0.75 to 1.25 days per log, `dryingDays` in the server config). Green wood has sap-tinted end grain, drips water while fresh, burns a third as long, and cannot be made into planks or charcoal.
- Logs placed by a player stay dry. Sneaking or breaking without an axe breaks one block at a time. Creative mode is not restricted.
