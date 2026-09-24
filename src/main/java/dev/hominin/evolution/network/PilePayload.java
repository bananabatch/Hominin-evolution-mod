package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What is in a pile, for its menu: whose pile and who laid it down first; then, bottom first, each thing, what it
 * is, who laid it, and a code - the mark in the low bits, then whether it is yours and whether you may take it.
 * Empty closes the menu.
 */
public record PilePayload(BlockPos pos, List<String> header, List<ItemStack> stacks, List<String> details,
        List<String> by, List<Integer> codes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PilePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "pile"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PilePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PilePayload::pos,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PilePayload::header,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), PilePayload::stacks,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PilePayload::details,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PilePayload::by,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), PilePayload::codes,
            PilePayload::new);

    public static void handle(PilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.PileScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
