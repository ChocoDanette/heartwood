package com.chocodanette.heartwood.species;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

/**
 * An irregular, slightly flattened ball of leaves around each attachment (branch end or
 * trunk top). The outer shell is thinned at random so the crowns look ragged, not round.
 */
public class ClusterFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<ClusterFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(i ->
            foliagePlacerParts(i).and(i.group(
                    Codec.intRange(1, 8).fieldOf("height").forGetter(p -> p.height),
                    Codec.floatRange(0f, 1f).fieldOf("irregularity").forGetter(p -> p.irregularity)
            )).apply(i, ClusterFoliagePlacer::new));

    private final int height;
    private final float irregularity;

    public ClusterFoliagePlacer(IntProvider radius, IntProvider offset, int height, float irregularity) {
        super(radius, offset);
        this.height = height;
        this.irregularity = irregularity;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return TreeShapes.CLUSTER_FOLIAGE.get();
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliageSetter setter, RandomSource random, TreeConfiguration config,
                                 int maxFreeTreeHeight, FoliageAttachment attachment, int foliageHeight, int radius, int offset) {
        BlockPos centre = attachment.pos().above(offset);
        float r = Math.max(1, radius) + 0.5f;
        float up = foliageHeight / 2f + 0.5f, down = Math.max(1f, foliageHeight / 2f - 0.5f);
        int ri = Math.max(1, radius);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -Math.round(down); dy <= Math.round(up); dy++) {
            float vy = dy >= 0 ? dy / up : dy / down;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    float d = (dx * dx + dz * dz) / (r * r) + vy * vy;
                    if (d > 1f) continue;
                    if (d > 0.55f && random.nextFloat() < irregularity * (d - 0.4f) * 1.6f) continue;
                    p.setWithOffset(centre, dx, dy, dz);
                    tryPlaceLeaf(level, setter, random, config, p);
                }
            }
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return height;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int x, int y, int z, int range, boolean large) {
        return false;
    }
}
