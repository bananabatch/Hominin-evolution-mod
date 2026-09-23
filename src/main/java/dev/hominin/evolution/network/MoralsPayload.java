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
 * The Culture tab's contents: the season, and for every moral (in {@code Morals.Moral} order) where
 * it stands, how long until that changes, and whether it binds right now.
 */
public record MoralsPayload(String season, int daysLeft, boolean dryDay, List<Integer> states, List<Integer> minutes,
        List<Integer> binding) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MoralsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "morals"));

    public static final StreamCodec<ByteBuf, MoralsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MoralsPayload::season,
            ByteBufCodecs.VAR_INT, MoralsPayload::daysLeft,
            ByteBufCodecs.BOOL, MoralsPayload::dryDay,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), MoralsPayload::states,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), MoralsPayload::minutes,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), MoralsPayload::binding,
            MoralsPayload::new);

    public static void handle(MoralsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.CultureScreen.show(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
