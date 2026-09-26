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
    /** Players only - any player, or those its layer named (see PilePlayers); never a band member. Piles alone. */
    public static final int FOR_PLAYERS = 3;

    /** Who is taking: may they have something laid down by this one, marked this way? */
    @FunctionalInterface
    public interface Access {
        boolean allows(@Nullable UUID layer, int mark);
    }

    /** Raiders, and a pile broken up: marks mean nothing to them. */
    public static final Access ANYONE = (layer, mark) -> true;

    /** What a pile is for: set by the first thing laid on it, and only that kind goes on after. */
    public enum Kind {
        TOOLS("Tool pile"), FOOD("Food pile"), ROCKS("Rock pile"), STORE("Store heap"), SACRED("The Pile"),
        /** Sticks, branches, logs - and what is made of them: clubs, spears. */
        WOOD("Wood pile");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Which kind of pile a thing belongs on. */
    public static Kind kindOf(ItemStack stack) {
        if (stack.has(net.minecraft.core.component.DataComponents.FOOD) || dev.hominin.evolution.band.ToolPiles.isBone(stack)
                || stack.is(dev.hominin.evolution.ModItems.DEAD_BRANCH.get())) {
            return Kind.FOOD;
        }
        if (smallStick(stack)) {
            // Sticks of every kind lie with the stone tools.
            return Kind.TOOLS;
        }
        if (stack.is(dev.hominin.evolution.ModTags.Items.STONE_TOOLS) || dev.hominin.evolution.band.BandMember.isWeapon(stack)) {
            return Kind.TOOLS;
        }
        if (stack.is(dev.hominin.evolution.ModTags.Items.ROCKS) || stack.is(dev.hominin.evolution.ModTags.Items.KNAPPABLE_STONE)) {
            return Kind.ROCKS;
        }
        return Kind.STORE;
    }

    /** Wood, worked or not: sticks, branches, logs, clubs, spears, a digging stick. */
    public static boolean wooden(ItemStack stack) {
        return ToolRackBlockEntity.rackable(stack) || stack.is(net.minecraft.world.item.Items.STICK)
                || stack.is(net.minecraft.tags.ItemTags.LOGS);
    }

    /** Sticks: small enough to lie on a tool pile. */
    public static boolean smallStick(ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.STICK) || stack.is(dev.hominin.evolution.ModItems.SHARPENED_STICK.get())
                || stack.is(dev.hominin.evolution.ModItems.POINTY_STICK.get())
                || stack.is(dev.hominin.evolution.ModItems.TERMITE_STICK.get());
    }

    /** Spears, clubs, branches, shafts, digging sticks, logs: too big for a pile. They lean on a rack. */
    public static boolean bigWood(ItemStack stack) {
        return wooden(stack) && !smallStick(stack);
    }

    /**
     * What the Pile takes: nothing ordinary. Obsidian, a chert hammerstone, a face pebble, a quartz crystal, a multi
     * tool, a fine Acheulean tool - anything above the useful tier.
     */
    public static boolean important(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(dev.hominin.evolution.ModItems.OBSIDIAN_ROCK.get()) || stack.is(dev.hominin.evolution.ModItems.CHERT_HAMMERSTONE.get())
                || stack.is(dev.hominin.evolution.ModItems.FACE_PEBBLE.get()) || stack.is(dev.hominin.evolution.ModItems.QUARTZ_CRYSTAL.get())
                || stack.is(dev.hominin.evolution.ModItems.OLDOWAN_MULTITOOL.get())) {
            return true;
        }
        Integer quality = stack.get(dev.hominin.evolution.ModDataComponents.QUALITY.get());
        if (quality != null && quality <= 1) {
            return true;
        }
        return dev.hominin.evolution.band.Trading.tierOf(stack, null) >= 3;
    }

    @Nullable
    private Kind kind;

    /** This pile's kind - from what is on it, for a pile laid before piles had kinds. */
    public Kind kind() {
        if (kind == null) {
            for (ItemStack stack : tools) {
                if (!stack.isEmpty()) {
                    return kindOf(stack);
                }
            }
            return Kind.TOOLS;
        }
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
        changed();
    }

    /** Whether this goes on this pile: its own kind only - and on the Pile, only what is worth giving up. */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (kind == Kind.SACRED) {
            return important(stack);
        }
        return isEmpty() && kind == null || kindOf(stack) == kind();
    }

    /** The first thing laid on an unset pile decides what it is. */
    private void settle(ItemStack stack) {
        if (kind == null && !stack.isEmpty()) {
            kind = kindOf(stack);
        }
    }

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
    /** Which kind of its owner laid it down - how many evolutions on. -1: from before anyone counted. */
    private int generation = -1;

    public int generation() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = generation;
        setChanged();
    }

    /** When the pile was started: a new pile is everybody's to sort out for a while before anyone keeps count. */
    private long madeAt;

    public long madeAt() {
        return madeAt;
    }

    public void setMadeAt(long time) {
        madeAt = time;
        setChanged();
    }

    /**
     * A long time passes over the pile, once for every kind since. Food rots away to nothing. A stone tool is
     * sometimes gone - broken up, carried off - and what is left is worn, sometimes a grade cruder than it was, and
     * marked as what it is: an artifact. Returns how many things it changed.
     */
    public int ageArtifacts(int steps, net.minecraft.util.RandomSource random) {
        int changed = 0;
        for (int step = 0; step < steps; step++) {
            for (int i = 0; i < MAX; i++) {
                ItemStack stack = tools.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
                    clear(i);
                    changed++;
                    continue;
                }
                if (!dev.hominin.evolution.item.StoneMaterial.isStoneTool(stack)) {
                    continue;
                }
                changed++;
                if (random.nextFloat() < 0.2F) {
                    clear(i);
                    continue;
                }
                if (stack.isDamageableItem()) {
                    int max = stack.getMaxDamage();
                    int wear = (int) (max * (0.25F + random.nextFloat() * 0.25F));
                    stack.setDamageValue(Math.min(max - 1, stack.getDamageValue() + wear));
                }
                Integer quality = stack.get(dev.hominin.evolution.ModDataComponents.QUALITY.get());
                if (quality != null && quality < 4 && random.nextFloat() < 0.35F) {
                    stack.set(dev.hominin.evolution.ModDataComponents.QUALITY.get(), quality + 1);
                }
                stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                        java.util.List.of(net.minecraft.network.chat.Component.literal("An artifact - left by those who "
                                + "came before").withStyle(net.minecraft.ChatFormatting.GRAY,
                                        net.minecraft.ChatFormatting.ITALIC))));
            }
        }
        if (changed > 0) {
            compact();
            changed();
        }
        return changed;
    }

    /**
     * Whoever laid these things down is long dead: no names on them, no marks, nobody who ever gave to the pile. What
     * is left is for whoever finds it.
     */
    public void forgetPeople() {
        Arrays.fill(layers, null);
        Arrays.fill(layerNames, "");
        Arrays.fill(marks, FOR_EVERYONE);
        contributors.clear();
        firstBy = null;
        firstName = "";
        changed();
    }

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
            marks[slot] = Math.floorMod(mark, 4);
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
        if (tool.isEmpty() || !accepts(tool)) {
            return false;
        }
        settle(tool);
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
        if (stack.isEmpty() || !accepts(stack)) {
            return 0;
        }
        settle(stack);
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
    /** So many out of one slot, leaving the rest where it lies - other slots do not move. */
    public ItemStack takeFromSlot(int slot, int count) {
        if (slot < 0 || slot >= MAX || tools.get(slot).isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = tools.get(slot).split(count);
        if (tools.get(slot).isEmpty()) {
            clear(slot);
        }
        changed();
        return taken;
    }

    /** One of it, whatever it is made of and however worn - or empty. */
    public ItemStack takeOne(Predicate<ItemStack> wanted) {
        for (int i = MAX - 1; i >= 0; i--) {
            ItemStack stack = tools.get(i);
            if (!stack.isEmpty() && wanted.test(stack)) {
                ItemStack one = stack.split(1);
                if (stack.isEmpty()) {
                    clear(i);
                }
                compact();
                changed();
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    /** So many of it, out of whatever stacks it lies in. Returns how many were taken. */
    public int takeCount(Predicate<ItemStack> wanted, int count) {
        int taken = 0;
        for (int i = MAX - 1; i >= 0 && taken < count; i--) {
            ItemStack stack = tools.get(i);
            if (stack.isEmpty() || !wanted.test(stack)) {
                continue;
            }
            int from = Math.min(count - taken, stack.getCount());
            stack.shrink(from);
            taken += from;
            if (stack.isEmpty()) {
                clear(i);
            }
        }
        if (taken > 0) {
            compact();
            changed();
        }
        return taken;
    }

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
        tag.putInt("Generation", generation);
        tag.putLong("MadeAt", madeAt);
        if (kind != null) {
            tag.putString("Kind", kind.name());
        }
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
        generation = tag.contains("Generation") ? tag.getInt("Generation") : -1;
        madeAt = tag.getLong("MadeAt");
        kind = null;
        if (tag.contains("Kind")) {
            try {
                kind = Kind.valueOf(tag.getString("Kind"));
                if (kind == Kind.WOOD) {
                    // There are no wood piles: what was laid on one lies with the tools now.
                    kind = Kind.TOOLS;
                }
            } catch (IllegalArgumentException ignored) {
                // An unknown kind: worked out from what is on it.
            }
        }
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
                marks[slot] = Math.floorMod(m.getInt("Mark"), 4);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, tools, true, registries);
        // The kind goes to the client too: a rock pile is drawn as a heap.
        tag.putString("Kind", kind().name());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
