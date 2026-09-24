package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The gift chosen: which stacks, from whose hands. Checked again on the server before anything moves. */
public record GiftOfferPayload(String band, List<Integer> sources, List<Integer> slots, List<Integer> counts)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GiftOfferPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "gift_offer"));

    public static final StreamCodec<ByteBuf, GiftOfferPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GiftOfferPayload::band,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)), GiftOfferPayload::sources,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)), GiftOfferPayload::slots,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)), GiftOfferPayload::counts,
            GiftOfferPayload::new);

    public static void handle(GiftOfferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                dev.hominin.evolution.band.Relations.giveGift(player, payload.band(), payload.sources(), payload.slots(),
                        payload.counts());
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
