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

    public ThrownObject(EntityType<? extends ThrownObject> type, Level level) {
        super(type, level);
    }

    public ThrownObject(Level level, LivingEntity thrower) {
        super(ModEntities.THROWN_OBJECT.get(), thrower, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.ROCK.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        boolean landed = target.hurt(damageSources().thrown(this, getOwner()), damage);
        boolean hammerstone = dev.hominin.evolution.combat.ThreatDisplay.isHammerstone(getItem());
        if (hammerstone && landed && target instanceof LivingEntity living && !level().isClientSide()) {
            // Stone that heavy to the head: it rings the skull.
            dev.hominin.evolution.combat.HeadTraumaHandler.stoneToTheHead(getOwner(), living);
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

    /** The hammerstone flies apart: gone - though now and then a piece of it is worth keeping as a flake. */
    private void shatter() {
        shattered = true;
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.STONE_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.7F);
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(new net.minecraft.core.particles.ItemParticleOption(
                    net.minecraft.core.particles.ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 12, 0.1D, 0.1D,
                    0.1D, 0.08D);
        }
        if (random.nextBoolean()) {
            ItemStack flake = dev.hominin.evolution.item.StoneMaterial.stamp(new ItemStack(ModItems.FLAKE.get()),
                    getItem().is(ModItems.CHERT_HAMMERSTONE.get()) ? dev.hominin.evolution.item.StoneMaterial.CHERT
                            : dev.hominin.evolution.item.StoneMaterial.of(getItem()));
            level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), flake));
        }
        if (getOwner() instanceof net.minecraft.world.entity.player.Player player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("The hammerstone shatters."), true);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
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
