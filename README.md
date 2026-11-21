# Heartwood

Realistic tree felling and wood seasoning for Minecraft 1.21.1 (NeoForge). Heartwood works on vanilla worlds and only needs NeoForge.

## How to fell a tree

Trees grow from boughs of variable thickness: trunks thicken towards the ground, up to two blocks across on old trees, and branches thin out to twigs.

1. **Notch.** Hold an axe and break the foot of the trunk on the side you want the tree to fall. A V notch is carved into that face. The rest of a standing trunk can't be cut into.
2. **Back cut.** Walk around and break the foot again from the opposite side. The tree tips over towards the notch and leaves a stump, which rots away in about a day.
3. **Stand clear.** The falling trunk hurts whatever it lands on.
4. **Limb.** Cut every branch off the fallen tree; each comes off with all it carries. Swinging an axe at the trunk while branches are left makes them flash white.
5. **Buck.** Once the tree is limbed, cut the trunk with an axe. Pieces more than half a block thick give a green log (thick trunks several), half-block poles a green log one time in two, thinner ones sticks.

Cutting a branch off a standing tree brings down everything it carries; thick branches need an axe.

In creative mode, breaking the foot of a tree removes the whole tree, and breaking it while sneaking fells it.

## Green and dry wood

Freshly cut wood is green. Place green logs somewhere sheltered (no sky above them) and they dry into normal logs in about one in-game day. Green logs show sap-coloured end grain and drip water while fresh. Green wood burns poorly and cannot be made into planks or charcoal.

The drying time is set by `dryingDays` in `serverconfig/heartwood-server.toml` (default 1).

## Growing trees

Trees drop seeds, not saplings. Plant a seed on earth: it makes a small mound, comes up as a shoot and grows one random step every half day or so, so no two trees look alike. The pace is set by `growthIntervalDays` in `serverconfig/heartwood-server.toml` (default 0.5).

## Tree species

Dark oak is renamed **beech** and jungle **teak**, with matching textures. Heartwood also adds **ash**, **Scots pine**, **sweet chestnut** and **maple**, each with a full set of wood blocks, boats and signs. They grow in the overworld forests that suit them and only appear in new chunks.

## Building

Push to GitHub: the Build workflow compiles the jar and uploads it as an artifact. Running the Release workflow (or pushing a tag `vX.Y.Z` that matches `mod_version`) publishes a GitHub release with the jar and the matching section of `CHANGELOG.md`.

## License

MIT. See [LICENSE](LICENSE).
