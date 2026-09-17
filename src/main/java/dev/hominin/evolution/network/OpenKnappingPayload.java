package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Tells the client to open the knapping screen for the stone in hand.
 *
 * <p>It carries the stone's name, whether it is any good, and which choices the
 * server will accept for it - so the screen only ever offers what the stone can
 * actually become, without the client having to know any of the rules.
 */
public record OpenKnappingPayload(String stoneName, boolean goodStone, List<Integer> choices)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenKnappingPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "open_knapping"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenKnappingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenKnappingPayload::stoneName,
                    ByteBufCodecs.BOOL, OpenKnappingPayload::goodStone,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OpenKnappingPayload::choices,
                    OpenKnappingPayload::new);

    public static void handle(OpenKnappingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.KnappingScreen.open(
                payload.stoneName(), payload.goodStone(), payload.choices()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
