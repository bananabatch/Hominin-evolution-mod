package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A short list for the player to pick one from - what to teach, and the like. */
public record ChoicesPayload(int entityId, int action, String title, List<String> labels, List<Integer> values)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChoicesPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "choices"));

    public static final StreamCodec<ByteBuf, ChoicesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ChoicesPayload::entityId,
            ByteBufCodecs.VAR_INT, ChoicesPayload::action,
            ByteBufCodecs.STRING_UTF8, ChoicesPayload::title,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ChoicesPayload::labels,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ChoicesPayload::values,
            ChoicesPayload::new);

    public static void handle(ChoicesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.ChoiceScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
