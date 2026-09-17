package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Whether a player is climbing. Other players need it for the animation; the climber
 * only receives it when the server overrules them - a refused start, or rotten wood
 * giving way - and {@code regripTicks} is how long before they may try again.
 */
public record ClimbStatePayload(int entityId, boolean climbing, int regripTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClimbStatePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "climb_state"));

    public static final StreamCodec<ByteBuf, ClimbStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClimbStatePayload::entityId,
            ByteBufCodecs.BOOL, ClimbStatePayload::climbing,
            ByteBufCodecs.VAR_INT, ClimbStatePayload::regripTicks,
            ClimbStatePayload::new);

    public static void handle(ClimbStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.ClientSync.climbState(
                payload.entityId(), payload.climbing(), payload.regripTicks()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
