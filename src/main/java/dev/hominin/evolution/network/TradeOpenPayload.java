package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Everything a hominin will consider trading, and the era its worth is measured in. */
public record TradeOpenPayload(int entityId, String name, String stage, List<Integer> slots, List<ItemStack> stacks)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TradeOpenPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "trade_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeOpenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TradeOpenPayload::entityId,
            ByteBufCodecs.STRING_UTF8, TradeOpenPayload::name,
            ByteBufCodecs.STRING_UTF8, TradeOpenPayload::stage,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list()), TradeOpenPayload::slots,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), TradeOpenPayload::stacks,
            TradeOpenPayload::new);

    public static void handle(TradeOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.TradeScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
