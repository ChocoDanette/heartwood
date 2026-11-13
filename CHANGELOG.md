# Changelog

## 0.4.1

First public release, for Minecraft 1.21.1 (NeoForge). Works on vanilla worlds.

- **Directional felling.** With an axe, the first cut on a standing trunk carves a V notch into the face you hit. The back cut, on the opposite face, fells the tree towards the notch.
- **Realistic fall.** The tree tips over around the stump, slowly then faster (1 to 3 seconds), and hits the ground with an impact sound and debris. Living things under the trunk take 6 damage plus 1 per trunk section (20 at most); under the branches, 2.
- **Limbing and bucking.** The fallen crown becomes branches: cut them with an axe for 1 to 3 sticks and a small chance of a sapling. A trunk section can only be bucked with an axe once no branches touch it.
- **Green and dry wood.** Natural wood always breaks into green logs (8 vanilla woods). A green log only dries as a placed block with no sky above it, in about one in-game day (0.75 to 1.25 days per log, `dryingDays` in the server config). Green wood has sap-tinted end grain, drips water while fresh, burns a third as long, and cannot be made into planks or charcoal.
- Logs placed by a player stay dry. Sneaking or breaking without an axe breaks one block at a time. Creative mode is not restricted.
