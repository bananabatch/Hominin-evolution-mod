package dev.hominin.evolution.combat;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.entity.WoundedFleeGoal;
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
    /**
     * How likely a cut is to bleed, and the worst kind of wound this edge can open.
     *
     * <p>The tier is the weapon's ceiling, not its guarantee: a spear usually opens
     * something internal and occasionally opens a body outright.
     */
    private record Edge(float chance, Bleeding.Tier tier, float worstChance) {
    }

    private static final Edge FLAKE = new Edge(0.6F, Bleeding.Tier.EXTERNAL, 0.0F);
    /** Fine enough to cut, not heavy enough to make a cut worse. */
    private static final Edge POINTY_STICK = new Edge(0.35F, Bleeding.Tier.EXTERNAL, 0.0F);
    /** Driven in rather than drawn across: this is what internal bleeding is. */
    private static final Edge SPEAR = new Edge(0.75F, Bleeding.Tier.INTERNAL, 0.08F);
    private static final Edge HARDENED_SPEAR = new Edge(0.85F, Bleeding.Tier.INTERNAL, 0.18F);
    /** Tier 3 more than half the time, and tier 2 three times in four of the rest. */
    private static final Edge SCHONINGEN = new Edge(0.89F, Bleeding.Tier.INTERNAL, 0.62F);
    private static final Edge STONE_TIPPED = new Edge(0.9F, Bleeding.Tier.INTERNAL, 0.35F);
    /** A prepared edge: finer than any flake struck at random. */
    private static final Edge LEVALLOIS = new Edge(0.7F, Bleeding.Tier.EXTERNAL, 0.0F);
    private static final Edge KNIFE = new Edge(0.75F, Bleeding.Tier.EXTERNAL, 0.05F);

    /** Priority 0 puts flight above the animal's own wandering and grazing. */
    private static final int FLEE_PRIORITY = 0;

    @Nullable
    private static Edge edgeOf(ItemStack weapon) {
        if (weapon.is(ModItems.SCHONINGEN_SPEAR.get())) {
            return SCHONINGEN;
        }
        if (weapon.is(ModItems.STONE_TIPPED_SPEAR.get())) {
            return STONE_TIPPED;
        }
        if (weapon.is(ModItems.KNIFE.get())) {
            return KNIFE;
        }
        if (weapon.is(ModItems.LEVALLOIS_FLAKE.get()) || weapon.is(ModItems.LEVALLOIS_BLADE.get())) {
            return LEVALLOIS;
        }
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
        Bleeding.inflict(target, tierOf(edge, target));

        // A saber-toothed cat does not run from a cut, and a baboon with its troop behind it attacks instead.
        boolean standsGround = WoundedFleeGoal.standsGround(target);
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
        Bleeding.inflict(target, tierOf(edge, target));
    }

    /** Most blows do what the weapon usually does. Occasionally one goes all the way in. */
    private static Bleeding.Tier tierOf(Edge edge, LivingEntity target) {
        if (edge.worstChance() > 0.0F && target.getRandom().nextFloat() < edge.worstChance()) {
            return Bleeding.Tier.CATASTROPHIC;
        }
        return edge.tier();
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
