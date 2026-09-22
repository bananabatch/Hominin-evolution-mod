package dev.hominin.evolution.block;

import dev.hominin.evolution.ModBlockEntities;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What is laid out at a knapping station: the hammerstone, the bone for the fine work, and
 * four rows of stone waiting to be worked. It stays put when you walk away - a station is a
 * place, and the stone left beside it is still there in the morning.
 */
public class KnappingStationBlockEntity extends BlockEntity {
    public static final int HAMMER = 0;
    public static final int BOPPER = 1;
    public static final int STONES_START = 2;
    public static final int STONE_SLOTS = 36;
    public static final int SIZE = STONES_START + STONE_SLOTS;

    private final SimpleContainer items = new SimpleContainer(SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            KnappingStationBlockEntity.this.setChanged();
            refreshLook();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return accepts(slot, stack);
        }
    };

    public KnappingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KNAPPING_STATION.get(), pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    public static boolean isHammer(ItemStack stack) {
        return stack.is(ModTags.Items.HAMMERSTONES);
    }

    public static boolean isBopper(ItemStack stack) {
        return stack.is(ModItems.LONG_BONE.get()) || stack.is(Items.BONE);
    }

    public static boolean isStone(ItemStack stack) {
        return stack.is(ModTags.Items.KNAPPABLE_STONE) || stack.is(ModTags.Items.ROCKS)
                || stack.is(ModItems.CHERT_HAMMERSTONE.get());
    }

    public static boolean accepts(int slot, ItemStack stack) {
        return slot == HAMMER ? isHammer(stack) : slot == BOPPER ? isBopper(stack) : isStone(stack);
    }

    /** How many stones are laid out, all told. */
    public int stoneCount() {
        int count = 0;
        for (int slot = STONES_START; slot < SIZE; slot++) {
            count += items.getItem(slot).getCount();
        }
        return count;
    }

    /** The block shows what is on it: hammer, bone, and a pile of stone that grows. */
    private void refreshLook() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof KnappingStationBlock)) {
            return;
        }
        int stones = stoneCount();
        int pile = stones == 0 ? 0 : stones <= 8 ? 1 : stones <= 24 ? 2 : 3;
        BlockState shown = state
                .setValue(KnappingStationBlock.HAMMER, !items.getItem(HAMMER).isEmpty())
                .setValue(KnappingStationBlock.BOPPER, !items.getItem(BOPPER).isEmpty())
                .setValue(KnappingStationBlock.STONES, pile);
        if (shown != state) {
            level.setBlock(worldPosition, shown, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int slot = 0; slot < SIZE; slot++) {
            list.set(slot, items.getItem(slot));
        }
        ContainerHelper.saveAllItems(tag, list, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int slot = 0; slot < SIZE; slot++) {
            items.setItem(slot, list.get(slot));
        }
    }
}
