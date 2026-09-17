package dev.hominin.evolution.combat;

import java.util.Set;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.data.HeadTrauma;
import dev.hominin.evolution.entity.ConcussedGoal;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import dev.hominin.evolution.data.PlayerEvolutionData;

/**
 * Blunt head trauma from wooden weapons, and what it does to an animal over
 * repeated blows.
 *
 * <p>A branch cannot kill outright, so it escalates instead: daze, then a
 * concussion that wrecks the animal's coordination, then - past that - a chance
 * of a bleed that finishes the job unattended. A club skips much of the ladder
 * because it concentrates far more force into the same swing.
 */
public final class HeadTraumaHandler {
    /** How many blows a bare branch needs before the skull gives. */
    private static final int BRANCH_BLOWS_TO_CONCUSS = 3;

    private static final int STUN_TICKS = 100;

    /** Chance a concussion leaves the animal too addled to keep fighting. */
    private static final float PACIFY_CHANCE = 0.4F;

    /** Chance per pair of post-concussion blows that one opens a bleed. */
    private static final float BRANCH_BLEED_CHANCE = 0.2F;

    private static final float CLUB_CONCUSS_CHANCE = 0.4F;
    private static final float CLUB_BLEED_CHANCE = 0.15F;
    private static final float CLUB_FRACTURE_CHANCE = 0.6F;

    /** A bleed runs long enough to be fatal if the animal is simply left alone. */
    private static final int BLEED_TICKS = 1200;

    /** A drop onto the target counts as a much heavier blow - the mace rule. */
    private static final float MACE_FALL_DISTANCE = 3.0F;

    /**
     * Only the tree-climbing stages get the drop bonus. Erectus has the club and
     * the fire; dropping out of a canopy is the earlier hominins' advantage, and
     * tying it to them is what makes climbing worth doing.
     */
    private static final Set<ResourceLocation> CLIMBING_STAGES = Set.of(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "australopithecus"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "homo_habilis"));

    /** The profile of the weapon that landed the blow. */
    private record Profile(boolean club, int blowsToConcuss, float concussChance, float bleedChance) {
    }

    private static final Profile BRANCH = new Profile(false, BRANCH_BLOWS_TO_CONCUSS, 1.0F, BRANCH_BLEED_CHANCE);
    private static final Profile CLUB = new Profile(true, 1, CLUB_CONCUSS_CHANCE, CLUB_BLEED_CHANCE);

    private static Profile profileFor(ItemStack stack) {
        if (stack.is(ModItems.WOODEN_CLUB.get())) {
            return CLUB;
        }
        if (stack.is(ModItems.LONG_BRANCH.get())) {
            return BRANCH;
        }
        return null;
    }

    /**
     * Applies a blow to the target's skull. Returns true if the weapon was one
     * that causes head trauma at all.
     */
    public static boolean strike(Player player, LivingEntity target) {
        Profile profile = profileFor(player.getMainHandItem());
        if (profile == null) {
            return false;
        }
        // Only a fully wound-up swing lands hard enough to matter.
        if (player.getAttackStrengthScale(0.5F) < 0.9F) {
            return false;
        }
        PlayerEvolutionData self = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        boolean falling = player.fallDistance >= MACE_FALL_DISTANCE && !player.onGround()
                && CLIMBING_STAGES.contains(self.getStage());

        HeadTrauma trauma = target.getData(Attachments.HEAD_TRAUMA);
        trauma.addBlow();

        // Slowness VII takes movement speed to zero outright. The 6-arg constructor
        // separates `visible` from `showIcon`; particles key off `visible` alone, so
        // this is the only way to daze something without swirling particles round it.
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 6, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 1, false, false, true));

        if (player instanceof ServerPlayer serverPlayer && isPredator(player, target)) {
            EvolutionManager.incrementCriterion(serverPlayer, "stun_predator", 1);
        }

        if (profile.club() && target.level().getRandom().nextFloat() < CLUB_FRACTURE_CHANCE) {
            fracture(target);
        }

        if (!trauma.isConcussed()) {
            boolean enoughBlows = trauma.getBlows() >= profile.blowsToConcuss();
            boolean rolled = target.level().getRandom().nextFloat() < profile.concussChance();
            // A drop onto the target concusses regardless of the count.
            if (falling || (enoughBlows && rolled)) {
                concuss(player, target, trauma);
            }
            return true;
        }

        // Past a concussion, every further pair of blows risks a bleed - and a
        // drop from height skips the wait.
        boolean pairLanded = trauma.consumeBlowPair();
        if (falling || (pairLanded && target.level().getRandom().nextFloat() < profile.bleedChance())) {
            bleed(player, target, trauma);
        }
        return true;
    }

    /**
     * A predator is anything hostile by nature, or anything that had decided to
     * come for this player - which is what makes a stunned bear or wolf count.
     */
    private static boolean isPredator(Player player, LivingEntity target) {
        return target instanceof Enemy || (target instanceof Mob mob && mob.getTarget() == player);
    }

    private static void concuss(Player player, LivingEntity target, HeadTrauma trauma) {
        trauma.setConcussed(true);
        if (target instanceof PathfinderMob mob) {
            // Priority 0 outranks the mob's own movement goals, so the stagger wins.
            mob.goalSelector.addGoal(0, new ConcussedGoal(mob, 1.0D));
        }
        if (target.level().getRandom().nextFloat() < PACIFY_CHANCE) {
            pacify(target, trauma);
        }
        player.displayClientMessage(
                Component.literal("The blow lands square - it stops tracking you and starts staggering."), true);
    }

    /** An addled animal loses its grip on what it was hunting. */
    private static void pacify(LivingEntity target, HeadTrauma trauma) {
        trauma.setPacified(true);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.targetSelector.getAvailableGoals().clear();
        }
    }

    private static void bleed(Player player, LivingEntity target, HeadTrauma trauma) {
        if (trauma.isBleeding()) {
            return;
        }
        trauma.setBleeding(true);
        target.addEffect(new MobEffectInstance(ModEffects.BRAIN_BLEED, BLEED_TICKS, 0, false, true, true));
        player.displayClientMessage(
                Component.literal("Something gives inside the skull. It will not last the hour."), true);
    }

    /** A club breaks what it hits: a fractured limb cannot carry weight or strike back. */
    private static void fracture(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BLEED_TICKS, 2, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BLEED_TICKS, 2, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.JUMP, BLEED_TICKS, 128, false, false, false));
    }

    private HeadTraumaHandler() {
    }
}
