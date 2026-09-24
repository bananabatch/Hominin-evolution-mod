package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Every blueprint the server has, as the text it read them from: the client builds the same ones. */
public record BlueprintsPayload(List<String> ids, List<String> texts) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlueprintsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "blueprints"));

    public static final StreamCodec<ByteBuf, BlueprintsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BlueprintsPayload::ids,
            ByteBufCodecs.stringUtf8(1 << 16).apply(ByteBufCodecs.list()), BlueprintsPayload::texts,
            BlueprintsPayload::new);

    public static void handle(BlueprintsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.build.Blueprints.fromServer(payload.ids(), payload.texts()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
