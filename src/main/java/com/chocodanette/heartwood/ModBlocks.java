package com.chocodanette.heartwood;

import com.chocodanette.heartwood.felling.BranchesBlock;
import com.chocodanette.heartwood.species.Species;
import com.chocodanette.heartwood.felling.FelledLogBlock;
import com.chocodanette.heartwood.felling.FallingTreeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import com.chocodanette.heartwood.drying.GreenLogBlock;
import com.chocodanette.heartwood.drying.GreenLogItem;
import com.chocodanette.heartwood.drying.DryingLogBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import com.chocodanette.heartwood.felling.NotchedLogBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Blocks derived from the vanilla trees:
 * notched logs (the directional "V" cut), felled logs (trunk lying on the ground)
 * and branches (the crown of a felled tree, to be limbed with an axe).
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Heartwood.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Heartwood.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Heartwood.MOD_ID);

    private record Link(Supplier<Block> from, DeferredBlock<? extends Block> to) {}

    private static final List<Link> NOTCHED = new ArrayList<>();
    private static final List<Link> FELLED = new ArrayList<>();
    private static final List<Link> BRANCHES = new ArrayList<>();
    private static final List<Link> GREEN = new ArrayList<>();
    public static final List<DeferredBlock<GreenLogBlock>> ALL_GREEN = new ArrayList<>();
    public static final List<DeferredItem<GreenLogItem>> ALL_GREEN_ITEMS = new ArrayList<>();
    public static final List<DeferredBlock<BranchesBlock>> ALL_BRANCHES = new ArrayList<>();
    private static final List<Link> BOUGHS = new ArrayList<>();
    public static final List<DeferredBlock<com.chocodanette.heartwood.tree.BoughBlock>> ALL_BOUGHS = new ArrayList<>();
    private static final List<Link> STUMPS = new ArrayList<>();
    private static Map<Block, Block> stumpByBough;
    private static Map<Block, Block> notchedByLog, felledByLog, branchesByLeaves, greenByLog, boughByLog;

    /** A planted tree seed, until it comes up. */
    public static final DeferredBlock<com.chocodanette.heartwood.tree.SeedMoundBlock> SEED_MOUND = BLOCKS.register("seed_mound",
            () -> new com.chocodanette.heartwood.tree.SeedMoundBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT).strength(0.3f).sound(SoundType.ROOTED_DIRT).noLootTable()
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** The outer ring of a trunk more than a block thick. */
    public static final DeferredBlock<com.chocodanette.heartwood.tree.TrunkShellBlock> TRUNK_SHELL = BLOCKS.register("trunk_shell",
            () -> new com.chocodanette.heartwood.tree.TrunkShellBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noLootTable()
                    .noOcclusion().dynamicShape().pushReaction(PushReaction.BLOCK)));
    public static final List<DeferredItem<com.chocodanette.heartwood.tree.SeedItem>> SEEDS = new ArrayList<>();

    private static void seed(String name, String... features) {
        List<com.chocodanette.heartwood.tree.SeedItem.Kind> kinds = new ArrayList<>();
        for (String f : features) {
            String[] parts = f.split("\\*");
            kinds.add(new com.chocodanette.heartwood.tree.SeedItem.Kind(net.minecraft.resources.ResourceLocation.parse(parts[0]),
                    parts.length > 1 ? Integer.parseInt(parts[1]) : 1));
        }
        SEEDS.add(ITEMS.register(name, () -> new com.chocodanette.heartwood.tree.SeedItem(kinds, new Item.Properties())));
    }

    static {
        // Tree seeds, and the trees (configured features) they grow into, with their odds.
        seed("acorn", "minecraft:oak*9", "minecraft:fancy_oak*1");
        seed("birch_seeds", "minecraft:birch");
        seed("spruce_seeds", "minecraft:spruce");
        seed("teak_seeds", "minecraft:jungle_tree_no_vine");
        seed("acacia_seeds", "minecraft:acacia");
        seed("beechnut", "minecraft:dark_oak");
        seed("cherry_pit", "minecraft:cherry");
        seed("ash_key", "heartwood:ash");
        seed("scots_pine_seeds", "heartwood:scots_pine");
        seed("sweet_chestnut", "heartwood:chestnut");
        seed("maple_key", "heartwood:maple");
    }

    static {
        Species.init();
        for (Species.Wood w : Species.ALL) {
            log(w.name() + "_log", w.log()::get);
            green(w.name() + "_log", w.log()::get);
            branches(w.name(), w.leaves()::get);
            bough(w.name(), w.log()::get);
        }
        bough("oak", () -> Blocks.OAK_LOG);
        bough("spruce", () -> Blocks.SPRUCE_LOG);
        bough("birch", () -> Blocks.BIRCH_LOG);
        bough("jungle", () -> Blocks.JUNGLE_LOG);
        bough("acacia", () -> Blocks.ACACIA_LOG);
        bough("dark_oak", () -> Blocks.DARK_OAK_LOG);
        bough("mangrove", () -> Blocks.MANGROVE_LOG);
        bough("cherry", () -> Blocks.CHERRY_LOG);

        log("oak_log", () -> Blocks.OAK_LOG);
        log("spruce_log", () -> Blocks.SPRUCE_LOG);
        log("birch_log", () -> Blocks.BIRCH_LOG);
        log("jungle_log", () -> Blocks.JUNGLE_LOG);
        log("acacia_log", () -> Blocks.ACACIA_LOG);
        log("dark_oak_log", () -> Blocks.DARK_OAK_LOG);
        log("mangrove_log", () -> Blocks.MANGROVE_LOG);
        log("cherry_log", () -> Blocks.CHERRY_LOG);
        green("oak_log", () -> Blocks.OAK_LOG);
        green("spruce_log", () -> Blocks.SPRUCE_LOG);
        green("birch_log", () -> Blocks.BIRCH_LOG);
        green("jungle_log", () -> Blocks.JUNGLE_LOG);
        green("acacia_log", () -> Blocks.ACACIA_LOG);
        green("dark_oak_log", () -> Blocks.DARK_OAK_LOG);
        green("mangrove_log", () -> Blocks.MANGROVE_LOG);
        green("cherry_log", () -> Blocks.CHERRY_LOG);

        log("crimson_stem", () -> Blocks.CRIMSON_STEM);
        log("warped_stem", () -> Blocks.WARPED_STEM);

        branches("oak", () -> Blocks.OAK_LEAVES);
        branches("spruce", () -> Blocks.SPRUCE_LEAVES);
        branches("birch", () -> Blocks.BIRCH_LEAVES);
        branches("jungle", () -> Blocks.JUNGLE_LEAVES);
        branches("acacia", () -> Blocks.ACACIA_LEAVES);
        branches("dark_oak", () -> Blocks.DARK_OAK_LEAVES);
        branches("mangrove", () -> Blocks.MANGROVE_LEAVES);
        branches("cherry", () -> Blocks.CHERRY_LEAVES);
        branches("azalea", () -> Blocks.AZALEA_LEAVES);
        branches("flowering_azalea", () -> Blocks.FLOWERING_AZALEA_LEAVES);
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Heartwood.MOD_ID);

    public static final Supplier<EntityType<FallingTreeEntity>> FALLING_TREE = ENTITY_TYPES.register("falling_tree",
            () -> EntityType.Builder.<FallingTreeEntity>of(FallingTreeEntity::new, MobCategory.MISC)
                    .sized(1f, 1f).clientTrackingRange(10).updateInterval(20).noSave().noSummon()
                    .build("falling_tree"));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<DryingLogBlockEntity>> DRYING_LOG = BLOCK_ENTITIES.register("drying_log",
            () -> BlockEntityType.Builder.of(DryingLogBlockEntity::new,
                    ALL_GREEN.stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    private static void log(String name, Supplier<Block> log) {
        NOTCHED.add(new Link(log, BLOCKS.register("notched_" + name,
                () -> new NotchedLogBlock(log, BlockBehaviour.Properties.ofFullCopy(log.get())
                        // The copied map colour reads the log's AXIS property, which notched logs do not have.
                        .mapColor(log.get().defaultMapColor())
                        .noOcclusion()))));
        FELLED.add(new Link(log, BLOCKS.register("felled_" + name,
                () -> new FelledLogBlock(log, BlockBehaviour.Properties.ofFullCopy(log.get())
                        .mapColor(log.get().defaultMapColor())
                        .strength(3.0f)))));
    }

    private static void green(String name, Supplier<Block> log) {
        DeferredBlock<GreenLogBlock> block = BLOCKS.register("green_" + name,
                () -> new GreenLogBlock(log, BlockBehaviour.Properties.ofFullCopy(log.get())
                        .mapColor(log.get().defaultMapColor())
                        .randomTicks()));
        GREEN.add(new Link(log, block));
        ALL_GREEN.add(block);
        ALL_GREEN_ITEMS.add(ITEMS.register("green_" + name, () -> new GreenLogItem(block.get(), new Item.Properties())));
    }

    private static void branches(String name, Supplier<Block> leaves) {
        DeferredBlock<BranchesBlock> block = BLOCKS.register(name + "_branches",
                () -> new BranchesBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.PLANT)
                        .strength(0.4f)
                        .sound(SoundType.AZALEA_LEAVES)
                        .noOcclusion()
                        .isSuffocating((s, l, p) -> false)
                        .isViewBlocking((s, l, p) -> false)
                        .ignitedByLava()
                        .pushReaction(PushReaction.DESTROY)));
        BRANCHES.add(new Link(leaves, block));
        ALL_BRANCHES.add(block);
    }

    private static void bough(String name, Supplier<Block> log) {
        DeferredBlock<com.chocodanette.heartwood.tree.BoughBlock> block = BLOCKS.register(name + "_bough",
                () -> new com.chocodanette.heartwood.tree.BoughBlock(log, BlockBehaviour.Properties.ofFullCopy(log.get())
                        .mapColor(log.get().defaultMapColor())
                        .noOcclusion()
                        .dynamicShape()));
        BOUGHS.add(new Link(log, block));
        ALL_BOUGHS.add(block);
        DeferredBlock<com.chocodanette.heartwood.felling.StumpBlock> stump = BLOCKS.register(name + "_stump",
                () -> new com.chocodanette.heartwood.felling.StumpBlock(BlockBehaviour.Properties.ofFullCopy(log.get())
                        .mapColor(log.get().defaultMapColor())
                        .noOcclusion()));
        STUMPS.add(new Link(() -> block.get(), stump));
    }

    /** The stump a felled tree of this bough leaves, or null. */
    public static Block stumpFor(Block bough) {
        if (stumpByBough == null) stumpByBough = index(STUMPS);
        return stumpByBough.get(bough);
    }

    /** The variable-thickness tree piece made of this log, or null. */
    public static Block boughFor(Block log) {
        if (boughByLog == null) boughByLog = index(BOUGHS);
        return boughByLog.get(log);
    }

    private static Map<Block, Block> index(List<Link> links) {
        Map<Block, Block> map = new HashMap<>();
        for (Link l : links) map.put(l.from().get(), l.to().get());
        return map;
    }

    /** The notched variant of a standing log, or null. */
    public static Block notchedFor(Block log) {
        if (notchedByLog == null) notchedByLog = index(NOTCHED);
        return notchedByLog.get(log);
    }

    /** The felled (lying) variant of a log, or null. */
    public static Block felledFor(Block log) {
        if (felledByLog == null) felledByLog = index(FELLED);
        return felledByLog.get(log);
    }

    /** The branches block that replaces these leaves on a felled tree, or null. */
    public static Block branchesFor(Block leaves) {
        if (branchesByLeaves == null) branchesByLeaves = index(BRANCHES);
        return branchesByLeaves.get(leaves);
    }

    /** The green (freshly cut) variant of a dry log, or null. */
    public static Block greenFor(Block log) {
        if (greenByLog == null) greenByLog = index(GREEN);
        return greenByLog.get(log);
    }

    private ModBlocks() {}
}
