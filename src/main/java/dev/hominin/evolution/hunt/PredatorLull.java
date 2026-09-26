package dev.hominin.evolution.hunt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * Predators come in their own time, and pick their moment.
 *
 * <ul>
 * <li><b>A lull after each one.</b> When a hunter turns up near you - out of the grass, drawn to a kill, whatever
 * brought it - no other comes near you for five minutes; kill one or drive it off and it is seven and a half. A clan
 * arriving together is one arrival. And never more than two hunters about you at once. The country does not send one
 * after another until the band is gone.</li>
 * <li><b>Nobody goes for a crowd.</b> A hunter does not pick out anyone standing with three or more of their band
 * (grown ones, within seven blocks) - it looks for whoever has wandered off on their own, and goes for them instead.
 * Strike it and it answers whoever struck it; a hyena clan, a crowd itself, still fights over its food.</li>
 * </ul>
 */
public final class PredatorLull {
    private static final double NEAR = 96.0D;
    private static final long LULL = 6000L;
    private static final long AFTER_KILL = 9000L;
    private static final int MOST_ABOUT = 2;
    /** This many of the band close round someone and they are not the one a hunter picks. */
    private static final int CROWD = 3;
    private static final double CLOSE = 7.0D;
    private static final double LOOKS_FOR_STRAGGLERS = 24.0D;

    private static final Map<UUID, Long> lullUntil = new HashMap<>();
    private static final Map<UUID, Long> arrivedAt = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PredatorLull::onJoin);
        NeoForge.EVENT_BUS.addListener(PredatorLull::onTarget);
    }

    /** What hunts hominins: the tagged predators, and the giant baboon and the crocodile besides. */
    public static boolean hunter(LivingEntity entity) {
        return PredatorAppetite.isPredator(entity) || entity instanceof dev.hominin.evolution.entity.Dinopithecus
                || entity instanceof dev.hominin.evolution.entity.Crocodile;
    }

    // ------------------------------------------------------------ when they come

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Mob mob) || !hunter(mob)
                || dev.hominin.evolution.world.Oases.keepsTruce(mob)) {
            return;
        }
        ServerPlayer near = null;
        double best = NEAR * NEAR;
        for (ServerPlayer player : level.players()) {
            double d = player.distanceToSqr(mob);
            if (!player.isSpectator() && d < best) {
                best = d;
                near = player;
            }
        }
        if (near == null || near.isCreative()) {
            // Nobody about - or somebody testing, with spawn eggs in hand.
            return;
        }
        long now = level.getGameTime();
        if (arrivedAt.getOrDefault(near.getUUID(), -1L) == now) {
            // The rest of a clan, arriving with the first.
            return;
        }
        if (now < lullUntil.getOrDefault(near.getUUID(), 0L) || about(level, near) >= MOST_ABOUT) {
            event.setCanceled(true);
            return;
        }
        arrivedAt.put(near.getUUID(), now);
        lullUntil.put(near.getUUID(), now + LULL);
    }

    private static int about(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEAR), m -> m.isAlive() && hunter(m))
                .size();
    }

    /** One killed or driven off near here: the country is quieter for a good while. */
    public static void quiet(ServerLevel level, BlockPos at) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(at) < NEAR * NEAR) {
                lullUntil.merge(player.getUUID(), now + AFTER_KILL, Math::max);
            }
        }
    }

    // ------------------------------------------------------------ who they go for

    private static boolean hominin(LivingEntity entity) {
        return entity instanceof Player player && !player.isCreative() && !player.isSpectator()
                || entity instanceof BandMember;
    }

    /** How many grown hominins stand close round this one. */
    private static int company(LivingEntity target) {
        List<LivingEntity> near = target.level().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(CLOSE), e -> e != target && e.isAlive() && hominin(e)
                        && !(e instanceof BandMember m && m.isBaby()));
        return near.size();
    }

    private static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || !hunter(mob)
                || mob instanceof dev.hominin.evolution.entity.Crocuta) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || !hominin(target)) {
            return;
        }
        if (dev.hominin.evolution.world.Oases.keepsTruce(mob)) {
            // It came to drink, and leaves hominins be.
            event.setCanceled(true);
            return;
        }
        if (mob.getLastHurtByMob() == target && mob.tickCount - mob.getLastHurtByMobTimestamp() < 200) {
            // It was struck: it answers whoever struck it.
            return;
        }
        int crowd = dev.hominin.evolution.band.Postures.overwhelms(target) ? CROWD - 1 : CROWD;
        if (company(target) < crowd) {
            return;
        }
        LivingEntity straggler = straggler(mob);
        if (straggler != null) {
            event.setNewAboutToBeSetTarget(straggler);
        } else {
            event.setCanceled(true);
        }
    }

    /** Whoever has wandered off on their own, nearest first. */
    @Nullable
    private static LivingEntity straggler(Mob mob) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(LOOKS_FOR_STRAGGLERS), e -> e.isAlive() && hominin(e))) {
            double d = candidate.distanceToSqr(mob);
            if (d < bestDistance && company(candidate) <= 1) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    /** Developer: the country may send something again at once. */
    public static void endLull(UUID player) {
        lullUntil.remove(player);
        arrivedAt.remove(player);
    }

    public static void forget(UUID player) {
        lullUntil.remove(player);
        arrivedAt.remove(player);
    }

    private PredatorLull() {
    }
}
