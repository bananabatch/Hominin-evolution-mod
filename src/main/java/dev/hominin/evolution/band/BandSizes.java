package dev.hominin.evolution.band;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * How big a band each species lived in. Sizes count the player, so a band that
 * "starts at 4" is the player and three others.
 *
 * <p>Group size grows with brain size through the lineage - the social-brain idea -
 * with sapiens the most social of all. Neanderthals lived in smaller, tighter groups
 * than the sapiens who replaced them. Stages that do not exist in the mod yet are
 * listed anyway, so adding their stage file is all they will need.
 */
public final class BandSizes {
    public record Size(int start, int max) {
        /** Members other than the player a band starts with. */
        public int startingMembers() {
            return start - 1;
        }

        /** Most members other than the player a band can hold. */
        public int maxMembers() {
            return max - 1;
        }
    }

    private static final Size DEFAULT = new Size(4, 6);

    private static final Map<String, Size> SIZES = Map.ofEntries(Map.entry(
            "australopithecus", new Size(4, 6)),
            Map.entry("ardipithecus", new Size(3, 5)),
            Map.entry("homo_habilis", new Size(7, 12)),
            Map.entry("homo_erectus", new Size(12, 17)),
            Map.entry("homo_heidelbergensis", new Size(15, 19)),
            Map.entry("homo_sapiens", new Size(25, 40)),
            Map.entry("homo_neanderthalensis", new Size(18, 25)),
            // The fallbacks: smaller, poorer bands than the species they stand behind.
            Map.entry("australopithecus_anamensis", new Size(3, 5)),
            Map.entry("homo_rudolfensis", new Size(5, 9)),
            Map.entry("homo_ergaster", new Size(9, 14)),
            // Not a stage you can play: the robust neighbours, in small troops.
            Map.entry("paranthropus_boisei", new Size(6, 9)));

    public static Size of(ResourceLocation stage) {
        return SIZES.getOrDefault(stage.getPath(), DEFAULT);
    }

    private BandSizes() {
    }
}
