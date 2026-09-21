package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** J was pressed: send back everything the journal shows. */
public record OpenJournalPayload() implements CustomPacketPayload {
    public static final OpenJournalPayload INSTANCE = new OpenJournalPayload();
    public static final CustomPacketPayload.Type<OpenJournalPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "open_journal"));

    public static final StreamCodec<ByteBuf, OpenJournalPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(OpenJournalPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            dev.hominin.evolution.mind.Journal.send(player);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
