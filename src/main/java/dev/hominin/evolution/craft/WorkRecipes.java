package dev.hominin.evolution.craft;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What the primitive work station makes. Every recipe names what goes in the tool slot - and
 * the tool is what makes the difference: a log with a hand axe beside it becomes branches, the
 * same branches with a hammerstone become a club.
 *
 * <p>Grids are exact: a {@code null} cell must be empty. Recipes with one ingredient accept it
 * anywhere in the grid.
 */
public final class WorkRecipes {
    /** How the tool pays for the job: worn by one use, or spent - so many twine used up. */
    public enum ToolUse {
        WEAR, SPEND
    }

    public record WorkRecipe(String name, Predicate<ItemStack> tool, ToolUse use, int toolCost,
            List<Predicate<ItemStack>> grid, boolean anywhere, Supplier<Item> result, int count) {
    }

    private static Predicate<ItemStack> is(Supplier<? extends Item> item) {
        return stack -> stack.is(item.get());
    }

    private static final Predicate<ItemStack> LOG = stack -> stack.is(ItemTags.LOGS);
    private static final Predicate<ItemStack> BRANCH = is(ModItems.WORKABLE_BRANCH);
    private static final Predicate<ItemStack> ROCK = stack -> stack.is(ModTags.Items.ROCKS)
            || stack.is(ModTags.Items.KNAPPABLE_STONE);
    private static final Predicate<ItemStack> THATCH = is(ModItems.THATCH);
    private static final Predicate<ItemStack> HIDE = is(ModItems.HIDE);
    private static final Predicate<ItemStack> HAND_AXE = stack -> stack.is(ModTags.Items.HAND_AXE_TOOLS);
    private static final Predicate<ItemStack> HAMMER = stack -> stack.is(ModTags.Items.HAMMERSTONES);
    private static final Predicate<ItemStack> EDGE = stack -> stack.is(ModTags.Items.FLAKES)
            || stack.is(ModTags.Items.HAND_AXE_TOOLS);
    private static final Predicate<ItemStack> TWINE = is(ModItems.TWINE);

    private static List<Predicate<ItemStack>> grid(Predicate<ItemStack>... cells) {
        return java.util.Arrays.asList(cells);
    }

    public static final List<WorkRecipe> RECIPES = List.of(
            // A log split with a hand axe: two workable branches.
            new WorkRecipe("workable_branch_log", HAND_AXE, ToolUse.WEAR, 1, List.of(LOG), true,
                    ModItems.WORKABLE_BRANCH, 2),
            new WorkRecipe("workable_branch", HAND_AXE, ToolUse.WEAR, 1, List.of(is(ModItems.LONG_BRANCH)), true,
                    ModItems.WORKABLE_BRANCH, 1),
            // Two branches down the middle, battered with a hammerstone: a club.
            new WorkRecipe("club", HAMMER, ToolUse.WEAR, 1, grid(
                    null, null, null,
                    null, BRANCH, null,
                    null, BRANCH, null), false, ModItems.WOODEN_CLUB, 1),
            // The same two, worked to a point with an edge: a spear that outlasts its point.
            new WorkRecipe("workable_spear", EDGE, ToolUse.WEAR, 1, grid(
                    null, BRANCH, null,
                    null, BRANCH, null,
                    null, null, null), false, ModItems.WORKABLE_SPEAR, 1),
            // A branch set upright in a base of rocks: something to build with.
            new WorkRecipe("building_branch", stack -> true, ToolUse.WEAR, 0, grid(
                    null, null, null,
                    null, BRANCH, null,
                    ROCK, ROCK, ROCK), false, ModItems.BUILDING_BRANCH, 2),
            // A grid of thatch bound with twine: thatch blocks.
            new WorkRecipe("thatch_block", TWINE, ToolUse.SPEND, 4, grid(
                    THATCH, THATCH, THATCH,
                    THATCH, THATCH, THATCH,
                    THATCH, THATCH, THATCH), false, ModItems.THATCH_BLOCK, 4),
            // Hide over a layer of thatch, bound with plenty of twine: bedding. Two make a bed.
            new WorkRecipe("thatch_bedding", TWINE, ToolUse.SPEND, 10, grid(
                    HIDE, HIDE, HIDE,
                    THATCH, THATCH, THATCH,
                    null, null, null), false, ModItems.THATCH_BEDDING, 2),
            // A branch and a hammerstone side by side, lashed with twine: a proper digging stick.
            new WorkRecipe("digging_stick", TWINE, ToolUse.SPEND, 10, grid(
                    null, null, null,
                    BRANCH, HAMMER, null,
                    null, null, null), false, ModItems.DIGGING_STICK, 1));

    /** The recipe this grid and tool make, if any. */
    @Nullable
    public static WorkRecipe match(List<ItemStack> grid, ItemStack tool) {
        for (WorkRecipe recipe : RECIPES) {
            if (!recipe.tool().test(tool) || (recipe.use() == ToolUse.SPEND && tool.getCount() < recipe.toolCost())) {
                continue;
            }
            if (recipe.use() == ToolUse.WEAR && recipe.toolCost() > 0 && tool.isEmpty()) {
                continue;
            }
            if (recipe.anywhere() ? matchesAnywhere(recipe, grid) : matchesExactly(recipe, grid)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean matchesExactly(WorkRecipe recipe, List<ItemStack> grid) {
        for (int i = 0; i < 9; i++) {
            Predicate<ItemStack> wanted = recipe.grid().get(i);
            ItemStack stack = grid.get(i);
            if (wanted == null ? !stack.isEmpty() : stack.isEmpty() || !wanted.test(stack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAnywhere(WorkRecipe recipe, List<ItemStack> grid) {
        int found = 0;
        for (ItemStack stack : grid) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!recipe.grid().get(0).test(stack)) {
                return false;
            }
            found++;
        }
        return found == 1;
    }

    private WorkRecipes() {
    }
}
