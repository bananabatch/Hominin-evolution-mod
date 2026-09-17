package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Plays a one-shot whole-body animation on a player, for everyone who can see them. */
public record BodyAnimationPayload(int entityId, String animation) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BodyAnimationPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "body_animation"));

    public static final StreamCodec<ByteBuf, BodyAnimationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BodyAnimationPayload::entityId,
            ByteBufCodecs.STRING_UTF8, BodyAnimationPayload::animation,
            BodyAnimationPayload::new);

    public static void handle(BodyAnimationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.ClientSync.bodyAnimation(
                payload.entityId(), payload.animation()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
