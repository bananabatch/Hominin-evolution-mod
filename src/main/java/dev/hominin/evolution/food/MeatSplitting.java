package dev.hominin.evolution.food;

import java.util.Optional;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.tool.ToolUse;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Cutting a piece of meat into portions with a flake.
 *
 * <p>A carcass is too much for one mouth and useless in one piece to anybody else.
 * Portioning is what makes sharing possible at all, and sharing is the thing the
 * band will eventually judge you on - so this exists now, ahead of the tribes that
 * will care about it.
 *
 * <p>Each chunk carries a quarter of whatever it was cut from, stored on the stack
 * itself, so one chunk item covers beef, venison and anything another mod adds.
 */
public final class MeatSplitting {
    private static final int CHUNKS = 4;

    /** A mouthful, not a meal - chunks go down twice as fast as the whole cut. */
    private static final float CHUNK_EAT_SECONDS = 0.8F;

    /**
     * Splits the meat in the main hand if a cutting edge is in the off hand. Returns
     * true if the hands were set up for it, so the caller stops looking elsewhere.
     */
    public static boolean trySplit(ServerPlayer player, ItemStack meat, ItemStack blade) {
        if (!meat.is(ModTags.Items.SPLITTABLE_MEAT) || !blade.is(ModTags.Items.FLAKES)) {
            return false;
        }
        FoodProperties whole = meat.get(DataComponents.FOOD);
        if (whole == null) {
            return false;
        }
        ItemStack chunks = new ItemStack(ModItems.MEAT_CHUNK.get(), CHUNKS);
        chunks.set(DataComponents.FOOD, portion(whole));
        // Named after its source, so a stack of chunks still says what it was.
        chunks.set(DataComponents.ITEM_NAME,
                Component.translatable("item.hominin_evolution.meat_chunk.of", meat.getHoverName()));
        // Cut up, meat that had turned is still meat that had turned.
        Spoilage.carry(meat, chunks);

        meat.shrink(1);
        ToolUse.wear(player, InteractionHand.OFF_HAND);
        if (!player.getInventory().add(chunks)) {
            player.drop(chunks, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHEEP_SHEAR,
                SoundSource.PLAYERS, 0.6F, 0.8F);
        player.displayClientMessage(Component.literal("You cut it into four - enough to go round."), true);
        return true;
    }

    /**
     * A quarter of the whole. Saturation divides exactly; hunger restored is a whole
     * number, so it rounds, and never to zero - a chunk that fed nobody would not be
     * worth sharing. That lets a small raw cut come out slightly ahead, which is the
     * right side for the rounding to fall on.
     */
    private static FoodProperties portion(FoodProperties whole) {
        int nutrition = Math.max(1, Math.round(whole.nutrition() / (float) CHUNKS));
        return new FoodProperties(nutrition, whole.saturation() / CHUNKS, whole.canAlwaysEat(),
                CHUNK_EAT_SECONDS, Optional.empty(), whole.effects());
    }

    private MeatSplitting() {
    }
}
