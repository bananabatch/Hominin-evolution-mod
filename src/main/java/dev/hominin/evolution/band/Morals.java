package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.survival.Drought;
import dev.hominin.evolution.survival.Seasons;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A people's ways: the rules a band lives by, set from erectus on in the Culture tab.
 *
 * <p>None of them is simply good. Each one pays off in one kind of time and costs in another,
 * and most only bind at all in the times they are about - so which to hold depends on where the
 * band is and what season it is. They can be let go of, but not quickly: a rule takes two days
 * to fade out of a band, and still binds while it does. Once gone, it cannot be taken up again
 * for a day.
 */
public final class Morals {
    /** When a moral binds. */
    public enum When {
        ALWAYS("Always"),
        HARD_TIMES("In hard times: a dry season or a dry day"),
        PLENTY("In a prosperous season");

        private final String label;

        When(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Moral {
        EAT_THE_DEAD(Mortuary.NORM, "Our dead stay with us",
                "When one of the band dies, you eat of them together within a day, every time. Eating our own "
                        + "kind stops shaking the band - but leave the dead to the hyenas and it will not forget.",
                When.ALWAYS),
        TIGHT_TIMES(key("tight_times"), "It's okay not to share when times are tight",
                "In hard times the band asks you for less, and nobody holds an unmet want against you. But they "
                        + "keep their own food close as well: no gifts, and only friends hand food over.",
                When.HARD_TIMES),
        NO_THEFT_HARD(key("no_theft_hard"), "Stealing is wrong when there isn't much to go around",
                "In hard times, most who eye another's food think better of it, and anyone caught is made to give "
                        + "it back. Take food from a hungry member's pack yourself and they see you break it.",
                When.HARD_TIMES),
        NO_THEFT_PLENTY(key("no_theft_plenty"), "Stealing is wrong even when we have plenty",
                "In a prosperous season, nobody helps themselves to another's things, and a thief who is caught "
                        + "gives it back. Whoever is shamed for it thinks a little less of you.",
                When.PLENTY),
        ALWAYS_SHARE(key("always_share"), "Sharing is always good",
                "Every shared meal bonds the band more, and you can share twice as often. But they expect to be "
                        + "looked after: a want you leave unmet costs twice the bond.",
                When.ALWAYS),
        REVERSE_DOMINANCE(key("reverse_dominance"), "Nobody lords it over the rest",
                "The band keeps its bullies down together: most fights are stopped before they start, thieves who "
                        + "do not care are caught like anyone else, shunning works far better, and the antisocial come "
                        + "round in time.",
                When.ALWAYS);

        private final String key;
        private final String title;
        private final String description;
        private final When when;

        Moral(String key, String title, String description, When when) {
            this.key = key;
            this.title = title;
            this.description = description;
            this.when = when;
        }

        public String title() {
            return title;
        }

        public String description() {
            return description;
        }

        public When when() {
            return when;
        }

        @Nullable
        public static Moral byId(int id) {
            Moral[] values = values();
            return id >= 0 && id < values.length ? values[id] : null;
        }
    }

    /** What the Culture tab shows for each moral. */
    public static final int NONE = 0;
    public static final int HELD = 1;
    public static final int FADING = 2;
    public static final int COOLING = 3;

    /** Two days to let go of a rule, and a day before it can come back - in minutes of game time. */
    private static final int FADE_MINUTES = 40;
    private static final int COOLDOWN_MINUTES = 20;

    private static String key(String name) {
        return EvolutionManager.SKILL_PREFIX + "moral_" + name;
    }

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    private static int minute(ServerPlayer player) {
        return (int) (player.level().getGameTime() / 1200L);
    }

    @Nullable
    private static Moral conflict(Moral moral) {
        return switch (moral) {
            case TIGHT_TIMES -> Moral.ALWAYS_SHARE;
            case ALWAYS_SHARE -> Moral.TIGHT_TIMES;
            default -> null;
        };
    }

    /** Whether the band holds this rule - still true while it is fading. */
    public static boolean holds(Player player, Moral moral) {
        return player instanceof ServerPlayer server && counters(server).getOrDefault(moral.key, 0) > 0;
    }

    /** Whether it binds right now: held, and the times are the times it is about. */
    public static boolean applies(Player player, Moral moral) {
        if (!holds(player, moral)) {
            return false;
        }
        return switch (moral.when) {
            case ALWAYS -> true;
            case HARD_TIMES -> Seasons.strained(player.level());
            case PLENTY -> Seasons.plentiful(player.level());
        };
    }

    public static int state(ServerPlayer player, Moral moral) {
        Map<String, Integer> counters = counters(player);
        if (counters.getOrDefault(moral.key, 0) > 0) {
            return counters.getOrDefault(moral.key + "_fading", 0) > 0 ? FADING : HELD;
        }
        return counters.getOrDefault(moral.key + "_cooldown", 0) > minute(player) ? COOLING : NONE;
    }

    /** Minutes of game time until a fading rule is gone, or a let-go one can come back. */
    public static int minutesLeft(ServerPlayer player, Moral moral) {
        Map<String, Integer> counters = counters(player);
        int until = state(player, moral) == FADING ? counters.getOrDefault(moral.key + "_fading", 0)
                : counters.getOrDefault(moral.key + "_cooldown", 0);
        return Math.max(0, until - minute(player));
    }

    // ------------------------------------------------------------ taking up and letting go

    public static void adopt(ServerPlayer player, Moral moral) {
        if (!Mortuary.canAdopt(player)) {
            say(player, "Your band cannot hold a rule like that yet. (Erectus and later.)");
            return;
        }
        Map<String, Integer> counters = counters(player);
        int state = state(player, moral);
        if (state == HELD) {
            say(player, "It is already the way of your people.");
            return;
        }
        if (state == FADING) {
            counters.remove(moral.key + "_fading");
            player.sendSystemMessage(Component.literal("The band takes it up again, and seems relieved: \""
                    + moral.title + ".\"").withStyle(ChatFormatting.GOLD));
            send(player);
            return;
        }
        if (state == COOLING) {
            say(player, "The band only just let go of that. Give it " + describe(minutesLeft(player, moral)) + ".");
            return;
        }
        Moral against = conflict(moral);
        if (against != null && holds(player, against)) {
            say(player, "That goes against \"" + against.title + "\". Let go of it first.");
            return;
        }
        if (moral == Moral.EAT_THE_DEAD) {
            Mortuary.adopt(player);
        } else {
            counters.put(moral.key, 1);
            EvolutionManager.incrementCriterion(player, "adopt_norm", 1);
            player.sendSystemMessage(Component.literal("It is decided: \"" + moral.title + ".\"")
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.literal("(" + moral.when.label() + ". " + moral.description + ")")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        send(player);
    }

    public static void retract(ServerPlayer player, Moral moral) {
        int state = state(player, moral);
        if (state == NONE || state == COOLING) {
            say(player, "That is not one of your people's ways.");
            return;
        }
        if (state == FADING) {
            say(player, "The band is already letting go of it.");
            return;
        }
        counters(player).put(moral.key + "_fading", minute(player) + FADE_MINUTES);
        player.sendSystemMessage(Component.literal("You begin to let go of \"" + moral.title + ".\" It will take the "
                + "band two days to forget it - and until then, it still binds.").withStyle(ChatFormatting.GRAY));
        send(player);
    }

    private static String describe(int minutes) {
        // A day is twenty minutes of game time.
        if (minutes >= 20) {
            int days = minutes / 20;
            int hours = (minutes % 20) * 24 / 20;
            return days + (days == 1 ? " day" : " days") + (hours > 0 ? " " + hours + "h" : "");
        }
        return Math.max(1, minutes * 24 / 20) + "h";
    }

    // ------------------------------------------------------------ the tab

    /** Opens the Culture tab: every moral, where it stands, and what the season is. */
    public static void send(ServerPlayer player) {
        List<Integer> states = new ArrayList<>();
        List<Integer> minutes = new ArrayList<>();
        List<Integer> binding = new ArrayList<>();
        for (Moral moral : Moral.values()) {
            states.add(state(player, moral));
            minutes.add(minutesLeft(player, moral));
            binding.add(applies(player, moral) ? 1 : 0);
        }
        PacketDistributor.sendToPlayer(player, new dev.hominin.evolution.network.MoralsPayload(
                Seasons.of(player.level()).label(), Seasons.daysLeft(player.level()), Drought.isActive(player.level()),
                states, minutes, binding));
    }

    // ------------------------------------------------------------ every few seconds

    private static final int THEFT_CHECK_TICKS = 3000;

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 100 == 70) {
            Map<String, Integer> counters = counters(player);
            for (Moral moral : Moral.values()) {
                int until = counters.getOrDefault(moral.key + "_fading", 0);
                if (until > 0 && minute(player) >= until) {
                    counters.remove(moral.key);
                    counters.remove(moral.key + "_fading");
                    counters.put(moral.key + "_cooldown", minute(player) + COOLDOWN_MINUTES);
                    player.sendSystemMessage(Component.literal("Your band has let go of \"" + moral.title + ".\"")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (player.tickCount % THEFT_CHECK_TICKS == 1500) {
            theft(player);
        }
    }

    // ------------------------------------------------------------ theft

    /**
     * Now and then somebody helps themselves to what another has. Hungry mouths in hard times
     * take food; in good times it is the things people covet. A rule against it, in the times it
     * is about, stops most of it and turns the rest back - at the cost of the thief's goodwill.
     */
    private static void theft(ServerPlayer player) {
        List<BandMember> band = new ArrayList<>();
        for (BandMember member : Band.ownNear(player, 32.0D)) {
            if (!member.isBaby()) {
                band.add(member);
            }
        }
        if (band.size() < 2) {
            return;
        }
        var random = player.getRandom();
        boolean hard = Seasons.strained(player.level());
        boolean plenty = Seasons.plentiful(player.level());
        if (random.nextFloat() >= Math.min(0.9F, (hard ? 0.35F : 0.15F) * Mood.theftFactor(player))) {
            return;
        }
        BandMember thief = band.get(random.nextInt(band.size()));
        // Those who do not care what the band thinks are the ones who help themselves.
        List<BandMember> careless = band.stream().filter(BandMember::isAntisocial).toList();
        if (!careless.isEmpty() && random.nextFloat() < 0.7F) {
            thief = careless.get(random.nextInt(careless.size()));
        }
        if (hard) {
            for (BandMember member : band) {
                if (member.getHunger() < thief.getHunger()) {
                    thief = member;
                }
            }
        }
        BandMember victim = null;
        int slot = -1;
        for (BandMember member : band) {
            if (member == thief) {
                continue;
            }
            int found = worthTaking(member.getInventory(), hard);
            if (found >= 0) {
                victim = member;
                slot = found;
                break;
            }
        }
        if (victim == null) {
            return;
        }
        thief.ensureName();
        victim.ensureName();
        Moral rule = hard ? Moral.NO_THEFT_HARD : plenty ? Moral.NO_THEFT_PLENTY : null;
        // Somebody who does not care defies the rule half the time - unless the band keeps its bullies down.
        boolean ruled = rule != null && applies(player, rule)
                && !(thief.isAntisocial() && !Mood.checked(player) && random.nextBoolean());
        String what = victim.getInventory().getItem(slot).getHoverName().getString().toLowerCase();
        String victimName = victim.getName().getString();
        if (ruled && random.nextFloat() < 0.7F) {
            if (random.nextFloat() < 0.3F) {
                Lines.tell(thief, "theft_deterred", victimName, what);
            }
            return;
        }
        if (ruled) {
            // Caught, and made to hand it back. The rule held - but the thief was shamed for it.
            Lines.announce(thief, "theft_caught", victimName, what);
            thief.addBond(-1);
            Cohesion.add(player, 1);
            return;
        }
        ItemStack taken = victim.getInventory().removeItem(slot, 1);
        thief.addToInventory(taken);
        Lines.announce(thief, "theft", victimName, what);
        Lines.say(victim, "theft_victim");
        // A theft in the band is a misfortune, not your failing.
        Cohesion.add(player, -1, null);
    }

    /** The first thing in a pack worth stealing: food when times are hard, stone or food otherwise. */
    private static int worthTaking(SimpleContainer pack, boolean hard) {
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (Wants.isFood(stack) || (!hard && Wants.isGoodStone(stack))) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * The player took this from a member's pack. In hard times, taking food from someone who is
     * hungry is theft whoever does it - and under a rule against it, the whole band sees its
     * leader break it.
     */
    public static void tookFrom(ServerPlayer player, BandMember member, ItemStack taken) {
        if (!Seasons.strained(player.level()) || !Wants.isFood(taken) || member.getHunger() >= BandMember.HUNGRY) {
            return;
        }
        member.ensureName();
        member.addBond(-1);
        if (applies(player, Moral.NO_THEFT_HARD)) {
            Cohesion.add(player, -3, "left " + member.getName().getString() + " their food, as your own rule says");
            player.sendSystemMessage(Component.literal("You took food from a hungry mouth - against the very rule "
                    + "you gave them. The band saw. (Cohesion -3, bond -1 with " + member.getName().getString() + ")")
                    .withStyle(ChatFormatting.DARK_RED));
        } else {
            player.displayClientMessage(Component.literal(member.getName().getString()
                    + " watches you take the last of their food. (Bond -1)").withStyle(ChatFormatting.GRAY), true);
        }
    }

    // ------------------------------------------------------------ what the other systems ask

    /** Bond lost when a want runs out: nothing in hard times under the tight-times rule, double under sharing. */
    public static int unmetWantCost(Player leader) {
        if (applies(leader, Moral.TIGHT_TIMES)) {
            return 0;
        }
        return applies(leader, Moral.ALWAYS_SHARE) ? 2 : 1;
    }

    /** A thought in keeping with the band's rules, if it has any that bind right now. */
    @Nullable
    public static String poolForThought(Player leader) {
        List<String> kinds = new ArrayList<>();
        if (holds(leader, Moral.EAT_THE_DEAD)) {
            kinds.add("thought_norm_dead");
        }
        if (applies(leader, Moral.ALWAYS_SHARE)) {
            kinds.add("thought_share");
        }
        if (applies(leader, Moral.TIGHT_TIMES)) {
            kinds.add("thought_tight");
        }
        if (applies(leader, Moral.NO_THEFT_HARD) || applies(leader, Moral.NO_THEFT_PLENTY)) {
            kinds.add("thought_no_theft");
        }
        return kinds.isEmpty() ? null : kinds.get(leader.getRandom().nextInt(kinds.size()));
    }

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    private Morals() {
    }
}
