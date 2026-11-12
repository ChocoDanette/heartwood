package com.chocodanette.heartwood.client;

import com.chocodanette.heartwood.Heartwood;
import com.chocodanette.heartwood.ModBlocks;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Branches are tinted like the leaves they come from. */
@EventBusSubscriber(modid = Heartwood.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientColors {

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> FoliageColor.getEvergreenColor(), block("spruce"));
        event.register((state, level, pos, tint) -> FoliageColor.getBirchColor(), block("birch"));
        event.register((state, level, pos, tint) -> FoliageColor.getMangroveColor(), block("mangrove"));
        event.register((state, level, pos, tint) -> level != null && pos != null
                        ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(),
                block("oak"), block("jungle"), block("acacia"), block("dark_oak"));
    }

    private static Block block(String wood) {
        return ModBlocks.ALL_BRANCHES.stream()
                .filter(b -> b.getId().getPath().equals(wood + "_branches"))
                .findFirst().orElseThrow().get();
    }

    private ClientColors() {}
}
