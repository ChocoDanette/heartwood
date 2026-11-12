package com.chocodanette.heartwood;

import com.chocodanette.heartwood.felling.FellingEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Heartwood.MOD_ID)
public class Heartwood {
    public static final String MOD_ID = "heartwood";

    public Heartwood(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        NeoForge.EVENT_BUS.register(FellingEvents.class);
    }
}
