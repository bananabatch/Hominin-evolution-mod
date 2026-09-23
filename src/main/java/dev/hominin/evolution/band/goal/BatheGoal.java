package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Grooming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/**
 * Getting in the water on a hot day. Macaques and chimps both do it, and a band that
 * lives beside a river spends time in it. It cools them off and washes them down, which
 * counts for the same thing grooming does.
 */
public class BatheGoal extends Goal {
    private static final int SEARCH_RADIUS = 12;
    private static final int BATHE_TICKS = 200;
    private static final int GIVE_UP_TICKS = 400;
    private static final int COOLDOWN = 3600;

    private final BandMember member;
    @Nullable
    private BlockPos water;
    private int ticks;
    private int bathing;
    private int nextTry;

    public BatheGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Level level = member.level();
        if (member.tickCount < nextTry || member.isBaby() || member.inDanger() || member.isUpATree()
                || member.isHungry() || !level.isDay() || member.getRandom().nextInt(200) != 0) {
            return false;
        }
        water = findWater();
        if (water == null) {
            nextTry = member.tickCount + COOLDOWN / 2;
        }
        return water != null;
    }

    @Override
    public boolean canContinueToUse() {
        return water != null && bathing < BATHE_TICKS && ticks < GIVE_UP_TICKS && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
        bathing = 0;
        dev.hominin.evolution.band.Lines.tell(member, "bathe");
        walk();
    }

    @Override
    public void stop() {
        if (bathing >= BATHE_TICKS) {
            Grooming.markGroomed(member);
        }
        water = null;
        member.getNavigation().stop();
        nextTry = member.tickCount + COOLDOWN + member.getRandom().nextInt(COOLDOWN);
    }

    @Override
    public void tick() {
        ticks++;
        if (!member.isInWater()) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        bathing++;
        if (bathing % 25 == 0) {
            member.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            member.level().playSound(null, member.blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL,
                    0.5F, 1.0F + member.getRandom().nextFloat() * 0.3F);
            if (member.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SPLASH, member.getX(), member.getY() + 0.8D, member.getZ(),
                        8, 0.3D, 0.2D, 0.3D, 0.0D);
            }
        }
    }

    private void walk() {
        member.getNavigation().moveTo(water.getX() + 0.5D, water.getY(), water.getZ() + 0.5D, 1.0D);
    }

    /** Water worth standing in: a shallow edge, not the middle of a lake. */
    @Nullable
    private BlockPos findWater() {
        Level level = member.level();
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
            if (!level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.above()).is(FluidTags.WATER)
                    || !level.getBlockState(pos.below()).isSolid()) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }
}
