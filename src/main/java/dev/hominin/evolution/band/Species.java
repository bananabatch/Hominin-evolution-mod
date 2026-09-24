package dev.hominin.evolution.band;

import java.util.List;

import dev.hominin.evolution.mind.Skills;
import net.minecraft.resources.ResourceLocation;

/**
 * What a kind of hominin can do - and, for the marginal forms, what it never managed. The fallbacks are not simply
 * the species they stand beside, a shade smaller: each is its own animal, with its own hands.
 *
 * <ul>
 * <li><b>Australopithecus anamensis</b> - the older, more ape-like form: no knapping of any kind, and no idea that a
 * bone has anything in it. Forages, fishes for termites, climbs.</li>
 * <li><b>Homo rudolfensis</b> - flakes and choppers, but never the multi tool, and never learned to crack a bone
 * for its marrow.</li>
 * <li><b>Homo ergaster</b> - the Acheulean, but crude: its hand axes and cleavers are rough at best (tier 3). It
 * never made fire.</li>
 * </ul>
 *
 * Band members born with a kind carry its limits: they cannot be taught what their kind never knew. Wild bands
 * come already knowing what their kind knows.
 */
public final class Species {
    /** The best Acheulean tier a kind can knap: 0 is flawless, 4 crude. */
    public static int bestAcheuleanTier(ResourceLocation species) {
        return species.getPath().equals("homo_ergaster") ? 3 : 0;
    }

    /** A tool of this quality, as this kind would actually make it. */
    public static int capQuality(ResourceLocation species, int quality) {
        return Math.max(quality, bestAcheuleanTier(species));
    }

    /** Whether this kind knaps stone at all. */
    public static boolean knaps(ResourceLocation species) {
        String path = species.getPath();
        return !path.equals("australopithecus_anamensis") && !path.equals("ardipithecus");
    }

    public static boolean makesMultitool(ResourceLocation species) {
        return knaps(species) && !species.getPath().equals("homo_rudolfensis");
    }

    public static boolean cracksMarrow(ResourceLocation species) {
        String path = species.getPath();
        return !path.equals("homo_rudolfensis") && !path.equals("australopithecus_anamensis")
                && !path.equals("ardipithecus");
    }

    public static boolean makesFire(ResourceLocation species) {
        return Bands.erectusOn(species) && !species.getPath().equals("homo_ergaster");
    }

    /** Whether a member of this kind can ever know this. */
    public static boolean canLearn(ResourceLocation species, Skills.Skill skill) {
        return switch (skill) {
            case MARROW -> cracksMarrow(species);
            case FIRE -> makesFire(species);
            case LOMEKWIAN -> knaps(species);
            default -> true;
        };
    }

    /** What a wild band of this kind already knows. */
    public static List<Skills.Skill> nativeSkills(ResourceLocation species) {
        return switch (species.getPath()) {
            case "australopithecus_anamensis", "ardipithecus" -> List.of(Skills.Skill.TERMITE_FISHING);
            case "australopithecus" -> List.of(Skills.Skill.TERMITE_FISHING, Skills.Skill.LOMEKWIAN);
            case "homo_rudolfensis" -> List.of(Skills.Skill.TERMITE_FISHING, Skills.Skill.LOMEKWIAN);
            case "homo_habilis" -> List.of(Skills.Skill.TERMITE_FISHING, Skills.Skill.MARROW, Skills.Skill.LOMEKWIAN);
            case "homo_ergaster" -> List.of(Skills.Skill.MARROW, Skills.Skill.TRACKING);
            case "homo_erectus" -> List.of(Skills.Skill.MARROW, Skills.Skill.FIRE, Skills.Skill.TRACKING);
            default -> Bands.erectusOn(species) ? List.of(Skills.Skill.MARROW, Skills.Skill.FIRE, Skills.Skill.TRACKING)
                    : List.of();
        };
    }

    /** One line on what they can and cannot do, for the others' list and a member's info. */
    public static String abilities(ResourceLocation species) {
        return switch (species.getPath()) {
            case "australopithecus_anamensis" -> "They knap nothing, and do not know there is marrow in a bone.";
            case "australopithecus" -> "They bash stone into rough edges, and fish for termites.";
            case "homo_rudolfensis" -> "They make flakes and choppers - never a multi tool - and have never cracked a bone for marrow.";
            case "homo_habilis" -> "They knap Oldowan tools and crack bones for marrow.";
            case "homo_ergaster" -> "They knap hand axes, but crude ones - rough at best - and they have never made fire.";
            case "homo_erectus" -> "They knap fine hand axes and cleavers, and keep fire.";
            case "paranthropus_boisei" -> "They grind tough plants with huge jaws, and use little else.";
            default -> "";
        };
    }

    private Species() {
    }
}
