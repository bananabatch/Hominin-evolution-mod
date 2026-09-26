package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.ErectusWork;
import dev.hominin.evolution.band.Lines;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Going out for hides. While the band is short of them - bedding wants three, and raw thatch on a build one each -
 * an armed member picks out a grazer with a good skin on it and goes after it. Two at most at a time; never a
 * predator, never something too big to take, never anyone's tame animal. The attack itself is the ordinary fight:
 * this only chooses what to fight. The hide comes off the kill and they pick it up.
 */
public class HideHuntGoal extends Goal {
    private static final int GIVE_UP_TICKS = 900;
    private static final double LOOK = 20.0D;
    private static final int MAX_HUNTERS = 2;

    private final BandMember member;
    @Nullable
    private LivingEntity quarry;
    private int ticks;
    private int nextTry;

    public HideHuntGoal(BandMember member) {
        this.member = member;
        // No flags: the fighting goal does the running and the striking.
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (member.tickCount < nextTry) {
            return false;
        }
        nextTry = member.tickCount + 400 + member.getRandom().nextInt(400);
        if (!ErectusWork.works(member) || !member.level().isDay() || member.isTurnedIn() || member.inDanger()
                || member.getTarget() != null || member.isUpATree() || member.isOnWatch() || member.isInjured()
                || member.isPregnant() || !member.carriesWeapon() || !member.huntsWithLeader()) {
            return false;
        }
        ServerPlayer leader = ErectusWork.leader(member);
        // Getting ready for a feast, the band hunts for the meat as well as the skin.
        boolean feast = leader != null && dev.hominin.evolution.band.Feast.preparing(leader);
        if (leader == null || member.distanceToSqr(leader) > 40.0D * 40.0D || !ErectusWork.wantsHide(leader) && !feast
                || hunters(leader) >= (feast ? MAX_HUNTERS + 1 : MAX_HUNTERS)) {
            return false;
        }
        quarry = pick();
        return quarry != null;
    }

    /** How many of the band are out after a skin already. */
    private static int hunters(ServerPlayer leader) {
        int count = 0;
        for (BandMember other : Band.all(leader)) {
            if (other.getTarget() != null && huntable(other.getTarget())) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private LivingEntity pick() {
        List<LivingEntity> near = member.level().getEntitiesOfClass(LivingEntity.class,
                member.getBoundingBox().inflate(LOOK), HideHuntGoal::huntable);
        LivingEntity best = null;
        for (LivingEntity candidate : near) {
            if (best == null || candidate.distanceToSqr(member) < best.distanceToSqr(member)) {
                best = candidate;
            }
        }
        return best;
    }

    /** A grazer with a whole hide on it, grown, wild, and not something that fights like a cat. */
    public static boolean huntable(LivingEntity target) {
        if (!target.isAlive() || target.isBaby() || target instanceof Player || target instanceof BandMember
                || target instanceof net.minecraft.world.entity.monster.Enemy
                || target.getType().is(ModTags.EntityTypes.PREDATORS) || target.getType().is(ModTags.EntityTypes.MEGAFAUNA)
                || dev.hominin.evolution.hunt.Hides.chanceFor(target) < 1.0F) {
            return false;
        }
        if (target instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()
                || target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse && horse.isTamed()) {
            return false;
        }
        // Somebody's: on a lead, or given a name.
        return !(target instanceof Mob mob && (mob.isLeashed() || mob.hasCustomName()));
    }

    @Override
    public boolean canContinueToUse() {
        return quarry != null && quarry.isAlive() && ticks < GIVE_UP_TICKS && !member.inDanger()
                && member.distanceToSqr(quarry) < 32.0D * 32.0D;
    }

    @Override
    public void start() {
        ticks = 0;
        member.setTarget(quarry);
        Lines.announce(member, "hunt_hide", quarry.getName().getString().toLowerCase());
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
        if (quarry != null && !quarry.isAlive()) {
            Lines.announce(member, "hunt_hide_done", quarry.getName().getString().toLowerCase());
        } else if (quarry != null && member.getTarget() == quarry) {
            // It got away, or they gave up on it.
            member.setTarget(null);
        }
        quarry = null;
        nextTry = member.tickCount + 1200 + member.getRandom().nextInt(1200);
    }
}
