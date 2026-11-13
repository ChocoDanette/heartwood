# Heartwood

Realistic tree felling and wood seasoning for Minecraft 1.21.1 (NeoForge). Heartwood works on vanilla worlds and only needs NeoForge.

## How to fell a tree

1. **Notch.** Hold an axe and break the base of a trunk on the side you want the tree to fall. A V notch is carved into that face.
2. **Back cut.** Walk around and break the same block from the opposite side. The tree tips over towards the notch.
3. **Stand clear.** The falling trunk hurts whatever it lands on.
4. **Limb.** Cut the branches off the fallen tree with an axe to get sticks (and sometimes a sapling).
5. **Buck.** Once a trunk section no longer touches any branch, cut it with an axe to get a green log.

Sneak, or break without an axe, to take a trunk apart one block at a time.

## Green and dry wood

Freshly cut wood is green. Place green logs somewhere sheltered (no sky above them) and they dry into normal logs in about one in-game day. Green logs show sap-coloured end grain and drip water while fresh. Green wood burns poorly and cannot be made into planks or charcoal.

The drying time is set by `dryingDays` in `serverconfig/heartwood-server.toml` (default 1).

## Building

Push to GitHub: the Build workflow compiles the jar and uploads it as an artifact. Pushing a tag `vX.Y.Z` that matches `mod_version` publishes a GitHub release with the jar and the matching section of `CHANGELOG.md`.

## License

MIT. See [LICENSE](LICENSE).
