package dev.hominin.evolution.band;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.band.goal.CraftGoal;
import dev.hominin.evolution.mind.Journal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;

/**
 * Mates, pregnancy and birth - for the band, and for you.
 *
 * <p>From habilis on, hominins pair-bond: one mate, kept. Before that nobody is anybody's in
 * particular. You have a sex like everyone else, so you can be chosen too: groom somebody of
 * the other sex, or feed them (a favourite food counts for three), and in time they take you
 * as their mate. Then, picked out and asked under H, you have a child - one of you carries it.
 *
 * <p>A pregnancy eats: hunger runs faster, and food is what they ask for. Near the end the
 * mother goes off alone to give birth, and glows so you can find her, because everything
 * that hunts can find her too. For that stretch your band is braver - displays drive off
 * anything, and you fight harder.
 */
public final class Mating {
    /** How much courting it takes: three groomings or meals, or one favourite food. */
    public static final int COURTSHIP_NEEDED = 3;
    public static final int PREGNANCY_TICKS = 24000;
    /** The last quarter: labour, alone, and dangerous. */
    public static final int LABOUR_TICKS = 6000;
    /** The player's pregnancy: when it is due, in minutes of game time. */
    private static final String DUE = "pregnant_due_minute";
    private static final String LABOUR_ANNOUNCED = "labour_announced";
    private static final double PAIRING_RADIUS = 16.0D;
    /** Near enough to a birth to be part of guarding it. */
    public static final double GUARD_RADIUS = 48.0D;

    /** Before habilis there are no pair bonds: nobody is anybody's in particular. */
    public static boolean pairBonds(net.minecraft.resources.ResourceLocation stage) {
        return !CraftGoal.isPreOldowan(stage);
    }

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    public static boolean isFemale(ServerPlayer player) {
        return Journal.gender(player, player.getData(Attachments.PLAYER_EVOLUTION_DATA)) == 2;
    }

    // ------------------------------------------------------------ the player's mate

    @Nullable
    public static BandMember mateOf(ServerPlayer player) {
        for (BandMember member : Band.all(player)) {
            if (member.isMateOf(player.getUUID())) {
                return member;
            }
        }
        return null;
    }

    /** Bond needed to ask somebody outright. */
    public static final int MATE_BOND = 2;
    /** How often being hurt fires a mate up: a real pair notices every blow, but not every tick. */
    private static final long PROTECT_COOLDOWN = 60L;
    private static final java.util.Map<UUID, Long> lastProtect = new java.util.HashMap<>();

    /** "Be my mate", asked of one member: yes, if they like you enough and are free. */
    public static void makeMate(ServerPlayer player, BandMember member) {
        member.ensureName();
        String name = member.getName().getString();
        if (member.isMateOf(player.getUUID())) {
            player.displayClientMessage(Component.literal(name + " is already your mate."), true);
            return;
        }
        if (member.isBaby() || !member.isLedBy(player)) {
            player.displayClientMessage(Component.literal(name + " is not someone you can ask that."), true);
            return;
        }
        if (member.isFemale() == isFemale(player)) {
            player.displayClientMessage(Component.literal(name + " is not interested in you that way."), true);
            return;
        }
        boolean bonds = pairBonds(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        if (bonds && member.getMate() != null) {
            player.displayClientMessage(Component.literal(name + " already has a mate."), true);
            return;
        }
        UUID playerMate = PlayerTies.mateOf(player.server, player.getUUID());
        if (bonds && playerMate != null) {
            player.displayClientMessage(Component.literal("You already have a mate."), true);
            return;
        }
        BandMember current = mateOf(player);
        if (bonds && current != null) {
            current.ensureName();
            player.displayClientMessage(Component.literal("You already have a mate: "
                    + current.getName().getString() + "."), true);
            return;
        }
        if (member.getBond() < MATE_BOND) {
            player.displayClientMessage(Component.literal(name + " does not know you well enough yet. (Bond "
                    + member.getBond() + " of " + MATE_BOND + ")"), true);
            return;
        }
        member.addCourtship(COURTSHIP_NEEDED);
        court(player, member, 0);
    }

    /**
     * Pair bonders feel it when the other is hurt. From habilis on, when a predator strikes you
     * your mate is on it at once, faster and harder - and when one strikes your mate, so are you.
     */
    public static void onPlayerStruck(ServerPlayer player) {
        BandMember mate = mateOf(player);
        if (mate == null || !pairBonds(mate.getStage()) || mate.distanceToSqr(player) > 32.0D * 32.0D
                || !offProtectCooldown(mate.getUUID(), player.level().getGameTime())) {
            return;
        }
        rouse(mate);
    }

    public static void onMateStruck(BandMember mate) {
        if (!pairBonds(mate.getStage()) || mate.getMate() == null
                || !(mate.level().getPlayerByUUID(mate.getMate()) instanceof ServerPlayer player)
                || mate.distanceToSqr(player) > 32.0D * 32.0D
                || !offProtectCooldown(player.getUUID(), player.level().getGameTime())) {
            return;
        }
        rouse(player);
        mate.ensureName();
        dev.hominin.evolution.guide.Alerts.urgent(player, dev.hominin.evolution.guide.Alerts.Kind.DANGER, "Something has hurt " + mate.getName().getString() + ".", ChatFormatting.RED);
    }

    private static boolean offProtectCooldown(UUID who, long now) {
        if (now - lastProtect.getOrDefault(who, -PROTECT_COOLDOWN) < PROTECT_COOLDOWN) {
            return false;
        }
        lastProtect.put(who, now);
        return true;
    }

    private static void rouse(net.minecraft.world.entity.LivingEntity who) {
        who.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, false, true));
        who.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, true));
    }

    /** Grooming or feeding somebody of the other sex: courting, whether you meant it or not. */
    public static void court(ServerPlayer player, BandMember member, int amount) {
        if (member.isBaby() || !member.isLedBy(player) || member.isFemale() == isFemale(player)
                || member.isMateOf(player.getUUID())) {
            return;
        }
        boolean bonds = pairBonds(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        if (bonds && (member.getMate() != null || mateOf(player) != null)) {
            // Spoken for, one of you or both - and from habilis on that means something.
            return;
        }
        if (member.addCourtship(amount) < COURTSHIP_NEEDED) {
            return;
        }
        member.setMate(player.getUUID());
        member.ensureName();
        ((ServerLevel) member.level()).sendParticles(ParticleTypes.HEART, member.getX(), member.getEyeY() + 0.4D,
                member.getZ(), 10, 0.5D, 0.4D, 0.5D, 0.0D);
        player.sendSystemMessage(Component.literal(member.getName().getString() + " has chosen you as a mate."
                + (bonds ? " From now on it is the two of you." : ""))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.literal("(Pick them out and press H - Social - Let's have a child.)")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "Let's have a child", said to one member. */
    public static void haveChild(ServerPlayer player, BandMember member) {
        member.ensureName();
        String name = member.getName().getString();
        if (!member.isMateOf(player.getUUID())) {
            player.displayClientMessage(Component.literal(name + " is not your mate."), true);
            return;
        }
        if (!Band.hasRoomFor(player)) {
            player.displayClientMessage(Component.literal("Your band is as big as the land can feed."), true);
            return;
        }
        boolean mother = isFemale(player);
        if (mother ? isPregnant(player) : member.isPregnant()) {
            player.displayClientMessage(Component.literal(mother ? "You are already carrying a child."
                    : name + " is already carrying your child."), true);
            return;
        }
        ((ServerLevel) member.level()).sendParticles(ParticleTypes.HEART, member.getX(), member.getEyeY() + 0.4D,
                member.getZ(), 10, 0.5D, 0.4D, 0.5D, 0.0D);
        if (mother) {
            counters(player).put(DUE, (int) ((player.level().getGameTime() + PREGNANCY_TICKS) / 1200L));
            counters(player).remove(LABOUR_ANNOUNCED);
            player.sendSystemMessage(Component.literal("You are pregnant. " + name + " is the father. You will be "
                    + "hungrier than usual, and in about a day the child comes.").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            member.startPregnancy();
            player.sendSystemMessage(Component.literal(name + " is pregnant with your child. Keep them fed - and "
                    + "near the end, keep them safe.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        member.addBond(1);
    }

    // ------------------------------------------------------------ the player's pregnancy

    /** A player carrying a child by another player: due in about a day. */
    public static void conceive(ServerPlayer mother, String father) {
        counters(mother).put(DUE, (int) ((mother.level().getGameTime() + PREGNANCY_TICKS) / 1200L));
        counters(mother).remove(LABOUR_ANNOUNCED);
        mother.sendSystemMessage(Component.literal("You are pregnant. " + father + " is the father. You will be hungrier "
                + "than usual, and in about a day the child comes.").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static boolean isPregnant(ServerPlayer player) {
        return counters(player).getOrDefault(DUE, 0) > 0;
    }

    private static long ticksLeft(ServerPlayer player) {
        return counters(player).getOrDefault(DUE, 0) * 1200L - player.level().getGameTime();
    }

    public static String describePregnancy(ServerPlayer player) {
        long left = ticksLeft(player);
        return left <= LABOUR_TICKS ? "the child is coming - stay safe" : "due in about " + Math.max(1, left / 1000) + " hours";
    }

    public static void clearPregnancy(ServerPlayer player) {
        counters(player).remove(DUE);
        counters(player).remove(LABOUR_ANNOUNCED);
    }

    /** Every tick for the player: a pregnancy, a labour, and anybody of yours in labour nearby. */
    public static void tick(ServerPlayer player) {
        if (isPregnant(player)) {
            // Eating for two.
            player.causeFoodExhaustion(0.008F);
            long left = ticksLeft(player);
            if (left <= 0L) {
                birth(player);
            } else if (left <= LABOUR_TICKS && player.tickCount % 20 == 0) {
                labour(player, player.blockPosition(), counters(player).getOrDefault(LABOUR_ANNOUNCED, 0) == 0);
                counters(player).put(LABOUR_ANNOUNCED, 1);
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
            }
        }
        if (player.tickCount % 20 == 0 && guarding(player)) {
            // Somebody of yours is giving birth: everything in you is ready for a fight.
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, false));
        }
    }

    /** Whether a birth is happening near this player: theirs, or one of their band's. */
    public static boolean guarding(ServerPlayer player) {
        if (isPregnant(player) && ticksLeft(player) <= LABOUR_TICKS) {
            return true;
        }
        for (BandMember member : Band.ownNear(player, GUARD_RADIUS)) {
            if (member.isInLabour()) {
                return true;
            }
        }
        return false;
    }

    private static void birth(ServerPlayer player) {
        clearPregnancy(player);
        SacredPile.event(player, "your child's birth");
        Chatter.news(player, "news_birth", "");
        ServerLevel level = player.serverLevel();
        BandMember baby = ModEntities.BAND_MEMBER.get().create(level);
        if (baby == null) {
            return;
        }
        baby.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        baby.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), MobSpawnType.BREEDING, null);
        baby.setStage(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        // Born into the band the mother lives in: hers, or the one she leads with somebody else.
        UUID host = Newcomers.hostOf(player);
        baby.setLeader(host != null ? host : player.getUUID());
        baby.makeBaby();
        baby.ensureName();
        level.addFreshEntity(baby);
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getEyeY(), player.getZ(), 10, 0.5D, 0.4D, 0.5D, 0.0D);
        BandMember mate = mateOf(player);
        if (mate != null) {
            mate.addBond(2);
        }
        player.sendSystemMessage(Component.literal("You have given birth to " + baby.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Labour: once to announce it and bring something in, then now and again something else
     * finds the smell of it. Called every second while it lasts.
     */
    public static void labour(ServerPlayer leader, BlockPos where, boolean starting) {
        if (starting) {
            spawnPredatorNear(leader.serverLevel(), where);
            return;
        }
        if (leader.getRandom().nextInt(90) == 0) {
            spawnPredatorNear(leader.serverLevel(), where);
        }
    }

    private static void spawnPredatorNear(ServerLevel level, BlockPos where) {
        if (dev.hominin.evolution.entity.Bonobo.sanctuary(level, where)) {
            return;
        }
        EntityType<? extends Mob> type = switch (level.random.nextInt(3)) {
            case 0 -> ModEntities.PACHYCROCUTA.get();
            case 1 -> ModEntities.SABERTOOTH.get();
            default -> ModEntities.HOMOTHERIUM.get();
        };
        BlockPos spot = Band.standingSpotNear(level, where, 22, level.random.nextFloat() * net.minecraft.util.Mth.TWO_PI);
        Mob predator = type.create(level);
        if (predator == null) {
            return;
        }
        predator.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        predator.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        level.addFreshEntity(predator);
    }

    // ------------------------------------------------------------ the band on its own

    /** Whether this member is in a state to think about a mate: fed, whole, grown. */
    private static boolean thriving(BandMember member) {
        return !member.isBaby() && member.getLeader() != null && !member.isPregnant()
                && member.getHunger() > 15 && member.getHealth() >= member.getMaxHealth() * 0.8F
                && !member.hasEffect(dev.hominin.evolution.ModEffects.BLEEDING);
    }

    /** From joining a band to looking for a mate: half a day to a day and a half. */
    private static final int PAIR_MIN_TICKS = 12000;
    private static final int PAIR_SPREAD_TICKS = 24000;
    /** The share who never pair up by themselves (you can still ask them). */
    private static final float NEVER_PAIRS = 0.15F;

    /** Once a minute or so, per member of a player's band. */
    public static void tickMember(BandMember member) {
        if (!thriving(member) || !(member.leaderPlayer() instanceof ServerPlayer leader)) {
            return;
        }
        List<BandMember> near = Band.near(member, PAIRING_RADIUS);
        boolean bonds = pairBonds(member.getStage());
        UUID mateId = member.getMate();
        if (bonds && mateId == null) {
            // Nobody arrives paired. A member takes a day or three to settle on anyone - and some
            // never do, on their own.
            long now = member.level().getGameTime();
            if (member.getPairReadyAt() < 0L) {
                member.setPairReadyAt(member.getRandom().nextFloat() < NEVER_PAIRS ? Long.MAX_VALUE
                        : now + PAIR_MIN_TICKS + member.getRandom().nextInt(PAIR_SPREAD_TICKS));
            }
            if (now < member.getPairReadyAt() || member.getRandom().nextInt(4) != 0) {
                return;
            }
            for (BandMember other : near) {
                if (other != member && other.isFemale() != member.isFemale() && other.getMate() == null
                        && other.isLedBy(leader) && thriving(other) && other.getPairReadyAt() >= 0L
                        && now >= other.getPairReadyAt()) {
                    member.setMate(other.getUUID());
                    other.setMate(member.getUUID());
                    member.ensureName();
                    other.ensureName();
                    leader.sendSystemMessage(Component.literal(member.getName().getString() + " and "
                            + other.getName().getString() + " have become mates.").withStyle(ChatFormatting.LIGHT_PURPLE));
                    return;
                }
            }
            return;
        }
        if (member.getRandom().nextInt(3) != 0 || !Band.hasRoomFor(leader)) {
            return;
        }
        for (BandMember other : near) {
            if (other == member || other.isFemale() == member.isFemale() || !other.isLedBy(leader) || !thriving(other)) {
                continue;
            }
            if (bonds && !other.getUUID().equals(mateId)) {
                continue;
            }
            BandMember mother = member.isFemale() ? member : other;
            BandMember father = mother == member ? other : member;
            mother.startPregnancy();
            mother.ensureName();
            father.ensureName();
            dev.hominin.evolution.guide.Alerts.urgent(leader, dev.hominin.evolution.guide.Alerts.Kind.BAND, Component.literal(mother.getName().getString() + " is expecting. "
                    + father.getName().getString() + " is the father.").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
    }

    /** A member has gone into labour. */
    public static void memberLabourBegan(BandMember mother) {
        if (!(mother.leaderPlayer() instanceof ServerPlayer leader)) {
            return;
        }
        mother.ensureName();
        dev.hominin.evolution.guide.Alerts.urgent(leader, dev.hominin.evolution.guide.Alerts.Kind.WARNING, Component.literal(mother.getName().getString() + " has gone off alone to give "
                + "birth. Everything that hunts will find her - find her first, and guard her.").withStyle(ChatFormatting.GOLD));
        labour(leader, mother.blockPosition(), true);
    }

    /** The mate, if any, as a name for the tribe list. */
    public static String mateName(BandMember member, ServerLevel level) {
        UUID mate = member.getMate();
        if (mate == null) {
            return null;
        }
        Player player = level.getPlayerByUUID(mate);
        if (player != null) {
            return "you";
        }
        return level.getEntity(mate) instanceof BandMember other ? other.getName().getString() : null;
    }

    /**
     * You slept the night through: by morning every one of your band who was carrying has had the child. Nobody wants
     * to be told at dawn there is still a day to go.
     */
    public static void nightSlept(ServerPlayer player) {
        if (player.level().getDayTime() % 24000L > 2000L) {
            return;
        }
        for (BandMember member : Band.all(player)) {
            if (member.isPregnant()) {
                member.deliverNow();
            }
        }
    }

    private Mating() {
    }
}
