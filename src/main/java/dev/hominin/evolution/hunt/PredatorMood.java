package dev.hominin.evolution.hunt;

import java.util.HashMap;
import java.util.Map;

import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Presence;
import dev.hominin.evolution.survival.Seasons;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * Not every predator that sees you wants you. A cat full from last night's kill, a clan with its own business, a
 * hunter that has learned your band means trouble - it looks, and lets you pass. Whether it does is weighed once
 * and held for a while: how much the country knows your band (presence), the season (in a dry season, or a dry
 * day, everything is hungrier and far more of them come for you), the hour (bolder by night), and luck.
 *
 * <p>Never an animal you have just hurt, or one that came for you on purpose - the ones drawn in by a camp that
 * has stayed too long.
 */
public final class PredatorMood {
    /** Put on a predator sent after the band, so it never looks away. */
    public static final String CAME_FOR_YOU = "hominin_evolution.came_for_you";
    /** A verdict holds this long, so an animal does not change its mind every second. */
    private static final long HOLD_TICKS = 2400L;
    /** Hurt this recently, and it fights. */
    private static final int PROVOKED_TICKS = 400;

    private record Verdict(boolean ignores, long until) {
    }

    private static final Map<String, Verdict> verdicts = new HashMap<>();

    /** How likely a predator is to leave you alone. */
    public static float ignoreChance(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float chance = 0.30F;
        chance += Math.min(16, Presence.get(player)) * 0.02F;
        if (Presence.weak(player)) {
            // Presence 0-5: nothing out there hesitates.
            chance -= 0.25F;
        }
        chance += level.isDay() ? 0.12F : -0.12F;
        if (Seasons.superDry(level)) {
            chance -= 0.45F;
        } else if (Seasons.strained(level)) {
            chance -= 0.30F;
        } else if (Seasons.isProsperous(level)) {
            chance += 0.08F;
        }
        if (level.isRaining()) {
            chance += 0.05F;
        }
        if (player.getHealth() < player.getMaxHealth() * 0.5F) {
            // It can smell weakness.
            chance -= 0.15F;
        }
        return Mth.clamp(chance, 0.0F, 0.85F);
    }

    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()
                || !PredatorAppetite.isPredator(mob) || mob.getTags().contains(CAME_FOR_YOU)) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        ServerPlayer player = target instanceof ServerPlayer p ? p
                : target instanceof BandMember member && !member.isWild()
                        && member.leaderPlayer() instanceof ServerPlayer leader ? leader : null;
        if (player == null || player.isCreative() || player.isSpectator() || provoked(mob, player)) {
            return;
        }
        long now = mob.level().getGameTime();
        String key = mob.getUUID() + "|" + player.getUUID();
        Verdict verdict = verdicts.get(key);
        if (verdict == null || now >= verdict.until()) {
            if (verdicts.size() > 1024) {
                verdicts.values().removeIf(v -> now >= v.until());
            }
            verdict = new Verdict(mob.getRandom().nextFloat() < ignoreChance(player), now + HOLD_TICKS);
            verdicts.put(key, verdict);
            if (verdict.ignores() && mob.distanceToSqr(player) < 32.0D * 32.0D) {
                player.sendSystemMessage(Component.literal("The " + mob.getName().getString().toLowerCase()
                        + " watches you a while - and lets you pass.").withStyle(ChatFormatting.GRAY));
            }
        }
        if (verdict.ignores()) {
            event.setCanceled(true);
        }
    }

    /** Hurt lately by you or one of yours: it is past deciding. */
    private static boolean provoked(Mob mob, ServerPlayer player) {
        LivingEntity by = mob.getLastHurtByMob();
        if (by == null || mob.tickCount - mob.getLastHurtByMobTimestamp() > PROVOKED_TICKS) {
            return false;
        }
        return by == player || by instanceof BandMember member && member.isLedBy(player);
    }

    public static void forget(java.util.UUID mob) {
        verdicts.keySet().removeIf(key -> key.startsWith(mob.toString()));
    }

    private PredatorMood() {
    }
}
