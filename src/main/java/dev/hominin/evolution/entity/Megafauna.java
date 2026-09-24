package dev.hominin.evolution.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The great grazers that came after Pelorovis in the hunt: Mammuthus, Megalotragus, Rusingoryx.
 *
 * <p>They graze near water in small groups and never start a fight. Struck while still strong, the
 * ones that fight turn on you - and their blows are heavy and slow, and usually telegraphed: the
 * head comes up, the feet stamp, there is a moment to read it and get out of the way. Usually. Now
 * and then one comes without the warning. After a charge it has to gather itself again, and that is
 * the time to hit it. Worn down, they break and run like any megafauna, and it becomes a persistence
 * hunt (see {@link dev.hominin.evolution.hunt.Quarry}).
 *
 * <p>The biggest carry hide too thick for a sharpened stick or a flake to do much: they take their
 * full hurt only from a fire-hardened spear or better - a hand axe, a cleaver. Their skulls are too
 * heavy to be concussed, though a club still breaks their bones.
 */
public abstract class Megafauna extends Animal {
    private static final EntityDataAccessor<Boolean> WINDING = SynchedEntityData.defineId(Megafauna.class,
            EntityDataSerializers.BOOLEAN);

    protected Megafauna(EntityType<? extends Megafauna> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------ what kind of animal it is

    /** Below this share of its health it stops fighting and runs. Zero for an animal that never fights. */
    protected abstract float breaksAt();

    /** Whether weapons below a fire-hardened spear barely mark it. */
    protected abstract boolean thickHide();

    /** Ticks of warning before a charge; 0 never charges. */
    protected abstract int windUpTicks();

    /** The chance a charge comes without its warning. */
    protected float unwarnedChance() {
        return 0.2F;
    }

    /** The sound of the warning. */
    protected abstract SoundEvent warningSound();

    protected float warningPitch() {
        return 1.0F;
    }

    /** How far the charge carries it, at most. */
    protected double chargeSpeed() {
        return 1.6D;
    }

    public boolean standsGround() {
        return breaksAt() > 0.0F && getHealth() > getMaxHealth() * breaksAt();
    }

    /** Whether it is gathering itself to charge - the model raises the head and stamps. */
    public boolean isWindingUp() {
        return entityData.get(WINDING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WINDING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (windUpTicks() > 0) {
            goalSelector.addGoal(1, new ChargeGoal(this));
        }
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6D, 0.003F));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    // ------------------------------------------------------------ hide

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (thickHide() && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && !attacker.getMainHandItem().is(dev.hominin.evolution.ModTags.Items.BIG_GAME_WEAPONS)) {
            // A flake or a pointed stick barely gets through a hide like this.
            amount *= 0.25F;
            if (attacker instanceof Player player && random.nextInt(3) == 0) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "It barely marks the hide. This needs a fire-hardened spear, or a hand axe."), true);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    // ------------------------------------------------------------ the charge

    /**
     * Gather, then go. Standing: the head comes up and it stamps for a moment while it lines up on
     * you (sometimes it skips this). Then it runs straight at where you were, and anything it reaches
     * takes the blow. Then it has to stand and blow before it can do it again.
     */
    private static final class ChargeGoal extends Goal {
        private static final int GATHER = 0;
        private static final int CHARGE = 1;
        private static final int RECOVER = 2;
        private static final int CHARGE_TICKS = 30;
        private static final int RECOVER_TICKS = 50;

        private final Megafauna animal;
        private int phase;
        private int timer;
        private Vec3 aim = Vec3.ZERO;
        private boolean landed;

        ChargeGoal(Megafauna animal) {
            this.animal = animal;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = animal.getTarget();
            return target != null && target.isAlive() && animal.standsGround() && animal.distanceToSqr(target) < 24.0D * 24.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return (phase == RECOVER || canUse()) && animal.isAlive();
        }

        @Override
        public void start() {
            boolean unwarned = animal.getRandom().nextFloat() < animal.unwarnedChance();
            phase = unwarned ? CHARGE : GATHER;
            timer = unwarned ? CHARGE_TICKS : animal.windUpTicks();
            landed = false;
            animal.getNavigation().stop();
            if (unwarned) {
                beginCharge();
            } else {
                animal.entityData.set(WINDING, true);
                animal.playSound(animal.warningSound(), 2.0F, animal.warningPitch());
            }
        }

        @Override
        public void stop() {
            animal.entityData.set(WINDING, false);
            animal.getNavigation().stop();
            phase = GATHER;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private void beginCharge() {
            LivingEntity target = animal.getTarget();
            aim = target != null ? target.position() : animal.position();
            animal.entityData.set(WINDING, false);
            animal.getNavigation().moveTo(aim.x, aim.y, aim.z, animal.chargeSpeed());
        }

        @Override
        public void tick() {
            LivingEntity target = animal.getTarget();
            timer--;
            switch (phase) {
                case GATHER -> {
                    if (target != null) {
                        animal.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    }
                    if (timer <= 0) {
                        phase = CHARGE;
                        timer = CHARGE_TICKS;
                        beginCharge();
                    }
                }
                case CHARGE -> {
                    if (target != null && !landed) {
                        double reach = animal.getBbWidth() * 0.9D + target.getBbWidth();
                        if (animal.distanceToSqr(target) <= reach * reach) {
                            animal.doHurtTarget(target);
                            animal.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            landed = true;
                        }
                    }
                    if (timer <= 0 || landed || animal.getNavigation().isDone()) {
                        phase = RECOVER;
                        timer = RECOVER_TICKS;
                        animal.getNavigation().stop();
                    }
                }
                default -> {
                    // Blowing: it stands, head down, and takes whatever comes.
                    if (timer <= 0) {
                        phase = GATHER;
                        if (canUse()) {
                            start();
                        }
                    }
                }
            }
        }
    }
}
