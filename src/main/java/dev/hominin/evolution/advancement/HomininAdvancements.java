package dev.hominin.evolution.advancement;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.stage.StageDefinition;
import dev.hominin.evolution.stage.StageRegistry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Advancements the mod grants by hand.
 *
 * <p>Each of these uses a {@code minecraft:impossible} criterion, so nothing in vanilla
 * can ever trigger it and the only way in is through here. Stage advancements are
 * named after the stage - {@code hominin/<stage path>} - so adding a stage JSON and
 * an advancement file with the matching name is all a new stage needs.
 */
public final class HomininAdvancements {
    public static final String LUCY = "hominin/lucy";

    /** Grants every remaining criterion of one advancement. Unknown ids are ignored. */
    public static void award(ServerPlayer player, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, path);
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    /**
     * The player's current stage and every stage before it. Earlier ones are included
     * so a player moved forward by command, or one from a save that predates these
     * advancements, still ends up with the whole line.
     */
    public static void awardStages(ServerPlayer player) {
        ResourceLocation stage = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        // Bounded, in case a broken datapack makes the stages loop back on themselves.
        for (int i = 0; stage != null && i < 16; i++) {
            award(player, "hominin/" + stage.getPath());
            stage = previousStage(stage);
        }
    }

    private static ResourceLocation previousStage(ResourceLocation stage) {
        for (var entry : StageRegistry.all().entrySet()) {
            StageDefinition definition = entry.getValue();
            if (definition.nextStage().isPresent() && definition.nextStage().get().equals(stage)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            awardStages(player);
        }
    }

    private HomininAdvancements() {
    }
}
