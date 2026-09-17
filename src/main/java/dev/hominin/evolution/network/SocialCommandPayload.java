package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.band.Social;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Something said from the talk menu: to one member by entity id, or to the band nearby with -1. */
public record SocialCommandPayload(int entityId, int command) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SocialCommandPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "social_command"));

    public static final StreamCodec<ByteBuf, SocialCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SocialCommandPayload::entityId,
            ByteBufCodecs.VAR_INT, SocialCommandPayload::command,
            SocialCommandPayload::new);

    public static void handle(SocialCommandPayload payload, IPayloadContext context) {
        Social.Command command = Social.Command.byId(payload.command());
        if (command != null && context.player() instanceof ServerPlayer player) {
            Social.perform(player, payload.entityId(), command);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
