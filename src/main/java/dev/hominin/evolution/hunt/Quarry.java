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

    // ------------------------------------------------------------ megafauna: run, then blow

    /** A broken megafauna animal's hard run: Speed II for five seconds - distance, not escape. */
    private static final int MEGA_RUN_TICKS = 5 * 20;
    /** Then it has to stop and catch its breath: crippled for ten seconds. That is the hunter's window. */
    private static final int MEGA_BLOWN_TICKS = 10 * 20;
    /** Slowness IV: it can barely walk. */
    private static final int MEGA_BLOWN_AMPLIFIER = 3;
    /** Got its breath back, it runs again the moment the hunter comes this close. */
    private static final double MEGA_SHY_DISTANCE = 10.0D;

    private static final int RUNNING = 0;
    private static final int BLOWN = 1;
    private static final int RECOVERED = 2;

    private record Winded(UUID hunter, int phase, long until) {
    }

    private static final Map<UUID, Winded> winded = new HashMap<>();

    /** Megafauna that runs rather than fights once it is broken - the big grazers, not the big cats. */
    public static boolean isMegaGame(LivingEntity animal) {
        return animal.getType().is(ModTags.EntityTypes.MEGAFAUNA) && !animal.getType().is(ModTags.EntityTypes.PREDATORS);
    }

    /**
     * Megafauna between runs keeps its ground: catching its breath, or breathing again and
     * watching. It only flees during a run - it wants distance, not to be gone.
     */
    public static boolean holdsItsGround(LivingEntity animal) {
        Winded state = winded.get(animal.getUUID());
        return state != null && state.phase() != RUNNING;
    }

    /** The big animal breaks: one hard run. Returns true if it started one. */
    private static boolean megaBolt(LivingEntity animal, ServerPlayer hunter) {
        Winded state = winded.get(animal.getUUID());
        if (state != null && state.phase() != RECOVERED) {
            return false;
        }
        animal.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MEGA_RUN_TICKS, 1, false, false, true));
        winded.put(animal.getUUID(), new Winded(hunter.getUUID(), RUNNING,
                animal.level().getGameTime() + MEGA_RUN_TICKS));
        if (animal instanceof PathfinderMob mob) {
            dev.hominin.evolution.entity.WoundedFleeGoal.makeFlee(mob, hunter);
        }
        return true;
    }

    /** Once a second: runs end in blowing, blowing ends in watching, and watching ends when you come close. */
    private static void tickWinded(ServerLevel level, long now) {
        winded.entrySet().removeIf(entry -> {
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity animal) || !animal.isAlive()) {
                return level.getEntity(entry.getKey()) == null ? now - entry.getValue().until() > 20 * 60 : true;
            }
            Winded state = entry.getValue();
            ServerPlayer hunter = level.getPlayerByUUID(state.hunter()) instanceof ServerPlayer p ? p : null;
            if (state.phase() == RUNNING && now >= state.until()) {
                animal.removeEffect(MobEffects.MOVEMENT_SPEED);
                animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MEGA_BLOWN_TICKS,
                        MEGA_BLOWN_AMPLIFIER, false, true, true));
                if (animal instanceof PathfinderMob mob) {
                    mob.getNavigation().stop();
                }
                entry.setValue(new Winded(state.hunter(), BLOWN, now + MEGA_BLOWN_TICKS));
                if (hunter != null && hunter.distanceToSqr(animal) < 64.0D * 64.0D) {
                    hunter.displayClientMessage(Component.literal(
                            "It pulls up, sides heaving - it has to catch its breath. Now.")
                            .withStyle(ChatFormatting.GOLD), true);
                }
            } else if (state.phase() == BLOWN && now >= state.until()) {
                entry.setValue(new Winded(state.hunter(), RECOVERED, now + 20 * 60 * 3));
            } else if (state.phase() == RECOVERED) {
                if (now >= state.until()) {
                    return true;
                }
                if (hunter != null && hunter.isAlive() && hunter.distanceToSqr(animal) < MEGA_SHY_DISTANCE * MEGA_SHY_DISTANCE
                        && !standsGround(animal)) {
                    megaBolt(animal, hunter);
                    rollSight(hunter, animal, false);
                }
            }
            return false;
        });
    }

    // ------------------------------------------------------------ keeping it in sight

    /**
     * It broke and ran: does the hunter keep it in sight? The persistence skill decides - 20%,
     * 40%, 60%. If not, it has to be thought back together (K). Returns true if it stayed marked.
     */
    private static boolean rollSight(ServerPlayer hunter, LivingEntity quarry, boolean newHunt) {
        if (!hunts(hunter) || quarry.hasEffect(MobEffects.GLOWING)) {
            return quarry.hasEffect(MobEffects.GLOWING);
        }
        int level = Persistence.level(hunter);
        if (hunter.getRandom().nextFloat() < Persistence.highlightChance(level)) {
            quarry.addEffect(new MobEffectInstance(MobEffects.GLOWING, FIRST_RUN_TICKS, 0, false, false));
            Hunt hunt = hunts.get(hunter.getUUID());
            if (hunt != null && hunt.quarry().equals(quarry.getUUID())) {
                hunts.put(hunter.getUUID(), new Hunt(hunt.quarry(), hunt.ranAt(), true));
            }
            lost.remove(hunter.getUUID());
            hunter.displayClientMessage(Component.literal(newHunt ? "It breaks and runs - and you keep it in sight."
                    : "It runs again. You still have it.").withStyle(ChatFormatting.GOLD), true);
            return true;
        }
        hunter.displayClientMessage(Component.literal(
                "It breaks and runs, and you lose it among the grass. Hold K to think - pick its tracks up.")
                .withStyle(ChatFormatting.YELLOW), true);
        if (isErectus(hunter)) {
            dev.hominin.evolution.guide.Tips.offer(hunter, dev.hominin.evolution.guide.Tips.Tip.PERSISTENCE);
        } else if (!dev.hominin.evolution.mind.Skills.knows(hunter, dev.hominin.evolution.mind.Skills.Skill.EARLY_TRACKING)) {
            dev.hominin.evolution.guide.Tips.offer(hunter, dev.hominin.evolution.guide.Tips.Tip.EARLY_TRACKING);
        }
        return false;
    }

    /** Hunters who have lost sight of their quarry, and whose band is waiting for them to find it. */
    private static final java.util.Set<UUID> lost = new java.util.HashSet<>();
    /** How often someone in the band may find the tracks when you cannot. */
    private static final int TRACK_CHECK_TICKS = 5 * 20;
    private static final float MEMBER_TRACK_SHARE = 0.35F;

    /**
     * While the quarry is out of sight the band does not chase it blind: they stop and wait for
     * you to think. Now and then one who tracks at least as well as you finds it for you.
     */
    private static void tickSight(ServerLevel level, long now) {
        for (var entry : hunts.entrySet()) {
            if (!(level.getPlayerByUUID(entry.getKey()) instanceof ServerPlayer hunter)
                    || !(level.getEntity(entry.getValue().quarry()) instanceof LivingEntity quarry) || !quarry.isAlive()) {
                continue;
            }
            boolean marked = quarry.hasEffect(MobEffects.GLOWING);
            if (marked) {
                if (lost.remove(hunter.getUUID())) {
                    // Found again: the band takes it up.
                    dev.hominin.evolution.band.Band.assist(hunter, quarry);
                }
                continue;
            }
            if (!hunts(hunter) || !isBigGame(quarry)) {
                continue;
            }
            java.util.List<dev.hominin.evolution.band.BandMember> band = dev.hominin.evolution.band.Band.ownNear(hunter, 48.0D);
            if (lost.add(hunter.getUUID())) {
                if (!band.isEmpty()) {
                    hunter.displayClientMessage(Component.literal(
                            "You have lost sight of it. The others stop and wait for you - hold K to think.")
                            .withStyle(ChatFormatting.YELLOW), true);
                }
            }
            for (dev.hominin.evolution.band.BandMember member : band) {
                if (member.getTarget() == quarry) {
                    member.setTarget(null);
                    member.getNavigation().stop();
                }
            }
            if (now % TRACK_CHECK_TICKS >= 20) {
                continue;
            }
            int yours = Persistence.level(hunter);
            for (dev.hominin.evolution.band.BandMember member : band) {
                if (member.isBaby() || member.getHuntLevel() > yours || member.distanceToSqr(quarry) > 48.0D * 48.0D
                        || member.getRandom().nextFloat() >= Persistence.highlightChance(member.getHuntLevel()) * MEMBER_TRACK_SHARE) {
                    continue;
                }
                quarry.addEffect(new MobEffectInstance(MobEffects.GLOWING, REMARK_TICKS, 0, false, false));
                member.ensureName();
                hunter.sendSystemMessage(Component.literal(member.getName().getString()
                        + " finds the tracks again and points the way.").withStyle(ChatFormatting.GOLD));
                member.defendAgainst(quarry);
                break;
            }
        }
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
        if (target instanceof dev.hominin.evolution.entity.Pelorovis pelorovis) {
            return pelorovis.standsGround();
        }
        return target instanceof dev.hominin.evolution.entity.Baboon baboon && baboon.hasTroopBehindIt();
    }

    /** Who first drew blood from this animal, if anyone still remembered is a player. */
    @Nullable
    public static ServerPlayer firstBloodOf(LivingEntity dead) {
        FirstBlood blood = firstBlood.get(dead.getUUID());
        return blood != null && dead.level().getPlayerByUUID(blood.hunter()) instanceof ServerPlayer hunter
                ? hunter : null;
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
        UUID before = quarryOf(hunter);
        boolean ran;
        if (isMegaGame(victim)) {
            dev.hominin.evolution.guide.Tips.offer(hunter, dev.hominin.evolution.guide.Tips.Tip.MEGAFAUNA);
            // Broken megafauna does not scatter like a gazelle: one hard run for distance, then it
            // has to blow - and while it blows, it stands and takes what comes.
            ran = megaBolt(victim, hunter);
        } else {
            bolt(victim, hunter);
            ran = before == null || !before.equals(victim.getUUID());
        }
        // Everything grazing beside it goes too, which is the hard part of picking one.
        for (LivingEntity other : victim.level().getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(HERD_RADIUS))) {
            if (other != victim && other instanceof net.minecraft.world.entity.animal.Animal && !standsGround(other)) {
                bolt(other, hunter);
            }
        }
        if (isBigGame(victim)) {
            if (firstBlood.size() > 2048) {
                firstBlood.clear();
            }
            firstBlood.putIfAbsent(victim.getUUID(), new FirstBlood(hunter.getUUID(), victim.level().getGameTime()));
        }
        if (hunts(hunter) && isBigGame(victim)) {
            Hunt old = hunts.get(hunter.getUUID());
            boolean same = old != null && old.quarry().equals(victim.getUUID());
            hunts.put(hunter.getUUID(), new Hunt(victim.getUUID(), victim.level().getGameTime(), same && old.seeded()));
            if (ran) {
                rollSight(hunter, victim, !same);
            }
        }
    }

    private record FirstBlood(java.util.UUID hunter, long at) {
    }

    /** Who first drew blood from big game, and when: a persistence hunt is measured from there. */
    private static final java.util.Map<java.util.UUID, FirstBlood> firstBlood = new java.util.HashMap<>();
    /** Long enough that it was a chase, not a lucky blow. */
    private static final long PERSISTENCE_TICKS = 20 * 30;

    /** Big game that died a good while after you first wounded it: you ran it down. */
    public static void creditPersistence(LivingEntity dead) {
        FirstBlood blood = firstBlood.remove(dead.getUUID());
        if (blood == null || dead.level().getGameTime() - blood.at() < PERSISTENCE_TICKS
                || !(dead.level().getPlayerByUUID(blood.hunter()) instanceof ServerPlayer hunter)) {
            return;
        }
        dev.hominin.evolution.EvolutionManager.incrementCriterion(hunter, "persistence_kill", 1);
        Persistence.practise(hunter);
        hunter.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "It could not run any more. You could.").withStyle(net.minecraft.ChatFormatting.GOLD), true);
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
            dev.hominin.evolution.mind.Skills.learn(player, dev.hominin.evolution.mind.Skills.Skill.EARLY_TRACKING);
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
            boolean tracker = dev.hominin.evolution.mind.Skills.knows(player, dev.hominin.evolution.mind.Skills.Skill.TRACKING);
            quarry.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                    tracker ? FIRST_RUN_TICKS * 3 / 2 : FIRST_RUN_TICKS, 0, false, false));
            dev.hominin.evolution.mind.Skills.learn(player, dev.hominin.evolution.mind.Skills.Skill.TRACKING);
            // Habilis thinking the chase back together is where the line learns to hunt this way at all.
            dev.hominin.evolution.mind.Skills.learn(player, dev.hominin.evolution.mind.Skills.Skill.EARLY_TRACKING);
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
        lost.removeIf(id -> !hunts.containsKey(id));
        tickWinded(level, now);
        tickSight(level, now);
    }

    @Nullable
    public static UUID quarryOf(ServerPlayer player) {
        Hunt hunt = hunts.get(player.getUUID());
        return hunt == null ? null : hunt.quarry();
    }

    public static void forget(UUID player) {
        hunts.remove(player);
        lost.remove(player);
    }

    private Quarry() {
    }
}
