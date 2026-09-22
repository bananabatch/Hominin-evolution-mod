package dev.hominin.evolution.craft;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The primitive work station: a three-by-three grid, a tool slot beside it, and what comes
 * out. Nothing is made without the right thing in the tool slot.
 */
public class WorkStationMenu extends AbstractContainerMenu {
    public static final int RESULT = 0;
    public static final int TOOL = 1;
    private static final int GRID_START = 2;
    private static final int INVENTORY_START = 11;
    private static final int INVENTORY_END = 47;

    private final SimpleContainer grid = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final SimpleContainer tool = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    @Nullable
    private WorkRecipes.WorkRecipe current;

    public WorkStationMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public WorkStationMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.WORK_STATION.get(), id);
        this.access = access;
        this.player = inventory.player;
        addSlot(new Slot(result, 0, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player taker, ItemStack stack) {
                craft();
                super.onTake(taker, stack);
            }
        });
        addSlot(new Slot(tool, 0, 16, 35));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(grid, col + row * 3, 40 + col * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    private List<ItemStack> gridItems() {
        List<ItemStack> items = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            items.add(grid.getItem(i));
        }
        return items;
    }

    @Override
    public void slotsChanged(Container changed) {
        super.slotsChanged(changed);
        current = WorkRecipes.match(gridItems(), tool.getItem(0));
        result.setItem(0, current == null ? ItemStack.EMPTY
                : new ItemStack(current.result().get(), current.count()));
        broadcastChanges();
    }

    /** Taking what was made: the grid is used up and the tool pays for it. */
    private void craft() {
        WorkRecipes.WorkRecipe recipe = current;
        if (recipe == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (!grid.getItem(i).isEmpty()) {
                grid.getItem(i).shrink(1);
            }
        }
        ItemStack held = tool.getItem(0);
        if (recipe.use() == WorkRecipes.ToolUse.SPEND) {
            held.shrink(recipe.toolCost());
        } else if (recipe.toolCost() > 0 && held.isDamageableItem()) {
            held.hurtAndBreak(recipe.toolCost(), player, EquipmentSlot.MAINHAND);
            // hurtAndBreak on a stack outside a slot does not clear it; do it by hand.
            if (held.getDamageValue() >= held.getMaxDamage()) {
                tool.setItem(0, ItemStack.EMPTY);
            }
        }
        grid.setChanged();
        tool.setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == RESULT) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onTake(who, stack);
            return copy;
        }
        if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, GRID_START, INVENTORY_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        access.execute((level, pos) -> {
            clearContainer(who, grid);
            clearContainer(who, tool);
        });
    }

    @Override
    public boolean stillValid(Player who) {
        return stillValid(access, who, ModBlocks.WORK_STATION.get());
    }
}
