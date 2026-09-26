package dev.hominin.evolution.network;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** "Let's hunt": what is about - each animal, its kind, how it answers a blow, how far, how many of it - and who can go. */
public record HuntPayload(List<Target> targets, int maxMembers) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HuntPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "hunt"));

    public record Target(int entityId, String name, String kind, int distance, boolean mega, int herd) {
    }

    public static final StreamCodec<FriendlyByteBuf, HuntPayload> STREAM_CODEC =
            StreamCodec.ofMember(HuntPayload::write, HuntPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(targets.size());
        for (Target t : targets) {
            buf.writeVarInt(t.entityId());
            buf.writeUtf(t.name());
            buf.writeUtf(t.kind());
            buf.writeVarInt(t.distance());
            buf.writeBoolean(t.mega());
            buf.writeVarInt(t.herd());
        }
        buf.writeVarInt(maxMembers);
    }

    private static HuntPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            targets.add(new Target(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarInt()));
        }
        return new HuntPayload(targets, buf.readVarInt());
    }

    public static void handle(HuntPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.HuntScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
