package dev.hominin.evolution.survival;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * A dry spell. Every second day the land may be strained: less to forage, water worth
 * more, and other bands far less willing to share anything with anybody. Never two days
 * running - but in a dry season most second days are dry, and in the rains hardly any.
 *
 * <p>Worked out from the world seed and the day, so every player in a world sees the
 * same drought without anything having to be saved.
 */
public final class Drought {
    private static final float DRY_SEASON_CHANCE = 0.7F;
    private static final float PROSPEROUS_CHANCE = 0.1F;

    /** Days since the current age began: every evolution starts the count - and the seasons - over. */
    public static long dayOf(Level level) {
        return Math.max(0L, level.getDayTime() / 24000L - Era.startDay(level));
    }

    public static boolean isActive(Level level) {
        long day = dayOf(level);
        if (!(level instanceof ServerLevel server) || Seasons.veryProsperous(level)) {
            return false;
        }
        if (Seasons.superDry(level)) {
            // Any day at all, three in five.
            long h = server.getSeed() * 13L + day * 0xD1B54A32D192ED03L;
            h ^= h >>> 31;
            h *= 0x94d049bb133111ebL;
            h ^= h >>> 29;
            return Math.abs(h % 1000L) < 600L;
        }
        if (day % 2L != 0L) {
            return false;
        }
        long hash = server.getSeed() * 31L + day * 6364136223846793005L;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        float chance = Seasons.isDry(level) ? DRY_SEASON_CHANCE : PROSPEROUS_CHANCE;
        return Math.abs(hash % 1000L) < (long) (chance * 1000.0F);
    }

    private static final float PROSPEROUS_DAY_IN_RAINS = 0.4F;
    private static final float PROSPEROUS_DAY_IN_DRY = 0.08F;

    /**
     * The other kind of day: everything goes right. Foraging comes easy, fewer things that hunt are about,
     * ground gives far more before it is picked clean and comes back overnight. Only ever on the days a dry
     * day cannot fall - often in the rains, hardly ever in the dry.
     */
    public static boolean isProsperousDay(Level level) {
        long day = dayOf(level);
        if (!(level instanceof ServerLevel server) || Seasons.superDry(level)) {
            return false;
        }
        if (Seasons.veryProsperous(level)) {
            long h = server.getSeed() * 19L + day * 0xA24BAED4963EE407L;
            h ^= h >>> 31;
            h *= 0x9FB21C651E98DF25L;
            h ^= h >>> 29;
            return Math.abs(h % 1000L) < 600L;
        }
        if (day % 2L != 1L) {
            return false;
        }
        long hash = server.getSeed() * 17L + day * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 31;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 29;
        float chance = Seasons.isDry(level) ? PROSPEROUS_DAY_IN_DRY : PROSPEROUS_DAY_IN_RAINS;
        return Math.abs(hash % 1000L) < (long) (chance * 1000.0F);
    }

    /** How much of the usual luck foraging has today. */
    public static float forageMultiplier(Level level) {
        return (isActive(level) ? 0.55F : isProsperousDay(level) ? 1.35F : 1.0F) * Seasons.forageFactor(level);
    }

    private Drought() {
    }
}
