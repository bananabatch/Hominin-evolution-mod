package dev.hominin.evolution;

import dev.hominin.evolution.advancement.HomininAdvancements;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.stage.GateCriterion;
import dev.hominin.evolution.stage.MilestoneHandlers;
import dev.hominin.evolution.stage.Arrival;
import dev.hominin.evolution.stage.StageDefinition;
import dev.hominin.evolution.stage.StageRegistry;
import dev.hominin.evolution.stage.StageSync;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class EvolutionManager {
    private EvolutionManager() {
    }

    /**
     * Counters under this prefix are skills, not stage criteria: they measure what
     * the hands have learned, so they survive evolving into the next stage while
     * everything else is wiped.
     */
    public static final String SKILL_PREFIX = "skill_";

    public static void incrementCriterion(ServerPlayer player, String criterionId, int amount) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.current(data);
        if (stage == null) {
            return;
        }
        data.getCriterionCounters().merge(criterionId, amount, Integer::sum);
        checkReadiness(player, data, stage);
    }

    public static void forceSatisfyCriterion(ServerPlayer player, String criterionId) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.current(data);
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
        return stage.gate().required().stream().allMatch(criterion -> isCriterionSatisfied(data, criterion));
    }

    public static int optionalSatisfiedCount(PlayerEvolutionData data, StageDefinition stage) {
        return (int) stage.gate().optionalPool().stream().filter(c -> isCriterionSatisfied(data, c)).count();
    }

    public static boolean isGateReady(PlayerEvolutionData data, StageDefinition stage) {
        if (data.isDeveloperMode()) {
            return true;
        }
        return isRequiredSatisfied(data, stage) && optionalSatisfiedCount(data, stage) >= stage.gate().chooseCount();
    }

    private static void checkReadiness(ServerPlayer player, PlayerEvolutionData data, StageDefinition stage) {
        if (isGateReady(data, stage) && data.getNotifiedReadyStages().add(data.getStage())) {
            player.sendSystemMessage(Component.literal(
                    "You are ready to evolve — perform your milestone act: " + stage.milestone().description()));
            dev.hominin.evolution.guide.Tips.readyToEvolve(player, stage.milestone().type());
            // Ready to become something new: a thing worth a feast.
            dev.hominin.evolution.band.Feast.event(player, "all your people have done");
        }
    }

    /**
     * Whether performing this milestone act right now would actually evolve the player.
     * Lets a caller offer an ordinary use of an interaction instead of nagging about
     * a gate that is not open yet.
     */
    public static boolean isReadyForMilestone(ServerPlayer player, ResourceLocation milestoneType) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.current(data);
        return stage != null
                && stage.milestone().type().equals(milestoneType)
                && stage.nextStage().isPresent()
                && isGateReady(data, stage);
    }

    public static boolean attemptMilestone(ServerPlayer player, ResourceLocation milestoneType) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.current(data);
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
        // Extra effort: you do not get to skip the species that stands behind the one you
        // are reaching for. Only on the way up, and never when you are already it.
        if (player.serverLevel().getGameRules().getBoolean(dev.hominin.evolution.ModGameRules.EXTRA_EFFORT)) {
            ResourceLocation detour = dev.hominin.evolution.stage.Fallbacks.detourTo(nextStageId);
            if (detour != null && !detour.equals(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage())
                    && StageRegistry.get(detour) != null) {
                nextStageId = detour;
            }
        }
        // With others in the world, everyone gets fifteen seconds to choose first.
        if (dev.hominin.evolution.stage.Intermission.start(player, nextStageId)) {
            return;
        }
        become(player, nextStageId);
    }

    /**
     * Turns the player into this stage the whole way: the stage itself, the announcement, the
     * old band left behind, and - from any earlier stage - the cutscene, the move, the fresh
     * inventory and a new band. Evolving goes through here; so does {@code /hominin become}.
     */
    public static void become(ServerPlayer player, ResourceLocation nextStageId) {
        StageDefinition nextStage = StageRegistry.get(nextStageId);
        if (nextStage == null) {
            return;
        }
        // Where the line splits, nothing changes until a path is chosen.
        if (dev.hominin.evolution.stage.Lineage.holdFor(player, nextStageId)) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        ResourceLocation previousStageId = data.getStage();
        StageDefinition previousStage = StageRegistry.get(previousStageId);
        data.setStage(nextStageId);
        data.getCriterionCounters().keySet().removeIf(key -> !key.startsWith(SKILL_PREFIX));
        // An example set at a haven was the old kind's: the havens are open to be taken again.
        dev.hominin.evolution.world.Havens.newSpecies(player);
        dev.hominin.evolution.survival.Afflictions.relieve(player, dev.hominin.evolution.survival.Afflictions.Affliction.LACERATED);
        dev.hominin.evolution.combat.Bleeding.forget(player.getUUID());
        data.getNotifiedReadyStages().clear();
        // Distance credit is per-stage, so the new stage starts measuring from here.
        // Without this reset, ground covered as Australopithecus would immediately
        // satisfy habilis criteria the player never actually went looking for.
        data.setStageStartWalkDistance(player.walkDist);
        data.setDistanceCredits(0);
        // Said goodbye to while the old band is still standing: one name crosses with you,
        // under the species they actually walked as rather than the one you are now.
        dev.hominin.evolution.band.Remembrance.keep(player,
                previousStage == null ? null : previousStage.displayName());
        announceEvolution(player, nextStage);
        // A new body, new hands: erectus on, what you start able to do is rolled, and told once the
        // new band is round you.
        // A new body, and a new mind to hold places in.
        dev.hominin.evolution.mind.MentalMap.newBody(player);
        if (dev.hominin.evolution.hunt.Persistence.rollsSkills(nextStageId)) {
            dev.hominin.evolution.hunt.Persistence.rollFor(player);
        }
        StageSync.sync(player);
        HomininAdvancements.awardStages(player);
        Band.evolveWith(player, nextStageId);
        dev.hominin.evolution.band.WildBands.cullExtinct(player);
        if (previousStage != null) {
            // A long time later: every band you knew is gone, and nothing you held in mind is where it was.
            dev.hominin.evolution.band.Bands.newEra(player);
            // The places the band knew are still there, and the band still knows them.
            dev.hominin.evolution.world.Pois.passDown(player);
            // The band's tool piles are from before now: nobody's, and aged into artifacts.
            dev.hominin.evolution.band.ToolPiles.passDown(player);
            // And what they built has fallen down.
            dev.hominin.evolution.build.Building.wipeOld(player);
            // A new age: day one, the start of the rains - and a body that has eaten and drunk.
            dev.hominin.evolution.survival.Era.begin(player.serverLevel());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5.0F);
            dev.hominin.evolution.survival.Thirst.set(player, dev.hominin.evolution.survival.Thirst.MAX);
            player.getData(Attachments.MIND).wipe();
            player.getData(Attachments.MIND).setBodyName("");
            dev.hominin.evolution.mind.MentalMap.sync(player);
        }
        if (previousStage != null && Band.ARDIPITHECUS.equals(previousStageId)) {
            HomininAdvancements.award(player, "hominin/survivor");
        }
        if (previousStage == null) {
            Band.formNewBand(player);
        }
        if (previousStage != null) {
            Arrival.begin(player, previousStage, nextStage);
        }
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
