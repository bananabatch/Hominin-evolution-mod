package dev.hominin.evolution.combat;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.entity.WoundedFleeGoal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Cuts from sharp weapons, and what they make an animal do.
 *
 * <p>Only a real edge opens a wound that keeps bleeding: a flake, anything that
 * cuts like one, a spear point, or a stick finished to a fine point with a flake. A
 * plain sharpened stick is deliberately not here - it is sharp enough to jab, not to
 * open a cut that will not close.
 */
public final class WoundHandler {
    /** How likely a cut is to bleed, how long, and how bad a wound it can deepen to. */
    private record Edge(float chance, int ticks, int maxSeverity) {
    }

    private static final Edge FLAKE = new Edge(0.6F, 8 * 20, 1);
    /** Fine enough to cut, not heavy enough to make a cut worse. */
    private static final Edge POINTY_STICK = new Edge(0.35F, 6 * 20, 0);
    private static final Edge SPEAR = new Edge(0.75F, 10 * 20, 2);
    private static final Edge HARDENED_SPEAR = new Edge(0.85F, 12 * 20, 2);

    /** Priority 0 puts flight above the animal's own wandering and grazing. */
    private static final int FLEE_PRIORITY = 0;

    @Nullable
    private static Edge edgeOf(ItemStack weapon) {
        if (weapon.is(ModItems.FIRE_HARDENED_SPEAR.get())) {
            return HARDENED_SPEAR;
        }
        if (weapon.is(ModItems.SHARPENED_SPEAR.get())) {
            return SPEAR;
        }
        if (weapon.is(ModItems.POINTY_STICK.get())) {
            return POINTY_STICK;
        }
        return weapon.is(ModTags.Items.FLAKES) ? FLAKE : null;
    }

    /** Applies a cut to the target, if the weapon has an edge and the blow landed properly. */
    public static void strike(Player player, LivingEntity target) {
        Edge edge = edgeOf(player.getMainHandItem());
        if (edge == null || player.getAttackStrengthScale(0.5F) < 0.9F) {
            return;
        }
        if (target.getRandom().nextFloat() >= edge.chance()) {
            return;
        }
        // A fresh cut on an open wound makes it worse, up to what the weapon can do.
        MobEffectInstance existing = target.getEffect(ModEffects.BLEEDING);
        int severity = existing == null ? 0 : Math.min(edge.maxSeverity(), existing.getAmplifier() + 1);
        int ticks = existing == null ? edge.ticks() : Math.max(existing.getDuration(), edge.ticks());
        target.addEffect(new MobEffectInstance(ModEffects.BLEEDING, ticks, severity, false, true, true));
        dev.hominin.evolution.hunt.Quarry.wounded(target);

        // A saber-toothed cat does not run from a cut, and a baboon with its troop behind it attacks instead.
        boolean standsGround = target.getType().is(ModTags.EntityTypes.FEARLESS)
                || (target instanceof dev.hominin.evolution.entity.Baboon baboon && baboon.hasTroopBehindIt());
        if (target instanceof PathfinderMob mob && !(target instanceof Enemy) && !standsGround) {
            fleeFrom(mob, player);
        }
    }

    /**
     * A band member's blow. The same edges cut the same way; a member whose blood is up
     * ({@code bonus}) opens wounds more often.
     */
    public static void cutBy(LivingEntity attacker, ItemStack weapon, LivingEntity target, float bonus) {
        Edge edge = edgeOf(weapon);
        if (edge == null || target.getRandom().nextFloat() >= Math.min(0.95F, edge.chance() + bonus)) {
            return;
        }
        MobEffectInstance existing = target.getEffect(ModEffects.BLEEDING);
        int severity = existing == null ? 0 : Math.min(edge.maxSeverity(), existing.getAmplifier() + 1);
        int ticks = existing == null ? edge.ticks() : Math.max(existing.getDuration(), edge.ticks());
        target.addEffect(new MobEffectInstance(ModEffects.BLEEDING, ticks, severity, false, true, true));
        dev.hominin.evolution.hunt.Quarry.wounded(target);
    }

    /**
     * Hostile mobs press the attack instead - a zombie does not run from a cut. Everything
     * else bolts, and the goal is added once and re-aimed on every later wound.
     */
    private static void fleeFrom(PathfinderMob mob, Player hunter) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof WoundedFleeGoal flee) {
                flee.woundedBy(hunter);
                return;
            }
        }
        WoundedFleeGoal flee = new WoundedFleeGoal(mob);
        flee.woundedBy(hunter);
        mob.goalSelector.addGoal(FLEE_PRIORITY, flee);
        if (mob.getTarget() == hunter) {
            mob.setTarget(null);
        }
    }

    private WoundHandler() {
    }
}
