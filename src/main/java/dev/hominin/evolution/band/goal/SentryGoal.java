package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.List;

import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Keeping watch. Someone asked to stay up does not lie down with the rest: they walk slow circles round the
 * camp all night, looking outward, and the first thing that comes out of the dark - a cat, a hyena, people -
 * they shout about, and the camp wakes.
 */
public class SentryGoal extends Goal {
    private static final int CIRCLE = 9;
    private final BandMember member;
    private int nextPoint;
    private float angle;
    private int lastShout = -9999;

    public SentryGoal(BandMember member) {
        this.member = member;
        this.angle = member.getRandom().nextFloat() * Mth.TWO_PI;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return member.isOnWatch() && member.leaderPlayer() != null && !member.inDanger() && member.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        nextPoint = 0;
    }

    @Override
    public void tick() {
        Player leader = member.leaderPlayer();
        if (leader == null) {
            return;
        }
        if (--nextPoint <= 0) {
            // A slow circle round wherever the leader is lying.
            angle += 0.7F + member.getRandom().nextFloat() * 0.4F;
            double x = leader.getX() + Mth.cos(angle) * CIRCLE;
            double z = leader.getZ() + Mth.sin(angle) * CIRCLE;
            member.getNavigation().moveTo(x, leader.getY(), z, 0.7D);
            nextPoint = 80;
        }
        // Looking out, not in.
        member.getLookControl().setLookAt(member.getX() + (member.getX() - leader.getX()), member.getEyeY(),
                member.getZ() + (member.getZ() - leader.getZ()));
        if (member.tickCount % 20 != 0 || member.tickCount - lastShout < 600) {
            return;
        }
        List<Mob> hunters = member.level().getEntitiesOfClass(Mob.class, member.getBoundingBox().inflate(24.0D),
                m -> m.isAlive() && m.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS));
        if (hunters.isEmpty()) {
            return;
        }
        lastShout = member.tickCount;
        member.raiseAlarm(200);
        member.ensureName();
        if (leader instanceof ServerPlayer player) {
            if (player.isSleeping()) {
                player.stopSleepInBed(true, true);
            }
            player.sendSystemMessage(Component.literal("<" + member.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Wake up! Something out there - " + hunters.get(0).getName().getString()
                            .toLowerCase() + "!").withStyle(ChatFormatting.RED)));
        }
        for (BandMember other : Band.near(member, 24.0D)) {
            if (other.isLedBy(leader) && other.isSleeping()) {
                other.stopSleeping();
            }
        }
    }
}
