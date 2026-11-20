package com.chocodanette.heartwood.tree;

import com.chocodanette.heartwood.Heartwood;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Trees no longer give saplings: whatever block drops one (leaves, a sapling, a fallen crown,
 * broken by hand or along with something else), the sapling becomes the tree's seed.
 */
@EventBusSubscriber(modid = Heartwood.MOD_ID)
public final class SaplingDrops {
    private static final Map<String, String> SEED_OF = Map.ofEntries(
            Map.entry("minecraft:oak_sapling", "heartwood:acorn"),
            Map.entry("minecraft:birch_sapling", "heartwood:birch_seeds"),
            Map.entry("minecraft:spruce_sapling", "heartwood:spruce_seeds"),
            Map.entry("minecraft:jungle_sapling", "heartwood:teak_seeds"),
            Map.entry("minecraft:acacia_sapling", "heartwood:acacia_seeds"),
            Map.entry("minecraft:dark_oak_sapling", "heartwood:beechnut"),
            Map.entry("minecraft:cherry_sapling", "heartwood:cherry_pit"),
            Map.entry("heartwood:ash_sapling", "heartwood:ash_key"),
            Map.entry("heartwood:scots_pine_sapling", "heartwood:scots_pine_seeds"),
            Map.entry("heartwood:chestnut_sapling", "heartwood:sweet_chestnut"),
            Map.entry("heartwood:maple_sapling", "heartwood:maple_key"));

    private static Map<Item, Item> swap;

    private static Map<Item, Item> swap() {
        if (swap == null) {
            Map<Item, Item> m = new HashMap<>();
            SEED_OF.forEach((sapling, seed) -> m.put(
                    BuiltInRegistries.ITEM.get(ResourceLocation.parse(sapling)),
                    BuiltInRegistries.ITEM.get(ResourceLocation.parse(seed))));
            swap = m;
        }
        return swap;
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            Item seed = swap().get(stack.getItem());
            if (seed != null) drop.setItem(new ItemStack(seed, stack.getCount()));
        }
    }

    private SaplingDrops() {}
}
