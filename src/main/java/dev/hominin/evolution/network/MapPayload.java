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
 * The mental map, for the screen: grounds (yours in orange, other bands' in red), everyone and
 * everything marked on it, and what you are holding in mind. The terrain itself is the client's own -
 * it draws the country you have walked through.
 */
public record MapPayload(int x, int z, int slots, int presence, String ownName, boolean settled, List<Ground> grounds,
        List<Marker> markers) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MapPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "mental_map"));

    /** A band's ground. {@code kind}: 0 yours, 1 another band's, 2 a chimpanzee community's. {@code info}: hover text. */
    public record Ground(int x, int z, int radius, int kind, String label, String info) {
    }

    /**
     * Something on the map. {@code memory} is its slot among your memories, or -1 when it is not one of
     * them (a band, a troop, something told to you). {@code target} is what "lead me there" follows.
     */
    public record Marker(int x, int z, int kind, String label, int memory, String target) {
    }

    // Marker kinds.
    public static final int BAND = 0;
    public static final int PARANTHROPUS = 1;
    public static final int TROOP = 2;
    public static final int DEPOSIT = 3;
    public static final int LAVA = 4;
    public static final int TERMITES = 5;
    public static final int CLAN = 6;
    public static final int CUSTOM = 7;
    public static final int WATER = 8;
    public static final int TOLD = 9;
    public static final int CAMP = 10;
    public static final int WAYPOINT = 11;
    /** Where your band keeps its tools. */
    public static final int TOOL_STORE = 12;
    /** Something you built, or marked out to build. */
    public static final int STRUCTURE = 13;
    /** A place your band knows: this plus the kind of place (see {@link dev.hominin.evolution.world.Pois.Kind}). */
    public static final int PLACE = 20;

    public static final StreamCodec<FriendlyByteBuf, MapPayload> STREAM_CODEC =
            StreamCodec.ofMember(MapPayload::write, MapPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(x);
        buf.writeVarInt(z);
        buf.writeVarInt(slots);
        buf.writeVarInt(presence);
        buf.writeUtf(ownName);
        buf.writeBoolean(settled);
        buf.writeVarInt(grounds.size());
        for (Ground g : grounds) {
            buf.writeVarInt(g.x());
            buf.writeVarInt(g.z());
            buf.writeVarInt(g.radius());
            buf.writeVarInt(g.kind());
            buf.writeUtf(g.label());
            buf.writeUtf(g.info());
        }
        buf.writeVarInt(markers.size());
        for (Marker m : markers) {
            buf.writeVarInt(m.x());
            buf.writeVarInt(m.z());
            buf.writeVarInt(m.kind());
            buf.writeUtf(m.label());
            buf.writeVarInt(m.memory());
            buf.writeUtf(m.target());
        }
    }

    private static MapPayload read(FriendlyByteBuf buf) {
        int x = buf.readVarInt();
        int z = buf.readVarInt();
        int slots = buf.readVarInt();
        int presence = buf.readVarInt();
        String ownName = buf.readUtf();
        boolean settled = buf.readBoolean();
        int grounds = buf.readVarInt();
        List<Ground> groundList = new ArrayList<>();
        for (int i = 0; i < grounds; i++) {
            groundList.add(new Ground(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                    buf.readUtf()));
        }
        int markers = buf.readVarInt();
        List<Marker> markerList = new ArrayList<>();
        for (int i = 0; i < markers; i++) {
            markerList.add(new Marker(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(),
                    buf.readUtf()));
        }
        return new MapPayload(x, z, slots, presence, ownName, settled, groundList, markerList);
    }

    public static void handle(MapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.MapScreen.open(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
