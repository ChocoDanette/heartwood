package com.chocodanette.heartwood.drying;

import com.chocodanette.heartwood.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class GreenLogItem extends BlockItem {
    public GreenLogItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heartwood.green_log",
                String.format("%.1f", Config.dryingDays())).withStyle(ChatFormatting.GRAY));
    }
}
