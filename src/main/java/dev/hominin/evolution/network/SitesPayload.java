package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.build.SiteView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The builds near a player, and all of their own: what their ghosts are drawn from. */
public record SitesPayload(List<SiteView> sites) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SitesPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "sites"));

    public static final StreamCodec<ByteBuf, SitesPayload> STREAM_CODEC = StreamCodec.composite(
            SiteView.STREAM_CODEC.apply(ByteBufCodecs.list()), SitesPayload::sites, SitesPayload::new);

    public static void handle(SitesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SiteView.setKnown(payload.sites()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
