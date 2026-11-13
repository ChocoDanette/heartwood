package com.chocodanette.heartwood;

import com.chocodanette.heartwood.drying.PlacedLogs;
import com.chocodanette.heartwood.felling.FellingEvents;
import com.chocodanette.heartwood.species.Species;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
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
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modBus.addListener(Heartwood::addToCreativeTabs);
        NeoForge.EVENT_BUS.register(FellingEvents.class);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            ModBlocks.ALL_GREEN_ITEMS.forEach(item -> event.accept(item.get()));
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
            }
        }
    }
}
