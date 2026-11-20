package com.chocodanette.heartwood.tree;

import com.chocodanette.heartwood.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Trees that grow over time. When a sapling grows, it becomes a shoot instead of a full tree; the
 * shoot then grows, stage after stage, into the mature tree its seed would have given, which it
 * reaches after {@link Config#growthDays()} in-game days. Trees from world generation are mature.
 * Growth follows the world clock, so a tree in an unloaded chunk catches up when it is loaded again.
 */
public final class GrowingTrees extends SavedData {
    /** Number of growth stages between the shoot (stage 1) and the mature tree. */
    public static final int STAGES = 20;
    private static final int CHECK_EVERY = 100;
    private static final String NAME = "heartwood_growing_trees";

    private record Tree(ResourceKey<ConfiguredFeature<?, ?>> feature, long seed, long planted, int stage) {}

    private final Map<BlockPos, Tree> trees = new LinkedHashMap<>();

    public static GrowingTrees get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GrowingTrees::new, GrowingTrees::load, null), NAME);
    }

    /** Stops the growth of the tree rooted at root (it was felled or cut down). */
    public void forget(BlockPos root) {
        if (trees.remove(root) != null) setDirty();
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

    /** A growing sapling becomes a shoot that will grow into its tree, instead of a full tree at once. */
    @SubscribeEvent
    public static void onSaplingGrow(BlockGrowFeatureEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Holder<ConfiguredFeature<?, ?>> feature = event.getFeature();
        if (feature == null || !(feature.value().config() instanceof TreeConfiguration config)) return;
        if (!(config.trunkPlacer instanceof BoughTrunkPlacer)) return;
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> key = feature.unwrapKey();
        if (key.isEmpty()) return;
        event.setCanceled(true);

        BlockPos pos = event.getPos();
        BlockState sapling = level.getBlockState(pos);
        BlockPos root = pos;
        // Four saplings in a square (dark oak) grow into one tree from their corner.
        for (int i = 0; i >= -1 && root == pos; i--) {
            for (int j = 0; j >= -1; j--) {
                BlockPos c = pos.offset(i, 0, j);
                if (same(level, c, sapling) && same(level, c.east(), sapling) && same(level, c.south(), sapling)
                        && same(level, c.south().east(), sapling)) {
                    for (BlockPos q : List.of(c, c.east(), c.south(), c.south().east())) {
                        level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_INVISIBLE);
                    }
                    root = c;
                    break;
                }
            }
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_INVISIBLE);
        long seed = event.getRandom().nextLong();
        grow(level, root, config, seed, 1);
        if (!(level.getBlockState(root).getBlock() instanceof BoughBlock)) {
            level.setBlock(pos, sapling, Block.UPDATE_ALL);   // no room: the sapling stays
            return;
        }
        GrowingTrees data = get(level);
        data.trees.put(root, new Tree(key.get(), seed, level.getGameTime(), 1));
        data.setDirty();
    }

    private static boolean same(ServerLevel level, BlockPos p, BlockState sapling) {
        return level.getBlockState(p).is(sapling.getBlock());
    }

    /** Every few seconds, trees whose next stage is due grow a step (or several, after a long absence). */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % CHECK_EVERY != 0) return;
        GrowingTrees data = get(level);
        if (data.trees.isEmpty()) return;
        long stageTicks = Math.max(1L, (long) (Config.growthDays() * 24000.0 / (STAGES - 1)));
        var registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        Map<BlockPos, Tree> updates = new LinkedHashMap<>();
        Set<BlockPos> done = new HashSet<>();
        for (Iterator<Map.Entry<BlockPos, Tree>> it = data.trees.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Tree> e = it.next();
            BlockPos root = e.getKey();
            Tree t = e.getValue();
            if (!level.isAreaLoaded(root, 12)) continue;
            if (!(level.getBlockState(root).getBlock() instanceof BoughBlock)) { done.add(root); continue; }
            int stage = (int) Math.min(STAGES, 1 + (level.getGameTime() - t.planted()) / stageTicks);
            if (stage <= t.stage()) continue;
            Optional<Holder.Reference<ConfiguredFeature<?, ?>>> feature = registry.getHolder(t.feature());
            if (feature.isEmpty() || !(feature.get().value().config() instanceof TreeConfiguration config)) {
                done.add(root);
                continue;
            }
            grow(level, root, config, t.seed(), stage);
            if (stage >= STAGES) done.add(root);
            else updates.put(root, new Tree(t.feature(), t.seed(), t.planted(), stage));
        }
        if (updates.isEmpty() && done.isEmpty()) return;
        data.trees.putAll(updates);
        done.forEach(data.trees::remove);
        data.setDirty();
    }

    /**
     * Places the tree grown from seed at the given stage. The same seed always gives the same
     * mature skeleton, so each stage extends and thickens the previous one.
     */
    static void grow(ServerLevel level, BlockPos root, TreeConfiguration config, long seed, int stage) {
        RandomSource random = RandomSource.create(seed);
        int height = config.trunkPlacer.getTreeHeight(random);
        int foliageHeight = config.foliagePlacer.foliageHeight(random, height, config);
        int foliageRadius = config.foliagePlacer.foliageRadius(random, height - foliageHeight);
        Set<BlockPos> leaves = new HashSet<>();
        FoliagePlacer.FoliageSetter foliage = new FoliagePlacer.FoliageSetter() {
            @Override
            public void set(BlockPos pos, BlockState state) {
                if (TreeFeature.validTreePos(level, pos)) {
                    level.setBlock(pos, state, Block.UPDATE_ALL);
                    leaves.add(pos.immutable());
                }
            }

            @Override
            public boolean isSet(BlockPos pos) {
                return leaves.contains(pos);
            }
        };
        BoughTrunkPlacer.AGE.set(stage >= STAGES ? 1f : stage / (float) STAGES);
        try {
            List<FoliagePlacer.FoliageAttachment> crowns = config.trunkPlacer.placeTrunk(level,
                    (p, s) -> level.setBlock(p, s, Block.UPDATE_ALL), random, height, root, config);
            for (FoliagePlacer.FoliageAttachment crown : crowns) {
                config.foliagePlacer.createFoliage(level, foliage, random, config, height, crown, foliageHeight, foliageRadius);
            }
        } finally {
            BoughTrunkPlacer.AGE.remove();
        }
        // New leaves work out their distance to the wood, so they don't decay.
        for (BlockPos p : leaves) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof LeavesBlock) level.scheduleTick(p, s.getBlock(), 1);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        trees.forEach((pos, t) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", pos.asLong());
            c.putString("feature", t.feature().location().toString());
            c.putLong("seed", t.seed());
            c.putLong("planted", t.planted());
            c.putInt("stage", t.stage());
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
            data.trees.put(BlockPos.of(c.getLong("pos")), new Tree(ResourceKey.create(Registries.CONFIGURED_FEATURE, id),
                    c.getLong("seed"), c.getLong("planted"), c.getInt("stage")));
        }
        return data;
    }
}
