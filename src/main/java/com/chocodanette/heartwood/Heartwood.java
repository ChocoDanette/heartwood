package com.chocodanette.heartwood;

import com.chocodanette.heartwood.felling.FellingEvents;
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
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modBus.addListener(Heartwood::addToCreativeTabs);
        NeoForge.EVENT_BUS.register(FellingEvents.class);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            ModBlocks.ALL_GREEN_ITEMS.forEach(item -> event.accept(item.get()));
        }
    }
}
