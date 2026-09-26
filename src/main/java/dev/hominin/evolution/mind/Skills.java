package dev.hominin.evolution.mind;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * What you know how to do, as opposed to what you have.
 *
 * <p>A recipe is a thing your hands can make. A skill is a thing you have learned about
 * the world - how to fish a mound, how to make yourself small in front of a troop, how
 * to keep hold of an animal that ran. Each one comes from doing it (or, now and then,
 * from sitting still and thinking about the right thing at the right moment), and each
 * one tells you, once you have it, how to do it again.
 *
 * <p>Some are the body's: they belong to this species and go with it. Others are the kind
 * of knowledge that gets passed down, and those survive evolving - the first Lomekwian
 * core is still in the hands of whatever you become.
 */
public final class Skills {
    public enum Skill {
        LOMEKWIAN("Lomekwian knapping",
                "Bashing one stone against another until something with an edge comes off it.",
                "Hold a rock with a hammerstone in your off hand, press P, and try for more than your hands can make yet. "
                        + "Only the first hominins can pick this up - later kinds inherit it or never have it.",
                "Your hands know where not to strike. A multi tool never shatters in them, and whatever you become starts ahead at knapping.",
                true),
        TERMITE_FISHING("Termite fishing",
                "Feeding a stick into a mound and drawing it out covered in soldiers.",
                "Right-click a termite mound or rotten log holding a stick.",
                "One time in four a fat grub comes up with the soldiers.",
                true),
        MARROW("Marrow",
                "Knowing that the best food on a carcass is inside the bones.",
                "Hold a long bone and a flake, and right-click the air.",
                "You crack them cleanly, and one time in three there is more inside than you expected.",
                true),
        FIRE("Firemaking",
                "Making fire, rather than waiting for one.",
                "Two sticks in your hands and P makes a drill. Hold use with it on dry ground for three seconds.",
                "Your drills last: half the time a fire you light leaves the drill fit to use again.",
                true),
        EARLY_TRACKING("Early tracking",
                "The oldest hunting knowledge: holding an animal's flight in your head long enough to run it down.",
                "As habilis, right after something runs from you, hold K. Only habilis can pick this up.",
                "Whatever you evolve into starts a better persistence hunter - a level up.",
                true),
        TRACKING("Tracking",
                "Keeping hold of an animal that ran, by keeping its tracks in your head.",
                "Right after something runs from you, hold K.",
                "You hold the trail a good deal longer before it goes cold.",
                false),
        GROOMING("Grooming",
                "Going through somebody's hair for what does not belong there.",
                "Stand beside a band member and choose Groom them under H.",
                "You find more: one extra tick comes off everyone you groom.",
                false),
        DEESCALATION("Primate de-escalation",
                "Making yourself small and harmless in front of something that could kill you.",
                "In the five seconds after you strike a baboon near its troop, hold K.",
                "Troops give you three more seconds to put a mistake right.",
                true),
        LONG_VIEW("The long view",
                "Thinking about time: the days before this one, and the ones after it.",
                "Think with nothing in your hands, more than once.",
                "Thinking comes easier. The wait between thoughts is a minute shorter.",
                true),
        SUPER_WEAPONS("Great weapons",
                "Making the weapons that brought down the biggest animals there were: the Schoningen spear, and the "
                        + "stone-tipped spear.",
                "As heidelbergensis, far enough along, hold K with a workable shaft in hand - then make one.",
                "Your band makes them too, once you show them - and whatever you become sees them sooner: one hard "
                        + "requirement and one task fewer.",
                true),
        CLEAN_EYE("Clean eye",
                "Seeing what is in the grass before it moves.",
                "Run five animals down.",
                "Whatever stands in the grass near you shows itself - and when one of a herd runs, you keep hold of it "
                        + "and two more.",
                true),
        NOMAD("Nomad",
                "Knowing country by walking it.",
                "Move your band three times, each a long way (250 blocks) from the last camp.",
                "Far from camp, the country opens to you: places worth knowing, old tools and lone camps, giant "
                        + "carcasses, and anything about to drop dead.",
                true),
        JACK("Jack of all trades",
                "A little of everything, and no fear of the next thing.",
                "Three of: level 2 knapper, level 2 hunter, two places known, two skills taught, a tool worth something "
                        + "made.",
                "Every skill you learn brings your knapping or your hunting up a level with it, and both come easier.",
                true);

        private final String title;
        private final String about;
        private final String howTo;
        private final String effect;
        private final boolean carriesOver;

        Skill(String title, String about, String howTo, String effect, boolean carriesOver) {
            this.title = title;
            this.about = about;
            this.howTo = howTo;
            this.effect = effect;
            this.carriesOver = carriesOver;
        }

        public String title() {
            return title;
        }

        public String about() {
            return about;
        }

        public String howTo() {
            return howTo;
        }

        public String effect() {
            return effect;
        }

        /**
         * Some knowledge belongs to one moment in the line: Lomekwian knapping only to the first
         * hominins, early tracking only to habilis. Missed then, it is missed for good - unless it
         * was carried over.
         */
        public boolean learnableAs(net.minecraft.resources.ResourceLocation stage) {
            String path = stage.getPath();
            return switch (this) {
                case LOMEKWIAN -> path.equals("ardipithecus") || path.startsWith("australopithecus");
                case EARLY_TRACKING -> path.equals("homo_habilis") || path.equals("homo_rudolfensis");
                default -> true;
            };
        }

        /** The knacks are about the one who has them: nothing to show anybody. */
        public boolean teachable() {
            return this != CLEAN_EYE && this != NOMAD && this != JACK;
        }

        /** Whether a wild band of this kind might know it, to teach. The great weapons are not given away. */
        public boolean bandsKnow(net.minecraft.resources.ResourceLocation species) {
            return switch (this) {
                case NOMAD, JACK -> false;
                case MARROW -> dev.hominin.evolution.band.Species.cracksMarrow(species);
                case FIRE -> !dev.hominin.evolution.band.Species.neverMakesFire(species);
                case SUPER_WEAPONS, LONG_VIEW -> false;
                default -> true;
            };
        }

        /** Knowledge that survives evolving, as opposed to something only this body can do. */
        public boolean carriesOver() {
            return carriesOver;
        }

        /**
         * Carried skills live under the skill prefix, which evolving keeps; the rest are
         * plain counters, which evolving clears.
         */
        String key() {
            return (carriesOver ? EvolutionManager.SKILL_PREFIX : "") + "known_" + name().toLowerCase();
        }
    }

    public static boolean knows(Player player, Skill skill) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters()
                .getOrDefault(skill.key(), 0) > 0;
    }

    /** Learns it, if not already known. Returns true the first time. */
    public static boolean learn(ServerPlayer player, Skill skill) {
        // Every time you do it counts as showing it, for anyone watching to be taught.
        Teaching.demonstrated(player, skill);
        var stage = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        if (knows(player, skill) || !skill.learnableAs(stage)
                // What your kind never had, you do not pick up either.
                || skill == Skill.MARROW && !dev.hominin.evolution.band.Species.cracksMarrow(stage)
                || skill == Skill.FIRE && dev.hominin.evolution.band.Species.neverMakesFire(stage)) {
            return false;
        }
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put(skill.key(), 1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.5F, 1.8F);
        player.sendSystemMessage(Component.literal("New skill: " + skill.title() + ". ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(skill.effect() + " (J to see your skills)")
                        .withStyle(ChatFormatting.GRAY)));
        Knacks.learnedAnother(player, skill);
        return true;
    }

    /** Developer tools: set a skill known or unknown outright, without the fanfare. */
    public static void set(Player player, Skill skill, boolean known) {
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        if (known) {
            counters.put(skill.key(), 1);
        } else {
            counters.remove(skill.key());
        }
    }

    private Skills() {
    }
}
