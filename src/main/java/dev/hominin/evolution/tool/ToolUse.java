package dev.hominin.evolution.tool;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.data.PlayerEvolutionData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The shared rules for using a stone tool, wherever it is used.
 *
 * <p>Tools are asked for by role - a {@code #hammerstones} item, a {@code #choppers}
 * item - so a multi tool can stand in for any of them. And every use wears it the
 * same way: plain stones last forever, anything with durability loses a point.
 * Keeping both here means the knapping screen, two-handed work and deposits cannot
 * drift apart on what counts as a hammer or how fast it breaks.
 */
public final class ToolUse {
    /** Which hand holds something in this role, main hand first; null if neither. */
    @Nullable
    public static InteractionHand handWith(Player player, TagKey<Item> role) {
        if (player.getMainHandItem().is(role)) {
            return InteractionHand.MAIN_HAND;
        }
        return player.getOffhandItem().is(role) ? InteractionHand.OFF_HAND : null;
    }

    /** One use of whatever is in this hand. A hammerstone has no durability and is untouched. */
    public static void wear(ServerPlayer player, InteractionHand hand) {
        ItemStack tool = player.getItemInHand(hand);
        if (tool.isDamageableItem()) {
            tool.hurtAndBreak(1, player,
                    hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
    }

    /**
     * Counts toward "3 different Oldowan tools". Types, not copies - three flakes
     * are one tool - and it has to be called wherever a tool is made, because most
     * of them are no longer made on a crafting grid.
     */
    public static void creditOldowanTool(ServerPlayer player, Item made) {
        if (!isOldowanTool(made)) {
            return;
        }
        dev.hominin.evolution.band.Paranthropus.watched(player);
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.getCraftedOldowanTools().add(BuiltInRegistries.ITEM.getKey(made))) {
            EvolutionManager.incrementCriterion(player, "craft_oldowan_tools", 1);
        }
    }

    /**
     * Resolved per call rather than cached in a static set: this class can be
     * loaded before the item registry is filled.
     */
    /**
     * The Oldowan kit. A hammerstone counted here but nothing ever credited one, because
     * you do not craft a hammerstone - you pick it out of a seam - and the grinding stone
     * was missing outright. Between them the "three different tools" gate could be met
     * and still not register, which looked like the criterion was broken.
     */
    private static boolean isOldowanTool(Item item) {
        return item == ModItems.FLAKE.get()
                || item == ModItems.HAMMERSTONE.get()
                || item == ModItems.CHERT_HAMMERSTONE.get()
                || item == ModItems.CHOPPER.get()
                || item == ModItems.GRINDING_ROCK.get()
                || item == ModItems.DIGGING_STICK.get()
                || item == ModItems.OLDOWAN_MULTITOOL.get();
    }

    private ToolUse() {
    }
}
