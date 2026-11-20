package com.chocodanette.heartwood.tree;

import com.chocodanette.heartwood.Config;
import com.chocodanette.heartwood.ModBlocks;
import com.chocodanette.heartwood.species.OrganicTrunkPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Trees that grow one step at a time, from a planted seed (or a sapling) to a mature tree.
 *
 * <p>Every half day or so (more or less a quarter of a day, see {@link Config#growthIntervalDays()})
 * a young tree takes one growth step: the trunk rises (or leans), a new branch starts, a branch
 * reaches further or forks, or the tree just fills out. After each step its wood thickens (pipe
 * model, as the tree ages) and its crown develops. Each step is random, so no two trees end up
 * alike; the species (the tree feature's trunk placer) only sets the proportions. The tree itself
 * lives in the world as boughs; only its growth plan is kept here. Growth follows the world clock,
 * so a tree in an unloaded chunk catches up when it is loaded again. Generated trees are mature.
 */
public final class GrowingTrees extends SavedData {
    private static final String NAME = "heartwood_trees";
    private static final int CHECK_EVERY = 100;
    /** Most steps a tree catches up on in one check, so a long absence is spread over a few seconds. */
    private static final int MAX_STEPS_PER_CHECK = 4;
    /** Once grown, the foot of the trunk gains a pixel of radius every this many steps (about 3 days). */
    private static final int STEPS_PER_PIXEL = 6;

    private static final class Branch {
        BlockPos from, tip;
        float bearing, slope;
        int len, target;
        boolean forked, alive = true;
    }

    private static final class Tree {
        ResourceKey<ConfiguredFeature<?, ?>> feature;
        ResourceLocation seed;          // the seed item, while still a planted seed (null otherwise)
        long next;
        int age, targetHeight, targetBranches;
        Direction leanDir = Direction.NORTH;
        Set<Integer> leanAt = new HashSet<>();
        List<BlockPos> trunk = new ArrayList<>();
        boolean trunkAlive = true;
        List<Branch> branches = new ArrayList<>();
        long crownSeed;
        Set<Long> leaves = new HashSet<>();   // the leaves this tree grew
    }

    private final Map<BlockPos, Tree> trees = new LinkedHashMap<>();

    public static GrowingTrees get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GrowingTrees::new, GrowingTrees::load, null), NAME);
    }

    /** Stops the growth of the tree rooted at root (felled, cut down or dug up). */
    public void forget(BlockPos root) {
        if (trees.remove(root) != null) setDirty();
    }

    /** The seed item planted at pos, if pos is a planted seed that has not come up yet. */
    public Optional<ItemStack> plantedSeed(BlockPos pos) {
        Tree t = trees.get(pos);
        if (t == null || t.seed == null) return Optional.empty();
        return Optional.of(new ItemStack(BuiltInRegistries.ITEM.get(t.seed)));
    }

    /** Follows parents down from a piece of a tree to its root piece. */
    public static BlockPos rootOf(ServerLevel level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i < 256; i++) {
            BlockState s = level.getBlockState(p);
            BlockPos par = s.getBlock() instanceof BoughBlock ? BoughBlock.parentPos(s, p) : null;
            if (par == null || !(level.getBlockState(par).getBlock() instanceof BoughBlock)) return p;
            p = par;
        }
        return p;
    }

    /** True when the feature grows boughs, so a tree of its species can grow step by step. */
    public static boolean canGrow(Holder<ConfiguredFeature<?, ?>> feature) {
        return feature.value().config() instanceof TreeConfiguration config && config.trunkPlacer instanceof BoughTrunkPlacer
                && ModBlocks.boughFor(trunkLog(config)) != null;
    }

    private static Block trunkLog(TreeConfiguration config) {
        return config.trunkProvider.getState(RandomSource.create(0), BlockPos.ZERO).getBlock();
    }

    /**
     * Starts a tree at root. A planted seed (seed item given) first has to come up; a sapling comes
     * up at once as a shoot.
     */
    public void plant(ServerLevel level, BlockPos root, ResourceKey<ConfiguredFeature<?, ?>> key, ResourceLocation seed) {
        Optional<Holder.Reference<ConfiguredFeature<?, ?>>> feature =
                level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(key);
        if (feature.isEmpty() || !canGrow(feature.get())) return;
        TreeConfiguration config = (TreeConfiguration) feature.get().value().config();
        BoughTrunkPlacer placer = (BoughTrunkPlacer) config.trunkPlacer;
        RandomSource random = level.random;
        Tree t = new Tree();
        t.feature = key;
        t.seed = seed;
        t.crownSeed = random.nextLong();
        t.targetHeight = Math.max(2, placer.getTreeHeight(random));
        t.targetBranches = placer.branches().count().sample(random);
        t.leanDir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int leans = placer.branches().lean() > 0 ? random.nextInt(placer.branches().lean() + 1) : 0;
        for (int k = 0; k < leans; k++) {
            t.leanAt.add(Math.max(2, t.targetHeight / 3 + random.nextInt(Math.max(1, t.targetHeight * 2 / 3 - 1))));
        }
        t.next = level.getGameTime() + interval(random);
        if (seed == null) comeUp(level, root, t, config);
        trees.put(root.immutable(), t);
        setDirty();
    }

    private static long interval(RandomSource random) {
        return Math.max(20L, (long) (Config.growthIntervalDays() * 24000.0 * (0.5 + random.nextFloat())));
    }

    /** A growing sapling becomes a shoot that grows step by step, instead of a full tree at once. */
    @SubscribeEvent
    public static void onSaplingGrow(BlockGrowFeatureEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Holder<ConfiguredFeature<?, ?>> feature = event.getFeature();
        if (feature == null || !canGrow(feature)) return;
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> key = feature.unwrapKey();
        if (key.isEmpty()) return;
        event.setCanceled(true);

        BlockPos pos = event.getPos();
        BlockState sapling = level.getBlockState(pos);
        BlockPos root = pos;
        // Four saplings in a square (dark oak) grow into one tree from their corner.
        search:
        for (int i = 0; i >= -1; i--) {
            for (int j = 0; j >= -1; j--) {
                BlockPos c = pos.offset(i, 0, j);
                List<BlockPos> square = List.of(c, c.east(), c.south(), c.south().east());
                if (square.stream().allMatch(q -> level.getBlockState(q).is(sapling.getBlock()))) {
                    square.forEach(q -> level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
                    root = c;
                    break search;
                }
            }
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        get(level).plant(level, root, key.get(), null);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % CHECK_EVERY != 0) return;
        GrowingTrees data = get(level);
        if (data.trees.isEmpty()) return;
        var registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        long now = level.getGameTime();
        List<BlockPos> done = new ArrayList<>();
        boolean changed = false;
        for (Map.Entry<BlockPos, Tree> e : new ArrayList<>(data.trees.entrySet())) {
            BlockPos root = e.getKey();
            Tree t = e.getValue();
            if (t.next > now || !level.isAreaLoaded(root, 12)) continue;
            Optional<Holder.Reference<ConfiguredFeature<?, ?>>> feature = registry.getHolder(t.feature);
            if (feature.isEmpty() || !canGrow(feature.get())) { done.add(root); continue; }
            TreeConfiguration config = (TreeConfiguration) feature.get().value().config();
            for (int n = 0; n < MAX_STEPS_PER_CHECK && t.next <= now; n++) {
                if (!step(level, root, t, config)) { done.add(root); break; }
                t.next += interval(level.random);
            }
            changed = true;
        }
        done.forEach(data.trees::remove);
        if (changed || !done.isEmpty()) data.setDirty();
    }

    /** One growth step. Returns false when the tree is gone or fully grown. */
    private static boolean step(ServerLevel level, BlockPos root, Tree t, TreeConfiguration config) {
        BlockState rootState = level.getBlockState(root);
        if (t.seed != null) {
            if (!(rootState.getBlock() instanceof SeedMoundBlock)) return false;
            if (!comeUp(level, root, t, config)) return false;
            return true;
        }
        if (!(rootState.getBlock() instanceof BoughBlock)) return false;
        BoughTrunkPlacer placer = (BoughTrunkPlacer) config.trunkPlacer;
        OrganicTrunkPlacer.Branches shape = placer.branches();
        Block bough = ModBlocks.boughFor(trunkLog(config));
        RandomSource random = level.random;
        t.age++;

        // Parts cut off or broken stop growing.
        BlockPos top = t.trunk.get(t.trunk.size() - 1);
        if (!(level.getBlockState(top).getBlock() instanceof BoughBlock)) t.trunkAlive = false;
        for (Branch b : t.branches) {
            if (b.alive && !(level.getBlockState(b.tip).getBlock() instanceof BoughBlock)) b.alive = false;
        }

        int height = top.getY() - root.getY() + 1;
        int lowest = Math.max(1, Mth.floor(t.targetHeight * shape.start()));
        List<BlockPos> branchFrom = new ArrayList<>();
        for (BlockPos p : t.trunk) {
            int y = p.getY() - root.getY();
            if (y >= lowest && y < height - 1) branchFrom.add(p);
        }
        List<Branch> growing = new ArrayList<>();
        for (Branch b : t.branches) if (b.alive && b.len < b.target) growing.add(b);

        boolean trunkCan = t.trunkAlive && height < t.targetHeight;
        boolean branchCan = t.branches.stream().filter(b -> !b.forked).count() < t.targetBranches && !branchFrom.isEmpty();
        int wTrunk = trunkCan ? 3 : 0, wBranch = branchCan ? 2 : 0, wExtend = growing.isEmpty() ? 0 : 2, wFill = 1;
        float maturity = maturity(t, shape);
        int oldAge = Math.round(t.age - matureSteps(t, shape));
        if (!trunkCan && !branchCan && growing.isEmpty() && maturity >= 1f
                && (oldAge > 0 && fullGirth(level, root, placer.girth()) || oldAge > 400)) return false;

        int roll = random.nextInt(wTrunk + wBranch + wExtend + wFill);
        if (roll < wTrunk) {
            growTrunk(level, root, t, bough, top, height);
        } else if ((roll -= wTrunk) < wBranch) {
            startBranch(level, root, t, shape, bough, branchFrom, lowest, random);
        } else if ((roll -= wBranch) < wExtend) {
            extendBranch(level, t, shape, bough, growing.get(random.nextInt(growing.size())), random);
        }
        // Every step: the wood thickens with age and the crown fills out.
        thicken(level, root, placer.girth(), maturity, oldAge);
        foliage(level, t, config, maturity, random);
        return true;
    }

    /** How grown the tree is, from 0 to 1, by the number of steps it has taken. */
    private static float maturity(Tree t, OrganicTrunkPlacer.Branches shape) {
        return Math.min(1f, t.age / Math.max(1f, matureSteps(t, shape)));
    }

    /** Roughly how many steps the tree takes to reach its full height and spread. */
    private static float matureSteps(Tree t, OrganicTrunkPlacer.Branches shape) {
        float avgLen = (shape.length().getMinValue() + shape.length().getMaxValue()) / 2f;
        return 1.6f * (t.targetHeight + t.targetBranches * avgLen * (1f + shape.fork() / 2f));
    }

    /** The seed comes up: a one-piece shoot with a few leaves. */
    private static boolean comeUp(ServerLevel level, BlockPos root, Tree t, TreeConfiguration config) {
        Block bough = ModBlocks.boughFor(trunkLog(config));
        BlockState below = level.getBlockState(root.below());
        if (bough == null || below.isAir() || below.canBeReplaced()) return false;
        t.seed = null;
        level.setBlock(root, BoughBlock.state(bough, 1, Direction.DOWN, 8), Block.UPDATE_ALL);
        t.trunk.clear();
        t.trunk.add(root.immutable());
        foliage(level, t, config, 0f, level.random);
        return true;
    }

    private static void growTrunk(ServerLevel level, BlockPos root, Tree t, Block bough, BlockPos top, int height) {
        // A leaning trunk steps sideways once at each of its lean heights, then carries on up.
        // The first piece of a trunk always stands straight: no elbow at the foot.
        boolean lean = t.leanAt.remove(height) && height >= 2;
        BlockPos next = lean ? top.relative(t.leanDir) : top.above();
        if (!TreeFeature.validTreePos(level, next)) { t.trunkAlive = false; return; }
        place(level, bough, next, top);
        t.trunk.add(next.immutable());
    }

    private static void startBranch(ServerLevel level, BlockPos root, Tree t, OrganicTrunkPlacer.Branches shape, Block bough,
                                    List<BlockPos> branchFrom, int lowest, RandomSource random) {
        BlockPos from = branchFrom.get(random.nextInt(branchFrom.size()));
        // The bearing furthest from the other branches, out of a few random tries.
        float bearing = 0f, best = -1f;
        for (int k = 0; k < 4; k++) {
            float b = random.nextFloat() * Mth.TWO_PI, gap = Mth.PI;
            for (Branch o : t.branches) gap = Math.min(gap, Math.abs(Mth.wrapDegrees((b - o.bearing) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD);
            if (gap > best) { best = gap; bearing = b; }
        }
        int y = from.getY() - root.getY();
        float rel = t.targetHeight - 1 > lowest ? (y - lowest) / (float) (t.targetHeight - 1 - lowest) : 0f;
        Branch b = new Branch();
        b.from = from.immutable();
        b.tip = from.immutable();
        b.bearing = bearing;
        b.slope = shape.slope();
        b.target = Math.max(1, Math.round(shape.length().sample(random) * (1f - shape.taper() * rel)));
        t.branches.add(b);
        extendBranch(level, t, shape, bough, b, random);
        if (b.len == 0) t.branches.remove(b);   // no room for it
    }

    private static void extendBranch(ServerLevel level, Tree t, OrganicTrunkPlacer.Branches shape, Block bough, Branch b,
                                     RandomSource random) {
        float reach = b.len + 1.5f;
        double tx = b.from.getX() + 0.5 + Mth.cos(b.bearing) * reach;
        double ty = b.from.getY() + 0.5 + b.slope * reach;
        double tz = b.from.getZ() + 0.5 + Mth.sin(b.bearing) * reach;
        double dx = tx - (b.tip.getX() + 0.5), dy = ty - (b.tip.getY() + 0.5), dz = tz - (b.tip.getZ() + 0.5);
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        // The first piece always leaves the trunk sideways.
        if (b.len == 0) ay = 0;
        Direction d = ax >= ay && ax >= az ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : ay >= az ? (dy > 0 ? Direction.UP : Direction.DOWN)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos next = b.tip.relative(d);
        if (!TreeFeature.validTreePos(level, next)) { b.alive = false; return; }
        place(level, bough, next, b.tip);
        b.tip = next.immutable();
        b.len++;
        // Halfway, a branch may fork once.
        if (!b.forked && b.len == Math.max(2, b.target / 2) && b.target >= 3 && random.nextFloat() < shape.fork()) {
            Branch f = new Branch();
            f.from = b.tip;
            f.tip = b.tip;
            f.bearing = b.bearing + (random.nextBoolean() ? 0.8f : -0.8f);
            f.slope = b.slope * 1.2f;
            f.target = Math.max(2, (b.target - b.len));
            f.forked = true;
            t.branches.add(f);
        }
    }

    private static void place(ServerLevel level, Block bough, BlockPos pos, BlockPos parent) {
        Direction toParent = Direction.getNearest(parent.getX() - pos.getX(), parent.getY() - pos.getY(), parent.getZ() - pos.getZ());
        BlockState ps = level.getBlockState(parent);
        int pr = ps.getBlock() instanceof BoughBlock ? ps.getValue(BoughBlock.RADIUS) : 1;
        level.setBlock(pos, BoughBlock.state(bough, 1, toParent, pr), Block.UPDATE_ALL);
    }

    /** Pipe model over the whole tree as it stands: thicker towards the root, and with age. */
    private static void thicken(ServerLevel level, BlockPos root, BoughTrunkPlacer.Girth girth, float maturity, int oldAge) {
        List<BlockPos> pieces = new ArrayList<>(BoughBlock.descendants(level, root, 4000));   // twigs first
        pieces.add(root);
        Map<BlockPos, Float> area = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        for (BlockPos p : pieces) {
            float a = area.getOrDefault(p, 0f) + 1f + girth.growth();
            area.put(p, a);
            BlockPos par = p.equals(root) ? null : BoughBlock.parentPos(level.getBlockState(p), p);
            parent.put(p, par);
            if (par != null) area.merge(par, a, Float::sum);
        }
        // The upright part of the trunk (up to its first lean) keeps thickening once the tree is
        // grown, a pixel every few steps, most at the foot: big species end up over a block across.
        int column = 0;
        java.util.Set<BlockPos> upright = new HashSet<>();
        for (int i = pieces.size() - 1; i >= 0; i--) {
            BlockPos p = pieces.get(i), par = parent.get(p);
            if (par == null || upright.contains(par) && par.equals(p.below())) {
                upright.add(p);
                column = Math.max(column, p.getY() - root.getY() + 1);
            }
        }
        int extra = Math.min(girth.extraFor(column), Math.max(0, oldAge / STEPS_PER_PIXEL));
        float k = 0.25f + 0.75f * maturity;
        Map<BlockPos, Integer> radius = new HashMap<>();
        for (int i = pieces.size() - 1; i >= 0; i--) {   // root first
            BlockPos p = pieces.get(i);
            int r = Mth.clamp(Math.round((float) Math.sqrt(area.get(p) * k)), 1, girth.maxRadius());
            if (upright.contains(p) && extra > 0) {
                r = Math.min(girth.trunk(), r + girth.taper(extra, p.getY() - root.getY(), column));
                if (r > 8 && !roomFor(level, p)) r = 8;
            } else if (!upright.contains(p)) {
                r = girth.limb(r, extra);
            }
            BlockPos par = parent.get(p);
            if (par != null && radius.containsKey(par)) r = Math.min(r, radius.get(par));
            BlockState s = level.getBlockState(p);
            r = Math.max(r, s.getValue(BoughBlock.RADIUS));   // wood never gets thinner
            radius.put(p, r);
            int pr = par != null && radius.containsKey(par) ? Math.min(8, radius.get(par)) : s.getValue(BoughBlock.PARENT_RADIUS);
            BlockState n = s.setValue(BoughBlock.RADIUS, r).setValue(BoughBlock.PARENT_RADIUS, Math.max(Math.min(8, r), pr));
            if (n != s) level.setBlock(p, n, Block.UPDATE_CLIENTS);
        }
    }

    /** True when the ring around an upright trunk piece is free for its shells. */
    private static boolean roomFor(ServerLevel level, BlockPos p) {
        for (BlockPos q : BoughBlock.ring(p, Direction.Axis.Y)) if (!BoughBlock.shellFits(level, q, p)) return false;
        return true;
    }

    /** True when the foot of the trunk has reached the species' full girth. */
    private static boolean fullGirth(ServerLevel level, BlockPos root, BoughTrunkPlacer.Girth girth) {
        BlockState s = level.getBlockState(root);
        int column = 1;
        while (column < 64 && level.getBlockState(root.above(column)).getBlock() instanceof BoughBlock) column++;
        return !(s.getBlock() instanceof BoughBlock) || s.getValue(BoughBlock.RADIUS) >= girth.maxRadius() + girth.extraFor(column)
                || !roomFor(level, root);
    }

    /** Leaves on the tips, as a small tuft on a shoot and as crowns that widen as the tree matures. */
    private static void foliage(ServerLevel level, Tree t, TreeConfiguration config, float maturity, RandomSource unused) {
        // Work out the crown the tree should have now (the same tip always gives the same tuft), then
        // add the leaves it lacks and take away those it has outgrown: a crown follows its branches.
        Map<BlockPos, BlockState> want = new LinkedHashMap<>();
        FoliagePlacer.FoliageSetter setter = new FoliagePlacer.FoliageSetter() {
            @Override
            public void set(BlockPos pos, BlockState state) {
                BlockState there = level.getBlockState(pos);
                if (there.isAir() || there.canBeReplaced() || t.leaves.contains(pos.asLong()) && there.getBlock() instanceof LeavesBlock) {
                    want.putIfAbsent(pos.immutable(), state);
                }
            }

            @Override
            public boolean isSet(BlockPos pos) {
                return want.containsKey(pos);
            }
        };
        BlockPos top = t.trunk.get(t.trunk.size() - 1);
        if (t.trunk.size() <= 2 && t.branches.isEmpty()) {
            RandomSource r = RandomSource.create(t.crownSeed);
            setter.set(top.above(), config.foliageProvider.getState(r, top.above()));
            Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            if (t.trunk.size() == 2) setter.set(top.relative(side), config.foliageProvider.getState(r, top.relative(side)));
        } else {
            List<BlockPos> tips = new ArrayList<>();
            if (level.getBlockState(top).getBlock() instanceof BoughBlock) tips.add(top.above());
            for (Branch b : t.branches) if (b.len > 0 && level.getBlockState(b.tip).getBlock() instanceof BoughBlock) tips.add(b.tip);
            int offset = maturity < 0.25f ? -2 : maturity < 0.6f ? -1 : 0;
            int height = t.trunk.size();
            RandomSource sizes = RandomSource.create(t.crownSeed ^ (offset * 31L + height));
            int fh = Math.max(1, Math.round(config.foliagePlacer.foliageHeight(sizes, height, config) * (0.3f + 0.7f * maturity)));
            int fr = config.foliagePlacer.foliageRadius(sizes, height - fh);
            for (BlockPos tip : tips) {
                RandomSource r = RandomSource.create(t.crownSeed ^ tip.asLong() * 0x9E3779B97F4A7C15L ^ offset);
                config.foliagePlacer.createFoliage(level, setter, r, config, height,
                        new FoliagePlacer.FoliageAttachment(tip, offset, false), fh, fr);
            }
        }

        Set<Long> keep = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> e : want.entrySet()) keep.add(e.getKey().asLong());
        for (long old : t.leaves) {
            if (keep.contains(old)) continue;
            BlockPos p = BlockPos.of(old);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (Map.Entry<BlockPos, BlockState> e : want.entrySet()) {
            BlockPos p = e.getKey();
            if (level.getBlockState(p).getBlock() instanceof LeavesBlock) continue;
            level.setBlock(p, e.getValue(), Block.UPDATE_ALL);
            // New leaves work out their distance to the wood, so they don't decay.
            level.scheduleTick(p, e.getValue().getBlock(), 1);
        }
        t.leaves = keep;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        trees.forEach((pos, t) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", pos.asLong());
            c.putString("feature", t.feature.location().toString());
            if (t.seed != null) c.putString("seed", t.seed.toString());
            c.putLong("next", t.next);
            c.putInt("age", t.age);
            c.putInt("target_height", t.targetHeight);
            c.putInt("target_branches", t.targetBranches);
            c.putInt("lean_dir", t.leanDir.get3DDataValue());
            c.putIntArray("lean_at", t.leanAt.stream().mapToInt(Integer::intValue).toArray());
            c.putLongArray("trunk", t.trunk.stream().mapToLong(BlockPos::asLong).toArray());
            c.putBoolean("trunk_alive", t.trunkAlive);
            c.putLong("crown_seed", t.crownSeed);
            c.putLongArray("leaves", t.leaves.stream().mapToLong(Long::longValue).toArray());
            ListTag bl = new ListTag();
            for (Branch b : t.branches) {
                CompoundTag bc = new CompoundTag();
                bc.putLong("from", b.from.asLong());
                bc.putLong("tip", b.tip.asLong());
                bc.putFloat("bearing", b.bearing);
                bc.putFloat("slope", b.slope);
                bc.putInt("len", b.len);
                bc.putInt("target", b.target);
                bc.putBoolean("forked", b.forked);
                bc.putBoolean("alive", b.alive);
                bl.add(bc);
            }
            c.put("branches", bl);
            list.add(c);
        });
        tag.put("trees", list);
        return tag;
    }

    private static GrowingTrees load(CompoundTag tag, HolderLookup.Provider registries) {
        GrowingTrees data = new GrowingTrees();
        for (Tag e : tag.getList("trees", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            ResourceLocation id = ResourceLocation.tryParse(c.getString("feature"));
            if (id == null) continue;
            Tree t = new Tree();
            t.feature = ResourceKey.create(Registries.CONFIGURED_FEATURE, id);
            t.seed = c.contains("seed") ? ResourceLocation.tryParse(c.getString("seed")) : null;
            t.next = c.getLong("next");
            t.age = c.getInt("age");
            t.targetHeight = c.getInt("target_height");
            t.targetBranches = c.getInt("target_branches");
            t.leanDir = Direction.from3DDataValue(c.getInt("lean_dir"));
            for (int y : c.getIntArray("lean_at")) t.leanAt.add(y);
            for (long p : c.getLongArray("trunk")) t.trunk.add(BlockPos.of(p));
            t.trunkAlive = c.getBoolean("trunk_alive");
            t.crownSeed = c.contains("crown_seed") ? c.getLong("crown_seed") : c.getLong("pos");
            for (long l : c.getLongArray("leaves")) t.leaves.add(l);
            for (Tag be : c.getList("branches", Tag.TAG_COMPOUND)) {
                CompoundTag bc = (CompoundTag) be;
                Branch b = new Branch();
                b.from = BlockPos.of(bc.getLong("from"));
                b.tip = BlockPos.of(bc.getLong("tip"));
                b.bearing = bc.getFloat("bearing");
                b.slope = bc.getFloat("slope");
                b.len = bc.getInt("len");
                b.target = bc.getInt("target");
                b.forked = bc.getBoolean("forked");
                b.alive = bc.getBoolean("alive");
                t.branches.add(b);
            }
            if (t.seed == null && t.trunk.isEmpty()) continue;
            data.trees.put(BlockPos.of(c.getLong("pos")), t);
        }
        return data;
    }
}
