package dev.hominin.evolution.knapping;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModMenus;
import dev.hominin.evolution.block.KnappingStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Sitting at a knapping station: the hammerstone and bone slots, four rows of stone, and
 * the player's own inventory. What to make is chosen with the buttons beside it, which come
 * back here as menu button clicks carrying the {@link KnappingChoice} ordinal.
 */
public class KnappingStationMenu extends AbstractContainerMenu {
    /** Synced to the screen: the player's knapping level, and whether they can work Acheulean. */
    public static final int DATA_LEVEL = 0;
    public static final int DATA_ACHEULEAN = 1;
    public static final int DATA_LEVALLOIS = 2;

    public static final int STORAGE_X = 8;
    public static final int STORAGE_Y = 46;
    public static final int TOOL_SLOT_Y = 16;
    public static final int INVENTORY_Y = 140;
    public static final int HOTBAR_Y = 198;

    private static final int STATION_SLOTS = KnappingStationBlockEntity.SIZE;
    private static final int INVENTORY_END = STATION_SLOTS + 36;

    private final Container station;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos pos;

    public KnappingStationMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, new SimpleContainer(STATION_SLOTS), new SimpleContainerData(3), BlockPos.ZERO,
                ContainerLevelAccess.NULL);
    }

    public KnappingStationMenu(int id, Inventory inventory, Container station, BlockPos pos) {
        this(id, inventory, station, serverData(inventory.player), pos,
                ContainerLevelAccess.create(inventory.player.level(), pos));
    }

    private KnappingStationMenu(int id, Inventory inventory, Container station, ContainerData data, BlockPos pos,
            ContainerLevelAccess access) {
        super(ModMenus.KNAPPING_STATION.get(), id);
        this.station = station;
        this.data = data;
        this.pos = pos;
        this.access = access;
        addSlot(new StationSlot(station, KnappingStationBlockEntity.HAMMER, 8, TOOL_SLOT_Y));
        addSlot(new StationSlot(station, KnappingStationBlockEntity.BOPPER, 26, TOOL_SLOT_Y));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new StationSlot(station, KnappingStationBlockEntity.STONES_START + col + row * 9,
                        STORAGE_X + col * 18, STORAGE_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    /** The server side reads the player's real skill every time it syncs. */
    private static ContainerData serverData(Player player) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (!(player instanceof ServerPlayer server)) {
                    return 0;
                }
                // Level plus one: a synced 0 means nothing has arrived yet, not a flawless knapper.
                return index == DATA_LEVEL ? Acheulean.level(server) + 1
                        : index == DATA_ACHEULEAN ? (Acheulean.canUse(server) ? 1 : 0)
                        : Acheulean.canUseLevallois(server) ? 1 : 0;
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public int knappingLevel() {
        int synced = data.get(DATA_LEVEL);
        return synced == 0 ? 4 : Math.max(0, Math.min(4, synced - 1));
    }

    public boolean canWorkAcheulean() {
        return data.get(DATA_ACHEULEAN) == 1;
    }

    public boolean canWorkLevallois() {
        return data.get(DATA_LEVALLOIS) == 1;
    }

    public Container station() {
        return station;
    }

    /** The stone that will be worked next: the first one laid out. */
    public ItemStack workingStone() {
        for (int slot = KnappingStationBlockEntity.STONES_START; slot < KnappingStationBlockEntity.SIZE; slot++) {
            ItemStack stack = station.getItem(slot);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        KnappingChoice choice = KnappingChoice.byId(id);
        if (choice != null && player instanceof ServerPlayer server) {
            StationKnapping.knap(server, station, choice, pos);
        }
        return choice != null;
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < STATION_SLOTS) {
            if (!moveItemStackTo(stack, STATION_SLOTS, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (KnappingStationBlockEntity.isHammer(stack) && !slots.get(KnappingStationBlockEntity.HAMMER).hasItem()
                && moveItemStackTo(stack, KnappingStationBlockEntity.HAMMER, KnappingStationBlockEntity.HAMMER + 1, false)) {
            // Hammer went in its slot.
        } else if (KnappingStationBlockEntity.isBopper(stack)
                && moveItemStackTo(stack, KnappingStationBlockEntity.BOPPER, KnappingStationBlockEntity.BOPPER + 1, false)) {
            // Bone went in its slot.
        } else if (!KnappingStationBlockEntity.isStone(stack)
                || !moveItemStackTo(stack, KnappingStationBlockEntity.STONES_START, STATION_SLOTS, false)) {
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
    public boolean stillValid(Player who) {
        return stillValid(access, who, ModBlocks.KNAPPING_STATION.get());
    }

    /** A slot that only takes what belongs there: a hammer, a bone, or stone. */
    private static class StationSlot extends Slot {
        StationSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return KnappingStationBlockEntity.accepts(getContainerSlot(), stack);
        }
    }
}
