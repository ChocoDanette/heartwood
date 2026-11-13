package com.chocodanette.heartwood.felling;

import com.chocodanette.heartwood.ModBlocks;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;
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

        // What is seen falling (the standing tree) and what lands (lying trunk and branches).
        List<FallingTreeEntity.Part> parts = new ArrayList<>();
        Map<BlockPos, BlockState> moved = new LinkedHashMap<>();
        int height = 1;
        for (BlockPos p : logs) {
            BlockState s = level.getBlockState(p);
            parts.add(new FallingTreeEntity.Part(p.subtract(cut), s));
            moved.put(p, felled(rotateLog(s, dir)));
            height = Math.max(height, p.getY() - cut.getY());
        }
        for (BlockPos p : leaves) {
            BlockState s = branches(level.getBlockState(p));
            parts.add(new FallingTreeEntity.Part(p.subtract(cut), s));
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

        level.playSound(null, cut, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.7f, 0.6f);
        // Taller trees take longer to come down.
        int duration = Math.min(60, 20 + height * 2);
        FallingTreeEntity.spawn(level, cut, dir, parts, target, duration);
    }

    /** Lays the fallen tree on the ground once the fall animation is over, and hurts what is under it. */
    static void land(ServerLevel level, BlockPos cut, Direction dir, Map<BlockPos, BlockState> target, Entity source) {
        // Lower the fallen tree until a log would touch the ground.
        int drop = 0;
        while (drop < MAX_DROP && canLower(level, target, drop + 1)) drop++;

        AABB trunkBox = null, crownBox = null;
        for (Map.Entry<BlockPos, BlockState> e : target.entrySet()) {
            BlockPos p = e.getKey().below(drop);
            BlockState s = e.getValue();
            AABB box = new AABB(p);
            if (isTrunk(s)) trunkBox = trunkBox == null ? box : trunkBox.minmax(box);
            else crownBox = crownBox == null ? box : crownBox.minmax(box);
            BlockState there = level.getBlockState(p);
            if (there.isAir() || there.canBeReplaced() || isTrunk(s) && isCrown(there)) {
                if (!there.isAir()) Block.dropResources(there, level, p);
                level.setBlock(p, s, Block.UPDATE_ALL);
                if (level.random.nextInt(3) == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s),
                            p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.1);
                }
            } else if (isTrunk(s)) {
                // Blocked by terrain: the log pops out as an item instead.
                Block.dropResources(s, level, p.above());
            }
        }
        hurt(level, target.keySet(), drop, target, source);

        BlockPos middle = cut.relative(dir, Math.max(1, target.size() > 0 ? 4 : 1));
        level.playSound(null, middle, SoundEvents.WOOD_FALL, SoundSource.BLOCKS, 2.0f, 0.5f);
        level.playSound(null, middle, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0f, 0.5f);
    }

    /** Living things under the trunk take heavy damage; under the branches, light damage. */
    private static void hurt(ServerLevel level, Set<BlockPos> positions, int drop, Map<BlockPos, BlockState> target, Entity source) {
        int trunkLength = 0;
        for (BlockState s : target.values()) if (isTrunk(s)) trunkLength++;
        float trunkDamage = Math.min(20f, 6f + trunkLength);
        Map<LivingEntity, Float> hits = new java.util.HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : target.entrySet()) {
            BlockPos p = e.getKey().below(drop);
            float damage = isTrunk(e.getValue()) ? trunkDamage : 2f;
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(p))) {
                hits.merge(living, damage, Math::max);
            }
        }
        hits.forEach((living, damage) -> living.hurt(level.damageSources().fallingBlock(source), damage));
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
            if (!isTrunk(e.getValue())) continue;
            BlockPos below = e.getKey().below(drop);
            BlockState s = level.getBlockState(below);
            if (!(s.isAir() || s.canBeReplaced() || isCrown(s))) return false;
        }
        return true;
    }

    /** A lying trunk section, keeping the log's (already rotated) axis. */
    static BlockState felled(BlockState log) {
        Block felled = ModBlocks.felledFor(log.getBlock());
        if (felled == null) return log;
        BlockState s = felled.defaultBlockState();
        if (log.hasProperty(BlockStateProperties.AXIS)) s = s.setValue(BlockStateProperties.AXIS, log.getValue(BlockStateProperties.AXIS));
        return s;
    }

    /** The branches block for these leaves; unknown leaves just stop decaying. */
    static BlockState branches(BlockState leaves) {
        Block branches = ModBlocks.branchesFor(leaves.getBlock());
        if (branches != null) return branches.defaultBlockState();
        if (leaves.hasProperty(LeavesBlock.PERSISTENT)) return leaves.setValue(LeavesBlock.PERSISTENT, true);
        return leaves;
    }

    static boolean isTrunk(BlockState s) {
        return s.getBlock() instanceof FelledLogBlock || s.is(BlockTags.LOGS);
    }

    static boolean isCrown(BlockState s) {
        return s.getBlock() instanceof BranchesBlock || s.is(BlockTags.LEAVES);
    }

    private TreeFeller() {}
}
