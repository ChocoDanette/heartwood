package com.chocodanette.heartwood.tree;

import com.chocodanette.heartwood.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * The outer part of a trunk more than a block thick. It draws nothing (the trunk piece it
 * belongs to, its core, draws the whole round trunk) but takes the trunk's shape within its own
 * block, so the trunk can be hit, bumped into and cut from any side. Hitting a shell works on its
 * core; a shell goes when its core goes or thins down.
 */
public class TrunkShellBlock extends Block {
    /** Offset to the core, each from 0 to 2 (1 = same coordinate). */
    public static final IntegerProperty CX = IntegerProperty.create("cx", 0, 2);
    public static final IntegerProperty CY = IntegerProperty.create("cy", 0, 2);
    public static final IntegerProperty CZ = IntegerProperty.create("cz", 0, 2);

    private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public TrunkShellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CX, 1).setValue(CY, 1).setValue(CZ, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CX, CY, CZ);
    }

    public static BlockState pointingTo(BlockPos shell, BlockPos core) {
        return ModBlocks.TRUNK_SHELL.get().defaultBlockState()
                .setValue(CX, core.getX() - shell.getX() + 1)
                .setValue(CY, core.getY() - shell.getY() + 1)
                .setValue(CZ, core.getZ() - shell.getZ() + 1);
    }

    public static BlockPos core(BlockState shell, BlockPos pos) {
        return pos.offset(shell.getValue(CX) - 1, shell.getValue(CY) - 1, shell.getValue(CZ) - 1);
    }

    /** Removes the shells around a core. */
    public static void clear(Level level, BlockPos core) {
        for (BlockPos p : BlockPos.betweenClosed(core.offset(-1, -1, -1), core.offset(1, 1, 1))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof TrunkShellBlock && core.equals(core(s, p))) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** The core piece this shell belongs to, if it is still a thick piece. */
    private static BlockState coreState(BlockState shell, BlockGetter level, BlockPos pos) {
        BlockState c = level.getBlockState(core(shell, pos));
        return c.getBlock() instanceof BoughBlock && c.getValue(BoughBlock.RADIUS) > 8 ? c : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockState c = coreState(state, level, pos);
        if (c == null) return Shapes.empty();
        int r = c.getValue(BoughBlock.RADIUS);
        Direction.Axis axis = BoughBlock.axis(c);
        int dx = state.getValue(CX) - 1, dy = state.getValue(CY) - 1, dz = state.getValue(CZ) - 1;
        long key = ((((long) r * 3 + axis.ordinal()) * 3 + dx + 1) * 3 + dy + 1) * 3 + dz + 1;
        return SHAPES.computeIfAbsent(key, k -> {
            // The core's box, in this block's coordinates, clipped to this block.
            double lo = 8 - r, hi = 8 + r;
            double x0 = axis == Direction.Axis.X ? 0 : lo, x1 = axis == Direction.Axis.X ? 16 : hi;
            double y0 = axis == Direction.Axis.Y ? 0 : lo, y1 = axis == Direction.Axis.Y ? 16 : hi;
            double z0 = axis == Direction.Axis.Z ? 0 : lo, z1 = axis == Direction.Axis.Z ? 16 : hi;
            x0 += dx * 16; x1 += dx * 16; y0 += dy * 16; y1 += dy * 16; z0 += dz * 16; z1 += dz * 16;
            x0 = Math.max(0, x0); y0 = Math.max(0, y0); z0 = Math.max(0, z0);
            x1 = Math.min(16, x1); y1 = Math.min(16, y1); z1 = Math.min(16, z1);
            return x1 > x0 && y1 > y0 && z1 > z0 ? Block.box(x0, y0, z0, x1, y1, z1) : Shapes.empty();
        });
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockState c = coreState(state, level, pos);
        return c == null ? 1f : c.getDestroyProgress(player, level, core(state, pos));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return coreState(state, level, pos) == null ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
