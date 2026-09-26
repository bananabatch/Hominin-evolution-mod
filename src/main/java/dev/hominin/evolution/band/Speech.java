package dev.hominin.evolution.band;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * How each kind talks. A band says only what its kind could say - nobody without fire speaks of a fire, nobody who
 * never knapped offers good stone - and every kind has its own things on its mind: ardipithecus its fig trees,
 * anamensis the lake shore, afarensis the termite mounds and sleeping trees, rudolfensis its cobbles and choppers,
 * habilis carcasses and marrow, ergaster the fire it carries and the long walks, erectus its hearth and its hunts.
 * And a band speaks differently to its own kind than to strangers who do not look like it.
 */
public final class Speech {
    private record Kind(List<String> warm, List<String> wary, List<String> own) {
    }

    private static final Kind ARDIPITHECUS = new Kind(
            List.of("The figs are ripe up in the tall trees. Climb with us.",
                    "We sleep in the branches. The ground is for the cats.",
                    "Soft fruit by the stream today - plenty for two bands.",
                    "You walk upright so much. Doesn't it tire you?"),
            List.of("Stay down there. These trees are ours.", "We see you from up here. We see everything.",
                    "That fig tree is ours. Find your own."),
            List.of("The fruit is better higher up.", "I'd feel safer in the branches.",
                    "My hands miss the trees when we walk this far.", "Soft figs by the water. I'll show you."));
    private static final Kind ANAMENSIS = new Kind(
            List.of("The lake shore has good roots this season.", "We dig along the water's edge. Come and dig with us.",
                    "Hard nuts down by the reeds - our teeth can take them.", "We sleep up high. You should too."),
            List.of("The shore is ours. Dig somewhere else.", "Those reeds are picked clean. Nothing for you.",
                    "Don't follow us to the water."),
            List.of("My jaw aches. These roots are tough.", "The lake was higher last season.",
                    "We should sleep up high tonight.", "I found tubers by the reeds."));
    private static final Kind AFARENSIS = new Kind(
            List.of("The termites are out on the red mound. Bring a stick.",
                    "We cross the open ground by day and climb by night. You too?",
                    "Our little ones like playing with yours.", "Crack those nuts on a rock, not your teeth."),
            List.of("That termite mound is ours.", "Don't come near our sleeping trees.",
                    "We walk this way every dry season. Not you."),
            List.of("The termites are swarming - bring a stick.", "Those tracks in the ash - were they ours?",
                    "I'll keep watch from the tree tonight.", "A sharp rock would open that nut."));
    private static final Kind RUDOLFENSIS = new Kind(
            List.of("The river gravels have good cobbles. Take what you need.",
                    "Big flakes cut hide best. We'll show you.",
                    "The cats left plenty on that carcass by the thorn trees.",
                    "Chopping roots is easier with a heavy stone."),
            List.of("Those gravels are ours. Knap somewhere else.", "We saw that carcass first.",
                    "Keep your hands off our cobbles."),
            List.of("I kept a good cobble for you.", "The cats are done with that carcass. Our turn.",
                    "My chopper is going blunt.", "These roots need a heavy stone."));
    private static final Kind HABILIS = new Kind(
            List.of("There's a carcass past the ridge - bring a hammerstone for the bones.",
                    "Marrow tonight, if the hyenas don't beat us to it.",
                    "We knap down by the river, where the good stone is.", "Your flakes are sharp. Who taught you?"),
            List.of("That carcass is ours. The bones too.", "Keep away from our knapping place.",
                    "We don't share marrow with strangers."),
            List.of("Crack me a bone later? I'm starving.", "Good stone by the river. I marked the spot.",
                    "The vultures are circling - there's a carcass.", "My flake broke. Got another?"));
    private static final Kind ERGASTER = new Kind(
            List.of("We carried fire here from the burnt hills. Sit near it - don't touch it.",
                    "We walked two days to get here. We never stay long.", "Our hand axes are rough, but they cut.",
                    "Long legs, long walks. The herds can't lose us."),
            List.of("Don't let our fire die - and don't come near it.",
                    "We move on soon. Stay out of our way till then.", "We walked a long way for this ground."),
            List.of("Keep the embers alive. We can't make them again.", "My legs want to keep walking.",
                    "The herd went north. We could catch it by dusk.", "Rough edge, but it cuts."));
    private static final Kind ERECTUS = new Kind(
            List.of("You are always welcome at our fire.", "Sit by our hearth tonight - the meat's nearly cooked.",
                    "We've tracked that herd since dawn. Hunt with us.",
                    "Our cleaver takes a leg off clean. Want to see?"),
            List.of("Stay away from our hearth.", "We saw your smoke. We know where you sleep.",
                    "Our hunters are better than yours. Remember that."),
            List.of("I'll bank the fire tonight.", "Cooked meat sits better. Let's roast it.",
                    "I'll shape you a better hand axe.", "We ran that herd down. We can do it again."));

    private static final List<String> SAME_WARM = List.of("One of our own kind. It's good to see you.",
            "Your faces are like ours. Where do your people come from?");
    private static final List<String> SAME_WARY = List.of("Our own kind - and still they come to take.",
            "They look like us. That doesn't mean we trust them.");
    private static final List<String> OTHER_WARM = List.of("Strange ones - but they share.",
            "Their faces aren't like ours, but they're good people.");
    private static final List<String> OTHER_WARY = List.of("Look at their faces. Not our kind.",
            "What are they? Don't get close.", "They are not like us. Watch them.");

    @Nullable
    private static Kind kindOf(@Nullable ResourceLocation species) {
        if (species == null) {
            return null;
        }
        return switch (species.getPath()) {
            case "ardipithecus" -> ARDIPITHECUS;
            case "australopithecus_anamensis" -> ANAMENSIS;
            case "australopithecus" -> AFARENSIS;
            case "homo_rudolfensis" -> RUDOLFENSIS;
            case "homo_habilis" -> HABILIS;
            case "homo_ergaster" -> ERGASTER;
            case "homo_erectus" -> ERECTUS;
            default -> Bands.erectusOn(species) ? ERECTUS : null;
        };
    }

    /** Whether this kind would ever say a shared line: fire, stone, hunting and meat each belong to some kinds only. */
    public static boolean fits(String line, @Nullable ResourceLocation species) {
        if (species == null) {
            return true;
        }
        String text = line.toLowerCase(Locale.ROOT);
        String path = species.getPath();
        if (text.contains("fire") || text.contains("hearth") || text.contains("ember") || text.contains("smoke")) {
            return Bands.erectusOn(species);
        }
        if (text.contains("stone") || text.contains("flake") || text.contains("knap") || text.contains("axe")) {
            return Species.knaps(species);
        }
        if (text.contains("hunt")) {
            return path.startsWith("homo");
        }
        if (text.contains("meat")) {
            return path.startsWith("homo") || path.equals("australopithecus");
        }
        return true;
    }

    /** The shared lines this kind would say. */
    public static List<String> fitting(List<String> lines, @Nullable ResourceLocation species) {
        return lines.stream().filter(line -> fits(line, species)).toList();
    }

    /** What another band of this kind says, besides the shared lines: warm from friends, wary from the rest. */
    public static List<String> band(@Nullable ResourceLocation species, boolean warm) {
        Kind kind = kindOf(species);
        return kind == null ? List.of() : warm ? kind.warm() : kind.wary();
    }

    /** What one of your own band of this kind says to you, besides the shared lines. */
    public static List<String> own(@Nullable ResourceLocation species) {
        Kind kind = kindOf(species);
        return kind == null ? List.of() : kind.own();
    }

    /** Remarks on who you are: your own kind, or something else with a different face. */
    public static List<String> kin(@Nullable ResourceLocation theirs, @Nullable ResourceLocation yours, boolean warm) {
        if (theirs == null || yours == null) {
            return List.of();
        }
        boolean same = dev.hominin.evolution.stage.Kinds.line(theirs).equals(dev.hominin.evolution.stage.Kinds.line(yours));
        return same ? (warm ? SAME_WARM : SAME_WARY) : (warm ? OTHER_WARM : OTHER_WARY);
    }

    /** Where a band gathers at night: a fire, if its kind keeps one - otherwise its sleeping trees. */
    public static String camp(@Nullable ResourceLocation species) {
        return species != null && Bands.erectusOn(species) ? "the fire" : "the sleeping trees";
    }

    private Speech() {
    }
}
