package com.chocodanette.heartwood.tree;

import com.chocodanette.heartwood.ModBlocks;
import com.chocodanette.heartwood.species.OrganicTrunkPlacer;
import com.chocodanette.heartwood.species.TreeShapes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Grows a tree out of variable-thickness boughs. The skeleton (leaning trunk, branches at their
 * own heights and bearings, forks) is built as a graph of face-adjacent blocks, each knowing its
 * parent; thickness is then worked out from the tips down, Dynamic Trees fashion: the cross
 * section of a piece is the sum of its children's plus a little growth (pipe model).
 */
public class BoughTrunkPlacer extends TrunkPlacer {
    /** Thickness: growth added per piece (in squared pixels) and the largest radius (8 = full block). */
    public record Girth(float growth, int maxRadius) {
        public static final Codec<Girth> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 8f).fieldOf("growth").forGetter(Girth::growth),
                Codec.intRange(1, 8).fieldOf("max_radius").forGetter(Girth::maxRadius)
        ).apply(i, Girth::new));
    }

    public static final MapCodec<BoughTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i ->
            trunkPlacerParts(i).and(i.group(
                    OrganicTrunkPlacer.Branches.CODEC.fieldOf("branches").forGetter(p -> p.branches),
                    Girth.CODEC.fieldOf("girth").forGetter(p -> p.girth)
            )).apply(i, BoughTrunkPlacer::new));

    /**
     * Age of the tree being grown on this thread, from 0 (a shoot) to 1 (mature); unset for a
     * mature tree (world generation). See {@link GrowingTrees}.
     */
    public static final ThreadLocal<Float> AGE = new ThreadLocal<>();

    private final OrganicTrunkPlacer.Branches branches;
    private final Girth girth;

    public BoughTrunkPlacer(int baseHeight, int randA, int randB, OrganicTrunkPlacer.Branches branches, Girth girth) {
        super(baseHeight, randA, randB);
        this.branches = branches;
        this.girth = girth;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TreeShapes.BOUGH_TRUNK.get();
    }

    /** The tree skeleton: each position mapped to its parent (null for the root). Insertion order = parents first. */
    private static final class Skeleton {
        final Map<BlockPos, BlockPos> parent = new LinkedHashMap<>();

        /** Extends from an existing piece towards a target with face-adjacent steps; returns the last piece. */
        BlockPos grow(BlockPos from, double tx, double ty, double tz) {
            BlockPos cur = from;
            for (int guard = 0; guard < 64; guard++) {
                double dx = tx - (cur.getX() + 0.5), dy = ty - (cur.getY() + 0.5), dz = tz - (cur.getZ() + 0.5);
                double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
                if (ax < 0.5 && ay < 0.5 && az < 0.5) break;
                Direction d = ax >= ay && ax >= az ? (dx > 0 ? Direction.EAST : Direction.WEST)
                        : ay >= az ? (dy > 0 ? Direction.UP : Direction.DOWN)
                        : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                BlockPos next = cur.relative(d);
                if (!parent.containsKey(next)) parent.put(next, cur);
                cur = next;
            }
            return cur;
        }
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> setter,
                                                            RandomSource random, int height, BlockPos origin, TreeConfiguration config) {
        setDirtAt(level, setter, random, origin.below(), config);
        Skeleton sk = new Skeleton();
        sk.parent.put(origin, null);
        List<FoliagePlacer.FoliageAttachment> crowns = new ArrayList<>();

        // Trunk: straight up, stepping sideways at a few heights when it leans.
        Direction leanDir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int leans = branches.lean() > 0 ? random.nextInt(branches.lean() + 1) : 0;
        List<Integer> leanAt = new ArrayList<>();
        for (int k = 0; k < leans; k++) leanAt.add(height / 3 + random.nextInt(Math.max(1, height * 2 / 3 - 1)));
        BlockPos cur = origin;
        List<BlockPos> trunk = new ArrayList<>();
        trunk.add(cur);
        for (int y = 1; y < height; y++) {
            if (leanAt.contains(y)) {
                BlockPos side = cur.relative(leanDir);
                sk.parent.putIfAbsent(side, cur);
                cur = side;
                trunk.add(cur);
            }
            BlockPos up = cur.above();
            sk.parent.putIfAbsent(up, cur);
            cur = up;
            trunk.add(cur);
        }
        crowns.add(new FoliagePlacer.FoliageAttachment(cur.above(), 0, false));

        // Branches spread around the trunk, rising at a slope, some forking.
        int n = branches.count().sample(random);
        float bearing0 = random.nextFloat() * Mth.TWO_PI;
        int lowest = Math.max(1, Mth.floor(height * branches.start()));
        for (int k = 0; k < n; k++) {
            int y = lowest + (height - 1 > lowest ? random.nextInt(height - 1 - lowest) : 0);
            BlockPos from = trunkAt(trunk, origin.getY() + y);
            float bearing = bearing0 + k * Mth.TWO_PI / n + (random.nextFloat() - 0.5f) * 0.9f;
            float rel = height - 1 > lowest ? (y - lowest) / (float) (height - 1 - lowest) : 0f;
            int len = Math.max(1, Math.round(branches.length().sample(random) * (1f - branches.taper() * rel)));
            BlockPos end = sk.grow(from, from.getX() + 0.5 + Mth.cos(bearing) * len,
                    from.getY() + 0.5 + branches.slope() * len, from.getZ() + 0.5 + Mth.sin(bearing) * len);
            crowns.add(new FoliagePlacer.FoliageAttachment(end, 0, false));
            if (len >= 3 && random.nextFloat() < branches.fork()) {
                // Fork from a point halfway along the branch.
                BlockPos mid = nearest(sk, from.getX() + 0.5 + Mth.cos(bearing) * len / 2.0,
                        from.getY() + 0.5 + branches.slope() * len / 2.0, from.getZ() + 0.5 + Mth.sin(bearing) * len / 2.0);
                float b2 = bearing + (random.nextBoolean() ? 0.8f : -0.8f);
                int l2 = Math.max(2, len / 2);
                BlockPos forkEnd = sk.grow(mid, mid.getX() + 0.5 + Mth.cos(b2) * l2,
                        mid.getY() + 0.5 + branches.slope() * 1.2 * l2, mid.getZ() + 0.5 + Mth.sin(b2) * l2);
                crowns.add(new FoliagePlacer.FoliageAttachment(forkEnd, -1, false));
            }
        }

        Float age = AGE.get();
        if (age == null || age >= 1f) {
            place(level, setter, random, origin, config, sk, null);
            return crowns;
        }

        // A young tree: only the part of the mature skeleton nearest the root, grown out from it,
        // with smaller crowns on the tips it has reached so far.
        Map<BlockPos, Integer> depth = new HashMap<>();
        int maxDepth = 0;
        for (Map.Entry<BlockPos, BlockPos> e : sk.parent.entrySet()) {
            int d = e.getValue() == null ? 0 : depth.get(e.getValue()) + 1;
            depth.put(e.getKey(), d);
            maxDepth = Math.max(maxDepth, d);
        }
        int reach = Math.max(1, Mth.ceil(age * maxDepth));
        java.util.Set<BlockPos> grown = new java.util.HashSet<>();
        for (Map.Entry<BlockPos, Integer> e : depth.entrySet()) if (e.getValue() <= reach) grown.add(e.getKey());
        java.util.Set<BlockPos> placed = place(level, setter, random, origin, config, sk, grown);

        java.util.Set<BlockPos> carrying = new java.util.HashSet<>();
        for (BlockPos p : placed) {
            BlockPos par = sk.parent.get(p);
            if (par != null) carrying.add(par);
        }
        int offset = age < 0.35f ? -2 : age < 0.7f ? -1 : 0;
        List<FoliagePlacer.FoliageAttachment> tips = new ArrayList<>();
        for (BlockPos p : placed) if (!carrying.contains(p)) tips.add(new FoliagePlacer.FoliageAttachment(p, offset, false));
        return tips;
    }

    private static BlockPos trunkAt(List<BlockPos> trunk, int y) {
        BlockPos best = trunk.get(0);
        for (BlockPos p : trunk) if (p.getY() == y) best = p;
        return best;
    }

    private static BlockPos nearest(Skeleton sk, double x, double y, double z) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : sk.parent.keySet()) {
            double d = p.distToCenterSqr(x, y, z);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /**
     * Works out thickness from the tips down, then places every piece (or only those in {@code only},
     * for a young tree). Returns the pieces placed.
     */
    private java.util.Set<BlockPos> place(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> setter, RandomSource random,
                                          BlockPos origin, TreeConfiguration config, Skeleton sk, java.util.Set<BlockPos> only) {
        List<BlockPos> order = new ArrayList<>(sk.parent.keySet());
        if (only != null) order.removeIf(p -> !only.contains(p));
        Map<BlockPos, Float> area = new HashMap<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            BlockPos p = order.get(i);
            float a = area.getOrDefault(p, 0f) + girth.growth() + (area.containsKey(p) ? 0f : 1f);
            area.put(p, a);
            BlockPos par = sk.parent.get(p);
            if (par != null) area.merge(par, a, Float::sum);
        }
        Map<BlockPos, Integer> radius = new HashMap<>();
        for (BlockPos p : order) {
            int r = Mth.clamp(Math.round((float) Math.sqrt(area.get(p))), 1, girth.maxRadius());
            BlockPos par = sk.parent.get(p);
            if (par != null) r = Math.min(r, radius.get(par));   // never thicker than the parent
            radius.put(p, r);
        }

        BlockState logState = config.trunkProvider.getState(random, origin);
        Block bough = ModBlocks.boughFor(logState.getBlock());
        // A piece blocked by something (often a neighbouring tree) is left out with all it carries,
        // so no branch ever hangs from another tree.
        java.util.Set<BlockPos> placed = new java.util.HashSet<>();
        for (BlockPos p : order) {
            BlockPos par = sk.parent.get(p);
            if (par != null && !placed.contains(par)) continue;
            // A growing tree may replace its own younger, thinner pieces.
            boolean own = only != null && bough != null && level.isStateAtPosition(p, st -> st.is(bough));
            if (!own && !TreeFeature.validTreePos(level, p)) continue;
            placed.add(p);
            Direction toParent = par == null ? Direction.DOWN : Direction.getNearest(
                    par.getX() - p.getX(), par.getY() - p.getY(), par.getZ() - p.getZ());
            int r = radius.get(p);
            int pr = par == null ? 8 : radius.get(par);
            if (bough != null) {
                setter.accept(p, BoughBlock.state(bough, r, toParent, pr));
            } else {
                setter.accept(p, logState.hasProperty(RotatedPillarBlock.AXIS)
                        ? logState.setValue(RotatedPillarBlock.AXIS, toParent.getAxis()) : logState);
            }
        }
        return placed;
    }
}
