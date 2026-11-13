package com.chocodanette.heartwood.drying;

import com.chocodanette.heartwood.Heartwood;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Remembers, per chunk, the vanilla logs that are dry: placed by a player, or dried
 * from green logs. Every other vanilla log is natural wood and breaks into green wood.
 */
public final class PlacedLogs {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Heartwood.MOD_ID);

    private static final Codec<Set<Long>> CODEC = Codec.LONG.listOf().xmap(HashSet::new, ArrayList::new);

    public static final Supplier<AttachmentType<Set<Long>>> DRY_LOGS = ATTACHMENTS.register("dry_logs",
            () -> AttachmentType.<Set<Long>>builder(() -> new HashSet<>()).serialize(CODEC).build());

    public static boolean isDry(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.hasData(DRY_LOGS) && chunk.getData(DRY_LOGS).contains(pos.asLong());
    }

    public static void markDry(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (chunk.getData(DRY_LOGS).add(pos.asLong())) chunk.setUnsaved(true);
    }

    public static void forget(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (chunk.hasData(DRY_LOGS) && chunk.getData(DRY_LOGS).remove(pos.asLong())) chunk.setUnsaved(true);
    }

    private PlacedLogs() {}
}
