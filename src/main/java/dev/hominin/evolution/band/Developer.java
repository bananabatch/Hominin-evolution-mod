package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.entity.Baboon;
import dev.hominin.evolution.entity.TroopRelations;
import dev.hominin.evolution.mind.Skills;
import dev.hominin.evolution.survival.Afflictions;
import dev.hominin.evolution.survival.Infestation;
import dev.hominin.evolution.survival.Thirst;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Developer tab under H: every number the mod keeps about you, set by hand.
 *
 * <p>For testing. Nothing here is reachable outside developer mode - the tab is hidden,
 * and every command checks again on the server in case something asks anyway.
 */
public final class Developer {
    private static final double RANGE = 16.0D;

    public static void run(ServerPlayer player, int entityId, Social.Command command) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (!data.isDeveloperMode()) {
            player.displayClientMessage(Component.literal("That is a developer tool. /hominin dev turns it on."),
                    true);
            return;
        }
        var counters = data.getCriterionCounters();
        String done = switch (command) {
            case DEV_BOND_UP -> bond(player, entityId, 5);
            case DEV_BOND_DOWN -> bond(player, entityId, -5);
            case DEV_COHESION -> {
                Cohesion.add(player, 10);
                yield "Band cohesion is now " + Cohesion.get(player) + "/" + Cohesion.MAX + ".";
            }
            case DEV_COHESION_DOWN -> {
                Cohesion.add(player, -10);
                yield "Band cohesion is now " + Cohesion.get(player) + "/" + Cohesion.MAX + ".";
            }
            case DEV_TROOP_TRUST -> troop(player, TroopRelations.TRUSTED);
            case DEV_TROOP_GRUDGE -> troop(player, -1);
            case DEV_TICKS_UP -> {
                Infestation.set(player, Infestation.of(player) + 3);
                yield "Ticks: " + Infestation.of(player) + ".";
            }
            case DEV_HEAL_CLEAR -> {
                Infestation.set(player, 0);
                Afflictions.clear(player);
                yield "Ticks and afflictions cleared.";
            }
            case DEV_WATER -> {
                Thirst.set(player, Thirst.MAX);
                yield "Water filled.";
            }
            case DEV_SKILLS_ALL -> {
                for (Skills.Skill skill : Skills.Skill.values()) {
                    Skills.set(player, skill, true);
                }
                yield "You know every skill.";
            }
            case DEV_SKILLS_NONE -> {
                for (Skills.Skill skill : Skills.Skill.values()) {
                    Skills.set(player, skill, false);
                }
                yield "You have forgotten every skill.";
            }
            case DEV_TRAINING -> {
                counters.put("play_tag", BandMember.MAX_TRAINING);
                counters.put("play_wrestle", BandMember.MAX_TRAINING);
                yield "Play training at its maximum.";
            }
            case DEV_TEACH_BAND -> {
                List<BandMember> band = Band.ownNear(player, RANGE);
                for (BandMember member : band) {
                    for (Skills.Skill skill : Skills.Skill.values()) {
                        member.learnSkill(skill);
                    }
                }
                yield band.size() + " band members now know every skill.";
            }
            default -> "";
        };
        if (!done.isEmpty()) {
            player.sendSystemMessage(Component.literal("[dev] " + done).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Bond with whoever you picked out, or everybody in earshot. */
    private static String bond(ServerPlayer player, int entityId, int change) {
        List<BandMember> members = new ArrayList<>();
        if (entityId >= 0 && player.level().getEntity(entityId) instanceof BandMember member) {
            members.add(member);
        } else {
            members.addAll(Band.ownNear(player, RANGE));
        }
        if (members.isEmpty()) {
            return "Nobody of yours is near enough.";
        }
        for (BandMember member : members) {
            member.addBond(change);
        }
        BandMember first = members.get(0);
        first.ensureName();
        return members.size() == 1 ? first.getName().getString() + "'s bond is now " + first.getBond() + "."
                : "Bond " + (change > 0 ? "+" : "") + change + " for " + members.size() + " members.";
    }

    /** Whatever troop is nearest: set how it feels about you. */
    private static String troop(ServerPlayer player, int trust) {
        Baboon nearest = null;
        for (Baboon baboon : player.level().getEntitiesOfClass(Baboon.class,
                player.getBoundingBox().inflate(48.0D), b -> b.getTroop() != null)) {
            if (nearest == null || baboon.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = baboon;
            }
        }
        if (nearest == null) {
            return "No baboon troop within 48 blocks.";
        }
        TroopRelations.setTrustFor(player, nearest.getTroop(), trust);
        return trust < 0 ? "The nearest troop now holds a grudge." : "The nearest troop now trusts you.";
    }

    private Developer() {
    }
}
