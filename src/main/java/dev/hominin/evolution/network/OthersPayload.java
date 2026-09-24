package dev.hominin.evolution.network;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "The others": every band you know of, nearest first, and what you can do about each. {@code near} is
 * whether any of them are close enough to deal with face to face.
 */
public record OthersPayload(List<View> bands) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OthersPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "others"));

    /** One band as the player knows it. {@code lines} are the details, ready to show. */
    public record View(String id, String name, int standing, boolean near, boolean ransom, boolean canTravel,
            boolean nomadic, List<String> lines, int presence, int cohesion, int desperation) {
    }

    public static final StreamCodec<FriendlyByteBuf, OthersPayload> STREAM_CODEC =
            StreamCodec.ofMember(OthersPayload::write, OthersPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(bands.size());
        for (View v : bands) {
            buf.writeUtf(v.id());
            buf.writeUtf(v.name());
            buf.writeVarInt(v.standing());
            buf.writeBoolean(v.near());
            buf.writeBoolean(v.ransom());
            buf.writeBoolean(v.canTravel());
            buf.writeBoolean(v.nomadic());
            buf.writeVarInt(v.lines().size());
            v.lines().forEach(buf::writeUtf);
            buf.writeVarInt(v.presence());
            buf.writeVarInt(v.cohesion());
            buf.writeVarInt(v.desperation());
        }
    }

    private static OthersPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<View> views = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            int standing = buf.readVarInt();
            boolean near = buf.readBoolean();
            boolean ransom = buf.readBoolean();
            boolean canTravel = buf.readBoolean();
            boolean nomadic = buf.readBoolean();
            int lineCount = buf.readVarInt();
            List<String> lines = new ArrayList<>();
            for (int j = 0; j < lineCount; j++) {
                lines.add(buf.readUtf());
            }
            views.add(new View(id, name, standing, near, ransom, canTravel, nomadic, lines, buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt()));
        }
        return new OthersPayload(views);
    }

    public static void handle(OthersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.OthersScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
