package com.chocodanette.heartwood.drying;

import com.chocodanette.heartwood.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drying progress of a placed green log, in game ticks spent sheltered. Progress is
 * caught up from the clock at each check, so it does not depend on the luck of
 * random ticks, and it counts time skipped by sleeping and time spent unloaded.
 */
public class DryingLogBlockEntity extends BlockEntity {
    private long dried;
    private long lastGameTime = -1;
    private long lastDayTime = -1;
    /** Per-log spread of the drying time (0.75 to 1.25), so a stack does not dry all at once. */
    private float spread = -1;

    public DryingLogBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DRYING_LOG.get(), pos, state);
    }

    /** Adds the time elapsed since the last check (only if sheltered) and returns the total. */
    public long update(Level level, boolean sheltered) {
        if (spread < 0) spread = 0.75f + level.random.nextFloat() * 0.5f;
        long game = level.getGameTime(), day = level.getDayTime();
        if (lastGameTime >= 0) {
            long dGame = Math.max(0, game - lastGameTime);
            long dDay = day - lastDayTime;
            // Sleeping skips day time but not game time; take the larger of the two.
            long elapsed = dDay >= 0 ? Math.max(dGame, dDay) : dGame;
            if (sheltered) dried += elapsed;
        }
        lastGameTime = game;
        lastDayTime = day;
        setChanged();
        return dried;
    }

    public float spread() {
        return spread < 0 ? 1f : spread;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("dried", dried);
        tag.putLong("lastGameTime", lastGameTime);
        tag.putLong("lastDayTime", lastDayTime);
        tag.putFloat("spread", spread);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        dried = tag.getLong("dried");
        lastGameTime = tag.contains("lastGameTime") ? tag.getLong("lastGameTime") : -1;
        lastDayTime = tag.contains("lastDayTime") ? tag.getLong("lastDayTime") : -1;
        spread = tag.contains("spread") ? tag.getFloat("spread") : -1;
    }
}
