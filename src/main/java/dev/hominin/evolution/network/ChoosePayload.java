package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The player's pick from a {@link ChoicesPayload}. */
public record ChoosePayload(int entityId, int action, int value) implements CustomPacketPayload {
    /** A name clicked in the band list. */
    public static final int ACTION_FIND = 3;

    public static final CustomPacketPayload.Type<ChoosePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "choose"));

    public static final StreamCodec<ByteBuf, ChoosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ChoosePayload::entityId,
            ByteBufCodecs.VAR_INT, ChoosePayload::action,
            ByteBufCodecs.VAR_INT, ChoosePayload::value,
            ChoosePayload::new);

    public static void handle(ChoosePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (payload.action() == dev.hominin.evolution.mind.Teaching.ACTION_TEACH) {
            dev.hominin.evolution.mind.Teaching.choose(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Commissions.ACTION_KNAP) {
            dev.hominin.evolution.band.Commissions.choose(player, payload.entityId(), payload.value());
        } else if (payload.action() == ACTION_FIND) {
            dev.hominin.evolution.band.Social.find(player, payload.entityId());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
