package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Trading;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** This from my hotbar, for that from your pack. */
public record TradeRequestPayload(int entityId, int offerSlot, int wantedSlot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TradeRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "trade_request"));

    public static final StreamCodec<ByteBuf, TradeRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TradeRequestPayload::entityId,
            ByteBufCodecs.VAR_INT, TradeRequestPayload::offerSlot,
            ByteBufCodecs.INT, TradeRequestPayload::wantedSlot,
            TradeRequestPayload::new);

    public static void handle(TradeRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.level().getEntity(payload.entityId()) instanceof BandMember member
                && member.isAlive() && member.distanceToSqr(player) <= 16.0D * 16.0D) {
            Trading.trade(player, member, payload.offerSlot(), payload.wantedSlot());
            // Show what they have now, so the screen stays open on the new state.
            Trading.openTrade(player, member);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
