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

/** Picks one thing out of what a band member carries: a pack slot, or -1 for what is in its hand. */
public record TakeItemPayload(int entityId, int slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TakeItemPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "take_item"));

    public static final StreamCodec<ByteBuf, TakeItemPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TakeItemPayload::entityId,
            ByteBufCodecs.INT, TakeItemPayload::slot,
            TakeItemPayload::new);

    public static void handle(TakeItemPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Social.takeItem(player, payload.entityId(), payload.slot());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
