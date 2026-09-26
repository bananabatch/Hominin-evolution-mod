package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.world.Pois;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * A spring is more than water. It is cold, clean and moving, and the body knows it.
 *
 * <ul>
 * <li><b>Drinking</b> from one mends you a little (half a heart a drink, a heart at most every five seconds), and one
 * drink in four settles a sick gut outright - food-borne illness gone.</li>
 * <li><b>Sitting in one</b>: ten seconds and the ticks let go and float off; thirty and torn-open wounds close clean -
 * lacerations gone. Get out and it starts again.</li>
 * </ul>
 */
public final class Springs {
    /** How long a soak takes to lift the ticks off, and to close lacerations, in seconds. */
    private static final int TICKS_OFF = 10;
    private static final int WOUNDS_CLOSED = 30;
    /** Drinking mends at most this often. */
    private static final long MEND_GAP = 100L;
    private static final float MEND = 2.0F;
    private static final float SETTLES_GUT = 0.25F;

    private static final Map<UUID, Integer> soaking = new HashMap<>();
    private static final Map<UUID, Long> mended = new HashMap<>();

    /** A drink straight from a spring. */
    public static void drank(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (player.getHealth() < player.getMaxHealth() && now - mended.getOrDefault(player.getUUID(), -MEND_GAP) >= MEND_GAP) {
            mended.put(player.getUUID(), now);
            player.heal(MEND);
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 3, 0.3D, 0.3D, 0.3D, 0.0D);
        }
        if (FoodIllness.has(player) && player.getRandom().nextFloat() < SETTLES_GUT) {
            FoodIllness.cure(player);
            player.sendSystemMessage(Component.literal("The cold spring water settles your gut. The sickness is gone.")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** Whether you are sitting in a spring right now. */
    public static boolean soakingIn(ServerPlayer player) {
        if (!player.isInWater()) {
            return false;
        }
        BlockPos at = player.blockPosition();
        return Pois.isSpring(player.serverLevel(), at) || Pois.isSpring(player.serverLevel(), at.below());
    }

    /** Once a second: a soak counts up while you stay in, and does its work as it goes. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 13 || player.isSpectator()) {
            return;
        }
        if (!soakingIn(player)) {
            soaking.remove(player.getUUID());
            return;
        }
        int seconds = soaking.merge(player.getUUID(), 1, Integer::sum);
        boolean ticks = Infestation.of(player) > 0;
        boolean torn = Afflictions.current(player) == Afflictions.Affliction.LACERATED;
        if (seconds == 1 && (ticks || torn)) {
            player.displayClientMessage(Component.literal("You sit in the spring. The cold water works at you - stay in.")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        if (seconds % 4 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 0.6D, player.getZ(),
                    6, 0.4D, 0.2D, 0.4D, 0.0D);
        }
        if (seconds >= TICKS_OFF && ticks) {
            int had = Infestation.of(player);
            Infestation.set(player, 0);
            Afflictions.relieve(player, Afflictions.Affliction.INFESTED);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.4F, 1.4F);
            player.sendSystemMessage(Component.literal("In the cold water the ticks let go - " + had
                    + (had == 1 ? " of them floats" : " of them float") + " off.").withStyle(ChatFormatting.GREEN));
        }
        if (seconds >= WOUNDS_CLOSED && torn) {
            Afflictions.relieve(player, Afflictions.Affliction.LACERATED);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.4F, 1.0F);
            player.sendSystemMessage(Component.literal("The spring has washed the wounds clean, and they close properly now."
                    + " You are not torn open any more.").withStyle(ChatFormatting.GREEN));
        } else if (seconds == WOUNDS_CLOSED / 2 && torn) {
            player.displayClientMessage(Component.literal("The wounds are cleaner. A little longer.")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    public static void forget(UUID player) {
        soaking.remove(player);
        mended.remove(player);
    }

    private Springs() {
    }
}
