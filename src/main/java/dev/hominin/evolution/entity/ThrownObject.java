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
        target.hurt(damageSources().thrown(this, getOwner()), damage);

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

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide()) {
            ItemEntity dropped = new ItemEntity(level(), getX(), getY(), getZ(), getItem().copy());
            dropped.setDeltaMovement(Vec3.ZERO);
            level().addFreshEntity(dropped);
            discard();
        }
    }
}
