package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What the knapper sees in their own hands while they knap - the stone, the bone, the hammerstone, the tool made -
 * drawn only: nothing is put in their inventory, so nothing shown can end up in it. {@code shown} false puts the
 * hands back as they are.
 */
public record KnapHandsPayload(boolean shown, ItemStack main, ItemStack off) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KnapHandsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "knap_hands"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KnapHandsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, KnapHandsPayload::shown,
            ItemStack.OPTIONAL_STREAM_CODEC, KnapHandsPayload::main,
            ItemStack.OPTIONAL_STREAM_CODEC, KnapHandsPayload::off,
            KnapHandsPayload::new);

    public static void handle(KnapHandsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.shown()) {
                dev.hominin.evolution.client.KnapHands.show(payload.main(), payload.off());
            } else {
                dev.hominin.evolution.client.KnapHands.clear();
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
