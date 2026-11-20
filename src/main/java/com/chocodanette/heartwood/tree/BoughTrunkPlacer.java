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
    /**
     * Thickness: growth added per piece (in squared pixels), the largest radius of the pipe model
     * (8 = full block), and the radius an old trunk can reach at its foot (up to 16, two blocks across).
     */
    public record Girth(float growth, int maxRadius, int trunkRadius) {
        public static final Codec<Girth> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 8f).fieldOf("growth").forGetter(Girth::growth),
                Codec.intRange(1, 8).fieldOf("max_radius").forGetter(Girth::maxRadius),
                Codec.intRange(0, BoughBlock.MAX_RADIUS).optionalFieldOf("trunk_radius", 0).forGetter(Girth::trunkRadius)
        ).apply(i, Girth::new));

        /** The thickest the trunk gets. */
        public int trunk() {
            return Math.max(maxRadius, trunkRadius);
        }

        /**
         * How much an old trunk can thicken beyond the pipe model, by its height: a short tree
         * never gets a big foot; the full girth needs a trunk about 14 blocks tall.
         */
        public int extraFor(int trunkHeight) {
            float tall = Mth.clamp((trunkHeight - 4) / 10f, 0f, 1f);
            return Math.round((trunk() - maxRadius) * tall);
        }

        /** The extra thickness at height y of a trunk column: most at the foot, none at the top. */
        public int taper(int extra, int y, int column) {
            return Math.round(extra * Math.max(0f, 1f - y / (float) Math.max(1, column)));
        }

        /** The big limbs of an old tree thicken a little too (never past a block). */
        public int limb(int r, int extra) {
            return r >= 3 ? Math.min(8, r + extra / 4) : r;
        }
    }

    public static final MapCodec<BoughTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i ->
            trunkPlacerParts(i).and(i.group(
                    OrganicTrunkPlacer.Branches.CODEC.fieldOf("branches").forGetter(p -> p.branches),
                    Girth.CODEC.fieldOf("girth").forGetter(p -> p.girth)
            )).apply(i, BoughTrunkPlacer::new));

    private final OrganicTrunkPlacer.Branches branches;
    private final Girth girth;

    public BoughTrunkPlacer(int baseHeight, int randA, int randB, OrganicTrunkPlacer.Branches branches, Girth girth) {
        super(baseHeight, randA, randB);
        this.branches = branches;
        this.girth = girth;
    }

    public OrganicTrunkPlacer.Branches branches() { return branches; }

    public Girth girth() { return girth; }

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
        // Never at the foot: the first piece of a trunk always stands straight.
        for (int k = 0; k < leans; k++) leanAt.add(Math.max(2, height / 3 + random.nextInt(Math.max(1, height * 2 / 3 - 1))));
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

        place(level, setter, random, origin, config, sk);
        return crowns;
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

    /** Works out thickness from the tips down, then places every piece. */
    private void place(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> setter, RandomSource random,
                       BlockPos origin, TreeConfiguration config, Skeleton sk) {
        List<BlockPos> order = new ArrayList<>(sk.parent.keySet());
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

        // Old trees: the upright foot of the trunk has kept thickening, more for big species.
        if (girth.trunk() > girth.maxRadius()) {
            int column = 0;
            java.util.Set<BlockPos> upright = new java.util.HashSet<>();
            for (BlockPos p : order) {
                BlockPos par = sk.parent.get(p);
                if (par == null || upright.contains(par) && par.equals(p.below())) {
                    upright.add(p);
                    column = Math.max(column, p.getY() - origin.getY() + 1);
                }
            }
            int extra = random.nextInt(girth.extraFor(column) + 1);
            for (BlockPos p : order) {
                int r;
                if (upright.contains(p)) {
                    r = Math.min(girth.trunk(), radius.get(p) + girth.taper(extra, p.getY() - origin.getY(), column));
                    if (r > 8) {
                        for (BlockPos q : BoughBlock.ring(p, Direction.Axis.Y)) {
                            if (!sk.parent.containsKey(q) && !TreeFeature.validTreePos(level, q)) { r = 8; break; }
                        }
                    }
                } else {
                    r = girth.limb(radius.get(p), extra);
                }
                BlockPos par = sk.parent.get(p);
                if (par != null) r = Math.min(r, radius.get(par));
                radius.put(p, r);
            }
        }

        BlockState logState = config.trunkProvider.getState(random, origin);
        Block bough = ModBlocks.boughFor(logState.getBlock());
        // A piece blocked by something (often a neighbouring tree) is left out with all it carries,
        // so no branch ever hangs from another tree.
        java.util.Set<BlockPos> placed = new java.util.HashSet<>();
        for (BlockPos p : order) {
            BlockPos par = sk.parent.get(p);
            if (par != null && !placed.contains(par)) continue;
            if (!TreeFeature.validTreePos(level, p)) continue;
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
        // The shells around trunk pieces over a block thick.
        if (bough != null) {
            for (BlockPos p : placed) {
                if (radius.get(p) <= 8) continue;
                for (BlockPos q : BoughBlock.ring(p, Direction.Axis.Y)) {
                    if (!sk.parent.containsKey(q) && TreeFeature.validTreePos(level, q)) setter.accept(q, TrunkShellBlock.pointingTo(q, p));
                }
            }
        }
    }
}
