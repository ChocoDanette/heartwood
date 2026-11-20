package com.chocodanette.heartwood.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A piece of a living tree, of variable thickness, in the manner of Dynamic Trees.
 * RADIUS is the half-thickness in pixels (1 = twig, 8 = full block). Each piece knows the side
 * of its parent (towards the roots) and the parent's radius, so that it can draw the joint:
 * its own core, plus an arm reaching the surface of the parent's core.
 * A trunk can grow thicker than a block (RADIUS 9 to 16, up to two blocks across): it is then
 * drawn round across its neighbours, and invisible {@link TrunkShellBlock}s fill the ring of
 * blocks around it so that it can be hit and walked into.
 */
public class BoughBlock extends Block {
    public enum Parent implements StringRepresentable {
        DOWN(Direction.DOWN), UP(Direction.UP), NORTH(Direction.NORTH), SOUTH(Direction.SOUTH),
        WEST(Direction.WEST), EAST(Direction.EAST), NONE(null);

        public final Direction dir;
        Parent(Direction dir) { this.dir = dir; }

        public static Parent of(Direction d) {
            for (Parent p : values()) if (p.dir == d) return p;
            return NONE;
        }

        @Override
        public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** The face carved by the felling notch (only on vertical trunk pieces). */
    public enum Notch implements StringRepresentable {
        NONE(null), NORTH(Direction.NORTH), SOUTH(Direction.SOUTH), WEST(Direction.WEST), EAST(Direction.EAST);

        public final Direction dir;
        Notch(Direction dir) { this.dir = dir; }

        public static Notch of(Direction d) {
            for (Notch n : values()) if (n.dir == d) return n;
            return NONE;
        }

        @Override
        public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** Thickest piece: a trunk two blocks across. Pieces over 8 overflow into a ring of shells. */
    public static final int MAX_RADIUS = 16;
    public static final EnumProperty<Notch> NOTCH = EnumProperty.create("notch", Notch.class);
    public static final IntegerProperty RADIUS = IntegerProperty.create("radius", 1, MAX_RADIUS);
    public static final EnumProperty<Parent> PARENT = EnumProperty.create("parent", Parent.class);
    public static final IntegerProperty PARENT_RADIUS = IntegerProperty.create("parent_radius", 1, 8);

    private static final VoxelShape[][][] SHAPES = new VoxelShape[MAX_RADIUS + 1][Parent.values().length][9];

    private final Supplier<Block> log;

    public BoughBlock(Supplier<Block> log, BlockBehaviour.Properties properties) {
        super(properties);
        this.log = log;
        registerDefaultState(stateDefinition.any().setValue(RADIUS, 8).setValue(PARENT, Parent.DOWN).setValue(PARENT_RADIUS, 8).setValue(NOTCH, Notch.NONE));
    }

    /** The log this tree is made of. */
    public Block log() {
        return log.get();
    }

    public static BlockState state(Block bough, int radius, Direction parent, int parentRadius) {
        return bough.defaultBlockState().setValue(RADIUS, radius).setValue(PARENT, Parent.of(parent))
                .setValue(PARENT_RADIUS, Math.min(8, Math.max(radius, parentRadius)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RADIUS, PARENT, PARENT_RADIUS, NOTCH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int r = state.getValue(RADIUS);
        Parent p = state.getValue(PARENT);
        int pr = state.getValue(PARENT_RADIUS);
        VoxelShape s = SHAPES[r][p.ordinal()][pr];
        if (s == null) SHAPES[r][p.ordinal()][pr] = s = shape(r, p, pr);
        return s;
    }

    private static VoxelShape shape(int r, Parent p, int pr) {
        if (r >= 8) return Shapes.block();
        double lo = 8 - r, hi = 8 + r;
        VoxelShape core = Block.box(lo, lo, lo, hi, hi, hi);
        if (p == Parent.NONE || r == 8) return core;
        // The arm, clipped to this block.
        VoxelShape arm = switch (p) {
            case DOWN -> Block.box(lo, 0, lo, hi, lo, hi);
            case UP -> Block.box(lo, hi, lo, hi, 16, hi);
            case NORTH -> Block.box(lo, lo, 0, hi, hi, lo);
            case SOUTH -> Block.box(lo, lo, hi, hi, hi, 16);
            case WEST -> Block.box(0, lo, lo, lo, hi, hi);
            case EAST -> Block.box(hi, lo, lo, 16, hi, hi);
            default -> Shapes.empty();
        };
        return Shapes.or(core, arm);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Twigs snap quickly; a full trunk is as hard as a log.
        // The trunk of a fallen tree can't be cut into while branches are still on it, and the trunk
        // of a standing tree only at its foot (where it is notched).
        if (level instanceof net.minecraft.world.level.Level l) {
            com.chocodanette.heartwood.felling.FallenTree tree = com.chocodanette.heartwood.felling.FallenTree.of(l, pos);
            if (tree.stemSet.contains(pos)) {
                if (isRooted(l, pos)) {
                    if (!tree.base.equals(pos) && state.getValue(NOTCH) == Notch.NONE) return 0f;
                } else if (!tree.limbed()) {
                    return 0f;
                }
            }
        }
        float scale = 8f / (state.getValue(RADIUS) + 0.0f);
        return super.getDestroyProgress(state, player, level, pos) * Math.min(4f, scale);
    }

    /** The neighbour this piece hangs from, or null for a root or a loose piece. */
    public static BlockPos parentPos(BlockState state, BlockPos pos) {
        Direction d = state.getValue(PARENT).dir;
        return d == null ? null : pos.relative(d);
    }

    /**
     * The piece this one really hangs from, or null. A notched piece (a foot being felled, or the
     * stump a fallen tree leaves) only carries the piece right above it, so a fallen trunk lying
     * against its stump is never taken as hanging from it.
     */
    public static BlockPos parentOf(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos par = parentPos(state, pos);
        if (par == null) return null;
        BlockState ps = level.getBlockState(par);
        if (!(ps.getBlock() instanceof BoughBlock)) return null;
        if (ps.getValue(NOTCH) != Notch.NONE && !par.equals(pos.below())) return null;
        return par;
    }

    /** True when this piece belongs to a standing tree: following parents leads down into the ground. */
    public static boolean isRooted(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockPos p = pos;
        BlockState s = level.getBlockState(p);
        for (int i = 0; i < 256 && s.getBlock() instanceof BoughBlock; i++) {
            if (com.chocodanette.heartwood.drying.PlacedLogs.isFallen(level, p)) return false;
            Parent parent = s.getValue(PARENT);
            if (parent == Parent.NONE) return false;
            BlockPos next = p.relative(parent.dir);
            BlockState ns = level.getBlockState(next);
            if (!(ns.getBlock() instanceof BoughBlock)) return parent == Parent.DOWN && !ns.isAir() && !ns.canBeReplaced();
            if (ns.getValue(NOTCH) != Notch.NONE && parent != Parent.DOWN) return false;   // lying against a stump
            p = next;
            s = ns;
        }
        return false;
    }

    /** Every piece that hangs from this one, directly or not (twigs first). */
    public static java.util.List<BlockPos> descendants(net.minecraft.world.level.Level level, BlockPos pos, int max) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(pos);
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        seen.add(pos);
        while (!queue.isEmpty() && out.size() < max) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n)) continue;
                BlockState ns = level.getBlockState(n);
                if (ns.getBlock() instanceof BoughBlock && p.equals(parentOf(level, n, ns))) {
                    seen.add(n);
                    out.add(n);
                    queue.add(n);
                }
            }
        }
        java.util.Collections.reverse(out);
        return out;
    }

    /** The state of a piece once its tree has fallen towards dir: the parent side turns with the tree. */
    public static BlockState fallen(BlockState s, Direction dir) {
        if (!(s.getBlock() instanceof BoughBlock)) return s;
        Direction d = s.getValue(PARENT).dir;
        if (d != null) {
            if (d == Direction.UP) d = dir;
            else if (d == Direction.DOWN) d = dir.getOpposite();
            else if (d == dir) d = Direction.DOWN;
            else if (d == dir.getOpposite()) d = Direction.UP;
        }
        return s.setValue(PARENT, Parent.of(d)).setValue(NOTCH, Notch.NONE);
    }

    /** When the piece it hangs from changes or goes away, the joint follows (or the arm disappears). */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return dir == state.getValue(PARENT).dir ? reconnect(state, neighbor) : state;
    }

    /**
     * The piece's state given what now stands on its parent side: another piece (the arm meets its
     * core), the ground under a trunk, or nothing, in which case the piece keeps only its core.
     */
    public static BlockState reconnect(BlockState state, BlockState parent) {
        if (!(state.getBlock() instanceof BoughBlock)) return state;
        Direction d = state.getValue(PARENT).dir;
        if (d == null) return state;
        boolean stump = parent.getBlock() instanceof BoughBlock && parent.getValue(NOTCH) != Notch.NONE && d != Direction.DOWN;
        if (parent.getBlock() instanceof BoughBlock && !stump) return state.setValue(PARENT_RADIUS, Math.min(8, parent.getValue(RADIUS)));
        if (d == Direction.DOWN && !parent.isAir() && !parent.canBeReplaced()) return state;
        // A trunk over a block thick has no arm to lose, and keeps lying along its axis.
        if (state.getValue(RADIUS) > 8) return state.setValue(NOTCH, Notch.NONE);
        return state.setValue(PARENT, Parent.NONE).setValue(NOTCH, Notch.NONE);
    }

    /** The axis a piece lies along: towards its parent (upright when it has none). */
    public static Direction.Axis axis(BlockState state) {
        Direction d = state.getValue(PARENT).dir;
        return d == null ? Direction.Axis.Y : d.getAxis();
    }

    /** The eight blocks around a piece, square to its axis: where the shells of a thick piece go. */
    public static java.util.List<BlockPos> ring(BlockPos pos, Direction.Axis axis) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>(8);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                out.add(switch (axis) {
                    case Y -> pos.offset(a, 0, b);
                    case X -> pos.offset(0, a, b);
                    case Z -> pos.offset(a, b, 0);
                });
            }
        }
        return out;
    }

    /** True when a shell of the piece at core may stand at pos (nothing solid is in the way). */
    public static boolean shellFits(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockPos core) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof TrunkShellBlock) return core.equals(TrunkShellBlock.core(s, pos));
        return s.isAir() || s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.getBlock() instanceof BoughBlock;
    }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level.isClientSide) return;
        boolean wasThick = old.getBlock() instanceof BoughBlock && old.getValue(RADIUS) > 8;
        if (wasThick && (state.getValue(RADIUS) <= 8 || axis(old) != axis(state))) TrunkShellBlock.clear(level, pos);
        if (state.getValue(RADIUS) > 8) {
            for (BlockPos p : ring(pos, axis(state))) {
                BlockState there = level.getBlockState(p);
                if (there.getBlock() instanceof BoughBlock || there.getBlock() instanceof TrunkShellBlock) continue;
                if (shellFits(level, p, pos)) level.setBlock(p, TrunkShellBlock.pointingTo(p, pos), Block.UPDATE_ALL);
            }
        }
    }

    /** A stump left by a felled tree rots away. */
    public static void scheduleRot(net.minecraft.world.level.Level level, BlockPos pos) {
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 20000 + level.random.nextInt(8000));
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(NOTCH) == Notch.NONE || !descendants(level, pos, 1).isEmpty()) return;
        level.levelEvent(2001, pos, Block.getId(state));
        level.removeBlock(pos, false);
    }

    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !newState.is(state.getBlock())) {
            if (state.getValue(RADIUS) > 8) TrunkShellBlock.clear(level, pos);
            com.chocodanette.heartwood.drying.PlacedLogs.setFallen(level, pos, false);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
