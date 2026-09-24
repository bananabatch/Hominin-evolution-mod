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

/**
 * What could be offered to another band as a gift: everything you carry, and everything your band near
 * you carries. {@code sources} is whose it is - -1 for you, else the member's entity id - and
 * {@code owners} their name.
 */
public record GiftStockPayload(String band, String bandName, List<Integer> sources, List<Integer> slots,
        List<ItemStack> stacks, List<String> owners) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GiftStockPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "gift_stock"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GiftStockPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GiftStockPayload::band,
            ByteBufCodecs.STRING_UTF8, GiftStockPayload::bandName,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), GiftStockPayload::sources,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), GiftStockPayload::slots,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), GiftStockPayload::stacks,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), GiftStockPayload::owners,
            GiftStockPayload::new);

    public static void handle(GiftStockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.GiftScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
