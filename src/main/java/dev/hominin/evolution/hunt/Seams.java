package dev.hominin.evolution.hunt;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * How much a single outcrop has already given up.
 *
 * <p>A deposit is not consumed by working it - the seam outlasts you, and that is the
 * point of finding one. But a hammerstone is not stone, it is a particular shape of
 * stone: one round, dense, fist-sized cobble that happened to shear off whole. A seam has
 * a few of those in it and then it does not have any more.
 *
 * <p>Without that, one quartzite face and a two-second cooldown was an infinite supply of
 * hammerstones, which made every other seam in the world pointless to walk to.
 */
public final class Seams {
    /** How many hammerstones one outcrop will ever give. */
    private static final int COBBLES_PER_SEAM = 2;

    /** A rough ceiling, so a long-lived world cannot grow this without bound. */
    private static final int MAX_TRACKED = 4096;

    private record Seam(ResourceKey<Level> level, BlockPos pos) {
    }

    private static final Map<Seam, Integer> taken = new HashMap<>();

    /**
     * Whether this outcrop still has a cobble in it, and takes one if so.
     *
     * @return true if the seam gave one up, false once it is worked out.
     */
    public static boolean takeCobble(Level level, BlockPos pos) {
        if (taken.size() > MAX_TRACKED) {
            taken.clear();
        }
        Seam seam = new Seam(level.dimension(), pos.immutable());
        int already = taken.getOrDefault(seam, 0);
        if (already >= COBBLES_PER_SEAM) {
            return false;
        }
        taken.put(seam, already + 1);
        return true;
    }

    /** Whether this outcrop has nothing round left in it. */
    public static boolean isWorkedOut(Level level, BlockPos pos) {
        return taken.getOrDefault(new Seam(level.dimension(), pos.immutable()), 0) >= COBBLES_PER_SEAM;
    }

    private Seams() {
    }
}
