# Changelog

## 1.0.0

- **No axe needed.** By default, trees can be notched, felled, limbed and bucked with anything in hand or bare-handed, the same way as with an axe (only an axe wears out). Set `axeRequired = true` in `serverconfig/heartwood-server.toml` to require an axe.
- **Whole branches flash.** Branches left on a fallen tree flash white as a whole, joints between blocks included, when you hit the trunk with something that cuts. On a standing tree, hitting the trunk above its foot makes the foot and the branches flash: the places where it can be cut.
- **Trees lie flat.** A bend of the trunk pointing the way of the fall is straightened out, so the tree no longer breaks on flat ground; bends on the other sides fall as they are and the branches under the tree are crushed. Where the ground rises under the trunk, the tree is raised by up to three blocks.
- **Crushed vegetation.** A fallen tree crushes the grass, flowers, ferns, saplings, bushes, crops and leaves (of other trees too) it lands on, without drops.
- **Water kept.** Branches and leaves falling into water no longer replace it; only the trunk lies in it.
- **What rests on the tree comes down.** When a tree falls, the blocks around it are updated: snow, vines (and the vines hanging below them), cocoa pods and anything else that needed the tree break, and bee nests fall with it.
- **Green wood knocks.** Hitting a green log gives a dull, muffled knock instead of the usual wood sound, until it is dry: an easy way to tell green wood from dry.
- **No more giant trees.** Mega jungle, mega spruce and mega pine trees grow as the single-trunk tree of their wood.
- **No more messages.** The action bar hints are gone.
- Tested on a dedicated server.

## 0.20.2

- **Trees grow from seeds.** Trees no longer give saplings: leaves and fallen crowns drop the tree's seed instead (acorn, beechnut, sweet chestnut, cherry pit, ash and maple keys, and birch, spruce, Scots pine, teak and acacia seeds). A seed planted on earth makes a small mound that takes the look of the ground it is in and comes up as a shoot.
- **Step-by-step growth.** A young tree grows one random step every half day or so (`growthIntervalDays`): the trunk rises or leans, a branch starts, reaches out or forks, wood thickens or the crown fills out. Its crown follows it as it grows. No two trees end up alike, and the foot of a trunk always stands straight. Felling a young tree stops its growth.
- **Thick trunks.** Trunks can grow up to two blocks across, in proportion to their height and to the species (chestnut, beech and teak are the thickest, birch stays under a block). A trunk only spreads over a block where the ground around its foot is level. Thick pieces give logs in proportion to their girth. Trees no longer turn the grass under them into dirt.
- **Felling at the foot.** The notch can only be carved at the foot of a standing tree; elsewhere on the trunk, the axe goes through as if through air. Branches can still be cut off one by one, with the leaves they hold.
- **Stumps.** A felled tree leaves a short stump facing the way it fell: the low floor of the notch, a ragged hinge and the back cut a little higher. The upper part of the cut falls with the tree. A stump can't be cut: it rots away in about a day.
- **Limbing first.** A fallen trunk is bucked only once the whole tree is limbed. While branches are left, the trunk can't be cut into, and swinging an axe at it makes those branches flash white for a few seconds. Leftover leaves do not count.
- **Fewer drops.** Twigs give a stick 15 % or 40 % of the time, or one stick, by thickness. A fallen crown gives a stick 4 % and a seed 3 % of the time.
- **Creative mode.** Breaking the foot of a standing tree takes the whole tree away; breaking it while sneaking fells it. Other blocks break the creative way.
- **Fixes.** Big trees no longer break apart when they land. Pieces cut off from the trunk after a fall break off with what they carry. Fixed a crash when drawing thick trunks.

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
