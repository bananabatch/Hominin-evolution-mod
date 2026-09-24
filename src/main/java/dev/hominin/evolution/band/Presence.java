package dev.hominin.evolution.band;

import java.util.Map;

import dev.hominin.evolution.Attachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * How much the country around your camp knows to leave your band alone: presence, 0 to 50.
 *
 * <p>Every new stretch of ground starts at 20 - nobody knows you yet. It rises when you make yourselves
 * felt: a predator killed on your ground (more for the great cats and the giant hyena, which nothing
 * else stands up to), a kill taken off a scavenger, big game brought down. It falls when the country
 * gets the better of you - one of the band taken, food stolen out of your camp - and it fades a little
 * every day it is not kept up. From erectus a band can build a real presence.
 *
 * <p>What it buys: after four and a half days on the same ground the things that hunt there start
 * testing you (see {@link dev.hominin.evolution.hunt.Predation}), and a strong presence turns most of
 * that away - but only most, and never the ground wearing out under you. And other bands can tell:
 * a band with no presence to speak of is one a hungry band might raid.
 */
public final class Presence {
    private static final String KEY = "presence";
    private static final String DAY = "presence_day";
    public static final int START = 20;
    public static final int MAX = 50;
    /** Below this, a desperate band will think about raiding you. */
    public static final int WEAK = 15;
    /** At and above this, the country mostly leaves you alone. */
    public static final int STRONG = 35;

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    public static int get(ServerPlayer player) {
        return Math.max(0, Math.min(MAX, counters(player).getOrDefault(KEY, START)));
    }

    public static String label(int presence) {
        return presence >= 45 ? "commanding - nothing out there wants to test you"
                : presence >= STRONG ? "strong - most things keep away"
                : presence >= WEAK ? "ordinary"
                : "weak - you are easy pickings";
    }

    /** New country: nobody here knows your band, for good or ill. */
    public static void newGround(ServerPlayer player) {
        counters(player).put(KEY, START);
    }

    public static void add(ServerPlayer player, int delta, String why) {
        int before = get(player);
        int after = Math.max(0, Math.min(MAX, before + delta));
        if (after == before) {
            return;
        }
        counters(player).put(KEY, after);
        player.displayClientMessage(Component.literal("Presence " + (delta > 0 ? "+" : "") + delta + " (" + why + "): "
                + after + "/" + MAX).withStyle(delta > 0 ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED), true);
        if (before < STRONG && after >= STRONG) {
            player.sendSystemMessage(Component.literal("The country around your camp has learned to leave your band "
                    + "alone. (Presence " + after + ")").withStyle(ChatFormatting.GREEN));
        } else if (before >= WEAK && after < WEAK) {
            player.sendSystemMessage(Component.literal("Your band looks weak on this ground now - to the animals, "
                    + "and to other bands. (Presence " + after + ")").withStyle(ChatFormatting.GOLD));
        }
    }

    /** A predator killed by you or yours. The great cats and the giant hyena count for more. */
    public static void predatorKilled(ServerPlayer player, LivingEntity predator) {
        boolean great = predator instanceof dev.hominin.evolution.entity.Sabertooth
                || predator instanceof dev.hominin.evolution.entity.Homotherium
                || predator instanceof dev.hominin.evolution.entity.Pachycrocuta
                || predator instanceof dev.hominin.evolution.entity.Crocodile;
        add(player, great ? 4 : 2, "a predator killed");
        Relations.helpedAgainst(player, predator);
    }

    // ------------------------------------------------------------ fires and building

    private static final String FIRE_MINUTES = "presence_fire_minutes";
    private static final String BUILT = "presence_built";
    private static final String GAINED_DAY = "presence_gained_day";
    private static final String FIRE_GAINED = "presence_fire_gained";
    private static final String BUILD_GAINED = "presence_build_gained";

    private static void newDay(ServerPlayer player) {
        Map<String, Integer> counters = counters(player);
        int day = (int) (player.level().getDayTime() / 24000L);
        if (counters.getOrDefault(GAINED_DAY, -1) != day) {
            counters.put(GAINED_DAY, day);
            counters.remove(FIRE_GAINED);
            counters.remove(BUILD_GAINED);
        }
    }

    /**
     * A fire pit has burned another minute. For everyone whose ground it is on: ten minutes of fire kept
     * is a point of presence, up to three a day. Smoke over a camp says somebody lives there.
     */
    public static void fireKept(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pit) {
        for (ServerPlayer player : level.players()) {
            if (!dev.hominin.evolution.hunt.Predation.onOwnGround(player, pit)) {
                continue;
            }
            newDay(player);
            Map<String, Integer> counters = counters(player);
            int minutes = counters.merge(FIRE_MINUTES, 1, Integer::sum);
            if (minutes >= 10 && counters.getOrDefault(FIRE_GAINED, 0) < 3) {
                counters.put(FIRE_MINUTES, 0);
                counters.merge(FIRE_GAINED, 1, Integer::sum);
                add(player, 1, "a fire kept on your ground");
            }
        }
    }

    /** What counts as building: things a band makes and leaves standing. */
    private static boolean isStructure(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(dev.hominin.evolution.ModBlocks.FIRE_PIT.get())
                || state.is(dev.hominin.evolution.ModBlocks.BUILDING_BRANCH.get())
                || state.is(dev.hominin.evolution.ModBlocks.THATCH_BLOCK.get())
                || state.is(dev.hominin.evolution.ModBlocks.THATCH_BEDDING.get())
                || state.is(dev.hominin.evolution.ModBlocks.KNAPPING_STATION.get())
                || state.is(dev.hominin.evolution.ModBlocks.WORK_STATION.get());
    }

    /** Something built on your ground: every six is a point of presence, up to two a day. */
    public static void built(ServerPlayer player, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        if (!isStructure(state) || !dev.hominin.evolution.hunt.Predation.onOwnGround(player, pos)) {
            return;
        }
        newDay(player);
        Map<String, Integer> counters = counters(player);
        int built = counters.merge(BUILT, 1, Integer::sum);
        if (built >= 6 && counters.getOrDefault(BUILD_GAINED, 0) < 2) {
            counters.put(BUILT, 0);
            counters.merge(BUILD_GAINED, 1, Integer::sum);
            add(player, 1, "you are building here");
        }
    }

    /**
     * How much of what hunts is kept from this spot: ground a band holds strongly (theirs, or yours, at 28 and
     * up), and a prosperous day. Up to six in ten never come.
     */
    public static float predatorsKeptOff(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos at) {
        float kept = Bands.predatorsKeptOff(level, at);
        if (dev.hominin.evolution.survival.Drought.isProsperousDay(level)) {
            kept = Math.max(kept, 0.35F);
        }
        for (ServerPlayer player : level.players()) {
            int presence = get(player);
            if (presence >= 28 && dev.hominin.evolution.hunt.Predation.onOwnGround(player, at)) {
                kept = Math.max(kept, Math.min(0.6F, (presence - 25) / 40.0F));
            }
        }
        return kept;
    }

    /** Once a day: presence nobody keeps up fades. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 600 != 200) {
            return;
        }
        int day = (int) (player.level().getDayTime() / 24000L);
        Map<String, Integer> counters = counters(player);
        if (counters.getOrDefault(DAY, -1) == day) {
            return;
        }
        boolean first = !counters.containsKey(DAY);
        counters.put(DAY, day);
        if (!first && get(player) > START) {
            counters.put(KEY, get(player) - 1);
        }
        if (!first && day % 2 == 0) {
            dev.hominin.evolution.band.Claims.addFeared(player, -1);
        }
        if (!first) {
            // Allies near you lend you their name: everything out there knows who stands with you.
            long allies = Bands.all(player.serverLevel()).stream().filter(b -> !b.nomadic()
                    && Relations.standing(player, b) >= Relations.ALLIED
                    && Bands.horizontal(b.home, player.blockPosition()) < 400.0D * 400.0D).count();
            if (allies > 0) {
                add(player, (int) Math.min(2, allies), allies == 1 ? "an ally stands with you" : "allies stand with you");
            }
        }
    }

    private Presence() {
    }
}
