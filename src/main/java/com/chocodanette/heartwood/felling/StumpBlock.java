package com.chocodanette.heartwood.felling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a felled tree of boughs leaves: a short stump, its front lowered by the notch, a ragged hinge
 * of wood across it and its back cut a little higher, facing the side the tree fell. It can't be
 * cut: it rots away in a day or so.
 */
public class StumpBlock extends Block {
    public static final IntegerProperty RADIUS = IntegerProperty.create("radius", 1, 16);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape[] SHAPES = new VoxelShape[17];

    public StumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RADIUS, 8).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RADIUS, FACING);
    }

    public static BlockState of(Block stump, int radius, Direction fell) {
        return stump.defaultBlockState().setValue(RADIUS, Math.max(1, Math.min(16, radius))).setValue(FACING, fell);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int r = state.getValue(RADIUS);
        VoxelShape s = SHAPES[r];
        if (s == null) {
            double half = Math.min(8, r);
            SHAPES[r] = s = Block.box(8 - half, 0, 8 - half, 8 + half, 7, 8 + half);
        }
        return s;
    }

    /** A stump can't be cut: it is left to rot. */
    @Override
    protected float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos) {
        return 0f;
    }

    /** The stump rots away in a day or so. */
    public static void scheduleRot(Level level, BlockPos pos) {
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 20000 + level.random.nextInt(8000));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.levelEvent(2001, pos, Block.getId(state));
        level.removeBlock(pos, false);
    }
}
