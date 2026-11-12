package com.chocodanette.heartwood.felling;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import java.util.function.Supplier;

/** A section of a felled trunk lying on the ground. It is bucked (cut) with an axe once limbed. */
public class FelledLogBlock extends RotatedPillarBlock {
    private final Supplier<Block> log;

    public FelledLogBlock(Supplier<Block> log, Properties properties) {
        super(properties);
        this.log = log;
    }

    public Block log() {
        return log.get();
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(log.get());
    }
}
