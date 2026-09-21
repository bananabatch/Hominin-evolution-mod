package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * The one thing stopping you healing.
 *
 * <p>Several systems have a reason to hold a body back from mending - blood already
 * lost, a gut full of something it should not have had, a coat full of ticks - and if
 * each of them held its own veto they would stack, and a run of ordinary bad luck would
 * leave a player who simply never healed again with no way to read why.
 *
 * <p>So there is exactly one at a time. A worse affliction takes over from a lesser one
 * and the lesser one is gone; a lesser one that arrives while something worse is running
 * changes nothing, because you are already in the worse trouble. Either way, one cause,
 * one timer, one thing to fix.
 */
public final class Afflictions {
    /**
     * What is keeping the body busy, worst last. The order is the whole arbitration
     * rule: anything further down this list overrides anything above it.
     */
    public enum Affliction {
        INFESTED("Ticks", "You are too bitten up to mend. Get somebody to groom you."),
        BLED_OUT("Blood loss", "You lost too much blood. It will be a while before you mend."),
        INFECTED("Infection", "The wound has gone bad. Nothing is healing until it passes."),
        LACERATED("Lacerations", "You are opened up too badly to heal. Drink, and keep drinking.");

        private final String label;
        private final String explanation;

        Affliction(String label, String explanation) {
            this.label = label;
            this.explanation = explanation;
        }

        public String label() {
            return label;
        }

        public String explanation() {
            return explanation;
        }
    }

    private record Held(Affliction affliction, long until) {
    }

    private static final Map<UUID, Held> held = new HashMap<>();

    /**
     * Afflicts the body, if this is worse than whatever it already has.
     *
     * @return true if this one took hold.
     */
    public static boolean afflict(LivingEntity entity, Affliction affliction, int ticks) {
        UUID id = entity.getUUID();
        long now = entity.level().getGameTime();
        Held current = held.get(id);
        boolean running = current != null && now < current.until();
        if (running && current.affliction().ordinal() > affliction.ordinal()) {
            return false;
        }
        held.put(id, new Held(affliction, now + ticks));
        // Only news the first time. Something already running and being kept going is
        // not worth telling anyone about again, twenty times a second.
        boolean alreadyKnown = running && current.affliction() == affliction;
        if (!alreadyKnown && entity instanceof Player player && !entity.level().isClientSide()) {
            player.sendSystemMessage(Component.literal(affliction.explanation())
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }

    /** What is currently stopping this body healing, if anything. */
    @Nullable
    public static Affliction current(LivingEntity entity) {
        Held current = held.get(entity.getUUID());
        if (current == null || entity.level().getGameTime() >= current.until()) {
            return null;
        }
        return current.affliction();
    }

    /** How much longer it has to run, in seconds. Zero if nothing is running. */
    public static int secondsLeft(LivingEntity entity) {
        Held current = held.get(entity.getUUID());
        if (current == null) {
            return 0;
        }
        long left = current.until() - entity.level().getGameTime();
        return left <= 0 ? 0 : (int) (left / 20L);
    }

    /** Mending it directly - grooming the ticks out, sleeping the infection off. */
    public static void relieve(LivingEntity entity, Affliction affliction) {
        Held current = held.get(entity.getUUID());
        if (current != null && current.affliction() == affliction) {
            held.remove(entity.getUUID());
        }
    }

    public static void clear(LivingEntity entity) {
        held.remove(entity.getUUID());
    }

    public static void forget(UUID entity) {
        held.remove(entity);
    }

    /** The single veto. Everything that wants to stop healing comes through here. */
    public static void onHeal(LivingHealEvent event) {
        if (current(event.getEntity()) != null) {
            event.setCanceled(true);
        }
    }

    private Afflictions() {
    }
}
