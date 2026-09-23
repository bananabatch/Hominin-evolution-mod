package dev.hominin.evolution.inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * How much a hominin can carry. With nothing to carry things in, early hominins hold what
 * their hands and arms can: the hotbar. Each later stage opens another row of the
 * inventory - erectus two rows, heidelbergensis and sapiens three - and Neanderthals all
 * of it.
 *
 * <p>Locked slots cannot be clicked, filled or picked up into, and anything left in one
 * (after a change of stage, say) is moved into free room or dropped.
 */
public final class InventoryLimits {
    public static final int ROW = 9;
    public static final int MAIN_SLOTS = 36;

    /** What the client has been told about each player's stage. Filled on the client only. */
    private static final Map<UUID, ResourceLocation> CLIENT_STAGES = new ConcurrentHashMap<>();

    public static void rememberClientStage(UUID player, ResourceLocation stage) {
        CLIENT_STAGES.put(player, stage);
    }

    /** Rows of the main inventory open at a stage, hotbar included. */
    public static int rowsFor(@Nullable ResourceLocation stage) {
        if (stage == null) {
            return 4;
        }
        return switch (stage.getPath()) {
            case "ardipithecus", "australopithecus", "homo_habilis" -> 1;
            case "homo_erectus" -> 2;
            case "homo_heidelbergensis", "homo_sapiens" -> 3;
            default -> 4;
        };
    }

    /** How many of the 36 main inventory slots this player may use. Creative players get them all. */
    public static int unlockedSlots(Player player) {
        if (player.getAbilities().instabuild || player.isSpectator()) {
            return MAIN_SLOTS;
        }
        ResourceLocation stage = player.level().isClientSide()
                ? CLIENT_STAGES.get(player.getUUID())
                : player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        return rowsFor(stage) * ROW;
    }

    public static boolean isLocked(Player player, int index) {
        return index >= ROW && index < MAIN_SLOTS && index >= unlockedSlots(player);
    }

    /**
     * Nothing may stay in a locked slot. This ran once a second, which left a whole
     * second in which something could sit in a row you should not have - long enough to
     * see it, and long enough to use it. The sweep is cheap (it walks at most the closed
     * rows, and returns at once when none are closed), so it runs every tick instead.
     */
    public static void tick(ServerPlayer player) {
        int unlocked = unlockedSlots(player);
        if (unlocked >= MAIN_SLOTS) {
            return;
        }
        Inventory inventory = player.getInventory();
        boolean dropped = false;
        for (int index = unlocked; index < MAIN_SLOTS; index++) {
            ItemStack stack = inventory.items.get(index);
            if (stack.isEmpty()) {
                continue;
            }
            inventory.items.set(index, ItemStack.EMPTY);
            // add() only looks at open slots now, so this moves it into room if there is any.
            inventory.add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
                dropped = true;
            }
        }
        if (dropped) {
            player.sendSystemMessage(Component.literal("Your arms are full - you can only carry " + unlocked
                    + " things, and let the rest fall.").withStyle(ChatFormatting.GRAY));
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.ARMS_FULL);
        }
    }

    private InventoryLimits() {
    }
}
