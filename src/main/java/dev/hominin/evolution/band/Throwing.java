package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.entity.ThrownObject;
import dev.hominin.evolution.entity.ThrownSpear;
import dev.hominin.evolution.item.SpearItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Erectus throws. Now and then, in a fight or a hunt, one of the band stops closing and throws instead: a rock at
 * whatever it is, or - if they have something else to fight with, or the thing is getting away - their spear. A
 * spear thrown is a spear gone until whatever it is in goes down, so nobody throws their only weapon lightly.
 */
public final class Throwing {
    /** Close enough to hit, far enough that walking up is slower than throwing. */
    private static final double NEAR = 4.5D;
    private static final double FAR = 15.0D;
    private static final int COOLDOWN = 120;
    private static final float SPEAR_SPEED = 1.9F;
    private static final float ROCK_SPEED = 1.5F;

    private static final Map<UUID, Long> nextThrow = new HashMap<>();

    /** Every tick, from the member's own AI step: most of the time, nothing. */
    public static void tick(BandMember member) {
        // Told to throw - a kinetic hunt, a ranged band - and anyone throws: rocks before erectus, spears after.
        boolean focus = member.isThrowFocused() || Postures.throwsFirst(member);
        if (member.tickCount % 10 != 5 || member.isBaby() || !focus && !Bands.erectusOn(member.getStage())) {
            return;
        }
        LivingEntity target = member.getTarget();
        if (target == null || !target.isAlive() || member.getRandom().nextFloat() > (focus ? 0.75F : 0.3F)) {
            return;
        }
        long now = member.level().getGameTime();
        if (now < nextThrow.getOrDefault(member.getUUID(), 0L)) {
            return;
        }
        double distance = Math.sqrt(member.distanceToSqr(target));
        if (distance < (focus ? 3.0D : NEAR) || distance > (focus ? 20.0D : FAR) || !member.hasLineOfSight(target)) {
            return;
        }
        boolean getting = target.getDeltaMovement().horizontalDistanceSqr() > 0.01D;
        if (!throwOne(member, target, distance, focus, getting)) {
            return;
        }
        if (nextThrow.size() > 512) {
            nextThrow.values().removeIf(t -> t < now);
        }
        int cooldown = focus ? COOLDOWN / 3 : COOLDOWN;
        nextThrow.put(member.getUUID(), now + cooldown + member.getRandom().nextInt(cooldown));
    }

    /** Whatever they have to throw, thrown: a spear if they may, a rock or hammerstone if not. */
    private static boolean throwOne(BandMember member, LivingEntity target, double distance, boolean focus,
            boolean getting) {
        ItemStack held = member.getMainHandItem();
        // Something else to fight with once the spear is gone - unless throwing is the whole idea.
        boolean spare = member.countCarried(BandMember::isWeapon) - (BandMember.isWeapon(held) ? 1 : 0) > 0;
        if (SpearItem.isSpear(held) && Bands.erectusOn(member.getStage())
                && (focus || spare || getting && distance > 7.0D && member.getRandom().nextInt(3) == 0)) {
            throwSpear(member, target, held, distance);
            return true;
        }
        if (member.countRocks() > 0) {
            throwRock(member, target, distance);
            return true;
        }
        return false;
    }

    /** An ambush sprung: whatever this one has, thrown now. */
    public static void throwNow(BandMember member, LivingEntity target) {
        double distance = Math.sqrt(member.distanceToSqr(target));
        if (member.isBaby() || distance > 22.0D || !member.hasLineOfSight(target)) {
            return;
        }
        if (throwOne(member, target, distance, true, false)) {
            nextThrow.put(member.getUUID(), member.level().getGameTime() + COOLDOWN / 2);
        }
    }

    private static void throwSpear(BandMember member, LivingEntity target, ItemStack held, double distance) {
        ItemStack thrown = held.split(1);
        ThrownSpear spear = new ThrownSpear(member.level(), member, thrown);
        spear.setThrowDamage(SpearItem.damageOf(thrown));
        spear.pickup = AbstractArrow.Pickup.DISALLOWED;
        aim(member, spear, target, distance, SPEAR_SPEED, 2.0F);
        member.level().addFreshEntity(spear);
        member.swing(InteractionHand.MAIN_HAND);
        member.level().playSound(null, member.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.NEUTRAL,
                0.9F, 0.8F);
        member.ensureName();
        Lines.tell(member, "throw_spear", target.getName().getString().toLowerCase());
        member.updateHands();
    }

    private static void throwRock(BandMember member, LivingEntity target, double distance) {
        ItemStack rock = member.takeFirst(s -> s.is(ModTags.Items.ROCKS));
        if (rock.isEmpty()) {
            return;
        }
        ThrownObject thrown = new ThrownObject(member.level(), member);
        thrown.setItem(rock);
        thrown.setDamage(3.0F);
        thrown.setAimed(true);
        aim(member, thrown, target, distance, ROCK_SPEED, 3.0F);
        member.level().addFreshEntity(thrown);
        member.swing(InteractionHand.MAIN_HAND);
        member.level().playSound(null, member.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.8F,
                0.6F);
        Lines.tell(member, "throw_rock", target.getName().getString().toLowerCase());
    }

    /** Where it will be, not where it is - and a little high, for the drop. */
    private static void aim(BandMember member, Projectile projectile, LivingEntity target, double distance, float speed,
            float spread) {
        double ticks = distance / speed;
        Vec3 lead = target.getDeltaMovement().scale(ticks);
        double dx = target.getX() + lead.x - member.getX();
        double dz = target.getZ() + lead.z - member.getZ();
        double dy = target.getY(0.5D) - projectile.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        projectile.shoot(dx, dy + flat * 0.12D, dz, speed, spread);
    }

    public static void forget(UUID member) {
        nextThrow.remove(member);
    }

    private Throwing() {
    }
}
