package com.chocodanette.heartwood.client;

import com.chocodanette.heartwood.Heartwood;
import com.chocodanette.heartwood.ModBlocks;
import com.chocodanette.heartwood.drying.GreenLogBlock;
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
        // Green logs: a greenish, damp tint that fades as they dry.
        ModBlocks.ALL_GREEN.forEach(b -> event.register(
                (state, level, pos, tint) -> greenTint(state.getValue(GreenLogBlock.DRYING)), b.get()));
        event.register((state, level, pos, tint) -> FoliageColor.getEvergreenColor(), block("spruce"));
        event.register((state, level, pos, tint) -> FoliageColor.getBirchColor(), block("birch"));
        event.register((state, level, pos, tint) -> FoliageColor.getMangroveColor(), block("mangrove"));
        event.register((state, level, pos, tint) -> level != null && pos != null
                        ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(),
                block("oak"), block("jungle"), block("acacia"), block("dark_oak"));
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        ModBlocks.ALL_GREEN_ITEMS.forEach(i -> event.register((stack, tint) -> 0xFF000000 | greenTint(0), i.get()));
    }

    /** From a strong damp green (stage 0) to a pale green (last stage); dry logs are untinted vanilla logs. */
    static int greenTint(int stage) {
        float t = stage / (float) GreenLogBlock.MAX_STAGE;
        int r = lerp(0x68, 0xCC, t), g = lerp(0xA0, 0xDC, t), b = lerp(0x48, 0xB4, t);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static Block block(String wood) {
        return ModBlocks.ALL_BRANCHES.stream()
                .filter(b -> b.getId().getPath().equals(wood + "_branches"))
                .findFirst().orElseThrow().get();
    }

    private ClientColors() {}
}
