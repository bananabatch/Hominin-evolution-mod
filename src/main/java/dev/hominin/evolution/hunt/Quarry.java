package dev.hominin.evolution.hunt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.survival.Thirst;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Running an animal down. Nothing else on the savannah can do this: a hominin is slow
 * over a hundred metres and unbeatable over ten thousand, because it sweats and they
 * pant. The animal wins every sprint and loses the day.
 *
 * <p>Anything struck bolts - a hard burst first, then a longer, slower run - and the
 * animals around it bolt with it, which is what makes a single quarry hard to keep hold
 * of. Keeping hold of it is the skill: from habilis, thinking about the one that ran
 * marks it out for its first run. Erectus can do it over and over, and pays in water.
 */
public final class Quarry {
    /** The first panic: two seconds where nothing on two legs can follow. */
    private static final int BURST_TICKS = 40;
    /** The run afterwards, which is where a hunter begins to catch up. */
    private static final int STRIDE_TICKS = 60;

    /** How long a seeded mark lasts, and how long the erectus re-mark lasts. */
    private static final int FIRST_RUN_TICKS = 30 * 20;
    private static final int REMARK_TICKS = 20 * 20;
    /** Erectus pays for every re-mark in water. */
    private static final int REMARK_THIRST_COST = 4;

    /** How long a wound keeps an animal from closing up. */
    private static final int NO_REGEN_TICKS = 3 * 60 * 20;

    /** How far the panic spreads to everything else grazing nearby. */
    private static final double HERD_RADIUS = 12.0D;
    /** How long a quarry stays yours after it ran, before the trail goes cold. */
    private static final int TRAIL_TICKS = 60 * 20;

    private record Hunt(UUID quarry, long ranAt, boolean seeded) {
    }

    private static final Map<UUID, Hunt> hunts = new HashMap<>();
    /** Animals in the second half of their run, and when to hand them the slower legs. */
    private static final Map<UUID, Long> secondWind = new HashMap<>();
    /** Wounds that will not close, and when they finally do. */

    /** Anything that will stand and fight does not bolt: predators, the fearless, a mobbing troop. */
    public static boolean standsGround(LivingEntity target) {
        if (target.getType().is(ModTags.EntityTypes.FEARLESS) || target.getType().is(ModTags.EntityTypes.PREDATORS)
                || target instanceof Enemy) {
            return true;
        }
        return target instanceof dev.hominin.evolution.entity.Baboon baboon && baboon.hasTroopBehindIt();
    }

    /** Too small for this to be worth it: persistence hunting is for animals that can outrun you. */
    public static boolean isBigGame(LivingEntity target) {
        return target.getMaxHealth() > 10.0F || target.getBbWidth() > 1.0F;
    }

    /** Only from habilis: the earlier hominins take what small things they can catch. */
    private static boolean hunts(ServerPlayer player) {
        String stage = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage().getPath();
        return !stage.equals("ardipithecus") && !stage.equals("australopithecus");
    }

    private static boolean isErectus(ServerPlayer player) {
        String stage = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage().getPath();
        return !stage.equals("ardipithecus") && !stage.equals("australopithecus") && !stage.equals("homo_habilis");
    }

    /** Anything hurt by a player runs, unless it is the sort of thing that comes back at you. */
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer hunter)
                || victim instanceof Player || victim instanceof dev.hominin.evolution.band.BandMember) {
            return;
        }
        if (standsGround(victim)) {
            return;
        }
        bolt(victim, hunter);
        // Everything grazing beside it goes too, which is the hard part of picking one.
        for (LivingEntity other : victim.level().getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(HERD_RADIUS))) {
            if (other != victim && other instanceof net.minecraft.world.entity.animal.Animal && !standsGround(other)) {
                bolt(other, hunter);
            }
        }
        if (hunts(hunter) && isBigGame(victim)) {
            hunts.put(hunter.getUUID(), new Hunt(victim.getUUID(), victim.level().getGameTime(), false));
        }
    }

    /** The burst: a hard sprint that nothing on two legs can follow, and then a longer stride. */
    private static void bolt(LivingEntity animal, ServerPlayer hunter) {
        animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BURST_TICKS, 1, false, false, true));
        secondWind.put(animal.getUUID(), animal.level().getGameTime() + BURST_TICKS);
        if (animal instanceof PathfinderMob mob) {
            dev.hominin.evolution.entity.WoundedFleeGoal.makeFlee(mob, hunter);
        }
    }

    /**
     * A wound that keeps opening: nothing closes for three minutes. This goes through
     * {@link dev.hominin.evolution.survival.Afflictions} rather than holding a veto of
     * its own, so that it and everything else that stops a body healing can only ever
     * amount to one reason at a time.
     */
    public static void wounded(LivingEntity animal) {
        dev.hominin.evolution.survival.Afflictions.afflict(animal,
                dev.hominin.evolution.survival.Afflictions.Affliction.BLED_OUT, NO_REGEN_TICKS);
    }

    /**
     * Thinking about the one that ran. Returns true if that is what the player was doing,
     * so the ordinary business of having an idea is left alone.
     */
    public static boolean trySeed(ServerPlayer player) {
        Hunt hunt = hunts.get(player.getUUID());
        if (hunt == null || !hunts(player)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now - hunt.ranAt() > TRAIL_TICKS || !(level.getEntity(hunt.quarry()) instanceof LivingEntity quarry)
                || !quarry.isAlive()) {
            hunts.remove(player.getUUID());
            return false;
        }
        boolean marked = quarry.hasEffect(MobEffects.GLOWING);
        if (hunt.seeded() && !isErectus(player)) {
            // Habilis gets one look at it, and then has to keep up on its own.
            player.displayClientMessage(Component.literal(
                    "You have it in your head already. Now keep up with it."), true);
            return true;
        }
        if (marked) {
            player.displayClientMessage(Component.literal("You can still see where it went."), true);
            return true;
        }
        if (isErectus(player)) {
            if (Thirst.get(player) < REMARK_THIRST_COST) {
                player.displayClientMessage(Component.literal(
                        "You are too dry to hold the chase in your head. Drink first.")
                        .withStyle(ChatFormatting.AQUA), true);
                return true;
            }
            Thirst.drink(player, -REMARK_THIRST_COST);
            quarry.addEffect(new MobEffectInstance(MobEffects.GLOWING, REMARK_TICKS, 0, false, false));
            player.sendSystemMessage(Component.literal("You pick the tracks up again, and sweat for it.")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            quarry.addEffect(new MobEffectInstance(MobEffects.GLOWING, FIRST_RUN_TICKS, 0, false, false));
            player.sendSystemMessage(Component.literal(
                    "You hold the shape of the one that ran, and the ground it went over. It cannot lose you yet.")
                    .withStyle(ChatFormatting.GRAY));
        }
        hunts.put(player.getUUID(), new Hunt(hunt.quarry(), hunt.ranAt(), true));
        return true;
    }

    /** Once a second: hand out second winds and forget old trails. */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        long now = level.getGameTime();
        secondWind.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            if (level.getEntity(entry.getKey()) instanceof LivingEntity animal && animal.isAlive()) {
                // The sprint is over; now it settles into the long run that it will lose.
                animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, STRIDE_TICKS, 0, false, false, true));
            }
            return true;
        });
        hunts.values().removeIf(hunt -> now - hunt.ranAt() > TRAIL_TICKS);
    }

    @Nullable
    public static UUID quarryOf(ServerPlayer player) {
        Hunt hunt = hunts.get(player.getUUID());
        return hunt == null ? null : hunt.quarry();
    }

    public static void forget(UUID player) {
        hunts.remove(player);
    }

    private Quarry() {
    }
}
