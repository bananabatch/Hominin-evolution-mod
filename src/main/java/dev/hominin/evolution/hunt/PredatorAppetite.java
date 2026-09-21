package dev.hominin.evolution.hunt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.combat.Scare;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A predator is not a monster. It came for food, and once it has food it goes.
 *
 * <p>So it is deadly while it is here - it will take one of the band, maybe two - and
 * then it leaves with what it came for rather than fighting to the last hominin. And a
 * predator that gets its skull cracked by a club does not stay to find out what the next
 * blow does: it runs, and the band, which has more sense than to chase it, sees it off
 * with a display.
 */
public final class PredatorAppetite {
    /** How many of the band a predator takes before it is satisfied. */
    private static final int APPETITE = 2;
    /** How many each predator has taken so far. Held until the animal itself dies. */
    private static final Map<UUID, Integer> taken = new HashMap<>();
    /** Long enough that it is gone for good, as far as this fight is concerned. */
    private static final int LEAVE_TICKS = 1800;
    /** Predators already on their way out, so one departure is not announced twice. */
    private static final Map<UUID, Long> leaving = new HashMap<>();

    public static boolean isPredator(LivingEntity entity) {
        return entity.getType().is(ModTags.EntityTypes.PREDATORS);
    }

    /**
     * A band member killed by a predator. Two, and it has what it came for.
     *
     * <p>The count is deliberately never cleared while the animal lives. It used to be
     * wiped the moment the predator was satisfied, so an animal that then picked up a
     * new target started again from nothing and could work through an entire band. And
     * the departure is re-asserted on every kill past the second rather than latched
     * once, because a fright that failed to take used to leave it hunting forever with
     * nothing left to re-trigger it.
     */
    public static void onBandMemberKilled(BandMember dead, LivingEntity killer) {
        if (!(killer instanceof Mob predator) || !isPredator(predator)) {
            return;
        }
        UUID id = predator.getUUID();
        int count = taken.merge(id, 1, Integer::sum);
        if (count < APPETITE) {
            return;
        }
        boolean firstTime = !alreadyLeaving(predator, id);
        leave(predator, dead.position());
        Player leader = dead.leaderPlayer();
        if (leader != null && firstTime) {
            leader.sendSystemMessage(Component.literal("The " + predator.getName().getString()
                    + " has what it came for, and goes.").withStyle(ChatFormatting.DARK_RED));
        }
    }

    /**
     * A club has cracked its skull. It runs - and the band lets it. Chasing a wounded big
     * cat into the grass is how bands lose people; a display at its back costs nothing.
     */
    public static void drivenOff(Mob predator, LivingEntity striker) {
        if (alreadyLeaving(predator, predator.getUUID())) {
            return;
        }
        leave(predator, striker.position());
        predator.setTarget(null);
        BandMember caller = null;
        for (BandMember member : Band.near(predator, 24.0D)) {
            if (member.getTarget() == predator) {
                member.setTarget(null);
            }
            if (caller == null && !member.isBaby()
                    && (striker instanceof Player player ? member.isCompanionOf(player) : member.isAlliedTo(striker))) {
                caller = member;
            }
        }
        if (caller != null) {
            Band.memberDisplay(caller, 0);
        }
        if (striker instanceof Player player) {
            player.displayClientMessage(Component.literal(
                    "It reels off into the grass. The band sees it off with a roar and lets it go."), true);
        }
    }

    /**
     * Whether it is already walking away. Checked before anything else so that a
     * departure is never run - or announced - twice for the same animal.
     */
    private static boolean alreadyLeaving(Mob predator, UUID id) {
        Long until = leaving.get(id);
        return until != null && predator.level().getGameTime() < until;
    }

    /**
     * Sending it away for good. This goes through {@code depart} rather than a fright,
     * because a fearless animal is not being frightened - it is finished here. A
     * sabertooth that ignores every threat display still leaves when it has eaten.
     */
    private static void leave(Mob predator, Vec3 from) {
        leaving.put(predator.getUUID(), predator.level().getGameTime() + LEAVE_TICKS);
        if (predator instanceof PathfinderMob walker) {
            Scare.depart(walker, from, LEAVE_TICKS);
            return;
        }
        predator.setTarget(null);
        predator.setAggressive(false);
        predator.getNavigation().stop();
    }

    public static void forget(UUID predator) {
        taken.remove(predator);
        leaving.remove(predator);
    }

    private PredatorAppetite() {
    }
}
