package dev.hominin.evolution.stage;

import java.util.Map;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.band.Band;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The erectus checklist items that are about where you are and who is with you, rather than
 * anything you do in one moment: ranging far from where you began, and a band big enough to
 * be a people.
 */
public final class ErectusGoals {
    /** Out of Africa, in miniature. */
    private static final int RANGE_BLOCKS = 1000;
    private static final int BIG_BAND = 14;
    private static final String ORIGIN_X = "erectus_origin_x";
    private static final String ORIGIN_Z = "erectus_origin_z";

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 100 != 40 || player.isSpectator()) {
            return;
        }
        var data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        String era = data.getStage().getPath();
        if (!era.equals("homo_erectus") && !era.equals("homo_ergaster")) {
            return;
        }
        Map<String, Integer> counters = data.getCriterionCounters();
        // Counters are cleared on evolving, so this is wherever you first stood as erectus.
        counters.putIfAbsent(ORIGIN_X, player.getBlockX());
        counters.putIfAbsent(ORIGIN_Z, player.getBlockZ());
        if (counters.getOrDefault("range_far", 0) == 0) {
            double dx = player.getBlockX() - counters.get(ORIGIN_X);
            double dz = player.getBlockZ() - counters.get(ORIGIN_Z);
            if (dx * dx + dz * dz >= (double) RANGE_BLOCKS * RANGE_BLOCKS) {
                EvolutionManager.incrementCriterion(player, "range_far", 1);
                player.sendSystemMessage(Component.literal("A thousand blocks from where your people began. "
                        + "Nothing before you ever walked this far.").withStyle(ChatFormatting.GOLD));
            }
        }
        if (counters.getOrDefault("big_band", 0) == 0 && Band.all(player).size() >= BIG_BAND) {
            EvolutionManager.incrementCriterion(player, "big_band", 1);
        }
    }

    private ErectusGoals() {
    }
}
