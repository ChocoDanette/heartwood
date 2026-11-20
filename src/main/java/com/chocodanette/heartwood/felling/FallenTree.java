package com.chocodanette.heartwood.felling;

import com.chocodanette.heartwood.tree.BoughBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A tree lying on the ground, made of boughs: its trunk (the stem, from the cut end along the
 * thickest pieces, as long as they are at least half a block thick) and the branches it still carries. The trunk is bucked only once every branch,
 * leafy or bare, is off.
 */
public final class FallenTree {
    /** Thinner than this (half a block across), the top of a trunk is a branch. */
    public static final int TRUNK_RADIUS = 4;

    public final BlockPos base;
    public final List<BlockPos> stem = new ArrayList<>();
    public final Set<BlockPos> stemSet = new HashSet<>();
    /** The first piece of each branch still on the trunk. */
    public final List<BlockPos> branches = new ArrayList<>();
    /** Leafy branches still touching the trunk. */
    public final List<BlockPos> leafy = new ArrayList<>();

    private FallenTree(BlockPos base) {
        this.base = base;
    }

    /** The fallen tree the piece at pos belongs to. */
    public static FallenTree of(Level level, BlockPos pos) {
        // Down to the cut end: the piece whose parent is gone.
        BlockPos p = pos;
        for (int i = 0; i < 512; i++) {
            BlockPos par = BoughBlock.parentOf(level, p, level.getBlockState(p));
            if (par == null) break;
            p = par;
        }
        FallenTree t = new FallenTree(p);
        // Up the stem: always the thickest child, the straightest one when two are as thick.
        BlockPos cur = p;
        Direction along = null;
        for (int i = 0; i < 512 && cur != null; i++) {
            t.stem.add(cur);
            t.stemSet.add(cur);
            BlockPos next = null;
            int best = -1;
            for (Direction d : Direction.values()) {
                BlockPos n = cur.relative(d);
                BlockState ns = level.getBlockState(n);
                if (!(ns.getBlock() instanceof BoughBlock) || !cur.equals(BoughBlock.parentOf(level, n, ns))) continue;
                int score = ns.getValue(BoughBlock.RADIUS) * 2 + (d == along ? 1 : 0);
                if (score > best) { best = score; next = n; }
            }
            // The thin top of the trunk counts as a branch.
            if (next != null && level.getBlockState(next).getValue(BoughBlock.RADIUS) < TRUNK_RADIUS) next = null;
            if (next != null) along = Direction.getNearest(next.getX() - cur.getX(), next.getY() - cur.getY(), next.getZ() - cur.getZ());
            cur = next;
        }
        for (BlockPos s : t.stem) {
            for (Direction d : Direction.values()) {
                BlockPos n = s.relative(d);
                BlockState ns = level.getBlockState(n);
                if (ns.getBlock() instanceof BoughBlock && s.equals(BoughBlock.parentOf(level, n, ns)) && !t.stemSet.contains(n)) t.branches.add(n);
            }
            for (BlockPos n : BlockPos.betweenClosed(s.offset(-1, -1, -1), s.offset(1, 1, 1))) {
                if (level.getBlockState(n).getBlock() instanceof BranchesBlock) t.leafy.add(n.immutable());
            }
        }
        return t;
    }

    /** True once no branch of wood is left on the trunk (leftover leaves go as the trunk is bucked). */
    public boolean limbed() {
        return branches.isEmpty();
    }
}
