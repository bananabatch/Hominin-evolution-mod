package dev.hominin.evolution.client;

import dev.hominin.evolution.band.Trading;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Shows what an item is worth to another band, so trades can be planned. */
public final class TradeTierTooltip {
    public static void onTooltip(ItemTooltipEvent event) {
        int tier = Trading.tierOf(event.getItemStack());
        if (tier <= 0) {
            return;
        }
        String pips = "\u25CF".repeat(tier) + "\u25CB".repeat(Trading.MAX_TIER - tier);
        event.getToolTip().add(Component.literal("Trade: " + pips + " " + Trading.TIER_NAMES[tier])
                .withStyle(ChatFormatting.GOLD));
    }

    private TradeTierTooltip() {
    }
}
