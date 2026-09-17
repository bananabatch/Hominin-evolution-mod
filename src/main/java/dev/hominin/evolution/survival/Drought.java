package dev.hominin.evolution.survival;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * A dry spell. Every second day the land may be strained: less to forage, water worth
 * more, and other bands far less willing to share anything with anybody.
 *
 * <p>Worked out from the world seed and the day, so every player in a world sees the
 * same drought without anything having to be saved.
 */
public final class Drought {
    private static final float CHANCE = 0.3F;

    public static long dayOf(Level level) {
        return level.getDayTime() / 24000L;
    }

    public static boolean isActive(Level level) {
        long day = dayOf(level);
        if (day % 2L != 0L || !(level instanceof ServerLevel server)) {
            return false;
        }
        long hash = server.getSeed() * 31L + day * 6364136223846793005L;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        return Math.abs(hash % 1000L) < (long) (CHANCE * 1000.0F);
    }

    /** How much of the usual luck foraging has today. */
    public static float forageMultiplier(Level level) {
        return isActive(level) ? 0.55F : 1.0F;
    }

    private Drought() {
    }
}
