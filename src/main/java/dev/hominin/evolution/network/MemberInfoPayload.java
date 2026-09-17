package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One band member's details, already worded, for the info screen. */
public record MemberInfoPayload(String name, List<String> lines) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MemberInfoPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "member_info"));

    public static final StreamCodec<ByteBuf, MemberInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MemberInfoPayload::name,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MemberInfoPayload::lines,
            MemberInfoPayload::new);

    public static void handle(MemberInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.MemberInfoScreen.open(payload.name(), payload.lines()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
