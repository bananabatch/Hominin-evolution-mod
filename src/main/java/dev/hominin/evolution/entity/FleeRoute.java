package dev.hominin.evolution.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Which way to run. Vanilla's way of choosing somewhere to flee to is a random spot in the half of the world
 * away from the threat, picked again every time - and it often finds nowhere at all, so the animal simply stands
 * there. Picked again and again, the spots zig-zag, and an animal chased in circles runs in circles.
 *
 * <p>This holds a heading instead. Away from the threat, bent a little towards the way it was already going, so
 * a run is a run and not a scramble; and when the way ahead is blocked - water, a cliff, trees - it tries a
 * little to either side, then further, before it ever falls back on a random spot. Something is always found.
 */
public final class FleeRoute {
    /** Tried in this order: straight on, then wider and wider either side. */
    private static final float[] TURNS = {0.0F, 30.0F, -30.0F, 60.0F, -60.0F, 95.0F, -95.0F, 140.0F, -140.0F};

    /**
     * Sets the mob running from a threat. Returns the heading it took (flat, unit length), to be passed back in
     * next time so the run keeps its line - or null if it found nowhere to go.
     */
    @Nullable
    public static Vec3 run(PathfinderMob mob, Vec3 threat, @Nullable Vec3 heading, int distance, double speed) {
        Vec3 away = new Vec3(mob.getX() - threat.x, 0.0D, mob.getZ() - threat.z);
        if (away.lengthSqr() < 1.0E-4D) {
            away = Vec3.directionFromRotation(0.0F, mob.getYRot()).multiply(-1.0D, 0.0D, -1.0D);
        }
        away = away.normalize();
        Vec3 want = heading == null ? away : heading.scale(0.55D).add(away.scale(0.45D));
        if (want.lengthSqr() < 1.0E-4D) {
            want = away;
        }
        want = want.normalize();
        float jitter = (mob.getRandom().nextFloat() - 0.5F) * 20.0F;
        for (float turn : TURNS) {
            Vec3 dir = want.yRot((float) Math.toRadians(turn + jitter));
            // Never back towards what it is running from.
            if (dir.dot(away) < -0.2D) {
                continue;
            }
            int x = (int) Math.floor(mob.getX() + dir.x * distance);
            int z = (int) Math.floor(mob.getZ() + dir.z * distance);
            if (!mob.level().hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos target = new BlockPos(x, y, z);
            if (Math.abs(y - mob.getBlockY()) > 6 || !mob.level().getFluidState(target.below()).isEmpty()
                    || mob.level().getFluidState(target.below()).is(FluidTags.LAVA)) {
                continue;
            }
            Path path = mob.getNavigation().createPath(target, 1);
            if (path == null || path.getNodeCount() < 2) {
                continue;
            }
            // A path that gives up a few blocks out is no use: it has to actually get somewhere.
            BlockPos end = path.getEndNode() == null ? null : path.getEndNode().asBlockPos();
            if (end == null || end.distSqr(mob.blockPosition()) < (distance * 0.45D) * (distance * 0.45D)) {
                continue;
            }
            mob.getNavigation().moveTo(path, speed);
            return dir;
        }
        Vec3 spot = DefaultRandomPos.getPosAway(mob, distance, 7, threat);
        if (spot == null) {
            spot = LandRandomPos.getPosAway(mob, distance, 7, threat);
        }
        if (spot == null) {
            spot = LandRandomPos.getPos(mob, 10, 7);
        }
        if (spot != null && mob.getNavigation().moveTo(spot.x, spot.y, spot.z, speed)) {
            Vec3 dir = new Vec3(spot.x - mob.getX(), 0.0D, spot.z - mob.getZ());
            return dir.lengthSqr() < 1.0E-4D ? away : dir.normalize();
        }
        return null;
    }

    private FleeRoute() {
    }
}
