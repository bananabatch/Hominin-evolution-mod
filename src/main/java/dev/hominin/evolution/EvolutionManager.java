package dev.hominin.evolution;

import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.stage.GateCriterion;
import dev.hominin.evolution.stage.MilestoneHandlers;
import dev.hominin.evolution.stage.StageDefinition;
import dev.hominin.evolution.stage.StageRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class EvolutionManager {
    private EvolutionManager() {
    }

    public static void incrementCriterion(ServerPlayer player, String criterionId, int amount) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.get(data.getStage());
        if (stage == null) {
            return;
        }
        data.getCriterionCounters().merge(criterionId, amount, Integer::sum);
        checkReadiness(player, data, stage);
    }

    public static void forceSatisfyCriterion(ServerPlayer player, String criterionId) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.get(data.getStage());
        if (stage == null) {
            return;
        }
        data.getCriterionCounters().put(criterionId, Integer.MAX_VALUE);
        checkReadiness(player, data, stage);
    }

    public static boolean isCriterionSatisfied(PlayerEvolutionData data, GateCriterion criterion) {
        return data.getCriterionCounters().getOrDefault(criterion.id(), 0) >= criterion.requiredCount();
    }

    public static boolean isRequiredSatisfied(PlayerEvolutionData data, StageDefinition stage) {
        return isCriterionSatisfied(data, stage.gate().required());
    }

    public static int optionalSatisfiedCount(PlayerEvolutionData data, StageDefinition stage) {
        return (int) stage.gate().optionalPool().stream().filter(c -> isCriterionSatisfied(data, c)).count();
    }

    public static boolean isGateReady(PlayerEvolutionData data, StageDefinition stage) {
        return isRequiredSatisfied(data, stage) && optionalSatisfiedCount(data, stage) >= stage.gate().chooseCount();
    }

    private static void checkReadiness(ServerPlayer player, PlayerEvolutionData data, StageDefinition stage) {
        if (isGateReady(data, stage) && data.getNotifiedReadyStages().add(data.getStage())) {
            player.sendSystemMessage(Component.literal(
                    "You are ready to evolve — perform your milestone act: " + stage.milestone().description()));
        }
    }

    public static boolean attemptMilestone(ServerPlayer player, ResourceLocation milestoneType) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.get(data.getStage());
        if (stage == null || !stage.milestone().type().equals(milestoneType) || stage.nextStage().isEmpty()) {
            return false;
        }
        if (!isGateReady(data, stage)) {
            player.sendSystemMessage(Component.literal("You are not ready to evolve yet."));
            return false;
        }
        MilestoneHandlers.get(milestoneType).ifPresent(handler -> handler.accept(player, stage));
        evolve(player, stage.nextStage().get());
        return true;
    }

    public static void evolve(ServerPlayer player, ResourceLocation nextStageId) {
        StageDefinition nextStage = StageRegistry.get(nextStageId);
        if (nextStage == null) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        data.setStage(nextStageId);
        data.getCriterionCounters().clear();
        data.getNotifiedReadyStages().clear();
        announceEvolution(player, nextStage);
    }

    private static void announceEvolution(ServerPlayer player, StageDefinition nextStage) {
        MutableComponent announcement = Component.literal(
                player.getGameProfile().getName() + " has evolved into " + nextStage.displayName() + "!");
        player.getServer().getPlayerList().broadcastSystemMessage(announcement, false);

        if (!nextStage.behaviorsGained().isEmpty()) {
            player.sendSystemMessage(Component.literal("Gained: " + String.join(", ", nextStage.behaviorsGained())));
        }
        if (!nextStage.behaviorsLost().isEmpty()) {
            player.sendSystemMessage(Component.literal("Lost: " + String.join(", ", nextStage.behaviorsLost())));
        }
    }
}
