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

/** What was picked on the goods screen: how many of each thing on each side, the party's size, a message chosen. */
public record GoodsChoicePayload(int mode, int partySize, int option, List<Integer> left, List<Integer> right)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GoodsChoicePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "goods_choice"));

    public static final StreamCodec<ByteBuf, GoodsChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GoodsChoicePayload::mode,
            ByteBufCodecs.VAR_INT, GoodsChoicePayload::partySize,
            ByteBufCodecs.VAR_INT, GoodsChoicePayload::option,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), GoodsChoicePayload::left,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), GoodsChoicePayload::right,
            GoodsChoicePayload::new);

    public static void handle(GoodsChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                dev.hominin.evolution.band.Goods.chosen(player, payload.mode(), payload.partySize(), payload.option(),
                        payload.left(), payload.right());
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
