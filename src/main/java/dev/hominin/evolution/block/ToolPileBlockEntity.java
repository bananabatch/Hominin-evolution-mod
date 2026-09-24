package dev.hominin.evolution.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What lies in a pile of tools: up to eight, any mix of kinds and stones, one on top of the other - and whose
 * pile it is. In a store it holds food, bones and whatever else is put by as well, and those lie in heaps: a slot
 * of berries is as many berries as make a stack.
 *
 * <p>Each thing in it remembers who laid it down, and - if a player laid it down - who it is for: everyone, only
 * them, or only those close to them.
 */
public class ToolPileBlockEntity extends BlockEntity {
    public static final int MAX = 8;

    /** Who may use something in the pile. */
    public static final int FOR_EVERYONE = 0;
    public static final int FOR_ME = 1;
    public static final int FOR_CLOSE = 2;

    /** Who is taking: may they have something laid down by this one, marked this way? */
    @FunctionalInterface
    public interface Access {
        boolean allows(@Nullable UUID layer, int mark);
    }

    /** Raiders, and a pile broken up: marks mean nothing to them. */
    public static final Access ANYONE = (layer, mark) -> true;

    private final NonNullList<ItemStack> tools = NonNullList.withSize(MAX, ItemStack.EMPTY);
    private final UUID[] layers = new UUID[MAX];
    private final String[] layerNames = new String[MAX];
    private final int[] marks = new int[MAX];
    @Nullable
    private UUID owner;
    @Nullable
    private UUID firstBy;
    private String firstName = "";
    /** Everyone who has ever laid anything down here - whether or not it is still here. */
    private final java.util.Set<UUID> contributors = new java.util.HashSet<>();

    public ToolPileBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOOL_PILE.get(), pos, state);
        Arrays.fill(layerNames, "");
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        changed();
    }

    /** Whoever laid the first thing down here. */
    public String firstName() {
        return firstName;
    }

    @Nullable
    public UUID firstBy() {
        return firstBy;
    }

    public int count() {
        int n = 0;
        for (ItemStack stack : tools) {
            if (!stack.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public boolean isFull() {
        return count() >= MAX;
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    /** Everything in the pile, bottom first. */
    public List<ItemStack> contents() {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : tools) {
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    // ------------------------------------------------------------ one slot at a time, for the pile's menu

    public ItemStack at(int slot) {
        return slot >= 0 && slot < MAX ? tools.get(slot) : ItemStack.EMPTY;
    }

    @Nullable
    public UUID layerOf(int slot) {
        return slot >= 0 && slot < MAX ? layers[slot] : null;
    }

    public String layerNameOf(int slot) {
        return slot >= 0 && slot < MAX ? layerNames[slot] : "";
    }

    public int markOf(int slot) {
        return slot >= 0 && slot < MAX ? marks[slot] : FOR_EVERYONE;
    }

    public void setMark(int slot, int mark) {
        if (slot >= 0 && slot < MAX && !tools.get(slot).isEmpty()) {
            marks[slot] = Math.floorMod(mark, 3);
            changed();
        }
    }

    /** Whether this one has ever laid anything down here. */
    public boolean contributed(UUID who) {
        return contributors.contains(who);
    }

    /** Whether anyone but this one has. */
    public boolean othersContributed(UUID who) {
        for (UUID other : contributors) {
            if (!other.equals(who)) {
                return true;
            }
        }
        return false;
    }

    /** The topmost slot this taker may have, or -1. */
    public int topSlot(Access access) {
        for (int i = MAX - 1; i >= 0; i--) {
            if (!tools.get(i).isEmpty() && access.allows(layers[i], marks[i])) {
                return i;
            }
        }
        return -1;
    }

    /** The whole pile as it is saved - to be carried off and set down again. */
    public CompoundTag bundle(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    /** A gathered pile, set down here: everything back as it lay. */
    public void unbundle(CompoundTag tag, HolderLookup.Provider registries) {
        loadCustomOnly(tag, registries);
        changed();
    }

    public boolean mayTake(int slot, Access access) {
        return slot >= 0 && slot < MAX && !tools.get(slot).isEmpty() && access.allows(layers[slot], marks[slot]);
    }

    /** The whole of one slot, if this taker may have it. */
    public ItemStack takeSlot(int slot, Access access) {
        if (!mayTake(slot, access)) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = tools.get(slot);
        clear(slot);
        compact();
        changed();
        return taken;
    }

    // ------------------------------------------------------------ laying down

    private void noteFirst(@Nullable UUID by, String name) {
        if (firstBy == null && firstName.isEmpty()) {
            firstBy = by;
            firstName = name == null ? "" : name;
        }
    }

    /** Laid on top. One tool at a time: a pile is not a stack. */
    public boolean add(ItemStack tool) {
        return add(tool, null, "");
    }

    public boolean add(ItemStack tool, @Nullable UUID by, String name) {
        if (tool.isEmpty()) {
            return false;
        }
        for (int i = 0; i < MAX; i++) {
            if (tools.get(i).isEmpty()) {
                set(i, tool.copyWithCount(1), by, name);
                noteFirst(by, name);
                changed();
                return true;
            }
        }
        return false;
    }

    public int addStack(ItemStack stack) {
        return addStack(stack, null, "");
    }

    /**
     * Laid down whole: food and bones go in with their like - the same person's - and anything else takes a place
     * of its own. What does not fit stays in the stack. Returns how many went on.
     */
    public int addStack(ItemStack stack, @Nullable UUID by, String name) {
        if (stack.isEmpty()) {
            return 0;
        }
        int before = stack.getCount();
        if (stack.isStackable()) {
            for (int i = 0; i < MAX && !stack.isEmpty(); i++) {
                ItemStack here = tools.get(i);
                if (!here.isEmpty() && ItemStack.isSameItemSameComponents(here, stack) && Objects.equals(layers[i], by)
                        && here.getCount() < here.getMaxStackSize()) {
                    int move = Math.min(stack.getCount(), here.getMaxStackSize() - here.getCount());
                    here.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < MAX && !stack.isEmpty(); i++) {
            if (tools.get(i).isEmpty()) {
                set(i, stack.split(stack.isStackable() ? stack.getCount() : 1), by, name);
            }
        }
        int added = before - stack.getCount();
        if (added > 0) {
            noteFirst(by, name);
            changed();
        }
        return added;
    }

    // ------------------------------------------------------------ taking

    /** Up to this many of the first thing that fits: a handful of berries, not the whole heap. */
    public ItemStack takeSome(Predicate<ItemStack> wanted, int max) {
        return takeSome(wanted, max, ANYONE);
    }

    public ItemStack takeSome(Predicate<ItemStack> wanted, int max, Access access) {
        for (int i = MAX - 1; i >= 0; i--) {
            ItemStack stack = tools.get(i);
            if (!stack.isEmpty() && wanted.test(stack) && access.allows(layers[i], marks[i])) {
                ItemStack taken = stack.split(Math.max(1, max));
                if (stack.isEmpty()) {
                    clear(i);
                    compact();
                }
                changed();
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    /** How much of what fits lies here, counting every one in a heap. */
    public int total(Predicate<ItemStack> wanted) {
        return total(wanted, ANYONE);
    }

    public int total(Predicate<ItemStack> wanted, Access access) {
        int n = 0;
        for (int i = 0; i < MAX; i++) {
            ItemStack stack = tools.get(i);
            if (!stack.isEmpty() && wanted.test(stack) && access.allows(layers[i], marks[i])) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** The one on top. */
    public ItemStack takeTop() {
        return takeTop(ANYONE);
    }

    /** The topmost this taker may have. */
    public ItemStack takeTop(Access access) {
        for (int i = MAX - 1; i >= 0; i--) {
            if (!tools.get(i).isEmpty() && access.allows(layers[i], marks[i])) {
                ItemStack top = tools.get(i);
                clear(i);
                compact();
                changed();
                return top;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The first that fits what is wanted, wherever it lies in the pile. */
    public ItemStack take(Predicate<ItemStack> wanted) {
        return take(wanted, ANYONE);
    }

    public ItemStack take(Predicate<ItemStack> wanted, Access access) {
        for (int i = MAX - 1; i >= 0; i--) {
            ItemStack stack = tools.get(i);
            if (!stack.isEmpty() && wanted.test(stack) && access.allows(layers[i], marks[i])) {
                clear(i);
                compact();
                changed();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Everything, for when the pile is broken up. */
    public List<ItemStack> takeAll() {
        List<ItemStack> all = contents();
        for (int i = 0; i < MAX; i++) {
            clear(i);
        }
        changed();
        return all;
    }

    private void set(int slot, ItemStack stack, @Nullable UUID by, @Nullable String name) {
        if (by != null && !stack.isEmpty()) {
            contributors.add(by);
        }
        tools.set(slot, stack);
        layers[slot] = by;
        layerNames[slot] = name == null ? "" : name;
        marks[slot] = FOR_EVERYONE;
    }

    private void clear(int slot) {
        set(slot, ItemStack.EMPTY, null, "");
    }

    /** Whatever was under the one taken slides down: the pile has no gaps in it. */
    private void compact() {
        int to = 0;
        for (int from = 0; from < MAX; from++) {
            if (tools.get(from).isEmpty()) {
                continue;
            }
            if (to != from) {
                ItemStack stack = tools.get(from);
                UUID by = layers[from];
                String name = layerNames[from];
                int mark = marks[from];
                clear(from);
                set(to, stack, by, name);
                marks[to] = mark;
            }
            to++;
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, tools, true, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (firstBy != null) {
            tag.putUUID("FirstBy", firstBy);
        }
        tag.putString("FirstName", firstName);
        ListTag gave = new ListTag();
        for (UUID who : contributors) {
            gave.add(net.minecraft.nbt.NbtUtils.createUUID(who));
        }
        tag.put("Contributors", gave);
        ListTag meta = new ListTag();
        for (int i = 0; i < MAX; i++) {
            if (tools.get(i).isEmpty()) {
                continue;
            }
            CompoundTag m = new CompoundTag();
            m.putInt("Slot", i);
            if (layers[i] != null) {
                m.putUUID("By", layers[i]);
            }
            m.putString("Name", layerNames[i]);
            m.putInt("Mark", marks[i]);
            meta.add(m);
        }
        tag.put("Meta", meta);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < MAX; i++) {
            clear(i);
        }
        ContainerHelper.loadAllItems(tag, tools, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        firstBy = tag.hasUUID("FirstBy") ? tag.getUUID("FirstBy") : null;
        firstName = tag.getString("FirstName");
        contributors.clear();
        for (Tag entry : tag.getList("Contributors", Tag.TAG_INT_ARRAY)) {
            contributors.add(net.minecraft.nbt.NbtUtils.loadUUID(entry));
        }
        for (Tag entry : tag.getList("Meta", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) entry;
            int slot = m.getInt("Slot");
            if (slot >= 0 && slot < MAX) {
                layers[slot] = m.hasUUID("By") ? m.getUUID("By") : null;
                layerNames[slot] = m.getString("Name");
                marks[slot] = Math.floorMod(m.getInt("Mark"), 3);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, tools, true, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
