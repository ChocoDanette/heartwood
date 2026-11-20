package com.chocodanette.heartwood.tree;

import com.chocodanette.heartwood.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.List;

/** A tree seed: planted on top of earth, it makes a seed mound that will come up as a shoot. */
public class SeedItem extends Item {
    /** A tree the seed may grow into, and how often. */
    public record Kind(ResourceLocation feature, int weight) {}

    private final List<Kind> kinds;

    public SeedItem(List<Kind> kinds, Properties properties) {
        super(properties);
        this.kinds = kinds;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos ground = context.getClickedPos();
        BlockPos pos = ground.above();
        if (context.getClickedFace() != Direction.UP || !level.getBlockState(ground).is(BlockTags.DIRT)
                || !level.getBlockState(pos).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            server.setBlock(pos, ModBlocks.SEED_MOUND.get().defaultBlockState(), Block.UPDATE_ALL);
            GrowingTrees.get(server).plant(server, pos, ResourceKey.create(Registries.CONFIGURED_FEATURE, pick(server)),
                    BuiltInRegistries.ITEM.getKey(this));
            server.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (context.getPlayer() == null || !context.getPlayer().isCreative()) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private ResourceLocation pick(ServerLevel level) {
        int total = kinds.stream().mapToInt(Kind::weight).sum();
        int roll = level.random.nextInt(Math.max(1, total));
        for (Kind k : kinds) {
            if ((roll -= k.weight()) < 0) return k.feature();
        }
        return kinds.get(0).feature();
    }
}
