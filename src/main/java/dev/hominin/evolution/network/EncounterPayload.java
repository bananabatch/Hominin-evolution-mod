package dev.hominin.evolution.network;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Another band, face to face, with an offer or a threat: who, how they stand with you, how desperate they
 * are, what they say, and what they offer or want. {@code kind} is a {@link dev.hominin.evolution.band.Claims.Kind}.
 */
public record EncounterPayload(int kind, String band, String speaker, int standing, int desperation, String line,
        String detail, List<ItemStack> items) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EncounterPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "encounter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncounterPayload> STREAM_CODEC =
            StreamCodec.ofMember(EncounterPayload::write, EncounterPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(kind);
        buf.writeUtf(band);
        buf.writeUtf(speaker);
        buf.writeVarInt(standing);
        buf.writeVarInt(desperation);
        buf.writeUtf(line);
        buf.writeUtf(detail);
        buf.writeVarInt(items.size());
        for (ItemStack stack : items) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        }
    }

    private static EncounterPayload read(RegistryFriendlyByteBuf buf) {
        int kind = buf.readVarInt();
        String band = buf.readUtf();
        String speaker = buf.readUtf();
        int standing = buf.readVarInt();
        int desperation = buf.readVarInt();
        String line = buf.readUtf();
        String detail = buf.readUtf();
        int count = buf.readVarInt();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new EncounterPayload(kind, band, speaker, standing, desperation, line, detail, items);
    }

    public static void handle(EncounterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.EncounterScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
