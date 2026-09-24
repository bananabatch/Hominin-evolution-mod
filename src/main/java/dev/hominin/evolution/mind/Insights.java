package dev.hominin.evolution.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.world.Land;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Things nobody tells you. Some of what the country holds is not written anywhere - where the big herds
 * come to calve, which soil is richer than the rest - and all you get is a feeling: <i>you feel like
 * something deeper could be going on.</i> Stop and think (hold the think key) and you work out what.
 *
 * <p>Anything else that should be worked out rather than told can hang off this the same way: a hint,
 * and a reveal when you think on it where you are.
 */
public final class Insights {
    private static final long HINT_GAP_TICKS = 12000L;
    private static final Map<String, Long> hinted = new HashMap<>();

    /**
     * Every ten seconds: nothing of its own now. Hidden ground - a breeding ground, fertile soil - is a place
     * like any other (see {@link dev.hominin.evolution.world.Pois}), which does its own noticing.
     */
    public static void tick(ServerPlayer player) {
    }

    /**
     * The feeling, and the nudge to think: said at most every ten minutes for the same thing. Any system with
     * something to be worked out rather than told can call this.
     */
    public static void hint(ServerPlayer player, String key) {
        String id = player.getUUID() + "|" + key;
        long now = player.level().getGameTime();
        if (now - hinted.getOrDefault(id, -HINT_GAP_TICKS) < HINT_GAP_TICKS) {
            return;
        }
        if (hinted.size() > 2048) {
            hinted.clear();
        }
        hinted.put(id, now);
        player.sendSystemMessage(Component.literal("You feel like something deeper could be going on here. (Hold "
                + "the think key to think on it.)").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.THINK_DEEPER);
    }

    /**
     * A hold of the think key where something is waiting to be worked out: worked out. Free - it is noticing.
     * On your own ground, thinking back over all of it turns up what it hides, wherever that is.
     */
    public static boolean tryReveal(ServerPlayer player) {
        if (dev.hominin.evolution.world.Pois.revealHere(player)) {
            return true;
        }
        return revealOnGround(player);
    }

    private static boolean revealOnGround(ServerPlayer player) {
        if (!dev.hominin.evolution.hunt.Predation.onOwnGround(player, player.blockPosition())) {
            return false;
        }
        for (Land.Part part : Land.ofPlayer(player).parts()) {
            if (part.hidden() == null || part.where() == null || Land.knows(player, part.hidden())) {
                continue;
            }
            Land.learn(player, part.hidden());
            BlockPos where = part.where();
            int distance = (int) Math.sqrt(player.blockPosition().distSqr(new BlockPos(where.getX(), player.getBlockY(),
                    where.getZ())));
            String bearing = dev.hominin.evolution.band.WildBands.bearingTo(player, where);
            boolean breeding = part.hidden().startsWith("breeding");
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.6F, 0.8F);
            player.sendSystemMessage(Component.literal(breeding
                    ? "Thinking back over your ground, it comes together: the young you keep seeing, the trampled grass - "
                            + "the big herds come to calve about " + distance + " blocks " + bearing + " of here. A breeding "
                            + "ground. Anyone would want this ground."
                    : "Thinking back over your ground, you remember dark, soft soil, thick with roots, about " + distance
                            + " blocks " + bearing + " of here. Fertile ground: it gives far longer before it runs out.")
                    .withStyle(ChatFormatting.GOLD));
            dev.hominin.evolution.world.Pois.learnAt(player, where, part.hidden());
            return true;
        }
        return false;
    }

    public static void forget(UUID player) {
        hinted.keySet().removeIf(key -> key.startsWith(player.toString()));
    }

    private Insights() {
    }
}
