package com.chocodanette.heartwood.species;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A trunk that may lean a little, with real branches: each branch leaves the trunk at its own
 * height and bearing, rises at a given slope, may fork once, and carries a cluster of leaves
 * at its end. The top of the trunk carries one too.
 */
public class OrganicTrunkPlacer extends TrunkPlacer {
    /**
     * Branching parameters. start is the fraction of the height where branches begin; slope is
     * rise per block; fork is the chance a branch splits; taper shortens higher branches (conifers).
     */
    public record Branches(int lean, IntProvider count, IntProvider length, float start, float slope, float fork, float taper) {
        public static final Codec<Branches> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 4).optionalFieldOf("lean", 0).forGetter(Branches::lean),
                IntProvider.codec(0, 12).fieldOf("count").forGetter(Branches::count),
                IntProvider.codec(1, 10).fieldOf("length").forGetter(Branches::length),
                Codec.floatRange(0f, 1f).fieldOf("start").forGetter(Branches::start),
                Codec.floatRange(0f, 3f).fieldOf("slope").forGetter(Branches::slope),
                Codec.floatRange(0f, 1f).optionalFieldOf("fork", 0.3f).forGetter(Branches::fork),
                Codec.floatRange(0f, 1f).optionalFieldOf("taper", 0f).forGetter(Branches::taper)
        ).apply(i, Branches::new));
    }

    public static final MapCodec<OrganicTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i ->
            trunkPlacerParts(i).and(Branches.CODEC.fieldOf("branches").forGetter(p -> p.branches))
                    .apply(i, OrganicTrunkPlacer::new));

    private final Branches branches;

    public OrganicTrunkPlacer(int baseHeight, int randA, int randB, Branches branches) {
        super(baseHeight, randA, randB);
        this.branches = branches;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TreeShapes.ORGANIC_TRUNK.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> setter,
                                                            RandomSource random, int height, BlockPos origin, TreeConfiguration config) {
        setDirtAt(level, setter, random, origin.below(), config);
        List<FoliagePlacer.FoliageAttachment> crowns = new ArrayList<>();

        // Trunk, leaning one block sideways at a few heights in its upper part.
        Direction leanDir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int leans = branches.lean() > 0 ? random.nextInt(branches.lean() + 1) : 0;
        List<Integer> leanAt = new ArrayList<>();
        for (int k = 0; k < leans; k++) leanAt.add(height / 3 + random.nextInt(Math.max(1, height * 2 / 3 - 1)));
        BlockPos.MutableBlockPos cur = origin.mutable();
        List<BlockPos> trunk = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            placeLog(level, setter, random, cur, config, s -> axis(s, Direction.Axis.Y));
            trunk.add(cur.immutable());
            // A lean is a diagonal step: the next block sits one block over, touching by an edge.
            if (leanAt.contains(y) && y > 0) cur.move(leanDir);
            cur.move(Direction.UP);
        }
        BlockPos top = cur.immutable();
        crowns.add(new FoliagePlacer.FoliageAttachment(top, 0, false));

        // Branches, spread around the trunk.
        int n = branches.count().sample(random);
        float bearing0 = random.nextFloat() * Mth.TWO_PI;
        int lowest = Math.max(1, Mth.floor(height * branches.start()));
        for (int k = 0; k < n; k++) {
            int y = lowest + (height - 1 > lowest ? random.nextInt(height - 1 - lowest) : 0);
            BlockPos from = trunkAt(trunk, origin.getY() + y);
            float bearing = bearing0 + k * Mth.TWO_PI / n + (random.nextFloat() - 0.5f) * 0.9f;
            float rel = height - 1 > lowest ? (y - lowest) / (float) (height - 1 - lowest) : 0f;
            int len = Math.max(1, Math.round(branches.length().sample(random) * (1f - branches.taper() * rel)));
            BlockPos end = branch(level, setter, random, config, from, bearing, branches.slope(), len);
            crowns.add(new FoliagePlacer.FoliageAttachment(end, 0, false));
            if (len >= 3 && random.nextFloat() < branches.fork()) {
                BlockPos mid = branchPoint(from, bearing, branches.slope(), len / 2);
                float side = random.nextBoolean() ? 0.7f : -0.7f;
                BlockPos forkEnd = branch(level, setter, random, config, mid, bearing + side,
                        branches.slope() * 1.2f, Math.max(2, len / 2));
                crowns.add(new FoliagePlacer.FoliageAttachment(forkEnd, -1, false));
            }
        }
        return crowns;
    }

    private static BlockPos trunkAt(List<BlockPos> trunk, int y) {
        BlockPos best = trunk.get(0);
        for (BlockPos p : trunk) if (p.getY() == y) best = p;
        return best;
    }

    private static BlockPos branchPoint(BlockPos from, float bearing, float slope, int t) {
        return BlockPos.containing(from.getX() + 0.5 + Mth.cos(bearing) * t, from.getY() + 0.5 + slope * t,
                from.getZ() + 0.5 + Mth.sin(bearing) * t);
    }

    /** Draws a branch as a line of logs; returns its last block. */
    private BlockPos branch(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> setter, RandomSource random,
                            TreeConfiguration config, BlockPos from, float bearing, float slope, int len) {
        float dx = Mth.cos(bearing), dz = Mth.sin(bearing);
        Direction.Axis axis = slope > 1.0f ? Direction.Axis.Y : Math.abs(dx) > Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
        BlockPos last = from;
        for (int t = 1; t <= len; t++) {
            BlockPos p = branchPoint(from, bearing, slope, t);
            if (p.equals(last)) continue;
            if (!placeLog(level, setter, random, p, config, s -> axis(s, axis))) break;
            last = p;
        }
        return last;
    }

    private static BlockState axis(BlockState s, Direction.Axis axis) {
        return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, axis) : s;
    }
}
