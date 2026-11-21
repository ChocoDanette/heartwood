package com.chocodanette.heartwood.felling;

import com.chocodanette.heartwood.tree.BoughBlock;

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
        // A felled young tree stops growing.
        com.chocodanette.heartwood.tree.GrowingTrees.get(level).forget(com.chocodanette.heartwood.tree.GrowingTrees.rootOf(level, cut));
        Set<BlockPos> logs = level.getBlockState(cut).getBlock() instanceof com.chocodanette.heartwood.tree.BoughBlock
                ? new HashSet<>(com.chocodanette.heartwood.tree.BoughBlock.descendants(level, cut, MAX_LOGS))
                : findLogs(level, cut);
        Set<BlockPos> leaves = ownLeaves(level, cut, logs, MAX_LEAVES);
        // A bend of the trunk towards the fall would come down into the ground: it is taken out
        // of the fall, and what stands above it comes down as if the trunk were straight.
        Set<BlockPos> bends = new HashSet<>();
        Map<BlockPos, Integer> shift = straighten(level, cut, dir, logs, leaves, bends);

        // What is seen falling (the standing tree) and what lands (lying trunk and branches).
        List<FallingTreeEntity.Part> parts = new ArrayList<>();
        Map<BlockPos, BlockState> moved = new LinkedHashMap<>();
        int height = 1;
        for (BlockPos p : logs) {
            BlockState s = level.getBlockState(p);
            parts.add(new FallingTreeEntity.Part(p.subtract(cut), s));
            moved.put(p, felled(rotateLog(com.chocodanette.heartwood.tree.BoughBlock.fallen(s, dir), dir)));
            height = Math.max(height, p.getY() - cut.getY());
        }
        for (BlockPos p : leaves) {
            BlockState s = branches(level.getBlockState(p));
            parts.add(new FallingTreeEntity.Part(p.subtract(cut), s));
            moved.put(p, s);
        }

        // Remove the standing tree, without triggering leaf decay. A tree of boughs leaves its
        // foot as a short stump, which rots away in a day or so; a plain log drops.
        BlockState foot = level.getBlockState(cut);
        Block stump = com.chocodanette.heartwood.ModBlocks.stumpFor(foot.getBlock());
        if (stump != null) {
            BlockState left = StumpBlock.of(stump, foot.getValue(com.chocodanette.heartwood.tree.BoughBlock.RADIUS), dir);
            // The upper part of the cut goes down with the tree, only to be seen falling: it is not laid on the ground.
            parts.add(new FallingTreeEntity.Part(BlockPos.ZERO, left.setValue(StumpBlock.TOP, true)));
            level.setBlock(cut, left, Block.UPDATE_ALL);
            StumpBlock.scheduleRot(level, cut);
        } else {
            Block.dropResources(cutLog, level, cut, null, player, player.getMainHandItem());
            level.setBlock(cut, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos p : moved.keySet()) {
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        // Rotate around the cut: height above the cut becomes distance along dir.
        Map<BlockPos, BlockState> target = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : moved.entrySet()) {
            if (bends.contains(e.getKey())) continue;
            BlockPos from = e.getKey().relative(dir.getOpposite(), shift.getOrDefault(e.getKey(), 0));
            target.putIfAbsent(rotate(from, cut, dir), e.getValue());
        }

        level.playSound(null, cut, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.7f, 0.6f);
        // Taller trees take longer to come down.
        int duration = Math.min(60, 20 + height * 2);
        FallingTreeEntity.spawn(level, cut, dir, parts, target, duration);
    }

    /**
     * How far back towards the cut each piece of a tree of boughs (and each leaf) comes down: the
     * number of steps its trunk takes towards the fall below it. Those steps (the bends) are put in
     * bends, to be left out of the fall. Empty for a tree of plain logs.
     */
    private static Map<BlockPos, Integer> straighten(ServerLevel level, BlockPos cut, Direction dir, Set<BlockPos> logs,
                                                     Set<BlockPos> leaves, Set<BlockPos> bends) {
        Map<BlockPos, Integer> shift = new java.util.HashMap<>();
        if (!(level.getBlockState(cut).getBlock() instanceof BoughBlock)) return shift;
        int steps = 0;
        BlockPos prev = null;
        for (BlockPos p : FallenTree.of(level, cut).stem) {
            if (prev != null && p.equals(prev.relative(dir))) {
                steps++;
                bends.add(p);
            }
            shift.put(p, steps);
            prev = p;
        }
        if (steps == 0) return new java.util.HashMap<>();
        // A branch comes down with the trunk piece it grows from.
        for (BlockPos p : logs) {
            if (shift.containsKey(p)) continue;
            List<BlockPos> chain = new ArrayList<>();
            BlockPos q = p;
            Integer found = null;
            while (q != null && chain.size() < MAX_LOGS) {
                found = shift.get(q);
                if (found != null) break;
                chain.add(q);
                q = BoughBlock.parentOf(level, q, level.getBlockState(q));
            }
            int v = found == null ? 0 : found;
            for (BlockPos c : chain) shift.put(c, v);
        }
        // A leaf, with the closest wood.
        List<BlockPos> wood = new ArrayList<>(logs);
        for (BlockPos leaf : leaves) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos w : wood) {
                double d = w.distSqr(leaf);
                if (d < bestD) { bestD = d; best = w; }
            }
            shift.put(leaf, best == null ? 0 : shift.getOrDefault(best, 0));
        }
        return shift;
    }

    /** Lays the fallen tree on the ground once the fall animation is over, and hurts what is under it. */
    static void land(ServerLevel level, BlockPos cut, Direction dir, Map<BlockPos, BlockState> target, Entity source) {
        // Lower the fallen tree until a log would touch the ground.
        int drop = 0;
        while (drop < MAX_DROP && canLower(level, target, drop + 1)) drop++;

        AABB trunkBox = null, crownBox = null;
        // Pieces over a block thick are first laid down a block wide, and only get their full width
        // (and the shells around them) once the whole tree is down: otherwise the shells of one
        // piece would take the place where other pieces of the tree have to land, and those pieces
        // (with all they carry) would break off.
        Map<BlockPos, BlockState> thick = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : target.entrySet()) {
            BlockPos p = e.getKey().below(drop);
            BlockState s = e.getValue();
            if (s.getBlock() instanceof com.chocodanette.heartwood.tree.BoughBlock
                    && s.getValue(com.chocodanette.heartwood.tree.BoughBlock.RADIUS) > 8) {
                thick.put(p, s);
                s = s.setValue(com.chocodanette.heartwood.tree.BoughBlock.RADIUS, 8);
            }
            AABB box = new AABB(p);
            if (isTrunk(s)) trunkBox = trunkBox == null ? box : trunkBox.minmax(box);
            else crownBox = crownBox == null ? box : crownBox.minmax(box);
            BlockState there = level.getBlockState(p);
            if (there.isAir() || there.canBeReplaced() || isTrunk(s) && isCrown(there)) {
                if (!there.isAir()) Block.dropResources(there, level, p);
                level.setBlock(p, s, Block.UPDATE_ALL);
                if (s.getBlock() instanceof com.chocodanette.heartwood.tree.BoughBlock) {
                    com.chocodanette.heartwood.drying.PlacedLogs.setFallen(level, p, true);
                }
                if (level.random.nextInt(3) == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s),
                            p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.1);
                }
            } else if (isTrunk(s)) {
                // Blocked by terrain: the log pops out as an item instead.
                Block.dropResources(s, level, p.above());
            }
        }
        for (Map.Entry<BlockPos, BlockState> e : thick.entrySet()) {
            BlockState now = level.getBlockState(e.getKey());
            if (now.getBlock() instanceof com.chocodanette.heartwood.tree.BoughBlock) {
                level.setBlock(e.getKey(), now.setValue(com.chocodanette.heartwood.tree.BoughBlock.RADIUS,
                        e.getValue().getValue(com.chocodanette.heartwood.tree.BoughBlock.RADIUS)), Block.UPDATE_ALL);
            }
        }
        // Pieces whose parent could not land (blocked by terrain) lose the arm reaching for it.
        for (BlockPos t : target.keySet()) {
            BlockPos p = t.below(drop);
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof com.chocodanette.heartwood.tree.BoughBlock)) continue;
            BlockPos par = com.chocodanette.heartwood.tree.BoughBlock.parentPos(s, p);
            if (par == null) continue;
            BlockState fixed = com.chocodanette.heartwood.tree.BoughBlock.reconnect(s, level.getBlockState(par));
            if (fixed != s) level.setBlock(p, fixed, Block.UPDATE_CLIENTS);
        }
        breakLoose(level, target.keySet(), drop);
        hurt(level, target.keySet(), drop, target, source);

        BlockPos middle = cut.relative(dir, Math.max(1, target.size() > 0 ? 4 : 1));
        level.playSound(null, middle, SoundEvents.WOOD_FALL, SoundSource.BLOCKS, 2.0f, 0.5f);
        level.playSound(null, middle, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0f, 0.5f);
    }

    /**
     * Whatever the fall left unjoined to the trunk breaks off: pieces whose parent could not land,
     * with all they carry, and leafy branches that no longer touch the wood.
     */
    private static void breakLoose(ServerLevel level, Set<BlockPos> targets, int drop) {
        List<BlockPos> pieces = new ArrayList<>();
        List<BlockPos> crown = new ArrayList<>();
        for (BlockPos t : targets) {
            BlockPos p = t.below(drop);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof com.chocodanette.heartwood.tree.BoughBlock) pieces.add(p);
            else if (s.getBlock() instanceof BranchesBlock) crown.add(p);
        }
        // The pieces hanging from nothing; the one carrying the most is the trunk.
        BlockPos main = null;
        int most = -1;
        Map<BlockPos, List<BlockPos>> anchors = new LinkedHashMap<>();
        for (BlockPos p : pieces) {
            if (com.chocodanette.heartwood.tree.BoughBlock.parentOf(level, p, level.getBlockState(p)) != null) continue;
            List<BlockPos> carried = com.chocodanette.heartwood.tree.BoughBlock.descendants(level, p, MAX_LOGS);
            anchors.put(p, carried);
            if (carried.size() > most) { most = carried.size(); main = p; }
        }
        for (Map.Entry<BlockPos, List<BlockPos>> e : anchors.entrySet()) {
            if (e.getKey().equals(main)) continue;
            for (BlockPos p : e.getValue()) level.destroyBlock(p, true);
            level.destroyBlock(e.getKey(), true);
        }
        // Leafy branches still joined to the wood, through each other.
        Set<BlockPos> crownSet = new HashSet<>(crown), kept = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos p : pieces) {
            if (level.getBlockState(p).getBlock() instanceof com.chocodanette.heartwood.tree.BoughBlock) queue.add(p);
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                BlockPos q = n.immutable();
                if (crownSet.contains(q) && kept.add(q)) queue.add(q);
            }
        }
        for (BlockPos p : crown) {
            if (!kept.contains(p) && level.getBlockState(p).getBlock() instanceof BranchesBlock) level.destroyBlock(p, true);
        }
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
                BlockState ns = level.getBlockState(n);
                if (ns.is(BlockTags.LOGS) && !isOtherTrunk(level, n, ns, cut)) {
                    found.add(n);
                    queue.add(n);
                }
            }
        }
        return found;
    }

    /**
     * True when the log at n is part of another standing trunk: an upright column that goes down
     * to the ground at or below the cut, away from the cut trunk (a 2x2 trunk stays one tree).
     */
    private static boolean isOtherTrunk(ServerLevel level, BlockPos n, BlockState s, BlockPos cut) {
        if (Math.max(Math.abs(n.getX() - cut.getX()), Math.abs(n.getZ() - cut.getZ())) <= 1) return false;
        if (s.hasProperty(BlockStateProperties.AXIS) && s.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) return false;
        BlockPos p = n;
        for (int i = 0; i < 64; i++) {
            BlockPos below = p.below();
            BlockState b = level.getBlockState(below);
            if (b.is(BlockTags.LOGS)) { p = below; continue; }
            return p.getY() <= cut.getY() && !b.isAir() && !b.canBeReplaced();
        }
        return false;
    }

    /**
     * Leaves of the felled tree only: where its crown touches another tree, each leaf goes with
     * the closest trunk or branch, so the neighbour keeps its own leaves.
     */
    /** Removes a whole standing tree of boughs at once, with nothing dropped (creative). */
    public static void removeWhole(ServerLevel level, BlockPos foot) {
        com.chocodanette.heartwood.tree.GrowingTrees.get(level).forget(foot);
        Set<BlockPos> logs = new HashSet<>(com.chocodanette.heartwood.tree.BoughBlock.descendants(level, foot, MAX_LOGS));
        Set<BlockPos> leaves = ownLeaves(level, foot, logs, MAX_LEAVES);
        for (BlockPos p : leaves) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (BlockPos p : logs) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(foot, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    static Set<BlockPos> ownLeaves(ServerLevel level, BlockPos cut, Set<BlockPos> logs, int max) {
        Set<BlockPos> ours = findLeaves(level, logs, max);
        if (ours.isEmpty()) return ours;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : ours) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        int r = LeavesBlock.DECAY_DISTANCE;
        Set<BlockPos> others = new HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(minX - r, minY - r, minZ - r, maxX + r, maxY + r, maxZ + r)) {
            if (logs.contains(p) || p.equals(cut)) continue;
            if (p.getX() == cut.getX() && p.getZ() == cut.getZ() && p.getY() < cut.getY()) continue; // our stump
            if (level.getBlockState(p).is(BlockTags.LOGS)) others.add(p.immutable());
        }
        if (others.isEmpty()) return ours;
        Map<BlockPos, Integer> mine = leafDistances(level, logs, max);
        Map<BlockPos, Integer> theirs = leafDistances(level, others, max * 4);
        ours.removeIf(p -> theirs.containsKey(p) && theirs.get(p) < mine.getOrDefault(p, Integer.MAX_VALUE));
        return ours;
    }

    /** Distance (in leaves) from the given logs to every natural leaf they hold. */
    private static Map<BlockPos, Integer> leafDistances(ServerLevel level, Set<BlockPos> logs, int max) {
        Map<BlockPos, Integer> dist = new java.util.HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos log : logs) { queue.add(log); dist.put(log, 0); }
        int leaves = 0;
        while (!queue.isEmpty() && leaves < max) {
            BlockPos p = queue.poll();
            int d = dist.get(p);
            if (d >= LeavesBlock.DECAY_DISTANCE - 1) continue;
            for (Direction side : Direction.values()) {
                BlockPos n = p.relative(side);
                if (dist.containsKey(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
                    dist.put(n, d + 1);
                    leaves++;
                    queue.add(n);
                }
            }
        }
        return dist;
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
