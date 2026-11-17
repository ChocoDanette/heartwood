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

    public static final IntegerProperty RADIUS = IntegerProperty.create("radius", 1, 8);
    public static final EnumProperty<Parent> PARENT = EnumProperty.create("parent", Parent.class);
    public static final IntegerProperty PARENT_RADIUS = IntegerProperty.create("parent_radius", 1, 8);

    private static final VoxelShape[][][] SHAPES = new VoxelShape[9][Parent.values().length][9];

    private final Supplier<Block> log;

    public BoughBlock(Supplier<Block> log, BlockBehaviour.Properties properties) {
        super(properties);
        this.log = log;
        registerDefaultState(stateDefinition.any().setValue(RADIUS, 8).setValue(PARENT, Parent.DOWN).setValue(PARENT_RADIUS, 8));
    }

    /** The log this tree is made of. */
    public Block log() {
        return log.get();
    }

    public static BlockState state(Block bough, int radius, Direction parent, int parentRadius) {
        return bough.defaultBlockState().setValue(RADIUS, radius).setValue(PARENT, Parent.of(parent))
                .setValue(PARENT_RADIUS, Math.max(radius, Math.min(8, parentRadius)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RADIUS, PARENT, PARENT_RADIUS);
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
        float scale = 8f / (state.getValue(RADIUS) + 0.0f);
        return super.getDestroyProgress(state, player, level, pos) * Math.min(4f, scale);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
