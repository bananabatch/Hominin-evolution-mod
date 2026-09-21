package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Walking somebody to a place: ahead of them, but never so far ahead they lose sight of
 * it. When they fall behind it stops and waits, and when it gets there it points.
 */
public class GuideGoal extends Goal {
    private static final int GIVE_UP_TICKS = 20 * 60 * 3;
    private static final float WAIT_DISTANCE = 14.0F;

    private final BandMember member;
    private int ticks;

    public GuideGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return member.getGuideTarget() != null && member.guidedPlayer() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        member.stopGuiding();
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos target = member.getGuideTarget();
        Player player = member.guidedPlayer();
        // Ticked every tick, sometimes without canContinueToUse being asked first.
        if (target == null || player == null) {
            return;
        }
        ticks++;
        if (member.blockPosition().closerThan(target, 3.0D)) {
            member.getNavigation().stop();
            member.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            if (member.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5D, target.getY() + 1.2D,
                        target.getZ() + 0.5D, 12, 0.4D, 0.4D, 0.4D, 0.0D);
            }
            if (player.distanceToSqr(member) < 8.0D * 8.0D) {
                player.displayClientMessage(Component.literal(member.getName().getString()
                        + " crouches and slaps the stone. Here.").withStyle(ChatFormatting.GOLD), true);
                member.stopGuiding();
            }
            return;
        }
        if (member.distanceTo(player) > WAIT_DISTANCE) {
            // Waiting for you to catch up, looking back to see where you have got to.
            member.getNavigation().stop();
            member.getLookControl().setLookAt(player, 30.0F, 30.0F);
            return;
        }
        if (ticks % 20 == 1 || member.getNavigation().isDone()) {
            member.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.9D);
        }
    }
}
