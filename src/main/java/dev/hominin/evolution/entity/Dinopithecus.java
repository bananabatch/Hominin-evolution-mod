package dev.hominin.evolution.entity;

import java.util.List;

import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.combat.Bleeding;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Dinopithecus, a baboon the size of a person, with a person's confidence and none of
 * the reasons to be careful.
 *
 * <p>It is not a predator and it is not hunting you. It is simply large, armed with the
 * worst teeth in the family, and entirely unwilling to be crowded - and the mistake every
 * hominin makes is reading "not hunting me" as "safe to walk past".
 *
 * <p>Threatening it does not work. A display is a bluff that only pays against something
 * with more to lose than you, and this has less: it will take the display as an opening
 * move and answer it properly. Learning that costs you, once.
 *
 * <p>They were gone before erectus was, and once you get there they stop appearing.
 */
public class Dinopithecus extends PathfinderMob {
    /** Crowd one this closely and it stops tolerating you. */
    private static final double PERSONAL_SPACE = 5.0D;
    /** Inside this, it warns you: the yawn, the canines, the stare. */
    private static final double WARNING_SPACE = 11.0D;
    /** Warned, you have this long to back off before being that close counts against you. */
    private static final long WARNING_GRACE_TICKS = 50L;
    private static final long WARN_AGAIN_TICKS = 30 * 20L;
    /** Who it has warned, and when. */
    private final java.util.Map<java.util.UUID, Long> warned = new java.util.HashMap<>();
    /** How far a provoked one calls the others in from. */
    private static final double GROUP_RADIUS = 20.0D;
    /** Those canines are the largest in any monkey that ever lived. */
    private static final float CATASTROPHIC_BITE_CHANCE = 0.1F;
    private static final int CHECK_INTERVAL = 20;

    public Dinopithecus(EntityType<? extends Dinopithecus> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // It pauses between bites like the cats do, but it never backs off to do it.
        goalSelector.addGoal(1, new PredatorAttackGoal(this, 1.3D, 35, 14, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(Dinopithecus.class));
    }

    /**
     * It never goes looking. It only ever reacts - which is why it reads as safe right
     * up until the moment you are inside the distance it cares about.
     */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || tickCount % CHECK_INTERVAL != 0 || getTarget() != null) {
            return;
        }
        long now = level().getGameTime();
        for (LivingEntity near : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(WARNING_SPACE))) {
            if (near instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            if (!(near instanceof Player) && !(near instanceof BandMember)) {
                continue;
            }
            Long when = warned.get(near.getUUID());
            boolean fresh = when == null || now - when > WARN_AGAIN_TICKS;
            if (fresh) {
                warn(near, now);
                continue;
            }
            if (distanceToSqr(near) <= PERSONAL_SPACE * PERSONAL_SPACE && now - when >= WARNING_GRACE_TICKS) {
                // It told you. You came closer anyway.
                provoke(near);
                return;
            }
        }
    }

    /**
     * The warning: it rears up, yawns wide to show canines longer than a leopard's, and stares. Everyone who knows
     * baboons knows what that means.
     */
    private void warn(LivingEntity near, long now) {
        warned.put(near.getUUID(), now);
        if (warned.size() > 32) {
            warned.entrySet().removeIf(e -> now - e.getValue() > WARN_AGAIN_TICKS);
        }
        getNavigation().stop();
        getLookControl().setLookAt(near, 30.0F, 30.0F);
        playSound(ModSounds.BABOON_ANGRY.get(), 1.2F, 0.75F);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (near instanceof Player player) {
            dev.hominin.evolution.guide.Alerts.urgent(player, dev.hominin.evolution.guide.Alerts.Kind.DANGER, "The giant baboon rears up and yawns wide at you, all canine. "
                    + "Back off - now.", net.minecraft.ChatFormatting.RED);
        } else if (near instanceof BandMember member && member.getNavigation() != null) {
            // The band knows that look, and gives it room.
            net.minecraft.world.phys.Vec3 away = member.position().subtract(position()).normalize().scale(8.0D);
            member.getNavigation().moveTo(member.getX() + away.x, member.getY(), member.getZ() + away.z, 1.2D);
        }
    }

    /** Somebody has pushed it too far. It does not go alone. */
    public void provoke(LivingEntity offender) {
        setTarget(offender);
        playSound(ModSounds.BABOON_ANGRY.get(), 1.6F, 0.6F);
        for (Dinopithecus other : level().getEntitiesOfClass(Dinopithecus.class,
                getBoundingBox().inflate(GROUP_RADIUS))) {
            if (other != this && other.getTarget() == null) {
                other.setTarget(offender);
            }
        }
    }

    /**
     * The display that does not work. Everything else in the country reads a pant-hoot
     * and a thrown branch as a reason to be somewhere else; this reads it as a challenge
     * it is confident of winning, and it is usually right.
     *
     * @return true if any of them took it that way.
     */
    public static boolean answerDisplay(ServerPlayer player, double radius) {
        List<Dinopithecus> near = player.level().getEntitiesOfClass(Dinopithecus.class,
                player.getBoundingBox().inflate(radius));
        for (Dinopithecus baboon : near) {
            baboon.provoke(player);
        }
        if (!near.isEmpty()) {
            dev.hominin.evolution.EvolutionManager.incrementCriterion(player, "provoke_dinopithecus", 1);
        }
        return !near.isEmpty();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity bitten) {
            Bleeding.inflict(bitten, getRandom().nextFloat() < CATASTROPHIC_BITE_CHANCE
                    ? Bleeding.Tier.CATASTROPHIC : Bleeding.Tier.INTERNAL);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BABOON_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BABOON_HURT.get();
    }
}
