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

    /**
     * How many blows with a branch before the animal simply decides this is not worth
     * it. A branch is not a killing weapon and never was - it is a thing you wave to
     * make something else go somewhere else, and that is what it does here. It will not
     * move anything fearless, which is exactly why the club matters.
     */
    private static final int BRANCH_BLOWS_TO_ROUT = 4;
    private static final int ROUT_TICKS = 600;

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

        // A club puts an animal on the floor; a branch stings it. Dazing everything on
        // every swing made the branch a stunlock, so the stun is the club's alone.
        //
        // Slowness VII takes movement speed to zero outright. The 6-arg constructor
        // separates `visible` from `showIcon`; particles key off `visible` alone, so
        // this is the only way to daze something without swirling particles round it.
        if (profile.club()) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 6, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 1, false, false, true));
        }

        if (player instanceof ServerPlayer serverPlayer && isPredator(player, target)) {
            EvolutionManager.incrementCriterion(serverPlayer, "stun_predator", 1);
        }

        if (profile.club() && target.level().getRandom().nextFloat() < CLUB_FRACTURE_CHANCE) {
            fracture(target);
        }
        // Hit often enough with a branch and it gives up the ground rather than the fight.
        if (!profile.club() && rout(player, target, trauma)) {
            return true;
        }
        // A branch stings a big cat. It does not crack its skull - only a club does that.
        if (!profile.club() && dev.hominin.evolution.hunt.PredatorAppetite.isPredator(target)) {
            return true;
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
     * Battered off. Returns true once the animal has turned and gone, which ends the
     * blow there - nothing further happens to a thing that is already leaving.
     *
     * <p>A predator routed this way is handed to {@link dev.hominin.evolution.hunt.PredatorAppetite}
     * so the band stands down and sees it off properly instead of running after it.
     */
    private static boolean rout(LivingEntity attacker, LivingEntity target, HeadTrauma trauma) {
        if (trauma.getBlows() < BRANCH_BLOWS_TO_ROUT || !(target instanceof PathfinderMob mob)
                || !Scare.canBeScared(mob) || Scare.isScared(mob)) {
            return false;
        }
        if (dev.hominin.evolution.hunt.PredatorAppetite.isPredator(target)) {
            dev.hominin.evolution.hunt.PredatorAppetite.drivenOff(mob, attacker);
            return true;
        }
        Scare.scare(mob, attacker.position(), ROUT_TICKS);
        if (attacker instanceof Player player) {
            player.displayClientMessage(Component.literal(
                    "It has had enough of the stick and breaks away."), true);
        }
        return true;
    }

    /** Base odds, per blow from a band member, of a concussion and - after one - a bleed. */
    private static final float MEMBER_BRANCH_CONCUSS = 0.15F;

    /**
     * A band member's blow with a branch or club. Members do not count blows the way a
     * player's careful swings do: each hit simply has a chance to concuss, and once
     * concussed, to start a brain bleed. Blood up ({@code bonus}) makes both likelier.
     */
    public static void bludgeonBy(LivingEntity attacker, ItemStack weapon, LivingEntity target, float bonus) {
        Profile profile = profileFor(weapon);
        if (profile == null) {
            return;
        }
        var random = target.level().getRandom();
        HeadTrauma trauma = target.getData(Attachments.HEAD_TRAUMA);
        trauma.addBlow();
        if (profile.club()) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS / 2, 6, false, false, true));
        }
        if (profile.club() && random.nextFloat() < CLUB_FRACTURE_CHANCE / 2.0F + bonus) {
            fracture(target);
        }
        // The band wears things down with branches the same way you do.
        if (!profile.club() && rout(attacker, target, trauma)) {
            return;
        }
        boolean predator = dev.hominin.evolution.hunt.PredatorAppetite.isPredator(target);
        if (predator && !profile.club()) {
            return;
        }
        if (!trauma.isConcussed()) {
            float chance = (profile.club() ? CLUB_CONCUSS_CHANCE : MEMBER_BRANCH_CONCUSS) + bonus;
            if (random.nextFloat() < chance) {
                trauma.setConcussed(true);
                if (predator && target instanceof Mob mob) {
                    dev.hominin.evolution.hunt.PredatorAppetite.drivenOff(mob, attacker);
                    return;
                }
                if (target instanceof PathfinderMob mob) {
                    mob.goalSelector.addGoal(0, new ConcussedGoal(mob, 1.0D));
                }
                if (random.nextFloat() < PACIFY_CHANCE) {
                    pacify(target, trauma);
                }
            }
            return;
        }
        if (!trauma.isBleeding() && random.nextFloat() < profile.bleedChance() + bonus) {
            trauma.setBleeding(true);
            target.addEffect(new MobEffectInstance(ModEffects.BRAIN_BLEED, BLEED_TICKS, 0, false, true, true));
        }
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
        if (target instanceof Mob mob && dev.hominin.evolution.hunt.PredatorAppetite.isPredator(target)) {
            dev.hominin.evolution.hunt.PredatorAppetite.drivenOff(mob, player);
            return;
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

    /**
     * A bleed inside the skull. A second one on top of the first is not a worse headache
     * - there is nowhere for it to go, and that is what makes repeated concussions the
     * one unarmed way to open a catastrophic wound.
     */
    private static void bleed(Player player, LivingEntity target, HeadTrauma trauma) {
        if (trauma.isBleeding()) {
            Bleeding.inflict(target, Bleeding.Tier.CATASTROPHIC);
            player.displayClientMessage(
                    Component.literal("It goes again, on top of the first. That is the end of it."), true);
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
