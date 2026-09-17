package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.climb.ClimbingServer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The client starting or stopping a climb. Only the client knows which keys are held,
 * so it decides; the server can still refuse, and says so with a
 * {@link ClimbStatePayload}.
 */
public record ClimbPayload(boolean climbing) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClimbPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "climb"));

    public static final StreamCodec<ByteBuf, ClimbPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClimbPayload::climbing,
            ClimbPayload::new);

    public static void handle(ClimbPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ClimbingServer.request(player, payload.climbing());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
