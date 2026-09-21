package dev.hominin.evolution.survival;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Kuru: a prion disease, caught by eating a brain that carried it. There is no cure - not
 * now, not in 2026 - and this does not pretend otherwise.
 *
 * <p>The first day it is only trembling and nausea. Late on the second day the sight goes.
 * On the morning of the third, it ends. Meanwhile everything that hunts can tell something
 * is wrong with you.
 *
 * <p>It is tracked by the day it was caught, not by the effect, so milk or anything else
 * that clears effects only hides it for a moment.
 */
public final class Kuru {
    /** Chance that a brain carried it. */
    public static final float CHANCE = 0.3F;
    /** The day it was caught, plus one; zero is healthy. */
    private static final String SINCE = "kuru_since_day";
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "kuru"));

    public static boolean has(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().getOrDefault(SINCE, 0) > 0;
    }

    /** Eating a brain. Returns true if it carried the disease. */
    public static boolean exposed(ServerPlayer player) {
        if (has(player) || player.getRandom().nextFloat() >= CHANCE) {
            return false;
        }
        int day = (int) (player.level().getDayTime() / 24000L);
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put(SINCE, day + 1);
        return true;
    }

    public static void clear(ServerPlayer player) {
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().remove(SINCE);
        player.removeEffect(ModEffects.KURU);
    }

    /** Once a second. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || !has(player) || player.isCreative() || player.isSpectator()) {
            return;
        }
        int caught = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().get(SINCE) - 1;
        long time = player.level().getDayTime();
        int days = (int) (time / 24000L) - caught;
        long hour = time % 24000L;
        if (!player.hasEffect(ModEffects.KURU)) {
            player.addEffect(new MobEffectInstance(ModEffects.KURU, -1, 0, false, false, true));
        }
        // The morning of the third day.
        if (days >= 2 && hour < 12000L || days >= 3) {
            player.sendSystemMessage(Component.literal("The shaking stops.").withStyle(ChatFormatting.DARK_RED));
            clear(player);
            player.hurt(player.damageSources().source(DAMAGE), Float.MAX_VALUE);
            return;
        }
        // Trembling, and the stomach turning over, more often as it goes on.
        if (player.getRandom().nextInt(days >= 1 ? 20 : 40) == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 12, 0, false, false));
        }
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
        // Late on the second day, the sight goes.
        if (days >= 1 && hour >= 12000L) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
        }
    }

    /** For the journal. */
    public static String describe(ServerPlayer player) {
        if (!has(player)) {
            return "";
        }
        int caught = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().get(SINCE) - 1;
        int days = (int) (player.level().getDayTime() / 24000L) - caught;
        return days <= 0 ? "Kuru. The trembling has started." : days == 1 ? "Kuru. The second day." : "Kuru. It is nearly over.";
    }

    private Kuru() {
    }
}
