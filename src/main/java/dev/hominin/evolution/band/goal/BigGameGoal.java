package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Bands;
import dev.hominin.evolution.band.Lines;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

/**
 * Hunting without you. A member who is armed and hungry - or whose band is getting ready for a feast, or who simply
 * sees its chance - picks out game worth the running and goes after it, calling one or two of the others along. From
 * erectus, with enough spears together, that can be one of the giants. And whatever they have wounded they keep
 * after while it bleeds - running it down, as a player does - rather than letting it go the moment it bolts.
 *
 * <p>Like the hide hunt, this only chooses what to fight; the ordinary fighting goal does the running and the blows.
 */
public class BigGameGoal extends Goal {
    private static final double LOOK = 24.0D;
    private static final int GIVE_UP_TICKS = 1800;
    /** A giant needs this many spears at it, counting the one who starts it. */
    private static final int GIANT_PARTY = 3;

    private final BandMember member;
    @Nullable
    private LivingEntity quarry;
    private int ticks;
    private int nextTry;

    public BigGameGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (member.tickCount < nextTry) {
            return false;
        }
        nextTry = member.tickCount + 300 + member.getRandom().nextInt(300);
        if (member.isBaby() || !member.level().isDay() || member.isTurnedIn() || member.inDanger()
                || member.getTarget() != null || member.isUpATree() || member.isOnWatch() || member.isInjured()
                || member.isPregnant() || !member.carriesWeapon()) {
            return false;
        }
        Player leader = member.leaderPlayer();
        if (leader != null && (!member.huntsWithLeader() || member.distanceToSqr(leader) > 48.0D * 48.0D)) {
            return false;
        }
        boolean feast = member.level() instanceof net.minecraft.server.level.ServerLevel
                && dev.hominin.evolution.band.Feast.preparing(member);
        if (!member.isHungry() && !feast && member.getRandom().nextInt(6) != 0) {
            return false;
        }
        quarry = pick();
        return quarry != null;
    }

    @Nullable
    private LivingEntity pick() {
        List<BandMember> party = armedNear();
        boolean giants = Bands.erectusOn(member.getStage()) && party.size() + 1 >= GIANT_PARTY;
        LivingEntity best = null;
        for (LivingEntity candidate : member.level().getEntitiesOfClass(LivingEntity.class,
                member.getBoundingBox().inflate(LOOK), target -> game(target, giants))) {
            if (best == null || candidate.distanceToSqr(member) < best.distanceToSqr(member)) {
                best = candidate;
            }
        }
        return best;
    }

    private List<BandMember> armedNear() {
        return Band.near(member, 16.0D).stream().filter(other -> other != member && other.isAlliedTo(member)
                && !other.isBaby() && other.carriesWeapon() && other.getTarget() == null && !other.isInjured()
                && !other.isTurnedIn() && !other.isOnWatch()).toList();
    }

    /** Game worth a hunt: grown, wild, not a hunter itself, big enough to feed more than one - or a giant. */
    private static boolean game(LivingEntity target, boolean giants) {
        if (!target.isAlive() || target.isBaby() || target instanceof Player || target instanceof BandMember
                || target instanceof net.minecraft.world.entity.monster.Enemy
                || target.getType().is(ModTags.EntityTypes.PREDATORS)) {
            return false;
        }
        boolean giant = target.getType().is(ModTags.EntityTypes.MEGAFAUNA);
        if (giant ? !giants : !(target instanceof Animal) || target.getMaxHealth() < 8.0F) {
            return false;
        }
        if (target instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()
                || target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse && horse.isTamed()) {
            return false;
        }
        return !(target instanceof Mob mob && (mob.isLeashed() || mob.hasCustomName()));
    }

    @Override
    public boolean canContinueToUse() {
        if (quarry == null || !quarry.isAlive() || ticks >= GIVE_UP_TICKS || member.inDanger()) {
            return false;
        }
        // Bleeding, it cannot outrun them for long: they stay on it much further than on anything unhurt.
        double reach = quarry.hasEffect(ModEffects.BLEEDING) ? 64.0D : 32.0D;
        return member.distanceToSqr(quarry) < reach * reach;
    }

    @Override
    public void start() {
        ticks = 0;
        member.setTarget(quarry);
        Lines.say(member, "hunting_alone");
        // One or two come along - more for a giant.
        int calls = quarry.getType().is(ModTags.EntityTypes.MEGAFAUNA) ? GIANT_PARTY + 1 : 2;
        for (BandMember other : armedNear()) {
            if (calls-- <= 0) {
                break;
            }
            other.setTarget(quarry);
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (quarry != null && quarry.isAlive() && member.getTarget() == null) {
            member.setTarget(quarry);
        }
    }

    @Override
    public void stop() {
        if (quarry != null && quarry.isAlive() && member.getTarget() == quarry) {
            member.setTarget(null);
        }
        quarry = null;
        nextTry = member.tickCount + 2400 + member.getRandom().nextInt(2400);
    }
}
