package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.band.Morals;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Taking up a moral, or beginning to let one go, from the Culture tab. */
public record MoralActionPayload(int moral, boolean adopt) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MoralActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "moral_action"));

    public static final StreamCodec<ByteBuf, MoralActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MoralActionPayload::moral,
            ByteBufCodecs.BOOL, MoralActionPayload::adopt,
            MoralActionPayload::new);

    public static void handle(MoralActionPayload payload, IPayloadContext context) {
        Morals.Moral moral = Morals.Moral.byId(payload.moral());
        if (moral != null && context.player() instanceof ServerPlayer player) {
            if (payload.adopt()) {
                Morals.adopt(player, moral);
            } else {
                Morals.retract(player, moral);
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
