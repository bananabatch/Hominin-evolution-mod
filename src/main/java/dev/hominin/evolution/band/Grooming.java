package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;

/**
 * Picking through someone else's hair. Grooming is what holds a primate group together -
 * it is how a chimpanzee says you are mine and I am yours, and it costs time, which is
 * the whole point of it.
 *
 * <p>Later hominins have less and less hair to work with, so by erectus this is cleaning
 * rather than picking: skin, cuts, thorns. In a sapiens band it stops being touch at all
 * and becomes talk - but that is a long way off yet.
 */
public final class Grooming {
    /** A proper session: long enough that you have to stand still for it. */
    private static final int SESSION_TICKS = 100;
    private static final int COOLDOWN_TICKS = 10 * 60 * 20;
    private static final double RANGE = 4.0D;

    private record Session(UUID member, int ticks) {
    }

    private static final Map<UUID, Session> sessions = new HashMap<>();
    /** When each member was last groomed, by anybody. */
    private static final Map<UUID, Long> lastGroomed = new HashMap<>();

    /** How this stage grooms: by erectus there is not enough hair left to pick through. */
    public static boolean picksHair(BandMember member) {
        String stage = member.getStage().getPath();
        return stage.equals("ardipithecus") || stage.equals("australopithecus") || stage.equals("homo_habilis");
    }

    public static void begin(ServerPlayer player, BandMember member) {
        if (member.distanceToSqr(player) > RANGE * RANGE) {
            player.displayClientMessage(Component.literal("You need to be closer to do that."), true);
            return;
        }
        long now = player.level().getGameTime();
        if (now - lastGroomed.getOrDefault(member.getUUID(), -99999L) < COOLDOWN_TICKS) {
            player.displayClientMessage(Component.literal(member.getName().getString()
                    + " has been seen to already."), true);
            return;
        }
        sessions.put(player.getUUID(), new Session(member.getUUID(), 0));
        player.displayClientMessage(Component.literal(picksHair(member)
                ? "You start picking through " + member.getName().getString() + "'s hair. Stay close."
                : "You start cleaning the dirt and grit off " + member.getName().getString() + ". Stay close."), true);
    }

    /** Runs a session the player has started. Called once a tick. */
    public static void tick(ServerPlayer player) {
        Session session = sessions.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (!(player.serverLevel().getEntity(session.member()) instanceof BandMember member) || !member.isAlive()
                || member.distanceToSqr(player) > RANGE * RANGE || player.isSprinting()) {
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.literal("You break off."), true);
            return;
        }
        int ticks = session.ticks() + 1;
        sessions.put(player.getUUID(), new Session(session.member(), ticks));
        member.getNavigation().stop();
        member.getLookControl().setLookAt(player);
        member.beingGroomed(20);
        if (ticks % 20 == 0) {
            player.swing(InteractionHand.MAIN_HAND, true);
            member.playSound(SoundEvents.WOOL_HIT, 0.5F, 1.4F);
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, member.getX(), member.getEyeY(),
                    member.getZ(), 2, 0.3D, 0.3D, 0.3D, 0.0D);
        }
        if (ticks < SESSION_TICKS) {
            return;
        }
        sessions.remove(player.getUUID());
        finish(player, member);
    }

    private static void finish(ServerPlayer player, BandMember member) {
        lastGroomed.put(member.getUUID(), player.level().getGameTime());
        member.addBond(1);
        member.heal(1.0F);
        ((ServerLevel) member.level()).sendParticles(ParticleTypes.HEART, member.getX(), member.getEyeY() + 0.3D,
                member.getZ(), 5, 0.3D, 0.2D, 0.3D, 0.0D);
        dev.hominin.evolution.EvolutionManager.incrementCriterion(player, Band.COHESION, 1);
        player.displayClientMessage(Component.literal(picksHair(member)
                ? member.getName().getString() + " leans into it, and settles. They trust you a little more."
                : member.getName().getString() + " lets you clean them up, and settles. They trust you a little more.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Two members grooming each other: the same thing, without the player in it. */
    public static void betweenMembers(BandMember groomer, BandMember other) {
        lastGroomed.put(other.getUUID(), groomer.level().getGameTime());
        other.heal(1.0F);
        if (groomer.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, other.getX(), other.getEyeY(), other.getZ(),
                    3, 0.3D, 0.3D, 0.3D, 0.0D);
        }
        if (groomer.leaderPlayer() instanceof ServerPlayer leader) {
            dev.hominin.evolution.EvolutionManager.incrementCriterion(leader, Band.COHESION, 1);
        }
    }

    public static boolean wasGroomedRecently(BandMember member) {
        return member.level().getGameTime() - lastGroomed.getOrDefault(member.getUUID(), -99999L) < COOLDOWN_TICKS;
    }

    public static void markGroomed(BandMember member) {
        lastGroomed.put(member.getUUID(), member.level().getGameTime());
    }

    public static void forget(UUID player) {
        sessions.remove(player);
    }

    private Grooming() {
    }
}
