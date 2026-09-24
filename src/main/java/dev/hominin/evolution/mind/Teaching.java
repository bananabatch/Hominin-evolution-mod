package dev.hominin.evolution.mind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Passing it on.
 *
 * <p>Nobody learns a skill by standing near somebody who has it. You have to be shown -
 * so teaching is choosing what to teach, getting their attention, and then actually doing
 * it while they watch. A skill that cannot be shown - how to make yourself small in front
 * of a troop, what thinking about time is like - has to be put into words instead, which
 * works, but not on everyone.
 *
 * <p>What a band learns, its children learn from them as they grow. That is the whole of
 * culture, in the only form it could take before anything was written down.
 */
public final class Teaching {
    public static final int ACTION_TEACH = 1;
    /** How long they keep watching for you to show them. */
    private static final int WATCH_TICKS = 30 * 20;
    private static final double RANGE = 16.0D;
    /** How often words alone get it across. */
    private static final float TOLD_CHANCE = 0.6F;

    /** Things that can only be told, not shown. */
    private static final Set<Skills.Skill> TOLD = EnumSet.of(Skills.Skill.DEESCALATION, Skills.Skill.LONG_VIEW);

    private record Lesson(Skills.Skill skill, List<UUID> watchers, long until) {
    }

    private static final Map<UUID, Lesson> lessons = new HashMap<>();

    /** The list of what you could teach them. */
    public static void open(ServerPlayer player, int entityId) {
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (Skills.Skill skill : Skills.Skill.values()) {
            if (Skills.knows(player, skill)) {
                labels.add(skill.title() + (TOLD.contains(skill) ? "  (tell)" : "  (show)"));
                values.add(skill.ordinal());
            }
        }
        if (labels.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "You have nothing to teach yet. What you learn shows up under J."), true);
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new ChoicesPayload(entityId, ACTION_TEACH, "Teach them...", labels, values));
    }

    /** They picked something to teach. Now get the band to watch, or tell them. */
    public static void choose(ServerPlayer player, int entityId, int ordinal) {
        if (ordinal < 0 || ordinal >= Skills.Skill.values().length) {
            return;
        }
        Skills.Skill skill = Skills.Skill.values()[ordinal];
        if (!Skills.knows(player, skill)) {
            return;
        }
        List<BandMember> pupils = new ArrayList<>();
        if (entityId >= 0) {
            if (player.level().getEntity(entityId) instanceof BandMember member && member.isLedBy(player)
                    && member.distanceToSqr(player) < RANGE * RANGE) {
                pupils.add(member);
            }
        } else {
            for (BandMember member : Band.ownNear(player, RANGE)) {
                if (!member.isBaby()) {
                    pupils.add(member);
                }
            }
        }
        pupils.removeIf(member -> member.knowsSkill(skill));
        if (pupils.isEmpty()) {
            player.displayClientMessage(Component.literal("Everyone here already knows that."), true);
            return;
        }
        if (TOLD.contains(skill)) {
            tell(player, skill, pupils);
            return;
        }
        List<UUID> watchers = new ArrayList<>();
        for (BandMember member : pupils) {
            watchers.add(member.getUUID());
            member.attendTo(player, WATCH_TICKS);
        }
        lessons.put(player.getUUID(), new Lesson(skill, watchers, player.level().getGameTime() + WATCH_TICKS));
        player.sendSystemMessage(Component.literal(names(pupils) + (pupils.size() == 1 ? " watches" : " watch")
                + " you closely. Show them now: " + skill.howTo()).withStyle(ChatFormatting.AQUA));
    }

    /** Words: it works, just not for everyone. */
    private static void tell(ServerPlayer player, Skills.Skill skill, List<BandMember> pupils) {
        List<BandMember> understood = new ArrayList<>();
        List<BandMember> cannot = new ArrayList<>();
        for (BandMember member : pupils) {
            if (!member.canLearnSkill(skill)) {
                cannot.add(member);
                continue;
            }
            if (member.getRandom().nextFloat() < TOLD_CHANCE) {
                member.learnSkill(skill);
                understood.add(member);
            }
        }
        player.sendSystemMessage(Component.literal(understood.isEmpty()
                ? "You try to put " + skill.title().toLowerCase() + " into sounds and gestures. Nobody quite gets it. Try again another time."
                : "You explain " + skill.title().toLowerCase() + " as well as you can. " + names(understood)
                        + (understood.size() == 1 ? " understands." : " understand."))
                .withStyle(ChatFormatting.AQUA));
        notInThem(player, skill, cannot);
    }

    /** Some kinds never had it in them: they watch, and nothing takes. */
    private static void notInThem(ServerPlayer player, Skills.Skill skill, List<BandMember> cannot) {
        if (!cannot.isEmpty()) {
            player.sendSystemMessage(Component.literal(names(cannot) + (cannot.size() == 1 ? " watches" : " watch")
                    + ", but " + skill.title().toLowerCase() + " does not take - it is not in their kind.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Called every time the player does something that is a skill. If somebody asked to
     * be shown this and is still watching, now they have seen it.
     */
    static void demonstrated(ServerPlayer player, Skills.Skill skill) {
        Lesson lesson = lessons.get(player.getUUID());
        if (lesson == null || lesson.skill() != skill) {
            return;
        }
        lessons.remove(player.getUUID());
        if (player.level().getGameTime() > lesson.until()) {
            return;
        }
        List<BandMember> learned = new ArrayList<>();
        List<BandMember> cannot = new ArrayList<>();
        for (UUID id : lesson.watchers()) {
            if (player.serverLevel().getEntity(id) instanceof BandMember member && member.isAlive()
                    && member.distanceToSqr(player) < RANGE * RANGE) {
                if (!member.canLearnSkill(skill)) {
                    cannot.add(member);
                    continue;
                }
                member.learnSkill(skill);
                learned.add(member);
            }
        }
        notInThem(player, skill, cannot);
        if (!learned.isEmpty()) {
            player.sendSystemMessage(Component.literal(names(learned) + (learned.size() == 1 ? " has" : " have")
                    + " learned " + skill.title().toLowerCase() + " from you.").withStyle(ChatFormatting.AQUA));
        }
    }

    private static String names(List<BandMember> members) {
        List<String> names = new ArrayList<>();
        for (BandMember member : members) {
            member.ensureName();
            names.add(member.getName().getString());
        }
        if (names.size() > 3) {
            return names.get(0) + ", " + names.get(1) + " and " + (names.size() - 2) + " others";
        }
        return String.join(names.size() == 2 ? " and " : ", ", names);
    }

    public static void forget(UUID player) {
        lessons.remove(player);
    }

    private Teaching() {
    }
}
