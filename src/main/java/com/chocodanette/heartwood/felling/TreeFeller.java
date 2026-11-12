package com.chocodanette.heartwood.felling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Finds a standing tree and lays it down on the ground in one direction. */
public final class TreeFeller {
    private static final int MAX_LOGS = 400;
    private static final int MAX_LEAVES = 2000;
    private static final int MIN_NATURAL_LEAVES = 3;
    private static final int MAX_DROP = 4;

    /** True when the log at pos belongs to a natural tree: logs above it, with natural leaves around. */
    public static boolean isStandingTree(ServerLevel level, BlockPos pos) {
        Set<BlockPos> logs = findLogs(level, pos);
        if (logs.isEmpty()) return false;
        return findLeaves(level, logs, MIN_NATURAL_LEAVES).size() >= MIN_NATURAL_LEAVES;
    }

    /**
     * Fells the tree whose trunk is cut at pos. The cut block drops its log; every log and leaf
     * above is rotated by 90 degrees around the cut, towards dir, then lowered onto the ground.
     */
    public static void fell(ServerLevel level, BlockPos cut, Direction dir, BlockState cutLog, Player player) {
        Set<BlockPos> logs = findLogs(level, cut);
        Set<BlockPos> leaves = findLeaves(level, logs, MAX_LEAVES);

        Map<BlockPos, BlockState> moved = new LinkedHashMap<>();
        for (BlockPos p : logs) moved.put(p, rotateLog(level.getBlockState(p), dir));
        for (BlockPos p : leaves) {
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(LeavesBlock.PERSISTENT)) s = s.setValue(LeavesBlock.PERSISTENT, true);
            moved.put(p, s);
        }

        // Remove the cut block (with drops) and the standing tree, without triggering leaf decay.
        Block.dropResources(cutLog, level, cut, null, player, player.getMainHandItem());
        level.setBlock(cut, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos p : moved.keySet()) {
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        // Rotate around the cut: height above the cut becomes distance along dir.
        Map<BlockPos, BlockState> target = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : moved.entrySet()) {
            target.put(rotate(e.getKey(), cut, dir), e.getValue());
        }

        // Lower the fallen tree until a log would touch the ground.
        int drop = 0;
        while (drop < MAX_DROP && canLower(level, target, drop + 1)) drop++;

        for (Map.Entry<BlockPos, BlockState> e : target.entrySet()) {
            BlockPos p = e.getKey().below(drop);
            BlockState s = e.getValue();
            BlockState there = level.getBlockState(p);
            if (there.isAir() || there.canBeReplaced() || there.is(BlockTags.LEAVES) && !s.is(BlockTags.LEAVES)) {
                if (!there.isAir()) Block.dropResources(there, level, p);
                level.setBlock(p, s, Block.UPDATE_ALL);
            } else if (s.is(BlockTags.LOGS)) {
                // Blocked by terrain: the log pops out as an item instead.
                Block.dropResources(s, level, p.above());
            }
        }

        level.playSound(null, cut, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.7f, 0.6f);
        level.playSound(null, cut.relative(dir, 4), SoundEvents.WOOD_FALL, SoundSource.BLOCKS, 1.5f, 0.6f);
    }

    /** Logs connected to the cut, strictly above it (the stump and roots stay). */
    static Set<BlockPos> findLogs(ServerLevel level, BlockPos cut) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(cut);
        Set<BlockPos> seen = new HashSet<>();
        seen.add(cut);
        while (!queue.isEmpty() && found.size() < MAX_LOGS) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (n.getY() <= cut.getY() || !seen.add(n)) continue;
                if (level.getBlockState(n).is(BlockTags.LOGS)) {
                    found.add(n);
                    queue.add(n);
                }
            }
        }
        return found;
    }

    /** Natural (non-persistent) leaves reachable from the logs through leaves, up to the decay distance. */
    static Set<BlockPos> findLeaves(ServerLevel level, Set<BlockPos> logs, int max) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        java.util.Map<BlockPos, Integer> dist = new java.util.HashMap<>();
        for (BlockPos log : logs) { queue.add(log); dist.put(log, 0); }
        while (!queue.isEmpty() && found.size() < max) {
            BlockPos p = queue.poll();
            int d = dist.get(p);
            if (d >= LeavesBlock.DECAY_DISTANCE - 1) continue;
            for (Direction side : Direction.values()) {
                BlockPos n = p.relative(side);
                if (dist.containsKey(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
                    dist.put(n, d + 1);
                    found.add(n);
                    queue.add(n);
                }
            }
        }
        return found;
    }

    /** (along, up, side) -> (up, -along, side) around the cut block. */
    static BlockPos rotate(BlockPos p, BlockPos cut, Direction dir) {
        int dx = p.getX() - cut.getX(), dy = p.getY() - cut.getY(), dz = p.getZ() - cut.getZ();
        int along = dx * dir.getStepX() + dz * dir.getStepZ();
        int sideX = dx - along * dir.getStepX();
        int sideZ = dz - along * dir.getStepZ();
        return new BlockPos(cut.getX() + sideX + dir.getStepX() * dy, cut.getY() - along,
                cut.getZ() + sideZ + dir.getStepZ() * dy);
    }

    static BlockState rotateLog(BlockState s, Direction dir) {
        if (!s.hasProperty(RotatedPillarBlock.AXIS)) return s;
        Direction.Axis axis = s.getValue(BlockStateProperties.AXIS);
        Direction.Axis fall = dir.getAxis();
        if (axis == Direction.Axis.Y) axis = fall;
        else if (axis == fall) axis = Direction.Axis.Y;
        return s.setValue(BlockStateProperties.AXIS, axis);
    }

    private static boolean canLower(ServerLevel level, Map<BlockPos, BlockState> target, int drop) {
        for (Map.Entry<BlockPos, BlockState> e : target.entrySet()) {
            if (!e.getValue().is(BlockTags.LOGS)) continue;
            BlockPos below = e.getKey().below(drop);
            BlockState s = level.getBlockState(below);
            if (!(s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES))) return false;
        }
        return true;
    }

    private TreeFeller() {}
}
