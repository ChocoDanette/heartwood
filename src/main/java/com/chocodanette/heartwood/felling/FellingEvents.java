package com.chocodanette.heartwood.felling;

import com.chocodanette.heartwood.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Directional felling with an axe:
 * 1. the first cut on a standing trunk carves a notch into the face you hit;
 * 2. the back cut, on the opposite face, fells the tree towards the notch.
 * Sneaking, or breaking without an axe, keeps vanilla behaviour.
 */
public final class FellingEvents {

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (player == null || player.isShiftKeyDown()) return;
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(ItemTags.AXES)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Direction face = hitFace(player, pos);

        if (state.getBlock() instanceof NotchedLogBlock notched) {
            Direction notch = state.getValue(NotchedLogBlock.FACING);
            event.setCanceled(true);
            if (face != notch.getOpposite()) {
                player.displayClientMessage(Component.translatable("message.heartwood.back_cut_side"), true);
                return;
            }
            tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            TreeFeller.fell(level, pos, notch, notched.log().defaultBlockState(), player);
            return;
        }

        Block notchedBlock = ModBlocks.notchedFor(state.getBlock());
        if (notchedBlock == null) return;
        if (!TreeFeller.isStandingTree(level, pos)) return;

        event.setCanceled(true);
        tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        level.setBlock(pos, notchedBlock.defaultBlockState().setValue(NotchedLogBlock.FACING, face), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 0.8f);
        level.levelEvent(2001, pos, Block.getId(state)); // break particles
        player.displayClientMessage(Component.translatable("message.heartwood.notch_done"), true);
    }

    /** The horizontal face of the block the player is hitting; falls back to the face looking at the player. */
    private static Direction hitFace(Player player, BlockPos pos) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(pos) && blockHit.getDirection().getAxis().isHorizontal()) {
            return blockHit.getDirection();
        }
        return player.getDirection().getOpposite();
    }

    private FellingEvents() {}
}
