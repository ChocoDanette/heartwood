package com.chocodanette.heartwood;

import com.chocodanette.heartwood.felling.NotchedLogBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Notched variants of the vanilla logs (the directional "V" cut). */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Heartwood.MOD_ID);

    /** Original log -> its notched variant. Filled at registration. */
    private static final Map<Supplier<Block>, DeferredBlock<NotchedLogBlock>> NOTCHED = new LinkedHashMap<>();
    private static Map<Block, Block> byLog;

    static {
        notched("oak_log", () -> Blocks.OAK_LOG);
        notched("spruce_log", () -> Blocks.SPRUCE_LOG);
        notched("birch_log", () -> Blocks.BIRCH_LOG);
        notched("jungle_log", () -> Blocks.JUNGLE_LOG);
        notched("acacia_log", () -> Blocks.ACACIA_LOG);
        notched("dark_oak_log", () -> Blocks.DARK_OAK_LOG);
        notched("mangrove_log", () -> Blocks.MANGROVE_LOG);
        notched("cherry_log", () -> Blocks.CHERRY_LOG);
        notched("crimson_stem", () -> Blocks.CRIMSON_STEM);
        notched("warped_stem", () -> Blocks.WARPED_STEM);
    }

    private static void notched(String name, Supplier<Block> log) {
        DeferredBlock<NotchedLogBlock> block = BLOCKS.register("notched_" + name,
                () -> new NotchedLogBlock(log, BlockBehaviour.Properties.ofFullCopy(log.get()).noOcclusion()));
        NOTCHED.put(log, block);
    }

    /** The notched variant of a log, or null when the log has none. */
    public static Block notchedFor(Block log) {
        if (byLog == null) {
            Map<Block, Block> map = new java.util.HashMap<>();
            NOTCHED.forEach((orig, notched) -> map.put(orig.get(), notched.get()));
            byLog = map;
        }
        return byLog.get(log);
    }

    private ModBlocks() {}
}
