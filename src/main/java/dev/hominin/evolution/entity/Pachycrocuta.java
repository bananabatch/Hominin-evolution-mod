package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pachycrocuta, the giant short-faced hyena: a bone-crusher the size of a lion, and alone.
 *
 * <p>It lives off other animals' work. A megafauna carcass is what it wants most, then any
 * kill it can smell, and when it finds one with you standing over it, it does not wait to be
 * warned off - it takes it off you. Between meals it follows a band, keeping its distance,
 * holding still whenever someone looks its way. Let it go unwatched and it comes in for
 * whoever is at the edge, kills them, and is gone into the grass before the band has turned.
 *
 * <p>Spotting it is the defence, and it is not an easy one: a display only sometimes moves
 * it, and fighting it means fighting something that hits like a big cat and does not bleed out.
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
    /** How far it can smell a kill from. */
    private static final double MEAL_SEARCH = 56.0D;
    /** Anybody this close to its meal is standing between it and its meal. */
    private static final double GUARD_RANGE = 7.0D;
    private static final int EAT_TICKS = 100;
    /** Full, it leaves everyone alone for most of a day. */
    private static final int FED_TICKS = 12000;

    @Nullable
    private LivingEntity quarry;
    private int unwatchedTicks;
    private int watchedTicks;
    private int fleeTicks;
    @Nullable
    private Vec3 fleeFrom;
    @Nullable
    private BlockPos meal;
    private int eating;
    private long fedUntil;
    private boolean warnedOverMeal;

    public Pachycrocuta(EntityType<? extends Pachycrocuta> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 70.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(2, new PredatorAttackGoal(this, 1.4D, 40, 16));
        goalSelector.addGoal(3, new MealGoal());
        goalSelector.addGoal(4, new StalkGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    private boolean isFed() {
        return level().getGameTime() < fedUntil;
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
        if (tickMeal()) {
            return;
        }
        if (isFed()) {
            quarry = null;
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

    /**
     * Food first. Returns true while a meal is what it is doing - going to one, fighting over
     * one, or eating one - so the stalking waits.
     */
    private boolean tickMeal() {
        if (!(level() instanceof ServerLevel level)) {
            return false;
        }
        if (meal != null && !Crocuta.isCarcass(level.getBlockState(meal))) {
            meal = null;
            eating = 0;
        }
        // Full, it leaves the next carcass for later - and everyone alone.
        if (meal == null && getTarget() == null && !isFed() && tickCount % 40 == 0) {
            meal = dev.hominin.evolution.hunt.Carcasses.nearestKill(level, blockPosition(), MEAL_SEARCH, true);
            warnedOverMeal = false;
            eating = 0;
        }
        if (meal == null) {
            return false;
        }
        quarry = null;
        Vec3 food = Vec3.atCenterOf(meal);
        LivingEntity target = getTarget();
        if (target != null) {
            // Fighting over the carcass. Once whoever it was has left it, back to the food.
            if (!target.isAlive() || target.distanceToSqr(food) > 16.0D * 16.0D) {
                setTarget(null);
            }
            return true;
        }
        LivingEntity guard = guardOf(meal);
        if (guard != null && distanceToSqr(food) < 18.0D * 18.0D) {
            setTarget(guard);
            playSound(ModSounds.PACHYCROCUTA_GROWL.get(), 2.0F, 0.8F);
            if (!warnedOverMeal) {
                warnedOverMeal = true;
                Player told = guard instanceof Player player ? player
                        : guard instanceof BandMember member ? member.companionPlayer() : null;
                if (told != null) {
                    told.sendSystemMessage(Component.literal("A giant hyena has come for the kill, and it is not "
                            + "going to walk around you to get it.").withStyle(ChatFormatting.RED));
                }
            }
            return true;
        }
        if (distanceToSqr(food) <= 2.8D * 2.8D) {
            eating += 10;
            if (eating % 30 == 0) {
                level.playSound(null, meal, SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 1.0F, 0.6F);
            }
            if (eating >= EAT_TICKS) {
                finishMeal(level);
            }
        }
        return true;
    }

    /** Somebody standing over the carcass: you, or one of yours. */
    @Nullable
    private LivingEntity guardOf(BlockPos carcass) {
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, new AABB(carcass).inflate(GUARD_RANGE),
                e -> e.isAlive() && ((e instanceof Player p && !p.isCreative() && !p.isSpectator())
                        || e instanceof BandMember));
        LivingEntity nearest = null;
        for (LivingEntity entity : near) {
            if (nearest == null || entity.distanceToSqr(this) < nearest.distanceToSqr(this)) {
                nearest = entity;
            }
        }
        return nearest;
    }

    /** It cracks the whole thing and carries off what it cannot finish. Nothing is left. */
    private void finishMeal(ServerLevel level) {
        BlockPos eaten = meal;
        boolean giant = level.getBlockState(eaten).is(ModBlocks.GIANT_CARCASS.get());
        level.removeBlock(eaten, false);
        level.playSound(null, eaten, SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME, eaten.getX() + 0.5D,
                eaten.getY() + 0.4D, eaten.getZ() + 0.5D, 16, 0.4D, 0.2D, 0.4D, 0.0D);
        Player nearest = level.getNearestPlayer(eaten.getX(), eaten.getY(), eaten.getZ(), 48.0D, false);
        if (nearest != null) {
            nearest.displayClientMessage(Component.literal(giant
                    ? "The giant hyena has cracked the great carcass apart and dragged off what it could not eat."
                    : "The giant hyena has eaten the kill, bones and all."), true);
        }
        meal = null;
        eating = 0;
        fedUntil = level.getGameTime() + FED_TICKS;
    }

    @Nullable
    private LivingEntity findQuarry() {
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(QUARRY_SEARCH),
                e -> e.isAlive() && ((e instanceof Player p && !p.isCreative() && !p.isSpectator())
                        || e instanceof BandMember));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            // It wants one of the band, not you: children and stragglers first.
            double score = distanceToSqr(candidate) * (candidate.isBaby() ? 0.3D : 1.0D)
                    * (candidate instanceof Player ? 2.5D : 1.0D);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** Kills, and runs. It will not stand over a body with the rest of the band coming for it. */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (!hit || !(target instanceof LivingEntity bitten)) {
            return hit;
        }
        // Bone-cracking jaws. Whatever they close on does not stop bleeding by itself.
        dev.hominin.evolution.combat.Bleeding.inflict(bitten, dev.hominin.evolution.combat.Bleeding.Tier.INTERNAL);
        if (bitten.isDeadOrDying() && bitten instanceof BandMember member && meal == null) {
            Player leader = member.companionPlayer();
            flee(bitten.position());
            fedUntil = level().getGameTime() + FED_TICKS / 2;
            if (leader != null) {
                leader.sendSystemMessage(Component.literal("The giant hyena has what it came for, and is gone into "
                        + "the grass before anyone can turn.").withStyle(ChatFormatting.DARK_RED));
            }
        }
        return hit;
    }

    /** Whether it is close in - stalking, attacking, or at a kill - and so a display might move it. */
    public boolean isCloseThreat() {
        return getTarget() != null || quarry != null || meal != null;
    }

    private void flee(Vec3 from) {
        fleeTicks = FLEE_TICKS;
        fleeFrom = from;
        quarry = null;
        meal = null;
        eating = 0;
        unwatchedTicks = 0;
        setTarget(null);
        getNavigation().stop();
        dev.hominin.evolution.band.Band.standDown(this);
    }

    public boolean isFleeing() {
        return fleeTicks > 0;
    }

    /** Driven off. It will not be back for a good while. */
    public void scare(LivingEntity scarer) {
        if (meal != null && scarer instanceof ServerPlayer player) {
            dev.hominin.evolution.EvolutionManager.incrementCriterion(player, "take_kill", 1);
            dev.hominin.evolution.band.Presence.add(player, 4, "you drove the giant hyena off a kill");
        }
        flee(scarer.position());
        playSound(ModSounds.PACHYCROCUTA_HURT.get(), 1.2F, 1.3F);
    }

    /**
     * A display is worth trying and not worth counting on. Caught in the act of stalking, with
     * you staring straight at it, it usually goes; otherwise it takes a band shouting at your
     * back, and even then it often just stands there.
     */
    public static int scareNear(ServerPlayer player, double radius, int bandSize) {
        int scared = 0;
        int stood = 0;
        for (Pachycrocuta hyena : player.level().getEntitiesOfClass(Pachycrocuta.class,
                player.getBoundingBox().inflate(radius), Pachycrocuta::isCloseThreat)) {
            float chance = hyena.isWatchedBy(player) ? 0.7F : Math.min(0.55F, 0.2F + 0.08F * bandSize);
            if (hyena.getRandom().nextFloat() < chance) {
                hyena.scare(player);
                scared++;
            } else {
                stood++;
                hyena.playSound(ModSounds.PACHYCROCUTA_GROWL.get(), 1.8F, 0.75F);
            }
        }
        if (scared > 0) {
            player.displayClientMessage(Component.literal("The giant hyena slinks away from the noise."), true);
        } else if (stood > 0) {
            player.displayClientMessage(Component.literal("The giant hyena does not move. It has seen displays before.")
                    .withStyle(ChatFormatting.RED), true);
        }
        return scared;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && getHealth() < getMaxHealth() * 0.25F
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
    public float getVoicePitch() {
        return 0.8F + random.nextFloat() * 0.1F;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return meal == null;
    }

    // ------------------------------------------------------------ goals

    private class MealGoal extends Goal {
        MealGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return meal != null && getTarget() == null && fleeTicks == 0;
        }

        @Override
        public void tick() {
            if (meal == null) {
                return;
            }
            Vec3 food = Vec3.atCenterOf(meal);
            if (distanceToSqr(food) > 2.5D * 2.5D) {
                if (tickCount % 20 == 0) {
                    getNavigation().moveTo(food.x, meal.getY(), food.z, 1.1D);
                }
            } else {
                getNavigation().stop();
                getLookControl().setLookAt(food.x, meal.getY(), food.z);
            }
        }
    }

    private class StalkGoal extends Goal {
        StalkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return quarry != null && getTarget() == null && fleeTicks == 0 && meal == null;
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
                Vec3 away = DefaultRandomPos.getPosAway(Pachycrocuta.this, 40, 7,
                        fleeFrom != null ? fleeFrom : position());
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.45D);
                }
            }
        }
    }
}
