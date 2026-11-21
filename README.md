# Heartwood

Realistic tree felling and wood seasoning for Minecraft 1.21.1 (NeoForge). Heartwood works on vanilla worlds and only needs NeoForge.

## How to fell a tree

Trees grow from boughs of variable thickness: trunks thicken towards the ground, up to two blocks across on old trees, and branches thin out to twigs.

1. **Notch.** With an axe, or anything else in hand unless the server requires an axe (see [Settings](#settings)), break the foot of the trunk on the side you want the tree to fall. A V notch is carved into that face. The rest of a standing trunk can't be cut into: hitting it makes the foot and the branches flash white, the places where the tree can be cut.
2. **Back cut.** Walk around and break the foot again from the opposite side. The tree tips over towards the notch and leaves a stump, which rots away in about a day.
3. **Stand clear.** The falling trunk hurts whatever it lands on and crushes the grass, flowers, bushes and leaves under it. It lies flat on the ground: a bend of the trunk that points the way of the fall is straightened out, and the tree is raised by up to three blocks where the ground rises under it. Branches and leaves do not take the place of water. Snow, vines, cocoa pods, bee nests and anything else resting on the tree come down with it.
4. **Limb.** Cut every branch off the fallen tree; each comes off with all it carries. While branches are left, they flash white, joints included, when you aim at the trunk.
5. **Buck.** Once the tree is limbed, cut the trunk. Pieces more than half a block thick give a green log (thick trunks several), half-block poles a green log one time in two, thinner ones sticks.

Cutting a branch off a standing tree brings down everything it carries; thick branches need an axe.

In creative mode, breaking the foot of a tree removes the whole tree, and breaking it while sneaking fells it.

## Settings

In `serverconfig/heartwood-server.toml`:

- `axeRequired` (default `false`): by default, trees can be notched, felled, limbed and bucked with anything in hand or bare-handed, the same way as with an axe. Set it to `true` to require an axe.
- `dryingDays` (default 1): days a sheltered green log takes to dry.
- `growthIntervalDays` (default 0.5): days between two growth steps of a young tree.

## Green and dry wood

Freshly cut wood is green. Place green logs somewhere sheltered (no sky above them) and they dry into normal logs in about one in-game day. Green logs show sap-coloured end grain and drip water while fresh. Hitting green wood gives a dull, muffled knock, unlike dry wood. Green wood burns poorly and cannot be made into planks or charcoal.

The drying time is set by `dryingDays` in `serverconfig/heartwood-server.toml` (default 1).

## Growing trees

Trees drop seeds, not saplings. Plant a seed on earth: it makes a small mound, comes up as a shoot and grows one random step every half day or so, so no two trees look alike. The pace is set by `growthIntervalDays` in `serverconfig/heartwood-server.toml` (default 0.5).

## Tree species

Dark oak is renamed **beech** and jungle **teak**, with matching textures. Heartwood also adds **ash**, **Scots pine**, **sweet chestnut** and **maple**, each with a full set of wood blocks, boats and signs. They grow in the overworld forests that suit them and only appear in new chunks. Giant 2x2 trees (mega jungle, mega spruce, mega pine) grow as the ordinary tree of their wood.

## Building

Push to GitHub: the Build workflow compiles the jar and uploads it as an artifact. Running the Release workflow (or pushing a tag `vX.Y.Z` that matches `mod_version`) publishes a GitHub release with the jar and the matching section of `CHANGELOG.md`.

## License

MIT. See [LICENSE](LICENSE).
