package com.chocodanette.heartwood.drying;

import com.chocodanette.heartwood.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.HitResult;

import java.util.function.Supplier;

/**
 * A freshly cut (green) log, placed as a block. It only dries when sheltered: no sky
 * above it, so no rain or snow reaches it. Drying goes through stages 0..7, then
 * the block turns into the dry vanilla log.
 */
public class GreenLogBlock extends RotatedPillarBlock implements EntityBlock {
    public static final int MAX_STAGE = 7;
    public static final IntegerProperty DRYING = IntegerProperty.create("drying", 0, MAX_STAGE);

    private final Supplier<Block> dryLog;

    public GreenLogBlock(Supplier<Block> dryLog, Properties properties) {
        super(properties);
        this.dryLog = dryLog;
        registerDefaultState(defaultBlockState().setValue(DRYING, 0));
    }

    public Block dryLog() {
        return dryLog.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DRYING);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof DryingLogBlockEntity drying)) return;
        long dried = drying.update(level, isSheltered(level, pos));
        long total = (long) (Config.dryingDays() * 24000.0 * drying.spread());
        if (dried >= total) {
            level.setBlock(pos, dryLog.get().defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS)), Block.UPDATE_ALL);
            PlacedLogs.markDry(level, pos);
            return;
        }
        int stage = (int) Math.min(MAX_STAGE, dried * (MAX_STAGE + 1) / total);
        if (stage != state.getValue(DRYING)) level.setBlock(pos, state.setValue(DRYING, stage), Block.UPDATE_ALL);
    }

    /** Damp wood: water drips from the underside of fresh logs. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(DRYING) >= MAX_STAGE - 1 || random.nextInt(6) != 0) return;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER,
                    pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() - 0.05,
                    pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0, 0, 0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DryingLogBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Start the drying clock now rather than at the first random tick.
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof DryingLogBlockEntity drying) drying.update(level, false);
    }

    /** Sheltered: the sky cannot be seen from the block, so rain cannot reach it. */
    public static boolean isSheltered(LevelReader level, BlockPos pos) {
        return !level.canSeeSky(pos.above());
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(this);
    }
}
