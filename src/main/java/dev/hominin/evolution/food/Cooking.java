package dev.hominin.evolution.food;

import dev.hominin.evolution.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;

/**
 * What a fire makes of meat, and how long it takes - for anything hung over one on a cooking rack. The mod's own
 * meats are named here; anything else cooks the way a campfire would cook it.
 */
public final class Cooking {
    /** Over the fire this long after it is done, and it is black. */
    public static final int CHAR_TICKS = 5 * 60 * 20;

    /** What it comes out as, or empty if a fire does nothing for it. Spoiled meat is still spoiled, cooked. */
    public static ItemStack cooked(Level level, ItemStack raw) {
        Item result = raw.is(ModItems.MEAT_CHUNK.get()) ? ModItems.COOKED_MEAT_CHUNK.get()
                : raw.is(ModItems.RIB.get()) ? ModItems.COOKED_RIB.get()
                : raw.is(ModItems.BONE_MARROW.get()) ? ModItems.ROASTED_MARROW.get()
                : raw.is(ModItems.HOMININ_MEAT.get()) ? ModItems.COOKED_HOMININ_MEAT.get() : null;
        ItemStack done;
        if (result != null) {
            done = new ItemStack(result, raw.getCount());
        } else {
            SingleRecipeInput input = new SingleRecipeInput(raw.copyWithCount(1));
            done = level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, level)
                    .map(recipe -> recipe.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
            if (!done.isEmpty()) {
                done.setCount(Math.min(done.getMaxStackSize(), done.getCount() * raw.getCount()));
            }
        }
        if (!done.isEmpty()) {
            Spoilage.carry(raw, done);
        }
        return done;
    }

    /** Ticks over a fire before it is done; zero if a fire does nothing for it. */
    public static int cookTicks(Level level, ItemStack raw) {
        if (raw.is(ModItems.MEAT_CHUNK.get())) {
            return 400;
        }
        if (raw.is(ModItems.RIB.get())) {
            return 700;
        }
        if (raw.is(ModItems.BONE_MARROW.get())) {
            return 300;
        }
        if (raw.is(ModItems.HOMININ_MEAT.get())) {
            return 600;
        }
        SingleRecipeInput input = new SingleRecipeInput(raw.copyWithCount(1));
        return level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, level)
                .map(recipe -> recipe.value().getCookingTime()).orElse(0);
    }

    /** Already been over a fire. */
    public static boolean isCooked(ItemStack stack) {
        return stack.is(ModItems.COOKED_MEAT_CHUNK.get()) || stack.is(ModItems.COOKED_RIB.get())
                || stack.is(ModItems.ROASTED_MARROW.get()) || stack.is(ModItems.COOKED_HOMININ_MEAT.get())
                || stack.is(ModItems.CHARRED_MEAT.get()) || stack.is(Tags.Items.FOODS_COOKED_MEAT)
                || stack.is(Tags.Items.FOODS_COOKED_FISH);
    }

    /** Cooked meat left over the fire too long goes black. */
    public static boolean chars(ItemStack stack) {
        return isCooked(stack) && !stack.is(ModItems.CHARRED_MEAT.get()) && !stack.is(ModItems.ROASTED_MARROW.get());
    }

    private Cooking() {
    }
}
