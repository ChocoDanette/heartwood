package com.chocodanette.heartwood.felling;

import com.chocodanette.heartwood.tree.BoughBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Leaves go with the branch they grew on. */
public final class LeafCleanup {
    private static final int REACH = LeavesBlock.DECAY_DISTANCE - 1;

    /**
     * Breaks the leaves (natural leaves, or the leafy branches of a fallen tree) that were held by
     * the removed wood: those closer to it than to any wood still there.
     */
    public static void dropLeavesOf(ServerLevel level, Collection<BlockPos> removed, BlockPos except) {
        Map<BlockPos, Integer> theirs = spread(level, removed);
        if (theirs.isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : theirs.keySet()) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        Set<BlockPos> gone = new HashSet<>(removed);
        Set<BlockPos> wood = new HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(minX - REACH, minY - REACH, minZ - REACH, maxX + REACH, maxY + REACH, maxZ + REACH)) {
            if (gone.contains(p) || p.equals(except)) continue;
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.LOGS) || s.getBlock() instanceof BoughBlock) wood.add(p.immutable());
        }
        Map<BlockPos, Integer> ours = spread(level, wood);
        for (Map.Entry<BlockPos, Integer> e : theirs.entrySet()) {
            Integer other = ours.get(e.getKey());
            if (other == null || other > e.getValue()) level.destroyBlock(e.getKey(), true);
        }
    }

    /** Distance, through leaves, from the given wood to each leaf it holds. */
    private static Map<BlockPos, Integer> spread(ServerLevel level, Collection<BlockPos> from) {
        Map<BlockPos, Integer> dist = new HashMap<>();
        Map<BlockPos, Integer> leaves = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos p : from) { dist.put(p, 0); queue.add(p); }
        while (!queue.isEmpty() && leaves.size() < 4000) {
            BlockPos p = queue.poll();
            int d = dist.get(p);
            if (d >= REACH) continue;
            for (Direction side : Direction.values()) {
                BlockPos n = p.relative(side);
                if (dist.containsKey(n)) continue;
                if (isLeaf(level.getBlockState(n))) {
                    dist.put(n, d + 1);
                    leaves.put(n, d + 1);
                    queue.add(n);
                }
            }
        }
        return leaves;
    }

    private static boolean isLeaf(BlockState s) {
        return s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT) || s.getBlock() instanceof BranchesBlock;
    }

    private LeafCleanup() {}
}
