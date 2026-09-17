package dev.hominin.evolution.tool;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Putting an edge back on a worn stone tool against a grinding stone.
 *
 * <p>A stone tool that dulls is not finished - it can be reworked, and a rough flat
 * stone is how. That changes what a good tool is worth: it is something you keep and
 * maintain, not something you use up and replace.
 */
public final class Grinding {
    /** Durability one pass restores - about a sixth of a chopper. */
    private static final int REPAIR_PER_PASS = 16;

    /**
     * Grinds the tool in the main hand if a grinding stone is in the off hand. Returns
     * true if the hands were set up for it, so the caller stops looking elsewhere.
     */
    public static boolean tryGrind(ServerPlayer player, ItemStack tool, ItemStack stone) {
        if (!stone.is(ModItems.GRINDING_ROCK.get()) || !tool.is(ModTags.Items.STONE_TOOLS)) {
            return false;
        }
        if (!tool.isDamageableItem()) {
            player.displayClientMessage(Component.literal(
                    "There is nothing on this to wear down, so nothing to grind back."), true);
            return true;
        }
        if (!tool.isDamaged()) {
            player.displayClientMessage(Component.literal("The edge is as keen as it will get."), true);
            return true;
        }
        tool.setDamageValue(Math.max(0, tool.getDamageValue() - REPAIR_PER_PASS));
        ToolUse.wear(player, InteractionHand.OFF_HAND);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE,
                SoundSource.PLAYERS, 0.6F, 1.2F);
        player.displayClientMessage(Component.literal(tool.isDamaged()
                ? "You grind the edge back against the stone."
                : "You grind the edge back against the stone. It is keen again."), true);
        return true;
    }

    private Grinding() {
    }
}
