package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * How the band feels about you as its leader: 0 to 50, and a new band starts at 30.
 *
 * <ul>
 * <li><b>10 and under - dire.</b> One more failing of yours and they drive you out (and sometimes
 * make sure you know why first).
 * <li><b>20 and under - borderline.</b> They will not trade with you, they say what they think of
 * how you lead, and they tell you what you should have done.
 * <li><b>21 to 29 - tipping.</b> They tell you what you could be doing, and turn some trades down -
 * unless you promise to do better. Break that promise and it costs you badly.
 * <li><b>30 to 39 - neutral.</b> Life as usual.
 * <li><b>40 to 49 - positive.</b> They listen to you more, warm to you faster, give now and then,
 * and hold their nerve better when something comes for them.
 * <li><b>50 - perfect.</b> Everyone treats you at bond 2 at the least, warms to you fast, gives
 * often, forages and gathers better, and comes at once when you are hurt.
 * </ul>
 */
public final class Cohesion {
    public static final int MIN = 0;
    public static final int MAX = 50;
    public static final int NEUTRAL = 30;
    public static final int DIRE = 10;
    public static final int BORDERLINE = 20;
    public static final int POSITIVE = 40;

    /** Old saves counted up from nothing; this marks one already moved onto the 0-50 scale. */
    private static final String SCALED = EvolutionManager.SKILL_PREFIX + "cohesion_scaled";
    private static final String PROMISE_UNTIL = "cohesion_promise_minute";
    private static final String PROMISE_FROM = "cohesion_promise_from";
    private static final String NEXT_WORD = "cohesion_next_word_minute";
    private static final String UPKEEP_DAY = "cohesion_upkeep_day";
    /** A promise to do better is judged after a day: it has to have risen by this much. */
    private static final int PROMISE_MINUTES = 20;
    private static final int PROMISE_RISE = 3;
    private static final int PROMISE_BROKEN_COST = 6;

    /** What you last got wrong, for them to throw back at you. */
    private static final Map<UUID, String> lastFault = new HashMap<>();
    /** Per-player cooldowns on the small, repeatable things that build cohesion. */
    private static final Map<String, Long> cooldowns = new HashMap<>();

    private static Map<String, Integer> counters(Player player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    // ------------------------------------------------------------ reading it

    public static int get(Player player) {
        Map<String, Integer> counters = counters(player);
        if (!counters.containsKey(SCALED)) {
            // Onto the new scale: whatever was built up before counts a little above neutral.
            int old = counters.getOrDefault(Band.COHESION, 0);
            counters.put(Band.COHESION, Math.min(POSITIVE + 5, NEUTRAL + old / 3));
            counters.put(SCALED, 1);
        }
        return Math.max(MIN, Math.min(MAX, counters.getOrDefault(Band.COHESION, NEUTRAL)));
    }

    public static String label(int cohesion) {
        return cohesion <= DIRE ? "dire - one more failing and they drive you out"
                : cohesion <= BORDERLINE ? "borderline - no trades, and they say why"
                : cohesion < NEUTRAL ? "tipping - they want to see you do better"
                : cohesion < POSITIVE ? "neutral"
                : cohesion < MAX ? "positive - they listen to you" : "perfect - they would do anything for you";
    }

    public static boolean positive(@Nullable Player player) {
        return player != null && get(player) >= POSITIVE;
    }

    public static boolean perfect(@Nullable Player player) {
        return player != null && get(player) >= MAX;
    }

    // ------------------------------------------------------------ changing it

    /** A fresh band has no history with you. */
    public static void reset(Player player) {
        Map<String, Integer> counters = counters(player);
        counters.put(SCALED, 1);
        counters.put(Band.COHESION, NEUTRAL);
        counters.remove(PROMISE_UNTIL);
        counters.remove(PROMISE_FROM);
        lastFault.remove(player.getUUID());
    }

    /**
     * Adds or takes away. A loss that was your failing carries what you did wrong, for them to
     * remember - and only a failing, not a misfortune, is what finally makes them drive you out.
     */
    public static void add(ServerPlayer player, int delta, @Nullable String fault) {
        if (delta == 0) {
            return;
        }
        int before = get(player);
        int after = Math.max(MIN, Math.min(MAX, before + delta));
        counters(player).put(Band.COHESION, after);
        if (delta < 0) {
            if (fault != null) {
                lastFault.put(player.getUUID(), fault);
            }
            if (promised(player)) {
                breakPromise(player);
                after = get(player);
            }
            if (before <= DIRE && fault != null && !Band.all(player).isEmpty()) {
                // One failing too many.
                driveOut(player);
                return;
            }
        }
        if (after != before) {
            player.displayClientMessage(Component.literal("Band cohesion " + (delta > 0 ? "+" : "") + delta + ": "
                    + after + "/" + MAX).withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        }
        announceCrossing(player, before, after);
    }

    public static void add(ServerPlayer player, int delta) {
        add(player, delta, null);
    }

    /** A small repeatable good thing, counted at most once per cooldown. */
    public static void addLimited(ServerPlayer player, String what, int delta, long cooldownTicks) {
        String key = player.getUUID() + what;
        long now = player.level().getGameTime();
        if (now < cooldowns.getOrDefault(key, 0L)) {
            return;
        }
        cooldowns.put(key, now + cooldownTicks);
        add(player, delta);
    }

    private static void announceCrossing(ServerPlayer player, int before, int after) {
        String message = null;
        ChatFormatting style = ChatFormatting.GOLD;
        if (after <= DIRE && before > DIRE) {
            message = "The band has had nearly enough of you. One more failing and they will drive you out.";
            style = ChatFormatting.DARK_RED;
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.COHESION_DIRE);
        } else if (after <= BORDERLINE && before > BORDERLINE) {
            message = "The band's patience is thin. They will not trade with you now, and they are saying so.";
            style = ChatFormatting.RED;
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.COHESION_BORDERLINE);
        } else if (after < NEUTRAL && before >= NEUTRAL) {
            message = "The band is watching you. They want to see you do better. (H: \"I'll do better\")";
            style = ChatFormatting.YELLOW;
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.COHESION_TIPPING);
        } else if (after >= MAX && before < MAX) {
            message = "The band is as close as a band can be. They would do anything for you.";
            style = ChatFormatting.LIGHT_PURPLE;
        } else if (after >= POSITIVE && before < POSITIVE) {
            message = "The band trusts you. They listen more, and hold together under pressure.";
            style = ChatFormatting.GREEN;
        } else if (after >= NEUTRAL && before < NEUTRAL) {
            message = "The band settles back into its ways with you.";
            style = ChatFormatting.GRAY;
        }
        if (message != null) {
            player.sendSystemMessage(Component.literal(message + " (Cohesion " + after + "/" + MAX + ")").withStyle(style));
        }
    }

    // ------------------------------------------------------------ the bottom

    /** They have had enough: they turn on you, and send you off alone. */
    private static void driveOut(ServerPlayer player) {
        List<BandMember> band = Band.all(player);
        BandMember alpha = null;
        for (BandMember member : band) {
            if (!member.isBaby() && (alpha == null || member.getMaxHealth() > alpha.getMaxHealth())) {
                alpha = member;
            }
            if (!member.isBaby()) {
                Band.performDisplay(member);
            }
        }
        String why = lastFault.getOrDefault(player.getUUID(), "you let them down once too often");
        player.sendSystemMessage(Component.literal("They have had enough. " + capitalise(why) + " - and that was the last time. "
                + "The whole band turns on you and drives you out.").withStyle(ChatFormatting.DARK_RED));
        if (alpha != null && player.getRandom().nextFloat() < 0.3F) {
            // Sometimes they make sure you understand.
            alpha.ensureName();
            player.hurt(player.damageSources().mobAttack(alpha), player.getMaxHealth() * 0.6F);
            player.sendSystemMessage(Component.literal(alpha.getName().getString() + " beats you before they let you go.")
                    .withStyle(ChatFormatting.DARK_RED));
        }
        Band.driveOut(player);
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // ------------------------------------------------------------ the promise

    public static boolean promised(Player player) {
        return counters(player).getOrDefault(PROMISE_UNTIL, 0) > 0;
    }

    /** "I'll do better." Worth something below neutral - and a debt if it is not kept. */
    public static void promise(ServerPlayer player) {
        int cohesion = get(player);
        if (cohesion >= NEUTRAL) {
            player.displayClientMessage(Component.literal("Nobody is asking you to. The band is content."), true);
            return;
        }
        if (promised(player)) {
            player.displayClientMessage(Component.literal("You already promised. They are waiting to see it."), true);
            return;
        }
        if (cohesion <= BORDERLINE) {
            player.displayClientMessage(Component.literal("It has gone past promises. Only what you do now will count."),
                    true);
            return;
        }
        int minute = (int) (player.level().getGameTime() / 1200L);
        counters(player).put(PROMISE_UNTIL, minute + PROMISE_MINUTES);
        counters(player).put(PROMISE_FROM, cohesion);
        player.sendSystemMessage(Component.literal("You promise the band you will do better. They will hold you to it: "
                + "a day to show it (cohesion up by " + PROMISE_RISE + ", and no new failings). Until then they will "
                + "trade with you.").withStyle(ChatFormatting.AQUA));
    }

    private static void breakPromise(ServerPlayer player) {
        counters(player).remove(PROMISE_UNTIL);
        counters(player).remove(PROMISE_FROM);
        int after = Math.max(MIN, get(player) - PROMISE_BROKEN_COST);
        counters(player).put(Band.COHESION, after);
        player.sendSystemMessage(Component.literal("You promised them. They remember. (Cohesion -" + PROMISE_BROKEN_COST
                + ": " + after + "/" + MAX + ")").withStyle(ChatFormatting.DARK_RED));
    }

    // ------------------------------------------------------------ what it does

    /** A trade turned down, with the reason given - or null if they will trade. */
    @Nullable
    public static String refusesTrade(ServerPlayer player, BandMember member) {
        int cohesion = get(player);
        if (cohesion <= BORDERLINE) {
            return "\"Trade? With you? Not the way things are.\"";
        }
        if (cohesion < NEUTRAL && !promised(player) && member.getRandom().nextBoolean()) {
            return "\"Not today. Show us you can lead first.\" (Promise to do better, under H.)";
        }
        return null;
    }

    /** How many more come when you go after something: they listen to a leader they trust. */
    public static int extraHelpers(Player player) {
        int cohesion = get(player);
        return cohesion >= MAX ? 2 : cohesion >= POSITIVE ? 1 : 0;
    }

    /** Bond gained on top of what was earned: warmer, faster. */
    public static int bondBonus(@Nullable Player player, net.minecraft.util.RandomSource random) {
        if (player == null) {
            return 0;
        }
        int cohesion = get(player);
        return cohesion >= MAX ? 1 : cohesion >= POSITIVE && random.nextBoolean() ? 1 : 0;
    }

    /** How much more often a member gives unasked. */
    public static float giftFactor(@Nullable Player player) {
        if (player == null) {
            return 1.0F;
        }
        int cohesion = get(player);
        return cohesion >= MAX ? 2.0F : cohesion >= POSITIVE ? 1.5F : cohesion <= BORDERLINE ? 0.3F : 1.0F;
    }

    /**
     * How steady the band's nerve is: at the top freezing is rarer and standing to fight likelier; at
     * the bottom it is the other way round - a band that does not trust its leader freezes and bolts.
     */
    public static float nerve(@Nullable Player player) {
        if (player == null) {
            return 0.0F;
        }
        int cohesion = get(player);
        return cohesion >= MAX ? 0.25F : cohesion >= POSITIVE ? 0.15F : cohesion >= NEUTRAL ? 0.0F
                : cohesion > BORDERLINE ? -0.08F : cohesion > DIRE ? -0.15F : -0.25F;
    }

    // ------------------------------------------------------------ over time

    /** Every ten seconds, per player: promises judged, words said, and the upkeep of a close band. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 200 != 60 || Band.all(player).isEmpty()) {
            return;
        }
        Map<String, Integer> counters = counters(player);
        int minute = (int) (player.level().getGameTime() / 1200L);
        int cohesion = get(player);
        // A promise, judged.
        int until = counters.getOrDefault(PROMISE_UNTIL, 0);
        if (until > 0 && minute >= until) {
            int from = counters.getOrDefault(PROMISE_FROM, cohesion);
            counters.remove(PROMISE_UNTIL);
            counters.remove(PROMISE_FROM);
            if (cohesion >= from + PROMISE_RISE) {
                add(player, 2);
                player.sendSystemMessage(Component.literal("You kept your word, and the band saw it.")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                breakPromise(player);
            }
            return;
        }
        // Being close takes keeping up.
        int day = (int) (player.level().getDayTime() / 24000L);
        if (counters.getOrDefault(UPKEEP_DAY, -1) != day) {
            counters.put(UPKEEP_DAY, day);
            if (cohesion > POSITIVE) {
                counters.put(Band.COHESION, cohesion - 1);
            }
        }
        // Below neutral, they tell you.
        if (cohesion >= NEUTRAL || minute < counters.getOrDefault(NEXT_WORD, 0)) {
            return;
        }
        counters.put(NEXT_WORD, minute + 4 + player.getRandom().nextInt(4));
        List<BandMember> near = Band.ownNear(player, 24.0D);
        near.removeIf(BandMember::isBaby);
        if (near.isEmpty()) {
            return;
        }
        BandMember speaker = near.get(player.getRandom().nextInt(near.size()));
        speaker.ensureName();
        String line;
        if (cohesion <= BORDERLINE) {
            String fault = lastFault.get(player.getUUID());
            String[] scorn = {"You lead us like someone who has never been hungry.",
                    "We follow you because there is nobody else. That is all.",
                    "Every day with you costs us something."};
            line = scorn[player.getRandom().nextInt(scorn.length)]
                    + (fault != null ? " You should have - " + fault + "." : "");
        } else {
            String[] advice = {"Share food with us - all of us, not just whoever is closest.",
                    "When one of us needs something, that comes first. Before wants, before anything.",
                    "Groom us. Sit with us. We need to know you are one of us.",
                    "Bring what people ask you for. We notice who waits.",
                    "Our dead deserve their rites. Do not walk away from them.",
                    "Keep us moving. The country learns a band that stays."};
            line = advice[player.getRandom().nextInt(advice.length)];
        }
        player.sendSystemMessage(Component.literal("<" + speaker.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(cohesion <= BORDERLINE ? ChatFormatting.RED : ChatFormatting.WHITE)));
    }

    public static void forget(UUID player) {
        lastFault.remove(player);
    }

    private Cohesion() {
    }
}
