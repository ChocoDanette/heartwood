package com.chocodanette.heartwood.species;

import com.chocodanette.heartwood.Heartwood;
import com.chocodanette.heartwood.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Real tree species added by Heartwood, each with its full set of wood blocks. */
public final class Species {
    public enum Foliage { BIOME, EVERGREEN }

    public record Wood(String name, Foliage foliage,
                       DeferredBlock<Block> log, DeferredBlock<Block> wood,
                       DeferredBlock<Block> strippedLog, DeferredBlock<Block> strippedWood,
                       DeferredBlock<Block> planks, DeferredBlock<Block> stairs, DeferredBlock<Block> slab,
                       DeferredBlock<Block> fence, DeferredBlock<Block> fenceGate,
                       DeferredBlock<Block> door, DeferredBlock<Block> trapdoor,
                       DeferredBlock<Block> button, DeferredBlock<Block> pressurePlate,
                       DeferredBlock<Block> leaves, DeferredBlock<Block> sapling) {
        public List<DeferredBlock<Block>> blocks() {
            return List.of(log, wood, strippedLog, strippedWood, planks, stairs, slab, fence, fenceGate,
                    door, trapdoor, pressurePlate, button, leaves, sapling);
        }
    }

    public static final List<Wood> ALL = new ArrayList<>();
    public static Wood ASH, SCOTS_PINE, CHESTNUT, MAPLE;

    /** Registers the species; called first thing by ModBlocks' static initialiser. */
    public static void init() {
        if (!ALL.isEmpty()) return;
        ASH = register("ash", MapColor.SAND, MapColor.STONE, Foliage.BIOME);
        SCOTS_PINE = register("scots_pine", MapColor.COLOR_ORANGE, MapColor.TERRACOTTA_BROWN, Foliage.EVERGREEN);
        CHESTNUT = register("chestnut", MapColor.TERRACOTTA_YELLOW, MapColor.TERRACOTTA_GRAY, Foliage.BIOME);
        MAPLE = register("maple", MapColor.TERRACOTTA_WHITE, MapColor.COLOR_GRAY, Foliage.BIOME);
    }

    private static Wood register(String name, MapColor woodColor, MapColor barkColor, Foliage foliage) {
        BlockBehaviour.Properties wood = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).mapColor(woodColor);
        BlockBehaviour.Properties bark = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).mapColor(barkColor)
                .strength(2.0f).sound(SoundType.WOOD);

        DeferredBlock<Block> strippedLog = block("stripped_" + name + "_log", () -> new StrippableLogBlock(null, wood));
        DeferredBlock<Block> strippedWood = block("stripped_" + name + "_wood", () -> new StrippableLogBlock(null, wood));
        DeferredBlock<Block> log = block(name + "_log", () -> new StrippableLogBlock(strippedLog, bark));
        DeferredBlock<Block> woodBlock = block(name + "_wood", () -> new StrippableLogBlock(strippedWood, bark));
        DeferredBlock<Block> planks = block(name + "_planks", () -> new Block(wood));
        DeferredBlock<Block> stairs = block(name + "_stairs", () -> new StairBlock(planks.get().defaultBlockState(), wood));
        DeferredBlock<Block> slab = block(name + "_slab", () -> new SlabBlock(wood));
        DeferredBlock<Block> fence = block(name + "_fence", () -> new FenceBlock(wood));
        DeferredBlock<Block> gate = block(name + "_fence_gate", () -> new FenceGateBlock(WoodType.OAK, wood));
        BlockSetType set = BlockSetType.register(new BlockSetType(Heartwood.MOD_ID + ":" + name));
        DeferredBlock<Block> door = ModBlocks.BLOCKS.register(name + "_door",
                () -> new DoorBlock(set, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_DOOR).mapColor(woodColor)));
        ModBlocks.ITEMS.register(name + "_door", () -> new DoubleHighBlockItem(door.get(), new Item.Properties()));
        DeferredBlock<Block> trapdoor = block(name + "_trapdoor",
                () -> new TrapDoorBlock(set, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_TRAPDOOR).mapColor(woodColor)));
        DeferredBlock<Block> button = block(name + "_button",
                () -> new ButtonBlock(set, 30, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_BUTTON)));
        DeferredBlock<Block> plate = block(name + "_pressure_plate",
                () -> new PressurePlateBlock(set, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PRESSURE_PLATE).mapColor(woodColor)));
        DeferredBlock<Block> leaves = block(name + "_leaves", () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
        TreeGrower grower = new TreeGrower(Heartwood.MOD_ID + ":" + name, Optional.empty(),
                Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE,
                        ResourceLocation.fromNamespaceAndPath(Heartwood.MOD_ID, name))), Optional.empty());
        DeferredBlock<Block> sapling = block(name + "_sapling",
                () -> new SaplingBlock(grower, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING)));

        Wood w = new Wood(name, foliage, log, woodBlock, strippedLog, strippedWood, planks, stairs, slab, fence, gate,
                door, trapdoor, button, plate, leaves, sapling);
        ALL.add(w);
        return w;
    }

    private static DeferredBlock<Block> block(String name, Supplier<Block> factory) {
        DeferredBlock<Block> block = ModBlocks.BLOCKS.register(name, factory);
        ModBlocks.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private Species() {}
}
