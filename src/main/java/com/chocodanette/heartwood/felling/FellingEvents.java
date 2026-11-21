package com.chocodanette.heartwood.felling;

import com.chocodanette.heartwood.ModBlocks;
import com.chocodanette.heartwood.drying.PlacedLogs;
import com.chocodanette.heartwood.tree.BoughBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    /** From this radius on (half a block across), a bough is timber: it gives logs and needs an axe. */
    private static final int THICK = 4;

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        // In creative, breaking the foot of a standing tree takes the whole tree away at once, and
        // breaking it while sneaking fells it (away from the player). Any other block breaks the
        // creative way.
        if (player.isCreative()) {
            BlockPos at = event.getPos();
            BlockState st = event.getState();
            boolean shell = st.getBlock() instanceof com.chocodanette.heartwood.tree.TrunkShellBlock;
            if (shell) {
                at = com.chocodanette.heartwood.tree.TrunkShellBlock.core(st, at);
                st = level.getBlockState(at);
            }
            if (st.getBlock() instanceof BoughBlock bough && BoughBlock.isRooted(level, at)
                    && FallenTree.of(level, at).base.equals(at) && !BoughBlock.descendants(level, at, 1).isEmpty()) {
                event.setCanceled(true);
                if (player.isShiftKeyDown()) {
                    Direction away = player.getDirection();
                    level.setBlock(at, st.setValue(BoughBlock.NOTCH, BoughBlock.Notch.of(away)), Block.UPDATE_ALL);
                    TreeFeller.fell(level, at, away, greenOrDry(bough.log()), player);
                } else {
                    TreeFeller.removeWhole(level, at);
                }
            } else if (shell) {
                event.setCanceled(true);
                if (st.getBlock() instanceof BoughBlock) level.destroyBlock(at, false, player);
                else level.removeBlock(event.getPos(), false);
            }
            return;
        }
        ItemStack tool = player.getMainHandItem();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        // Logs placed by a player (or dried green logs) are dry wood and break the vanilla way.
        if (PlacedLogs.isDry(level, pos)) {
            PlacedLogs.forget(level, pos);
            return;
        }

        if (state.getBlock() instanceof BoughBlock bough) {
            onBreakBough(event, level, pos, state, bough, player, tool);
            return;
        }

        // The outer ring of a thick trunk: the cut works on the trunk piece it belongs to.
        if (state.getBlock() instanceof com.chocodanette.heartwood.tree.TrunkShellBlock) {
            BlockPos core = com.chocodanette.heartwood.tree.TrunkShellBlock.core(state, pos);
            BlockState coreState = level.getBlockState(core);
            event.setCanceled(true);
            if (!(coreState.getBlock() instanceof BoughBlock bough)) {
                level.removeBlock(pos, false);
                return;
            }
            BlockEvent.BreakEvent coreEvent = new BlockEvent.BreakEvent(level, core, coreState, player);
            onBreakBough(coreEvent, level, core, coreState, bough, player, tool);
            if (!coreEvent.isCanceled()) {
                if (tool.is(ItemTags.AXES) && !player.isCreative()) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                level.destroyBlock(core, !player.isCreative(), player);
            }
            return;
        }

        // Bucking: a felled trunk is cut with an axe, once its branches are off.
        if (state.getBlock() instanceof FelledLogBlock) {
            if (player.isCreative()) return;
            if (hasBranchesAround(level, pos)) {
                event.setCanceled(true);
            } else if (!com.chocodanette.heartwood.Config.cuts(tool)) {
                event.setCanceled(true);
            }
            return;
        }

        if (player.isShiftKeyDown() || !com.chocodanette.heartwood.Config.cuts(tool)) {
            breakAsGreen(event, level, pos, state, player, tool);
            return;
        }
        Direction face = hitFace(player, pos);

        if (state.getBlock() instanceof NotchedLogBlock notched) {
            Direction notch = state.getValue(NotchedLogBlock.FACING);
            event.setCanceled(true);
            if (face != notch.getOpposite()) {
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
    }

    /**
     * Trees made of boughs. On a standing tree, an axe on a vertical trunk piece at least half a block
     * thick carves the notch, then the back cut fells the tree; cutting any other piece brings down
     * everything that hangs from it. On a fallen tree, thick pieces are bucked with an axe once limbed.
     */
    private static void onBreakBough(BlockEvent.BreakEvent event, ServerLevel level, BlockPos pos, BlockState state,
                                     BoughBlock bough, Player player, ItemStack tool) {
        if (player.isCreative()) return;
        int radius = state.getValue(BoughBlock.RADIUS);
        boolean axe = com.chocodanette.heartwood.Config.cuts(tool);
        if (!BoughBlock.isRooted(level, pos)) {
            // Fallen tree. Its trunk is bucked only once every branch is off; branches come off
            // with all they carry (thick ones with an axe).
            FallenTree tree = FallenTree.of(level, pos);
            if (!tree.stemSet.contains(pos)) {
                if (radius >= THICK && !axe) {
                    event.setCanceled(true);
                    return;
                }
                cutOff(level, pos, player);
                return;
            }
            if (!tree.limbed()) {
                event.setCanceled(true);
                return;
            }
            if (!axe) {
                event.setCanceled(true);
                return;
            }
            // Leaves left on this part of the trunk go with it.
            LeafCleanup.dropLeavesOf(level, java.util.List.of(pos), pos);
            return;
        }
        // Standing tree: only its foot is notched; the rest of the trunk can't be cut into, while
        // branches can still be cut off one by one.
        FallenTree standing = FallenTree.of(level, pos);
        boolean foot = standing.base.equals(pos);
        Direction notch = state.getValue(BoughBlock.NOTCH).dir;
        if (notch != null && BoughBlock.descendants(level, pos, 1).isEmpty()) {
            // A stump: it comes out like a log.
            if (radius >= THICK && !axe) {
                event.setCanceled(true);
            }
            return;
        }
        if (notch == null && standing.stemSet.contains(pos) && !foot) {
            event.setCanceled(true);
            return;
        }
        if (radius >= THICK && !axe) {
            event.setCanceled(true);
            return;
        }
        if (notch != null) {
            event.setCanceled(true);
            if (axe && hitFace(player, pos) == notch.getOpposite()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                TreeFeller.fell(level, pos, notch, greenOrDry(bough.log()), player);
            }
            return;
        }
        if (foot && axe && !player.isShiftKeyDown() && radius >= THICK) {
            event.setCanceled(true);
            tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            level.setBlock(pos, state.setValue(BoughBlock.NOTCH, BoughBlock.Notch.of(hitFace(player, pos))), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.levelEvent(2001, pos, Block.getId(state));
            return;
        }
        // A branch cut off a standing tree takes everything beyond it down with it.
        cutOff(level, pos, player);
    }

    /**
     * A branch cut off at pos (the piece itself is broken by the game): everything it carries
     * comes down, and so do the leaves that grew on it.
     */
    private static void cutOff(ServerLevel level, BlockPos pos, Player player) {
        java.util.List<BlockPos> gone = new java.util.ArrayList<>(BoughBlock.descendants(level, pos, 2000));
        for (BlockPos p : gone) level.destroyBlock(p, true, player);
        gone.add(pos);
        LeafCleanup.dropLeavesOf(level, gone, pos);
    }

    /** The horizontal face of the block the player is hitting; otherwise the side of the block facing the player. */
    private static Direction hitFace(Player player, BlockPos pos) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(pos) && blockHit.getDirection().getAxis().isHorizontal()) {
            return blockHit.getDirection();
        }
        // Hitting the shell of a thick trunk, or a block in the way: the side of the trunk facing the player.
        double dx = player.getX() - (pos.getX() + 0.5), dz = player.getZ() - (pos.getZ() + 0.5);
        if (dx * dx + dz * dz < 1.0E-4) return player.getDirection().getOpposite();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /** True when a thin bough (a twig or small branch) still hangs from this piece. */
    private static boolean hasThinBoughs(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState s = level.getBlockState(n);
            if (s.getBlock() instanceof BoughBlock && s.getValue(BoughBlock.RADIUS) < THICK
                    && pos.equals(BoughBlock.parentPos(s, n))) return true;
        }
        return false;
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
