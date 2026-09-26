package dev.hominin.evolution.band;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Negotiating: getting more out of another band than they meant to give. Level 4 (tongue-tied) down to level 0
 * (flawless) - lower is better, like every skill. You start fair-spoken or better (2, sometimes 1); most of the band
 * is plainer than that, a few are good at it, and a psychopath is always flawless at it - it is what they are for.
 *
 * <p>Whoever near you talks best does the talking: at a trade, a gift, an offer for your ground. A party sent to
 * another band talks as well as the best of it. Good deals teach you: two at level 4, three at 3, four at 2, and
 * eight more at level 1 before nobody can talk you round.
 */
public final class Negotiation {
    /** Carries over when you evolve: a way with people is learned, not grown. */
    public static final String LEVEL = EvolutionManager.SKILL_PREFIX + "negotiating_level";
    private static final String PROGRESS = EvolutionManager.SKILL_PREFIX + "negotiating_progress";
    private static final int[] TO_ADVANCE = {0, 8, 4, 3, 2};
    /** Close enough to do the talking for you. */
    private static final double NEAR = 16.0D;

    /** Who is doing the talking, and how well: one of the band, or you (member null). */
    public record Talker(@Nullable BandMember member, int level) {
        public String name(ServerPlayer player) {
            if (member == null) {
                return "You";
            }
            member.ensureName();
            return member.getName().getString();
        }
    }

    // ------------------------------------------------------------ levels

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    /** The player's own: 2 or 1 to begin with. */
    public static int level(ServerPlayer player) {
        return counters(player).computeIfAbsent(LEVEL, key -> player.getRandom().nextFloat() < 0.4F ? 1 : 2);
    }

    /** A band member's: mostly 3 or 4, sometimes 2, rarely 1, very rarely 0. */
    public static int rollMember(RandomSource random) {
        float roll = random.nextFloat();
        return roll < 0.03F ? 0 : roll < 0.13F ? 1 : roll < 0.40F ? 2 : roll < 0.75F ? 3 : 4;
    }

    public static String word(int level) {
        return switch (level) {
            case 0 -> "(flawless - they would sell you their own ground)";
            case 1 -> "(persuasive - rare)";
            case 2 -> "(fair-spoken)";
            case 3 -> "(plain)";
            default -> "(tongue-tied)";
        };
    }

    /** How much further a deal goes their way: nothing at 4, half again at 0. */
    public static float edge(int level) {
        return switch (level) {
            case 0 -> 0.5F;
            case 1 -> 0.3F;
            case 2 -> 0.15F;
            case 3 -> 0.05F;
            default -> 0.0F;
        };
    }

    /** The chance an offer is talked up a tier in a trade. */
    private static float talkUpChance(int level) {
        return switch (level) {
            case 0 -> 1.0F;
            case 1 -> 0.6F;
            case 2 -> 0.3F;
            case 3 -> 0.1F;
            default -> 0.0F;
        };
    }

    /** You, or whoever near you talks better - a psychopath, if one is about, always does. */
    public static Talker talker(ServerPlayer player) {
        Talker best = new Talker(null, level(player));
        for (BandMember member : Band.ownNear(player, NEAR)) {
            if (!member.isBaby() && member.getNegotiateLevel() < best.level()) {
                best = new Talker(member, member.getNegotiateLevel());
            }
        }
        return best;
    }

    /** The best talker in a party sent off on its own. */
    public static int partyLevel(ServerLevel level, List<UUID> members) {
        int best = 4;
        for (UUID id : members) {
            if (level.getEntity(id) instanceof BandMember member) {
                best = Math.min(best, member.getNegotiateLevel());
            }
        }
        return best;
    }

    // ------------------------------------------------------------ at the dealing

    /** A trade with another band: what you offer is talked up a tier, sometimes. */
    public static int talkUp(ServerPlayer player, BandMember other, int offerTier) {
        if (!other.isWild()) {
            return offerTier;
        }
        Talker talker = talker(player);
        if (talker.member() != null && talker.member().isPsychopath()) {
            return Psychopaths.talkUp(player, other, offerTier);
        }
        if (player.getRandom().nextFloat() >= talkUpChance(talker.level())) {
            return offerTier;
        }
        player.displayClientMessage(Component.literal((talker.member() == null ? "You talk it up" : talker.name(player)
                + " leans in and talks it up") + " - they take it for more than it is worth.")
                .withStyle(ChatFormatting.GOLD), false);
        return offerTier + 1;
    }

    /** A gift counts for more, told well. */
    public static int talkUpGift(ServerPlayer player, int worth) {
        Talker talker = talker(player);
        if (talker.member() != null && talker.member().isPsychopath()) {
            return Psychopaths.talkUpGift(player, worth);
        }
        return worth + Math.round(worth * edge(talker.level()));
    }

    /** How far a band can be pushed over your ground: the more persuasive, the further. */
    public static int allowance(ServerPlayer player, int base) {
        return Math.round(base * (1.0F + edge(talker(player).level())));
    }

    /** What a band asks in return, scaled: a good talker gets the same for less. */
    public static float asking(int level) {
        return 1.0F - edge(level) * 0.5F;
    }

    // ------------------------------------------------------------ learning

    /** A good deal struck. Returns true if it took you down a level. */
    public static boolean practise(ServerPlayer player) {
        int level = level(player);
        if (level <= 0) {
            return false;
        }
        Map<String, Integer> counters = counters(player);
        int progress = counters.getOrDefault(PROGRESS, 0) + 1;
        if (progress < TO_ADVANCE[level]) {
            counters.put(PROGRESS, progress);
            return false;
        }
        counters.put(PROGRESS, 0);
        counters.put(LEVEL, level - 1);
        player.sendSystemMessage(Component.literal("You know how to talk to them now. Negotiating: level " + (level - 1)
                + (level - 1 == 0 ? " - flawless. Nobody talks you round." : ".")).withStyle(ChatFormatting.GOLD));
        return true;
    }

    public static String describe(ServerPlayer player) {
        int level = level(player);
        if (level <= 0) {
            return "level 0 " + word(0) + " - +50% on every deal";
        }
        return "level " + level + " " + word(level) + " - +" + Math.round(edge(level) * 100) + "% on deals ("
                + counters(player).getOrDefault(PROGRESS, 0) + "/" + TO_ADVANCE[level] + " good deals to the next)";
    }

    private Negotiation() {
    }
}
