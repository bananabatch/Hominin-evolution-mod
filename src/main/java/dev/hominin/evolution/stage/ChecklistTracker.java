package dev.hominin.evolution.stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.network.ChecklistPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Keeps each player's on-screen evolution checklist current.
 *
 * <p>Rebuilt on a slow tick and only sent when the text actually changes, so a
 * player standing still costs one list comparison every second and no packets.
 */
public final class ChecklistTracker {
    /** Markers the client reads to decide how to draw a line. */
    private static final String DONE = "+";
    private static final String TODO = "-";
    private static final String READY = "!";

    private static final Map<UUID, List<String>> lastSent = new HashMap<>();

    /** Players who have turned the overlay off with {@code /hominin checklist}. */
    private static final Set<UUID> hidden = new HashSet<>();

    /** A fresh join has the list resent, whatever the last world left on screen. */
    public static void onPlayerLoggedIn(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lastSent.remove(player.getUUID());
        }
    }

    public static void refresh(ServerPlayer player) {
        if (hidden.contains(player.getUUID())) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.get(data.getStage());
        if (stage == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        GateDefinition gate = stage.gate();
        if (!stage.warning().isEmpty()) {
            lines.add(READY + stage.warning());
        }

        for (GateCriterion required : gate.required()) {
            lines.add(line(data, required, ""));
        }

        int satisfied = EvolutionManager.optionalSatisfiedCount(data, stage);
        int needed = gate.chooseCount();
        if (!gate.optionalPool().isEmpty()) {
            lines.add((satisfied >= needed ? DONE : TODO)
                    + "Any " + needed + " of these (" + Math.min(satisfied, needed) + "/" + needed + "):");
        }
        for (GateCriterion criterion : gate.optionalPool()) {
            lines.add(line(data, criterion, "  "));
        }
        if (EvolutionManager.isGateReady(data, stage)) {
            lines.add(READY + stage.milestone().description());
        }
        List<String> previous = lastSent.get(player.getUUID());
        if (lines.equals(previous)) {
            return;
        }
        lastSent.put(player.getUUID(), lines);
        String age = StageAge.ago(stage.yearsAgo());
        String title = age.isEmpty() ? stage.displayName() : stage.displayName() + "  " + age;
        PacketDistributor.sendToPlayer(player, new ChecklistPayload(title, lines));
    }

    /**
     * Turns the overlay on or off for one player. Returns true if it is now shown.
     * Hiding sends an empty list so the client stops drawing immediately rather
     * than keeping the last state it was given.
     */
    public static boolean toggle(ServerPlayer player) {
        UUID id = player.getUUID();
        lastSent.remove(id);
        if (hidden.remove(id)) {
            refresh(player);
            return true;
        }
        hidden.add(id);
        PacketDistributor.sendToPlayer(player, new ChecklistPayload("", List.of()));
        return false;
    }

    /** The leading character is the client's line marker, never shown as text. */
    private static String line(PlayerEvolutionData data, GateCriterion criterion, String prefix) {
        boolean done = EvolutionManager.isCriterionSatisfied(data, criterion);
        int have = Math.min(data.getCriterionCounters().getOrDefault(criterion.id(), 0), criterion.requiredCount());
        String progress = criterion.requiredCount() > 1 ? " (" + have + "/" + criterion.requiredCount() + ")" : "";
        return (done ? DONE : TODO) + prefix + criterion.description() + progress;
    }

    public static void forget(ServerPlayer player) {
        lastSent.remove(player.getUUID());
    }

    private ChecklistTracker() {
    }
}
