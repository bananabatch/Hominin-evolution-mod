package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A wild band's alpha leading it across country. Everyone else follows the alpha, so
 * the whole band drifts over the landscape together instead of milling on one spot.
 */
public class RoamGoal extends Goal {
    private static final int MIN_DISTANCE = 16;
    private static final int MAX_DISTANCE = 36;
    private static final int GIVE_UP_TICKS = 800;

    private final BandMember member;
    private BlockPos target;
    private int ticks;

    public RoamGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (member.isInjured()) {
            // Laid up: nobody goes far on a bad leg.
            return false;
        }
        if (!member.isAlpha() || member.isGuest() || member.getLeavePos() != null || member.getRandom().nextInt(300) != 0) {
            return false;
        }
        float angle = member.getRandom().nextFloat() * Mth.TWO_PI;
        int distance = MIN_DISTANCE + member.getRandom().nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
        if (member.level() instanceof net.minecraft.server.level.ServerLevel server) {
            var band = dev.hominin.evolution.band.Bands.get(server, member.getBandId());
            if (band != null) {
                // Back towards camp when straying past the middle of their ground (a troop: towards today's camp).
                double keep = band.nomadic() ? 24.0D : band.radius() * 0.55D;
                double dx = band.home.getX() - member.getX();
                double dz = band.home.getZ() - member.getZ();
                if (dx * dx + dz * dz > keep * keep) {
                    angle = (float) Math.atan2(dz, dx) + (member.getRandom().nextFloat() - 0.5F) * 0.6F;
                }
            }
        }
        int x = member.getBlockX() + Math.round(Mth.cos(angle) * distance);
        int z = member.getBlockZ() + Math.round(Mth.sin(angle) * distance);
        if (!member.level().hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        target = new BlockPos(x, member.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < GIVE_UP_TICKS && !member.getNavigation().isDone();
    }

    @Override
    public void start() {
        ticks = 0;
        member.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.75D);
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void stop() {
        target = null;
    }
}
