package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Opens the build menu: every blueprint, and for each why it cannot be built yet (empty if it can). */
public record BuildMenuPayload(List<String> ids, List<String> locks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BuildMenuPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "build_menu"));

    public static final StreamCodec<ByteBuf, BuildMenuPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BuildMenuPayload::ids,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BuildMenuPayload::locks,
            BuildMenuPayload::new);

    public static void handle(BuildMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.BuildScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
