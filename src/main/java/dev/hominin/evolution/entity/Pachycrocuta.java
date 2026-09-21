package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pachycrocuta, the giant short-faced hyena: a bone-crusher the size of a lion.
 *
 * <p>It does not charge from across the plain. It picks out a hominin - the player, or
 * one of a band - and follows, quietly, keeping its distance. While someone is watching
 * it, it holds still. The moment nobody has looked its way for a few seconds, it comes in.
 *
 * <p>Spotting it is the defence. Turn and face it, and a threat display will very likely
 * drive it off. Ignore it, and it gets to choose when.
 */
public class Pachycrocuta extends PathfinderMob {
    /** How long it must go unwatched before it commits to an attack. */
    private static final int UNWATCHED_TICKS_TO_ATTACK = 100;
    private static final double STALK_DISTANCE = 16.0D;
    private static final double ATTACK_RANGE = 22.0D;
    private static final double QUARRY_SEARCH = 32.0D;
    /** How narrow a look counts as looking at it, as a dot product. */
    private static final double LOOKING_AT = 0.95D;
    private static final int FLEE_TICKS = 1200;

    @Nullable
    private LivingEntity quarry;
    private int unwatchedTicks;
    private int watchedTicks;
    private int fleeTicks;
    @Nullable
    private Vec3 fleeFrom;

    public Pachycrocuta(EntityType<? extends Pachycrocuta> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.45D, true));
        goalSelector.addGoal(3, new StalkGoal());
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------ being watched

    /** Whether this player is looking straight at it, with nothing in the way. */
    public boolean isWatchedBy(Player player) {
        if (player.isSpectator() || player.distanceToSqr(this) > 40.0D * 40.0D || !player.hasLineOfSight(this)) {
            return false;
        }
        Vec3 toMe = getEyePosition().subtract(player.getEyePosition()).normalize();
        return player.getViewVector(1.0F).normalize().dot(toMe) > LOOKING_AT;
    }

    /** Whoever might be keeping an eye out for the quarry: the quarry itself, or its band's leader. */
    @Nullable
    private Player watcherFor(LivingEntity target) {
        if (target instanceof Player player) {
            return player;
        }
        if (target instanceof BandMember member) {
            return member.companionPlayer();
        }
        return null;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (fleeTicks > 0) {
            fleeTicks--;
            quarry = null;
            setTarget(null);
            return;
        }
        if (tickCount % 10 != 0) {
            return;
        }
        if (quarry == null || !quarry.isAlive() || quarry.distanceToSqr(this) > QUARRY_SEARCH * QUARRY_SEARCH * 2
                || (quarry instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            quarry = findQuarry();
            unwatchedTicks = 0;
        }
        if (quarry == null || getTarget() != null) {
            return;
        }
        Player watcher = watcherFor(quarry);
        if (watcher != null && isWatchedBy(watcher)) {
            watchedTicks = 40;
            unwatchedTicks = 0;
            return;
        }
        watchedTicks = Math.max(0, watchedTicks - 10);
        unwatchedTicks += 10;
        if (unwatchedTicks >= UNWATCHED_TICKS_TO_ATTACK && distanceToSqr(quarry) < ATTACK_RANGE * ATTACK_RANGE) {
            setTarget(quarry);
            playSound(ModSounds.PACHYCROCUTA_GROWL.get(), 1.6F, 0.9F);
        }
    }

    @Nullable
    private LivingEntity findQuarry() {
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(QUARRY_SEARCH),
                e -> e.isAlive() && ((e instanceof Player p && !p.isCreative() && !p.isSpectator())
                        || e instanceof BandMember));
        LivingEntity best = null;
        for (LivingEntity candidate : candidates) {
            // Children and stragglers first, then whoever is nearest.
            double score = distanceToSqr(candidate) * (candidate.isBaby() ? 0.3D : 1.0D);
            if (best == null || score < distanceToSqr(best) * (best.isBaby() ? 0.3D : 1.0D)) {
                best = candidate;
            }
        }
        return best;
    }

    /** Whether it is close in - stalking or attacking - and so can be seen off by a display. */
    public boolean isCloseThreat() {
        return getTarget() != null || quarry != null;
    }

    /** Driven off by a threat display. It will not be back for a good while. */
    public void scare(LivingEntity scarer) {
        fleeTicks = FLEE_TICKS;
        fleeFrom = scarer.position();
        quarry = null;
        unwatchedTicks = 0;
        setTarget(null);
        getNavigation().stop();
        playSound(ModSounds.PACHYCROCUTA_HURT.get(), 1.2F, 1.3F);
    }

    /**
     * A display drives off every stalking hyena that can see it coming - one that is being
     * watched for certain, and one that thought itself unseen more often than not, better
     * with the band shouting too.
     */
    public static int scareNear(ServerPlayer player, double radius, int bandSize) {
        int scared = 0;
        for (Pachycrocuta hyena : player.level().getEntitiesOfClass(Pachycrocuta.class,
                player.getBoundingBox().inflate(radius), Pachycrocuta::isCloseThreat)) {
            float chance = hyena.isWatchedBy(player) ? 1.0F : Math.min(0.9F, 0.5F + 0.1F * bandSize);
            if (hyena.getRandom().nextFloat() < chance) {
                hyena.scare(player);
                scared++;
            }
        }
        if (scared > 0) {
            player.displayClientMessage(Component.literal("The hyena slinks away from the noise."), true);
        }
        return scared;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && getHealth() < getMaxHealth() * 0.3F
                && source.getEntity() instanceof LivingEntity attacker) {
            scare(attacker);
        }
        return hurt;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        // Silent while it follows someone. You will not hear it coming.
        return quarry != null || getTarget() != null ? null : ModSounds.PACHYCROCUTA_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PACHYCROCUTA_HURT.get();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    // ------------------------------------------------------------ goals

    private class StalkGoal extends Goal {
        StalkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return quarry != null && getTarget() == null && fleeTicks == 0;
        }

        @Override
        public void tick() {
            if (quarry == null) {
                return;
            }
            if (watchedTicks > 0) {
                // Caught out: freeze and stare back.
                getNavigation().stop();
                getLookControl().setLookAt(quarry, 20.0F, 20.0F);
                return;
            }
            if (tickCount % 20 != 0) {
                return;
            }
            Vec3 offset = position().subtract(quarry.position()).multiply(1.0D, 0.0D, 1.0D);
            if (offset.lengthSqr() < 1.0E-3D) {
                offset = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 spot = quarry.position().add(offset.normalize().scale(STALK_DISTANCE));
            getNavigation().moveTo(spot.x, spot.y, spot.z, 0.75D);
        }
    }

    private class FleeGoal extends Goal {
        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return fleeTicks > 0;
        }

        @Override
        public void tick() {
            if (getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(Pachycrocuta.this, 32, 7,
                        fleeFrom != null ? fleeFrom : position());
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.4D);
                }
            }
        }
    }
}
