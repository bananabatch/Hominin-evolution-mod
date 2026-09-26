package dev.hominin.evolution.entity;

import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A branch or rock thrown in a threat display. It stings more than it hurts, sends a
 * predator it hits running, and lands where it falls so it can be picked up again -
 * nobody on the savannah could afford to throw a good stone away.
 */
public class ThrownObject extends ThrowableItemProjectile {
    /** Set by the thrower: a wild heave barely stings, an aimed throw from erectus hurts. */
    private float damage = 1.0F;
    private static final double HIT_FLEE_SPEED = 1.4D;
    /** A hammerstone that hits something - flesh or ground - sometimes does not survive it. */
    private static final float SHATTER_ON_HIT = 0.2F;
    private static final float SHATTER_ON_GROUND = 0.1F;
    /** Whether this one has already gone to pieces. */
    private boolean shattered;
    /** An aimed throw, from an arm built for it - not a heave in a display. */
    private boolean aimed;

    public void setAimed(boolean aimed) {
        this.aimed = aimed;
    }

    public ThrownObject(EntityType<? extends ThrownObject> type, Level level) {
        super(type, level);
    }

    public ThrownObject(Level level, LivingEntity thrower) {
        super(ModEntities.THROWN_OBJECT.get(), thrower, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    /**
     * Not your own people: whatever you throw goes past your band - a chunk of glass would open them up as surely as
     * it opens anything else. A band member's throw passes its own, and its leader.
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        Entity owner = getOwner();
        if (target instanceof dev.hominin.evolution.band.BandMember member) {
            if (owner instanceof net.minecraft.world.entity.player.Player player && member.isCompanionOf(player)) {
                return false;
            }
            if (owner instanceof dev.hominin.evolution.band.BandMember thrower && member.isAlliedTo(thrower)) {
                return false;
            }
        }
        return !(target instanceof net.minecraft.world.entity.player.Player
                && owner instanceof dev.hominin.evolution.band.BandMember thrower && thrower.isAlliedTo(target));
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.GRANITE_ROCK.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        boolean landed = target.hurt(damageSources().thrown(this, getOwner()), damage);
        if (getItem().is(ModItems.OBSIDIAN_CHUNK.get()) && !level().isClientSide()) {
            // It bursts on whatever it hits: a cloud of glass, and the worst wound there is.
            if (target instanceof LivingEntity living) {
                dev.hominin.evolution.combat.Bleeding.inflict(living, dev.hominin.evolution.combat.Bleeding.Tier.CATASTROPHIC);
                if (getOwner() instanceof net.minecraft.server.level.ServerPlayer thrower) {
                    dev.hominin.evolution.advancement.HomininAdvancements.award(thrower, "hominin/death_by_a_million_cuts");
                }
            }
            burstGlass();
            return;
        }
        boolean hammerstone = dev.hominin.evolution.combat.ThreatDisplay.isHammerstone(getItem());
        if (hammerstone && landed && target instanceof LivingEntity living && !level().isClientSide()) {
            if (aimed) {
                // Stone that heavy, thrown hard, to the head: it rings the skull.
                dev.hominin.evolution.combat.HeadTraumaHandler.stoneToTheHead(getOwner(), living);
            } else {
                // A heave stings and staggers. It does not break anything.
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false, true));
            }
        }
        if (hammerstone && !level().isClientSide() && random.nextFloat() < SHATTER_ON_HIT) {
            shatter();
        }

        // A predator struck from range does not know what else is coming. It leaves.
        if (target instanceof PathfinderMob mob && mob.getType().is(ModTags.EntityTypes.PREDATORS)) {
            mob.setTarget(null);
            Vec3 from = getOwner() != null ? getOwner().position() : position();
            Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, from);
            if (away != null) {
                mob.getNavigation().moveTo(away.x, away.y, away.z, HIT_FLEE_SPEED);
            }
        }
    }

    /**
     * The hammerstone flies apart: gone. Only chert breaks clean enough to leave anything worth keeping - and then,
     * now and then, a sharp flake. The Kanzi method: taught to knap, the bonobo Kanzi worked out that throwing the
     * stone hard at the ground got him an edge faster.
     */
    private void shatter() {
        shattered = true;
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.STONE_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.7F);
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(new net.minecraft.core.particles.ItemParticleOption(
                    net.minecraft.core.particles.ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 12, 0.1D, 0.1D,
                    0.1D, 0.08D);
        }
        if (getItem().is(ModItems.CHERT_HAMMERSTONE.get()) && random.nextBoolean()) {
            ItemStack flake = dev.hominin.evolution.item.StoneMaterial.stamp(new ItemStack(ModItems.FLAKE.get()),
                    dev.hominin.evolution.item.StoneMaterial.CHERT);
            level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), flake));
            if (getOwner() instanceof net.minecraft.server.level.ServerPlayer thrower) {
                dev.hominin.evolution.advancement.HomininAdvancements.award(thrower, "hominin/kanzi_method");
                thrower.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "It shatters - and one piece of it has an edge you could cut with."), true);
                return;
            }
        }
        if (getOwner() instanceof net.minecraft.world.entity.player.Player player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("The hammerstone shatters."), true);
        }
    }

    /** The obsidian chunk, burst: glass everywhere, a few pieces worth picking up. */
    private void burstGlass() {
        shattered = true;
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.8F);
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(new net.minecraft.core.particles.ItemParticleOption(
                    net.minecraft.core.particles.ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 30, 0.3D, 0.3D,
                    0.3D, 0.2D);
        }
        level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(),
                new ItemStack(ModItems.OBSIDIAN_ROCK.get(), 1 + random.nextInt(2))));
        if (random.nextFloat() < 0.4F) {
            // Some of the glass comes down with an edge on it.
            level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), dev.hominin.evolution.item.StoneMaterial
                    .stamp(new ItemStack(ModItems.FLAKE.get()), dev.hominin.evolution.item.StoneMaterial.OBSIDIAN)));
        }
        discard();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide() && !isRemoved() && getItem().is(ModItems.OBSIDIAN_CHUNK.get())) {
            if (!shattered) {
                burstGlass();
            }
            return;
        }
        if (!level().isClientSide()) {
            if (!shattered && result.getType() == HitResult.Type.BLOCK
                    && dev.hominin.evolution.combat.ThreatDisplay.isHammerstone(getItem())
                    && random.nextFloat() < SHATTER_ON_GROUND) {
                shatter();
            }
            if (!shattered) {
                ItemEntity dropped = new ItemEntity(level(), getX(), getY(), getZ(), getItem().copy());
                dropped.setDeltaMovement(Vec3.ZERO);
                level().addFreshEntity(dropped);
            }
            discard();
        }
    }
}
