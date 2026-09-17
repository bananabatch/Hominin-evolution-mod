package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.client.ChecklistOverlay;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The evolution checklist, pushed to the client whenever it changes.
 *
 * <p>Sending rendered lines rather than syncing the whole save data keeps the
 * per-player attachment server-only, which is what it was built as - the client
 * only ever needs the text.
 */
public record ChecklistPayload(String title, List<String> lines) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChecklistPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "checklist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChecklistPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ChecklistPayload::title,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ChecklistPayload::lines,
            ChecklistPayload::new);

    public static void handle(ChecklistPayload payload, IPayloadContext context) {
        ChecklistOverlay.accept(payload.title(), payload.lines());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
