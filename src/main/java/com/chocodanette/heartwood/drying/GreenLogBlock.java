package com.chocodanette.heartwood.drying;

import com.chocodanette.heartwood.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
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
public class GreenLogBlock extends RotatedPillarBlock {
    public static final int MAX_STAGE = 7;
    public static final IntegerProperty DRYING = IntegerProperty.create("drying", 0, MAX_STAGE);

    /** Average game ticks between two random ticks of one block, at the default randomTickSpeed (3). */
    private static final double TICKS_PER_RANDOM_TICK = 4096.0 / 3.0;

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
        if (!isSheltered(level, pos)) return;
        // Chance per random tick so that the 8 stages take, on average, the configured number of days.
        double ticksPerStage = Config.dryingDays() * 24000.0 / (MAX_STAGE + 1);
        double chance = Math.min(1.0, TICKS_PER_RANDOM_TICK / ticksPerStage);
        if (random.nextDouble() >= chance) return;

        int stage = state.getValue(DRYING);
        if (stage < MAX_STAGE) {
            level.setBlock(pos, state.setValue(DRYING, stage + 1), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, dryLog.get().defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS)), Block.UPDATE_ALL);
        }
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
