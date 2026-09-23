package dev.hominin.evolution.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.survival.Afflictions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Blood loss, in three kinds, because they are not the same injury.
 *
 * <p>A cut bleeds and stops. Something driven in deep bleeds where you cannot reach it.
 * And a wound that opens a body properly does not have a severity - it has a clock, and
 * the only thing anybody could ever do about it was pour water into the person and hope.
 *
 * <p>All three ride on the same {@code BLEEDING} effect, one tier per amplifier, so the
 * existing wound and persistence-hunting machinery keeps working unchanged. What differs
 * is what each one does to a player on top of the damage.
 */
public final class Bleeding {
    /** Blood lost a little at a time, and all at once - and the bleed inside a skull. */
    public static final ResourceKey<DamageType> BLEEDING = key("bleeding");
    public static final ResourceKey<DamageType> BLED_OUT = key("bled_out");
    public static final ResourceKey<DamageType> BRAIN_BLEED = key("brain_bleed");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.hominin.evolution.HomininEvolutionMod.MODID, name));
    }

    /**
     * The three tiers. The ordinal is the effect amplifier, so the order matters.
     */
    public enum Tier {
        /** Teeth, flakes, any sharp edge. It bleeds, and then it stops. */
        EXTERNAL(8 * 20),
        /** Driven in: heavy tools, spears, the big predators. Nothing closes while it runs. */
        INTERNAL(14 * 20),
        /** A body opened up. Fatal, unless enough water goes into it in time. */
        CATASTROPHIC(30 * 20);

        private final int ticks;

        Tier(int ticks) {
            this.ticks = ticks;
        }

        public int ticks() {
            return ticks;
        }
    }

    /** How long a catastrophic wound gives you, and how much water it takes to come back. */
    private static final int CATASTROPHIC_TICKS = 60 * 20;
    private static final int WATER_TO_SURVIVE = 24;

    /** Lacerations last until the next dawn; an infection is a two-day business. */
    private static final int LACERATION_TICKS = 24000;
    private static final int INFECTION_TICKS = 2 * 24000;

    private record Catastrophic(long endsAt, int drunk) {
    }

    private static final Map<UUID, Catastrophic> dying = new HashMap<>();

    /**
     * Opens a wound of this tier. A worse one always overrides a lesser one; a lesser
     * one on top of a worse one only refreshes how long it runs.
     */
    public static void inflict(LivingEntity target, Tier tier) {
        MobEffectInstance existing = target.getEffect(ModEffects.BLEEDING);
        int severity = existing == null ? tier.ordinal() : Math.max(existing.getAmplifier(), tier.ordinal());
        int ticks = existing == null ? tier.ticks() : Math.max(existing.getDuration(), tier.ticks());
        target.addEffect(new MobEffectInstance(ModEffects.BLEEDING, ticks, severity, false, true, true));
        dev.hominin.evolution.hunt.Quarry.wounded(target);

        if (tier == Tier.INTERNAL) {
            Afflictions.afflict(target, Afflictions.Affliction.BLED_OUT, tier.ticks() * 3);
        }
        if (tier == Tier.CATASTROPHIC && target instanceof ServerPlayer player) {
            openCatastrophic(player);
        }
    }

    /**
     * The clock. You are going to die of this, and the only thing that has ever worked
     * is drinking far more than a body would normally take, for as long as it lasts.
     */
    private static void openCatastrophic(ServerPlayer player) {
        if (dying.containsKey(player.getUUID())) {
            return;
        }
        dying.put(player.getUUID(),
                new Catastrophic(player.level().getGameTime() + CATASTROPHIC_TICKS, 0));
        player.sendSystemMessage(Component.literal(
                "You are opened up, and it is not going to stop on its own. Drink. Keep drinking.")
                .withStyle(ChatFormatting.DARK_RED));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, 0.8F, 0.5F);
    }

    /** Whether this player is on the clock, for anything that wants to say so. */
    public static boolean isBleedingOut(Player player) {
        return dying.containsKey(player.getUUID());
    }

    /** Water going in. Enough of it, in time, and the wound closes on its own. */
    public static void drank(ServerPlayer player, int amount) {
        Catastrophic state = dying.get(player.getUUID());
        if (state == null) {
            return;
        }
        int drunk = state.drunk() + amount;
        if (drunk < WATER_TO_SURVIVE) {
            dying.put(player.getUUID(), new Catastrophic(state.endsAt(), drunk));
            player.displayClientMessage(Component.literal(
                    "Still going. (" + drunk + "/" + WATER_TO_SURVIVE + ")")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        survive(player);
    }

    /**
     * Coming through it. The wound closes, but it closes badly: torn open and barely
     * held, which is what lacerations are, and anything at all can undo that.
     */
    private static void survive(ServerPlayer player) {
        dying.remove(player.getUUID());
        player.removeEffect(ModEffects.BLEEDING);
        Afflictions.afflict(player, Afflictions.Affliction.LACERATED, LACERATION_TICKS);
        player.sendSystemMessage(Component.literal(
                "It closes. Badly, and it will open again if you let it - but it closes.")
                .withStyle(ChatFormatting.GOLD));
    }

    /** Running out of time. */
    public static void tick(ServerPlayer player) {
        Catastrophic state = dying.get(player.getUUID());
        if (state == null) {
            return;
        }
        long left = state.endsAt() - player.level().getGameTime();
        if (left <= 0) {
            dying.remove(player.getUUID());
            player.hurt(player.damageSources().source(BLED_OUT), Float.MAX_VALUE);
            return;
        }
        if (left % 100 == 0) {
            player.displayClientMessage(Component.literal("Bleeding out. " + (left / 20) + "s. Drink.")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    /**
     * A laceration reopened. Being hurt again, or eating something raw, turns a wound
     * that was closing into one that has gone bad - and an infection is far worse than
     * the bleed was, because it lasts two days and starves you while it runs.
     */
    public static void maybeInfect(ServerPlayer player, String cause) {
        if (Afflictions.current(player) != Afflictions.Affliction.LACERATED) {
            return;
        }
        Afflictions.afflict(player, Afflictions.Affliction.INFECTED, INFECTION_TICKS);
        player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 400, 0));
        player.sendSystemMessage(Component.literal(cause + " The wound has gone bad.")
                .withStyle(ChatFormatting.DARK_RED));
    }

    /** An infection eats what you put into it: hunger drains faster while it runs. */
    public static void tickInfection(ServerPlayer player) {
        if (player.tickCount % 100 != 0
                || Afflictions.current(player) != Afflictions.Affliction.INFECTED) {
            return;
        }
        player.causeFoodExhaustion(1.5F);
    }

    public static void forget(UUID player) {
        dying.remove(player);
    }

    private Bleeding() {
    }
}
