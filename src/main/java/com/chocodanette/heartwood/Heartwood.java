package com.chocodanette.heartwood;

import com.chocodanette.heartwood.drying.PlacedLogs;
import com.chocodanette.heartwood.felling.FellingEvents;
import com.chocodanette.heartwood.species.Species;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(Heartwood.MOD_ID)
public class Heartwood {
    public static final String MOD_ID = "heartwood";

    public Heartwood(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlocks.BLOCK_ENTITIES.register(modBus);
        ModBlocks.ENTITY_TYPES.register(modBus);
        PlacedLogs.ATTACHMENTS.register(modBus);
        com.chocodanette.heartwood.species.TreeShapes.TRUNK_PLACERS.register(modBus);
        com.chocodanette.heartwood.species.TreeShapes.FOLIAGE_PLACERS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modBus.addListener(Heartwood::addToCreativeTabs);
        modBus.addListener(Heartwood::addSignBlocks);
        NeoForge.EVENT_BUS.register(FellingEvents.class);
        NeoForge.EVENT_BUS.register(com.chocodanette.heartwood.tree.GrowingTrees.class);
    }

    /** Lets the vanilla sign block entities accept the species' signs. */
    private static void addSignBlocks(BlockEntityTypeAddBlocksEvent event) {
        for (Species.Wood w : Species.ALL) {
            event.modify(BlockEntityType.SIGN, w.sign().get(), w.wallSign().get());
            event.modify(BlockEntityType.HANGING_SIGN, w.hangingSign().get(), w.wallHangingSign().get());
        }
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            ModBlocks.ALL_GREEN_ITEMS.forEach(item -> event.accept(item.get()));
            ModBlocks.SEEDS.forEach(item -> event.accept(item.get()));
            for (Species.Wood w : Species.ALL) {
                event.accept(w.log().get());
                event.accept(w.leaves().get());
                event.accept(w.sapling().get());
            }
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (Species.Wood w : Species.ALL) {
                for (var b : w.blocks()) {
                    if (b != w.leaves() && b != w.sapling()) event.accept(b.get());
                }
                for (var item : w.items()) event.accept(item.get());
            }
        }
    }
}
