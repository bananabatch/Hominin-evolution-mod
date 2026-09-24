package dev.hominin.evolution.build;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * A build as a client knows it: enough to draw its ghost, tell its own from someone else's, and know a store when
 * it stands in one. The client's copies are kept here too - plain data, nothing that needs the client classes, so
 * a block can ask on either side.
 */
public record SiteView(int id, ResourceLocation blueprint, BlockPos origin, Direction forward, boolean mine,
        boolean built, boolean proposed, int use, String label, int placed, int total) {

    public static final StreamCodec<ByteBuf, SiteView> STREAM_CODEC = StreamCodec.of(
            (buf, view) -> {
                ByteBufCodecs.VAR_INT.encode(buf, view.id);
                ByteBufCodecs.STRING_UTF8.encode(buf, view.blueprint.toString());
                BlockPos.STREAM_CODEC.encode(buf, view.origin);
                ByteBufCodecs.VAR_INT.encode(buf, view.forward.get2DDataValue());
                ByteBufCodecs.BOOL.encode(buf, view.mine);
                ByteBufCodecs.BOOL.encode(buf, view.built);
                ByteBufCodecs.BOOL.encode(buf, view.proposed);
                ByteBufCodecs.VAR_INT.encode(buf, view.use);
                ByteBufCodecs.STRING_UTF8.encode(buf, view.label);
                ByteBufCodecs.VAR_INT.encode(buf, view.placed);
                ByteBufCodecs.VAR_INT.encode(buf, view.total);
            },
            buf -> new SiteView(ByteBufCodecs.VAR_INT.decode(buf), ResourceLocation.parse(ByteBufCodecs.STRING_UTF8.decode(buf)),
                    BlockPos.STREAM_CODEC.decode(buf), Direction.from2DDataValue(ByteBufCodecs.VAR_INT.decode(buf)),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)));

    public static SiteView of(Sites.Site site, boolean mine) {
        return new SiteView(site.id(), site.blueprint(), site.origin(), site.forward(), mine, site.built(),
                site.proposed, site.use().ordinal(), site.label(), site.placed, site.total);
    }

    public Sites.Use useKind() {
        return Sites.Use.values()[Math.max(0, Math.min(Sites.Use.values().length - 1, use))];
    }

    // ------------------------------------------------------------ the client's copies

    private static volatile List<SiteView> known = List.of();
    private static final Map<Integer, Footprint> FOOTPRINTS = new ConcurrentHashMap<>();

    public static void setKnown(List<SiteView> views) {
        known = List.copyOf(views);
        FOOTPRINTS.keySet().removeIf(id -> views.stream().noneMatch(v -> v.id == id));
    }

    public static List<SiteView> known() {
        return known;
    }

    public static void forget() {
        known = List.of();
        FOOTPRINTS.clear();
    }

    /** Where this build's blocks go - worked out once, and again only if it or its blueprint changes. */
    @Nullable
    public Footprint footprint() {
        Blueprint blueprint = Blueprints.get(this.blueprint);
        if (blueprint == null) {
            return null;
        }
        Footprint cached = FOOTPRINTS.get(id);
        if (cached == null || cached.blueprint() != blueprint || !cached.origin().equals(origin)
                || cached.forward() != forward) {
            cached = new Footprint(blueprint, origin, forward);
            FOOTPRINTS.put(id, cached);
        }
        return cached;
    }

    /** On the client: whether this spot is inside a store of your own. */
    public static boolean inOwnStore(BlockPos pos) {
        for (SiteView view : known) {
            if (view.mine && view.built && view.useKind() == Sites.Use.STORE) {
                Footprint footprint = view.footprint();
                if (footprint != null && footprint.isInside(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** On the client: whether this spot is in any finished build's room. */
    public static boolean inAnyRoom(BlockPos pos) {
        for (SiteView view : known) {
            Footprint footprint = view.built ? view.footprint() : null;
            if (footprint != null && footprint.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /** On the client: whether another build already has this spot. */
    public static boolean takenOnClient(BlockPos pos) {
        return takenOnClient(pos, 0);
    }

    /** The same, leaving out the one being moved. */
    public static boolean takenOnClient(BlockPos pos, int except) {
        for (SiteView view : known) {
            if (view.id == except) {
                continue;
            }
            Footprint footprint = view.footprint();
            if (footprint != null && footprint.contains(pos)) {
                return true;
            }
        }
        return false;
    }
}
