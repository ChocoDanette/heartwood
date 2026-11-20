# Heartwood

Realistic tree felling and wood seasoning for Minecraft 1.21.1 (NeoForge). Heartwood works on vanilla worlds and only needs NeoForge.

## How to fell a tree

Trees grow from boughs of variable thickness: trunks thicken towards the ground and branches thin out to twigs.

1. **Notch.** Hold an axe and break the base of a trunk (at least half a block thick) on the side you want the tree to fall. A V notch is carved into that face.
2. **Back cut.** Walk around and break the same block from the opposite side. The tree tips over towards the notch.
3. **Stand clear.** The falling trunk hurts whatever it lands on.
4. **Limb.** Cut the leafy branches and the thin boughs off the fallen tree to get sticks (and sometimes a sapling). A thin bough comes off with the twigs it carries.
5. **Buck.** Once a thick piece no longer carries leaves or thin boughs, cut it with an axe. Pieces more than half a block thick give a green log, half-block poles a green log one time in two, thinner ones sticks.

Cutting a branch off a standing tree brings down everything it carries; thick branches need an axe.

Sneak, or break without an axe, to take a trunk apart one block at a time.

## Green and dry wood

Freshly cut wood is green. Place green logs somewhere sheltered (no sky above them) and they dry into normal logs in about one in-game day. Green logs show sap-coloured end grain and drip water while fresh. Green wood burns poorly and cannot be made into planks or charcoal.

The drying time is set by `dryingDays` in `serverconfig/heartwood-server.toml` (default 1).

## Tree species

Dark oak is renamed **beech** and jungle **teak**, with matching textures. Heartwood also adds **ash**, **Scots pine**, **sweet chestnut** and **maple**, each with a full set of wood blocks, boats and signs. They grow in the overworld forests that suit them and only appear in new chunks.

## Building

Push to GitHub: the Build workflow compiles the jar and uploads it as an artifact. Running the Release workflow (or pushing a tag `vX.Y.Z` that matches `mod_version`) publishes a GitHub release with the jar and the matching section of `CHANGELOG.md`.

## License

MIT. See [LICENSE](LICENSE).
