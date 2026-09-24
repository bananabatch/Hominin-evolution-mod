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
 * <p>Grids are exact: a {@code null} cell must be empty - unless the recipe is shapeless, when each
 * ingredient listed goes anywhere in the grid, and nothing else may be in it.
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
    /** Any rock at all - the same kind or a mix, the band's stone or ordinary cobble. */
    private static final Predicate<ItemStack> ROCK = stack -> stack.is(ModTags.Items.ROCKS)
            || stack.is(ModTags.Items.KNAPPABLE_STONE) || stack.is(net.neoforged.neoforge.common.Tags.Items.COBBLESTONES)
            || stack.is(net.neoforged.neoforge.common.Tags.Items.STONES)
            || stack.is(net.minecraft.world.item.Items.FLINT);
    private static final Predicate<ItemStack> THATCH = is(ModItems.THATCH);
    private static final Predicate<ItemStack> HIDE = is(ModItems.HIDE);
    private static final Predicate<ItemStack> HAND_AXE = stack -> stack.is(ModTags.Items.HAND_AXE_TOOLS);
    private static final Predicate<ItemStack> HAMMER = stack -> stack.is(ModTags.Items.HAMMERSTONES);
    private static final Predicate<ItemStack> CLEAVER = is(ModItems.CLEAVER);
    private static final Predicate<ItemStack> TWINE = is(ModItems.TWINE);
    private static final Predicate<ItemStack> STICK = stack -> stack.is(net.minecraft.world.item.Items.STICK);

    private static List<Predicate<ItemStack>> grid(Predicate<ItemStack>... cells) {
        return java.util.Arrays.asList(cells);
    }

    public static final List<WorkRecipe> RECIPES = List.of(
            // A log split with a hand axe: two workable branches.
            new WorkRecipe("workable_branch_log", HAND_AXE, ToolUse.WEAR, 1, List.of(LOG), true,
                    ModItems.WORKABLE_BRANCH, 2),
            new WorkRecipe("workable_branch", HAND_AXE, ToolUse.WEAR, 1, List.of(is(ModItems.LONG_BRANCH)), true,
                    ModItems.WORKABLE_BRANCH, 1),
            // A workable branch trued end to end with a cleaver's straight edge: a shaft.
            new WorkRecipe("workable_shaft", CLEAVER, ToolUse.WEAR, 1, List.of(BRANCH), true,
                    ModItems.WORKABLE_SHAFT, 1),
            // Two branches down the middle, battered with a hammerstone: a club.
            new WorkRecipe("club", HAMMER, ToolUse.WEAR, 1, grid(
                    null, null, null,
                    null, BRANCH, null,
                    null, BRANCH, null), false, ModItems.WOODEN_CLUB, 1),
            // A branch set upright in a base of rocks - any three, anywhere: something to build with.
            new WorkRecipe("building_branch", stack -> true, ToolUse.WEAR, 0, List.of(BRANCH, ROCK, ROCK, ROCK), true,
                    ModItems.BUILDING_BRANCH, 2),
            // A branch forked at the top, stood on three sticks: one end of a cooking rack.
            new WorkRecipe("cooking_rack", stack -> true, ToolUse.WEAR, 0, List.of(BRANCH, STICK, STICK, STICK), true,
                    ModItems.COOKING_RACK, 1),
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
            // Three logs laid round, five sticks piled in the middle: a fire pit. Nothing needed in the slot.
            new WorkRecipe("fire_pit", stack -> true, ToolUse.WEAR, 0, grid(
                    STICK, null, STICK,
                    STICK, STICK, STICK,
                    LOG, LOG, LOG), false, ModItems.FIRE_PIT, 1),
            // Thatch over the end of a stick, three twine wound round to hold it: a torch.
            new WorkRecipe("torch", TWINE, ToolUse.SPEND, 3, grid(
                    null, THATCH, null,
                    null, STICK, null,
                    null, null, null), false, ModItems.TORCH, 1),
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

    /** Shapeless: every ingredient matched by exactly one thing in the grid, and nothing left over. */
    private static boolean matchesAnywhere(WorkRecipe recipe, List<ItemStack> grid) {
        List<ItemStack> present = new java.util.ArrayList<>();
        for (ItemStack stack : grid) {
            if (!stack.isEmpty()) {
                present.add(stack);
            }
        }
        return present.size() == recipe.grid().size()
                && assign(recipe.grid(), 0, present, new boolean[present.size()]);
    }

    private static boolean assign(List<Predicate<ItemStack>> wanted, int next, List<ItemStack> present, boolean[] used) {
        if (next == wanted.size()) {
            return true;
        }
        for (int i = 0; i < present.size(); i++) {
            if (!used[i] && wanted.get(next).test(present.get(i))) {
                used[i] = true;
                if (assign(wanted, next + 1, present, used)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }

    private WorkRecipes() {
    }
}
