package dev.hominin.evolution.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown spear. It flies point first, heavy and a little slow; into the ground it sticks, and waits to be pulled
 * out. Into an animal it goes deep and stays there - it hangs from the animal wherever it runs, and only comes free
 * when the animal is down. An animal struck while it is running staggers: a second of slowness while it gets its
 * legs back under it, which is the whole point of throwing.
 */
public class ThrownSpear extends AbstractArrow {
    /** The entity it is stuck in, by id - or -1. Synced, so the client can keep it with its host between updates. */
    private static final EntityDataAccessor<Integer> HOST = SynchedEntityData.defineId(ThrownSpear.class,
            EntityDataSerializers.INT);
    /** Where on the host, in the host's own frame (x right, z forward). */
    private static final EntityDataAccessor<Vector3f> OFFSET = SynchedEntityData.defineId(ThrownSpear.class,
            EntityDataSerializers.VECTOR3);
    /** Its heading relative to the host's body, so it turns with it. */
    private static final EntityDataAccessor<Float> TURN = SynchedEntityData.defineId(ThrownSpear.class,
            EntityDataSerializers.FLOAT);
    /** Which spear it is, for drawing. */
    private static final EntityDataAccessor<ItemStack> SHOWN = SynchedEntityData.defineId(ThrownSpear.class,
            EntityDataSerializers.ITEM_STACK);

    /** Staggered: Slowness II for a second. */
    private static final int STAGGER_TICKS = 20;
    private static final int STAGGER_LEVEL = 1;
    /** Moving faster than this, it counts as running. */
    private static final double RUNNING = 0.05D;

    private float damage = 5.0F;
    /** It has struck something already: it does not strike again on the way down. */
    private boolean dealtDamage;
    @Nullable
    private UUID hostId;
    private int hostMissing;

    public ThrownSpear(EntityType<? extends ThrownSpear> type, Level level) {
        super(type, level);
    }

    public ThrownSpear(Level level, LivingEntity thrower, ItemStack spear) {
        super(ModEntities.THROWN_SPEAR.get(), thrower, level, spear, null);
        entityData.set(SHOWN, spear.copyWithCount(1));
    }

    public void setThrowDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HOST, -1);
        builder.define(OFFSET, new Vector3f());
        builder.define(TURN, 0.0F);
        builder.define(SHOWN, new ItemStack(ModItems.SHARPENED_SPEAR.get()));
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.SHARPENED_SPEAR.get());
    }

    /** The spear itself, as it will be picked up. */
    public ItemStack spear() {
        return getPickupItemStackOrigin();
    }

    /** The spear as it is drawn - on either side. */
    public ItemStack shown() {
        return entityData.get(SHOWN);
    }

    public boolean isStuckInSomething() {
        return entityData.get(HOST) >= 0 || hostId != null;
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    // ------------------------------------------------------------ hitting

    /** Not the thrower's own people: a spear thrown past a band mate goes past them. */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        Entity owner = getOwner();
        if (target instanceof dev.hominin.evolution.band.BandMember member) {
            if (owner instanceof Player player && member.isLedBy(player)) {
                return false;
            }
            if (owner instanceof dev.hominin.evolution.band.BandMember thrower && member != thrower
                    && (thrower.getBandId() != null && thrower.getBandId().equals(member.getBandId())
                            || thrower.getLeader() != null && thrower.getLeader().equals(member.getLeader()))) {
                return false;
            }
        }
        return !(target instanceof Player && owner instanceof dev.hominin.evolution.band.BandMember thrower
                && target.getUUID().equals(thrower.getLeader()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = getOwner();
        if (target == owner) {
            return;
        }
        dealtDamage = true;
        boolean moving = target.getDeltaMovement().horizontalDistanceSqr() > RUNNING * RUNNING;
        boolean landed = target.hurt(damageSources().trident(this, owner == null ? this : owner), damage);
        setDeltaMovement(getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        playSound(SoundEvents.TRIDENT_HIT, 1.0F, 0.8F);
        if (level().isClientSide()) {
            return;
        }
        wear();
        if (spear().isEmpty()) {
            discard();
            return;
        }
        if (!landed || !(target instanceof LivingEntity living) || !living.isAlive() || target instanceof Player) {
            return;
        }
        if (moving) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STAGGER_TICKS, STAGGER_LEVEL, false, true,
                    true));
        }
        // A thrown point opens a wound as a thrust one does - a little more often, for the speed behind it.
        dev.hominin.evolution.combat.WoundHandler.cutBy(owner instanceof LivingEntity thrower ? thrower : living, spear(),
                living, 0.1F);
        if (living instanceof Mob) {
            stickIn(living, whereItWentIn(living));
        }
    }

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
        return dealtDamage ? null : super.findHitEntity(from, to);
    }

    /** One use off the spear; a spear worn through breaks. */
    private void wear() {
        ItemStack spear = spear();
        if (spear.isDamageableItem()) {
            spear.setDamageValue(spear.getDamageValue() + 1);
            if (spear.getDamageValue() >= spear.getMaxDamage()) {
                spear.setCount(0);
                playSound(SoundEvents.ITEM_BREAK, 1.0F, 0.8F);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        // A band member's spear is left lying where it fell, for them to go and get.
        if (!level().isClientSide() && getOwner() instanceof dev.hominin.evolution.band.BandMember) {
            dropHere();
        }
    }

    // ------------------------------------------------------------ stuck in something

    /**
     * Where the point went in. An entity hit is reported at the animal's feet, which left every spear hanging off
     * the bottom of it: follow the spear's own line into the body instead - and a spear lands in the back and flank,
     * the upper half, where a thrown point comes down on an animal.
     */
    private Vec3 whereItWentIn(LivingEntity host) {
        Vec3 from = position();
        Vec3 motion = getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement() : host.position().subtract(from);
        net.minecraft.world.phys.AABB body = host.getBoundingBox();
        Vec3 at = body.clip(from.subtract(motion.scale(2.0D)), from.add(motion.scale(3.0D))).orElse(body.getCenter());
        double low = body.minY + body.getYsize() * 0.5D;
        double high = body.minY + body.getYsize() * 0.95D;
        return new Vec3(at.x, Math.max(low, Math.min(high, at.y)), at.z);
    }

    /** Deep in an animal: it stays, point in, until the animal is down. */
    private void stickIn(LivingEntity host, Vec3 at) {
        Vec3 heading = getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement().normalize()
                : Vec3.directionFromRotation(getXRot(), getYRot());
        // The point is in; most of the shaft is out.
        Vec3 centre = at.subtract(heading.scale(0.35D));
        double yaw = Math.toRadians(host.yBodyRot);
        double dx = centre.x - host.getX();
        double dz = centre.z - host.getZ();
        float localX = (float) (dx * Math.cos(yaw) + dz * Math.sin(yaw));
        float localZ = (float) (-dx * Math.sin(yaw) + dz * Math.cos(yaw));
        float localY = (float) Math.max(host.getBbHeight() * 0.5D, Math.min(host.getBbHeight() * 0.95D,
                centre.y - host.getY()));
        entityData.set(OFFSET, new Vector3f(localX, localY, localZ));
        entityData.set(TURN, getYRot() + host.yBodyRot);
        entityData.set(HOST, host.getId());
        hostId = host.getUUID();
        setNoPhysics(true);
        setDeltaMovement(Vec3.ZERO);
        follow(host);
    }

    @Nullable
    private Entity host() {
        int id = entityData.get(HOST);
        if (id >= 0) {
            Entity host = level().getEntity(id);
            if (host != null) {
                return host;
            }
        }
        if (hostId != null && level() instanceof ServerLevel server) {
            Entity host = server.getEntity(hostId);
            if (host != null) {
                entityData.set(HOST, host.getId());
            }
            return host;
        }
        return null;
    }

    private void follow(Entity host) {
        float bodyYaw = host instanceof LivingEntity living ? living.yBodyRot : host.getYRot();
        double yaw = Math.toRadians(bodyYaw);
        Vector3f local = entityData.get(OFFSET);
        double x = local.x * Math.cos(yaw) - local.z * Math.sin(yaw);
        double z = local.x * Math.sin(yaw) + local.z * Math.cos(yaw);
        setPos(host.getX() + x, host.getY() + local.y, host.getZ() + z);
        setYRot(entityData.get(TURN) - bodyYaw);
    }

    @Override
    public void tick() {
        if (!isStuckInSomething()) {
            super.tick();
            return;
        }
        setOldPosAndRot();
        Entity host = host();
        if (host == null || !host.isAlive() || host.isRemoved()) {
            if (!level().isClientSide() && (host != null || ++hostMissing > 100)) {
                // Down, or gone: the spear comes free where it is.
                dropHere();
            }
            return;
        }
        hostMissing = 0;
        follow(host);
    }

    /** Lies on the ground as a spear to pick up. */
    private void dropHere() {
        ItemStack spear = spear();
        if (!spear.isEmpty()) {
            ItemEntity item = new ItemEntity(level(), getX(), getY() + 0.1D, getZ(), spear.copy());
            item.setDefaultPickUpDelay();
            level().addFreshEntity(item);
        }
        discard();
    }

    @Override
    public void playerTouch(Player player) {
        if (isStuckInSomething()) {
            // Still in the animal: nobody pulls it out of something running.
            return;
        }
        super.playerTouch(player);
    }

    @Override
    protected void tickDespawn() {
        // A spear is worth going back for: it only rots away if nobody may pick it up.
        if (pickup != Pickup.ALLOWED) {
            super.tickDespawn();
        }
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }

    // ------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ThrowDamage", damage);
        if (hostId != null) {
            tag.putUUID("Host", hostId);
            Vector3f offset = entityData.get(OFFSET);
            tag.putFloat("OffX", offset.x);
            tag.putFloat("OffY", offset.y);
            tag.putFloat("OffZ", offset.z);
            tag.putFloat("Turn", entityData.get(TURN));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.contains("ThrowDamage") ? tag.getFloat("ThrowDamage") : damage;
        if (!spear().isEmpty()) {
            entityData.set(SHOWN, spear().copyWithCount(1));
        }
        if (tag.hasUUID("Host")) {
            hostId = tag.getUUID("Host");
            entityData.set(OFFSET, new Vector3f(tag.getFloat("OffX"), tag.getFloat("OffY"), tag.getFloat("OffZ")));
            entityData.set(TURN, tag.getFloat("Turn"));
            setNoPhysics(true);
        }
    }
}
