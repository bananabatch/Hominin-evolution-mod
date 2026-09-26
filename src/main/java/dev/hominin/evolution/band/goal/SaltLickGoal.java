package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.world.Pois;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Going to the salt. Once a day, a member with a salt lick near enough walks over and licks the crust - a little
 * food and a while of mending, exactly what a player gets from it.
 */
public class SaltLickGoal extends Goal {
    private static final String SALT_DAY = "HomininSaltDay";
    private static final int RANGE = 48;
    private static final int GIVE_UP_TICKS = 600;

    private final BandMember member;
    @Nullable
    private BlockPos lick;
    private int ticks;

    public SaltLickGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private long today() {
        return member.level().getDayTime() / 24000L;
    }

    @Override
    public boolean canUse() {
        if (member.isBaby() || member.isSleeping() || member.getTarget() != null || member.inDanger()
                || member.getRandom().nextInt(400) != 0 || !(member.level() instanceof ServerLevel level)
                || member.getPersistentData().contains(SALT_DAY)
                        && member.getPersistentData().getLong(SALT_DAY) == today()) {
            return false;
        }
        BlockPos near = Pois.placedNear(level, member.blockPosition(), RANGE, Pois.Kind.LICK);
        if (near == null) {
            return false;
        }
        lick = saltAt(level, near);
        return lick != null;
    }

    /** A block of the crust itself, near the middle of the lick. */
    @Nullable
    private static BlockPos saltAt(ServerLevel level, BlockPos centre) {
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-3, -2, -3), centre.offset(3, 2, 3))) {
            if (level.getBlockState(pos).is(dev.hominin.evolution.ModBlocks.SALT_BLOCK.get())) {
                return pos.immutable();
            }
        }
        return null;
    }

    @Override
    public boolean canContinueToUse() {
        return lick != null && ticks < GIVE_UP_TICKS && member.getTarget() == null;
    }

    @Override
    public void start() {
        ticks = 0;
        member.getNavigation().moveTo(lick.getX() + 0.5D, lick.getY() + 1.0D, lick.getZ() + 0.5D, 1.0D);
    }

    @Override
    public void stop() {
        lick = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(lick.getX() + 0.5D, lick.getY() + 1.0D, lick.getZ() + 0.5D);
        if (member.distanceToSqr(lick.getX() + 0.5D, lick.getY() + 1.0D, lick.getZ() + 0.5D) > 2.5D * 2.5D) {
            if (ticks % 20 == 0 && member.getNavigation().isDone()) {
                member.getNavigation().moveTo(lick.getX() + 0.5D, lick.getY() + 1.0D, lick.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        member.getNavigation().stop();
        member.getPersistentData().putLong(SALT_DAY, today());
        member.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        member.playSound(SoundEvents.GENERIC_EAT, 0.6F, 1.3F);
        member.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 0));
        member.setHunger(member.getHunger() + 1);
        lick = null;
    }
}
