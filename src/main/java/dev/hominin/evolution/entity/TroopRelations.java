package dev.hominin.evolution.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What a baboon troop thinks of you.
 *
 * <p>Baboons and early hominins shared the same country for millions of years, and
 * neither could afford the other as an enemy. A troop that knows you is worth a great
 * deal: more eyes, more voices, and a mob that turns on anything with teeth. A troop
 * that has decided you are a threat is worth avoiding for as long as you live nearby.
 *
 * <p>And the line between the two is one bad swing. So a mistake is allowed to be a
 * mistake: hit one by accident and the troop stops, and waits, and gives you five
 * seconds to show it was not meant.
 */
public final class TroopRelations {
    /** Trust at which a troop starts treating you as one of the neighbours worth knowing. */
    public static final int TRUSTED = 6;
    /** How many of a trusted troop will travel with you by day. */
    public static final int MAX_ESCORTS = 3;
    /** The window to make it right. */
    public static final int WINDOW_TICKS = 5 * 20;
    /** A grudge is recorded as trust below zero. */
    private static final int GRUDGE = -1;

    private record Mistake(UUID troop, long deadline) {
    }

    private static final Map<UUID, Mistake> pending = new HashMap<>();

    /**
     * Kept in the player's evolution counters, so it survives a save - and is forgotten
     * when you evolve, because by then you are somewhere else, a long time later.
     */
    private static String key(UUID troop) {
        return "troop_" + troop;
    }

    public static int trust(Player player, UUID troop) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters()
                .getOrDefault(key(troop), 0);
    }

    private static void setTrust(Player player, UUID troop, int value) {
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put(key(troop), value);
    }

    public static boolean isTrusted(Player player, UUID troop) {
        return trust(player, troop) >= TRUSTED;
    }

    public static boolean holdsGrudge(Player player, UUID troop) {
        return trust(player, troop) <= GRUDGE;
    }

    /** Trust a gift of food buys. A trade buys half that. */
    public static final int GIFT_WORTH = 2;

    /** How many more gifts until the troop sends escorts. */
    public static int giftsToTrust(int trust) {
        return Math.max(0, (TRUSTED - trust + GIFT_WORTH - 1) / GIFT_WORTH);
    }

    /** Where you stand with a troop, in words and numbers. */
    public static Component standing(Player player, @Nullable UUID troop) {
        if (troop == null) {
            return Component.literal("This one has no troop yet.").withStyle(ChatFormatting.GRAY);
        }
        int trust = trust(player, troop);
        if (trust <= GRUDGE) {
            return Component.literal("This troop remembers what you did. Gifts will win them back - slowly.")
                    .withStyle(ChatFormatting.RED);
        }
        if (trust >= TRUSTED) {
            return Component.literal("This troop trusts you. Up to " + MAX_ESCORTS
                    + " will travel with you by day, and go home at night.").withStyle(ChatFormatting.GREEN);
        }
        int gifts = giftsToTrust(trust);
        return Component.literal("Troop trust: " + trust + "/" + TRUSTED + " - about " + gifts
                + (gifts == 1 ? " more gift" : " more gifts") + " of food before some will follow you.")
                .withStyle(ChatFormatting.YELLOW);
    }

    /** A trade, or a gift. Gifts count for more: nothing was asked back. */
    public static void goodwill(Player player, UUID troop, int amount) {
        int before = trust(player, troop);
        if (before <= GRUDGE) {
            // A troop you wronged does not forget over a few handouts - but it can be won
            // back, slowly, by somebody patient enough to keep trying.
            setTrust(player, troop, before + (player.getRandom().nextInt(4) == 0 ? 1 : 0));
            return;
        }
        int after = before + amount;
        setTrust(player, troop, after);
        if (before < TRUSTED && after >= TRUSTED) {
            player.sendSystemMessage(Component.literal(
                    "The troop has come to trust you. Some of them will travel with your band by day.")
                    .withStyle(ChatFormatting.GOLD));
        } else if (after < TRUSTED) {
            // How far there is still to go, so it is clear why none of them follow yet.
            int gifts = giftsToTrust(after);
            player.displayClientMessage(Component.literal(
                    "The troop is warming to you. (" + after + "/" + TRUSTED + " - " + gifts
                            + (gifts == 1 ? " more gift)" : " more gifts)"))
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    // ------------------------------------------------------------ the mistake

    /**
     * A blow landed on one of them. Returns true if the troop is holding off to see what
     * you do next - false if it is going for you now, because this was the second blow,
     * or because they already know what you are.
     */
    public static boolean mistake(ServerPlayer player, Baboon struck) {
        UUID troop = struck.getTroop();
        if (troop == null || holdsGrudge(player, troop)) {
            return false;
        }
        Mistake open = pending.get(player.getUUID());
        if (open != null && open.troop().equals(troop)) {
            // Hitting it again while they watch is not an accident.
            pending.remove(player.getUUID());
            fail(player, troop);
            return false;
        }
        pending.put(player.getUUID(), new Mistake(troop, player.level().getGameTime() + WINDOW_TICKS));
        dev.hominin.evolution.band.Band.standDown(struck);
        PacketDistributor.sendToPlayer(player, new dev.hominin.evolution.network.FocusPayload(WINDOW_TICKS));
        player.sendSystemMessage(Component.literal(
                "The whole troop goes still and stares at you. Give it something, or hold K and make yourself small.")
                .withStyle(ChatFormatting.RED));
        return true;
    }

    public static boolean hasPendingMistake(Player player) {
        return pending.containsKey(player.getUUID());
    }

    @Nullable
    public static UUID pendingTroop(Player player) {
        Mistake open = pending.get(player.getUUID());
        return open == null ? null : open.troop();
    }

    /** Put right in time: a gift, or a show of submission. */
    public static void forgive(ServerPlayer player, String how) {
        Mistake open = pending.remove(player.getUUID());
        if (open == null) {
            return;
        }
        // They let it go. They do not forget it entirely.
        setTrust(player, open.troop(), Math.max(0, trust(player, open.troop()) - 1));
        player.sendSystemMessage(Component.literal(how).withStyle(ChatFormatting.GREEN));
    }

    private static void fail(ServerPlayer player, UUID troop) {
        setTrust(player, troop, GRUDGE);
        List<Baboon> near = player.level().getEntitiesOfClass(Baboon.class,
                player.getBoundingBox().inflate(28.0D), b -> troop.equals(b.getTroop()));
        for (Baboon baboon : near) {
            baboon.turnOn(player);
        }
        player.sendSystemMessage(Component.literal(
                "The troop erupts. They will remember you.").withStyle(ChatFormatting.DARK_RED));
    }

    /** Runs out the clock on an open mistake, and recruits escorts from trusted troops. */
    public static void tick(ServerPlayer player) {
        Mistake open = pending.get(player.getUUID());
        if (open != null && player.level().getGameTime() >= open.deadline()) {
            pending.remove(player.getUUID());
            fail(player, open.troop());
        }
        if (player.tickCount % 100 == 0) {
            organiseEscorts(player);
        }
    }

    /**
     * By day, a few from a troop that trusts you come along. At night they go home -
     * a baboon sleeps with its own troop, in its own trees, whoever its friends are.
     */
    private static void organiseEscorts(ServerPlayer player) {
        List<Baboon> near = player.level().getEntitiesOfClass(Baboon.class,
                player.getBoundingBox().inflate(48.0D));
        boolean day = player.level().isDay();
        int escorting = 0;
        for (Baboon baboon : near) {
            if (baboon.isEscorting(player)) {
                if (!day || baboon.isAngry()) {
                    baboon.stopEscorting();
                    if (!day) {
                        player.displayClientMessage(Component.literal(
                                "The baboons head back to their troop for the night."), true);
                    }
                } else {
                    escorting++;
                }
            }
        }
        if (!day || escorting >= MAX_ESCORTS) {
            return;
        }
        for (Baboon baboon : near) {
            if (escorting >= MAX_ESCORTS) {
                break;
            }
            UUID troop = baboon.getTroop();
            if (troop != null && !baboon.isEscorting(player) && !baboon.isAngry() && !baboon.isBaby()
                    && isTrusted(player, troop) && baboon.distanceTo(player) < 32.0F) {
                baboon.escort(player);
                escorting++;
            }
        }
    }

    public static void forget(UUID player) {
        pending.remove(player);
    }

    private TroopRelations() {
    }
}
