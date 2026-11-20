package com.chocodanette.heartwood.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A planted seed: a small mound of earth, until the seed comes up as a shoot (see {@link GrowingTrees}).
 * Digging it up gives the seed back.
 */
public class SeedMoundBlock extends Block {
    /** What the mound is made of: the ground the seed was planted in. */
    public enum Ground implements net.minecraft.util.StringRepresentable {
        DIRT, GRASS, COARSE_DIRT, PODZOL, MYCELIUM, ROOTED_DIRT, MUD, MOSS;

        public static Ground of(BlockState ground) {
            if (ground.is(Blocks.GRASS_BLOCK)) return GRASS;
            if (ground.is(Blocks.COARSE_DIRT)) return COARSE_DIRT;
            if (ground.is(Blocks.PODZOL)) return PODZOL;
            if (ground.is(Blocks.MYCELIUM)) return MYCELIUM;
            if (ground.is(Blocks.ROOTED_DIRT)) return ROOTED_DIRT;
            if (ground.is(Blocks.MUD) || ground.is(Blocks.MUDDY_MANGROVE_ROOTS)) return MUD;
            if (ground.is(Blocks.MOSS_BLOCK)) return MOSS;
            return DIRT;
        }

        @Override
        public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Ground> GROUND =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("ground", Ground.class);

    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(5, 2, 5, 11, 3, 11));

    public SeedMoundBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GROUND, Ground.DIRT));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GROUND);
    }

    /** The mound for a seed planted in this ground. */
    public BlockState on(BlockState ground) {
        return defaultBlockState().setValue(GROUND, Ground.of(ground));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.DIRT);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (dir != Direction.DOWN) return state;
        return canSurvive(state, level, pos) ? state.setValue(GROUND, Ground.of(neighbor)) : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !newState.is(this) && !(newState.getBlock() instanceof BoughBlock)) {
            GrowingTrees data = GrowingTrees.get(server);
            data.plantedSeed(pos).ifPresent(seed -> Block.popResource(level, pos, seed));
            data.forget(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
