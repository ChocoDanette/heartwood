package com.chocodanette.heartwood.felling;

import com.chocodanette.heartwood.ModBlocks;
import com.chocodanette.heartwood.drying.PlacedLogs;
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
 * Sneaking, or breaking without an axe, breaks one block; natural wood always gives green logs.
 */
public final class FellingEvents {

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        ItemStack tool = player.getMainHandItem();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        // Logs placed by a player (or dried green logs) are dry wood and break the vanilla way.
        if (PlacedLogs.isDry(level, pos)) {
            PlacedLogs.forget(level, pos);
            return;
        }

        // Bucking: a felled trunk is cut with an axe, once its branches are off.
        if (state.getBlock() instanceof FelledLogBlock) {
            if (player.isCreative()) return;
            if (hasBranchesAround(level, pos)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("message.heartwood.limb_first"), true);
            } else if (!tool.is(ItemTags.AXES)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("message.heartwood.need_axe"), true);
            }
            return;
        }

        if (player.isShiftKeyDown() || !tool.is(ItemTags.AXES)) {
            breakAsGreen(event, level, pos, state, player, tool);
            return;
        }
        Direction face = hitFace(player, pos);

        if (state.getBlock() instanceof NotchedLogBlock notched) {
            Direction notch = state.getValue(NotchedLogBlock.FACING);
            event.setCanceled(true);
            if (face != notch.getOpposite()) {
                player.displayClientMessage(Component.translatable("message.heartwood.back_cut_side"), true);
                return;
            }
            tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            TreeFeller.fell(level, pos, notch, greenOrDry(notched.log()), player);
            return;
        }

        Block notchedBlock = ModBlocks.notchedFor(state.getBlock());
        if (notchedBlock == null) return;
        if (!TreeFeller.isStandingTree(level, pos)) {
            breakAsGreen(event, level, pos, state, player, tool);
            return;
        }

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

    /** True when branches still touch this trunk section (including diagonally). */
    private static boolean hasBranchesAround(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getBlockState(p).getBlock() instanceof BranchesBlock) return true;
        }
        return false;
    }

    /** Natural wood (any vanilla log not placed by a player) always breaks into green wood. */
    private static void breakAsGreen(BlockEvent.BreakEvent event, ServerLevel level, BlockPos pos, BlockState state,
                                     Player player, ItemStack tool) {
        Block green = ModBlocks.greenFor(state.getBlock());
        if (green == null || player.isCreative()) return;
        event.setCanceled(true);
        level.destroyBlock(pos, false, player);
        Block.popResource(level, pos, new ItemStack(green));
        if (tool.is(ItemTags.AXES)) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }

    /** A vanilla log placed by a player is dry wood. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && ModBlocks.greenFor(event.getPlacedBlock().getBlock()) != null) {
            PlacedLogs.markDry(level, event.getPos());
        }
    }

    private static BlockState greenOrDry(Block log) {
        Block green = ModBlocks.greenFor(log);
        return (green != null ? green : log).defaultBlockState();
    }

    private FellingEvents() {}
}
