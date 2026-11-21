package com.chocodanette.heartwood.net;

import com.chocodanette.heartwood.Heartwood;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/** Tells a player which blocks stand in the way of the tree they tried to fell, so they flash. */
public record FallBlockedPayload(List<BlockPos> obstacles) implements CustomPacketPayload {
    public static final Type<FallBlockedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Heartwood.MOD_ID, "fall_blocked"));
    public static final StreamCodec<ByteBuf, FallBlockedPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(512)), FallBlockedPayload::obstacles, FallBlockedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(TYPE, CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientSide.fallBlocked(payload.obstacles())));
    }

    /** Kept apart so a dedicated server never loads the client classes. */
    private static final class ClientSide {
        static void fallBlocked(List<BlockPos> obstacles) {
            com.chocodanette.heartwood.client.BranchFlash.flashBlocks(obstacles);
        }
    }
}
