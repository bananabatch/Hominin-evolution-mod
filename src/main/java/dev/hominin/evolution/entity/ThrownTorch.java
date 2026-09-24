package dev.hominin.evolution.entity;

import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A burning torch in flight. Whatever it hits catches fire; anything that hunts, hit by it, runs - even
 * the ones a threat display will not move. It lands still burning, where it can be picked up again.
 */
public class ThrownTorch extends ThrowableItemProjectile {
    /** Set on anything a torch has hit, with the game time: what dies burning comes apart cooked. */
    public static final String TORCHED = "hominin_torched";

    public ThrownTorch(EntityType<? extends ThrownTorch> type, Level level) {
        super(type, level);
    }

    public ThrownTorch(Level level, LivingEntity thrower) {
        super(ModEntities.THROWN_TORCH.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.LIT_TORCH.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.1D, getZ(), 0.0D, 0.01D, 0.0D);
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1D, getZ(), 0.0D, 0.02D, 0.0D);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        target.hurt(damageSources().thrown(this, getOwner()), 2.0F);
        target.igniteForSeconds(5.0F);
        target.getPersistentData().putLong(TORCHED, level().getGameTime());
        level().playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.7F, 1.3F);
        if (!(target instanceof PathfinderMob mob) || !mob.getType().is(ModTags.EntityTypes.PREDATORS)) {
            return;
        }
        Vec3 from = getOwner() != null ? getOwner().position() : position();
        // Fire is the one thing none of them will stand in front of.
        if (mob instanceof Crocuta hyena) {
            hyena.scatterClan(from);
        } else if (mob instanceof Pachycrocuta giant && getOwner() instanceof LivingEntity thrower) {
            giant.scare(thrower);
        } else {
            dev.hominin.evolution.combat.Scare.scare(mob, from, 300);
        }
        if (getOwner() instanceof ServerPlayer player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("It catches - and runs from the fire.")
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide()) {
            ItemEntity dropped = new ItemEntity(level(), getX(), getY(), getZ(), getItem().copy());
            dropped.setDeltaMovement(Vec3.ZERO);
            level().addFreshEntity(dropped);
            ((ServerLevel) level()).sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 8, 0.2D, 0.1D, 0.2D, 0.02D);
            discard();
        }
    }
}
