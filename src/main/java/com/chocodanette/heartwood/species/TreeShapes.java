package com.chocodanette.heartwood.species;

import com.chocodanette.heartwood.Heartwood;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers Heartwood's tree shape generators (usable from worldgen JSON). */
public final class TreeShapes {
    public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACERS =
            DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, Heartwood.MOD_ID);
    public static final DeferredRegister<FoliagePlacerType<?>> FOLIAGE_PLACERS =
            DeferredRegister.create(Registries.FOLIAGE_PLACER_TYPE, Heartwood.MOD_ID);

    public static final DeferredHolder<TrunkPlacerType<?>, TrunkPlacerType<OrganicTrunkPlacer>> ORGANIC_TRUNK =
            TRUNK_PLACERS.register("organic_trunk_placer", () -> new TrunkPlacerType<>(OrganicTrunkPlacer.CODEC));
    public static final DeferredHolder<TrunkPlacerType<?>, TrunkPlacerType<com.chocodanette.heartwood.tree.BoughTrunkPlacer>> BOUGH_TRUNK =
            TRUNK_PLACERS.register("bough_trunk_placer", () -> new TrunkPlacerType<>(com.chocodanette.heartwood.tree.BoughTrunkPlacer.CODEC));
    public static final DeferredHolder<FoliagePlacerType<?>, FoliagePlacerType<ClusterFoliagePlacer>> CLUSTER_FOLIAGE =
            FOLIAGE_PLACERS.register("cluster_foliage_placer", () -> new FoliagePlacerType<>(ClusterFoliagePlacer.CODEC));

    private TreeShapes() {}
}
