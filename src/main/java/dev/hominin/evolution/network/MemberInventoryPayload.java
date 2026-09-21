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
 * What one band member is carrying, so the player can pick what to ask for. Slots line
 * up with stacks; -1 is the item in its hand.
 */
public record MemberInventoryPayload(int entityId, String name, List<Integer> slots, List<ItemStack> stacks,
        List<Integer> band) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MemberInventoryPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "member_inventory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MemberInventoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MemberInventoryPayload::entityId,
            ByteBufCodecs.STRING_UTF8, MemberInventoryPayload::name,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list()), MemberInventoryPayload::slots,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), MemberInventoryPayload::stacks,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), MemberInventoryPayload::band,
            MemberInventoryPayload::new);

    public static void handle(MemberInventoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.ItemPickScreen.open(
                payload.entityId(), payload.name(), payload.slots(), payload.stacks(), payload.band()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
