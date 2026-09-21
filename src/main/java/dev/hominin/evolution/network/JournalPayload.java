package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The journal's contents. Stats are ready-made lines; each skill is a title, a body of
 * three newline-separated parts (what it is, how to do it, what it gives you), and a flag
 * word: bit 1 known, bit 2 carries over when you evolve.
 */
public record JournalPayload(String heading, List<String> stats, List<String> titles, List<String> bodies,
        List<Integer> flags) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JournalPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "journal"));

    public static final StreamCodec<ByteBuf, JournalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, JournalPayload::heading,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), JournalPayload::stats,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), JournalPayload::titles,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), JournalPayload::bodies,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), JournalPayload::flags,
            JournalPayload::new);

    public static void handle(JournalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.JournalScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
