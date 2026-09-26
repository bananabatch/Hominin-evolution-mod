package dev.hominin.evolution.block;

import java.util.Arrays;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlockEntities;
import dev.hominin.evolution.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What leans on one block of a tool rack's bar: three long things - spears, clubs, branches - butts on the ground,
 * tops against the bar, on whichever side they were leaned from. Each remembers who leaned it there and who it is
 * for, as on a pile.
 */
public class ToolRackBlockEntity extends BlockEntity implements Holding {
    public static final int PLACES = 3;

    private final NonNullList<ItemStack> places = NonNullList.withSize(PLACES, ItemStack.EMPTY);
    private final UUID[] layers = new UUID[PLACES];
    private final String[] names = new String[PLACES];
    private final int[] marks = new int[PLACES];
    /** Which side of the bar each leans on: true for the positive side of the other axis. */
    private final boolean[] sides = new boolean[PLACES];
    @Nullable
    private UUID firstBy;
    private String firstName = "";

    public ToolRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOOL_RACK.get(), pos, state);
        Arrays.fill(names, "");
    }

    /** Long wooden things: spears, clubs, pointed and sharpened sticks, branches, shafts, a digging stick. */
    public static boolean rackable(ItemStack stack) {
        return !stack.isEmpty() && (dev.hominin.evolution.item.SpearItem.isSpear(stack)
                || stack.is(ModItems.WOODEN_CLUB.get()) || stack.is(ModItems.BONE_CLUB.get())
                || stack.is(ModItems.POINTY_STICK.get())
                || stack.is(ModItems.SHARPENED_STICK.get()) || ModItems.isLongBranch(stack)
                || stack.is(ModItems.DIGGING_STICK.get()));
    }

    public NonNullList<ItemStack> places() {
        return places;
    }

    public boolean side(int slot) {
        return sides[slot];
    }

    public boolean isFull() {
        return places.stream().noneMatch(ItemStack::isEmpty);
    }

    private Direction.Axis axis() {
        BlockState state = getBlockState();
        return state.hasProperty(ToolRackBarBlock.AXIS) ? state.getValue(ToolRackBarBlock.AXIS) : Direction.Axis.Z;
    }

    /** Which place along the bar was touched. */
    private int placeAt(Vec3 hit) {
        double along = axis() == Direction.Axis.Z ? hit.z - worldPosition.getZ() : hit.x - worldPosition.getX();
        return Math.max(0, Math.min(PLACES - 1, (int) Math.floor(along * PLACES)));
    }

    /** Which side of the bar something is on. */
    private boolean sideOf(Vec3 at) {
        return axis() == Direction.Axis.Z ? at.x > worldPosition.getX() + 0.5D : at.z > worldPosition.getZ() + 0.5D;
    }

    private int nearestEmpty(int aimed) {
        if (places.get(aimed).isEmpty()) {
            return aimed;
        }
        for (int d = 1; d < PLACES; d++) {
            for (int s : new int[] {aimed - d, aimed + d}) {
                if (s >= 0 && s < PLACES && places.get(s).isEmpty()) {
                    return s;
                }
            }
        }
        return -1;
    }

    private void set(int slot, ItemStack stack, @Nullable UUID by, String name, boolean side) {
        places.set(slot, stack);
        layers[slot] = by;
        names[slot] = name;
        marks[slot] = ToolPileBlockEntity.FOR_EVERYONE;
        sides[slot] = side;
        if (firstBy == null && firstName.isEmpty()) {
            firstBy = by;
            firstName = name;
        }
    }

    /** Leans what is in hand against the bar - where it was touched, or the nearest free place. */
    public boolean put(ServerPlayer player, ItemStack stack, Vec3 hit) {
        if (!rackable(stack)) {
            return false;
        }
        int slot = nearestEmpty(placeAt(hit));
        if (slot < 0) {
            player.displayClientMessage(Component.literal("There is no room on this part of the rack."), true);
            return true;
        }
        set(slot, stack.copyWithCount(1), player.getUUID(), player.getName().getString(), sideOf(player.position()));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, worldPosition, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
        player.displayClientMessage(Component.literal("Leaned on the rack, for everyone. Look at it and press the work "
                + "key to say who it is for."), true);
        changed();
        return true;
    }

    /** Empty-handed: takes what leans where you reached - or the nearest thing there you may take. */
    public void take(ServerPlayer player, Vec3 hit) {
        ToolPileBlockEntity.Access access = dev.hominin.evolution.band.ToolPiles.access(player);
        int aimed = placeAt(hit);
        int slot = mayTake(aimed, access) ? aimed : -1;
        for (int d = 1; d < PLACES && slot < 0; d++) {
            for (int s : new int[] {aimed - d, aimed + d}) {
                if (s >= 0 && s < PLACES && mayTake(s, access)) {
                    slot = s;
                    break;
                }
            }
        }
        if (slot < 0) {
            boolean any = places.stream().anyMatch(s -> !s.isEmpty());
            player.displayClientMessage(Component.literal(any ? "What is on this part of the rack is not for you."
                    : "Nothing on the rack here. Lean spears, clubs or branches on it."), true);
            return;
        }
        ItemStack taken = takeSlot(slot, access);
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
        level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    /** For the band: the first thing on it that passes and they may take, taken off. */
    public ItemStack takeFirst(java.util.function.Predicate<ItemStack> wanted, ToolPileBlockEntity.Access access) {
        for (int i = 0; i < PLACES; i++) {
            if (mayTake(i, access) && wanted.test(places.get(i))) {
                return takeSlot(i, access);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Whether anything on it passes, and is there for this taker. */
    public boolean has(java.util.function.Predicate<ItemStack> wanted, ToolPileBlockEntity.Access access) {
        for (int i = 0; i < PLACES; i++) {
            if (mayTake(i, access) && wanted.test(places.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** For the band: leaned in the first free place, as theirs. False if it does not go on a rack, or there is no room. */
    public boolean lean(ItemStack stack, @Nullable UUID by, String name) {
        if (!rackable(stack)) {
            return false;
        }
        for (int i = 0; i < PLACES; i++) {
            if (places.get(i).isEmpty()) {
                set(i, stack.copyWithCount(1), by, name, i % 2 == 0);
                stack.shrink(1);
                changed();
                return true;
            }
        }
        return false;
    }

    /** The nearest rack within reach that passes, in the loaded chunks round about. */
    @Nullable
    public static ToolRackBlockEntity nearest(net.minecraft.server.level.ServerLevel level, BlockPos near, double within,
            java.util.function.Predicate<ToolRackBlockEntity> wanted) {
        ToolRackBlockEntity best = null;
        int reach = (int) Math.ceil(within / 16.0D);
        for (int cx = (near.getX() >> 4) - reach; cx <= (near.getX() >> 4) + reach; cx++) {
            for (int cz = (near.getZ() >> 4) - reach; cz <= (near.getZ() >> 4) + reach; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity entity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (entity instanceof ToolRackBlockEntity rack && rack.getBlockPos().distSqr(near) <= within * within
                            && wanted.test(rack) && (best == null
                                    || rack.getBlockPos().distSqr(near) < best.getBlockPos().distSqr(near))) {
                        best = rack;
                    }
                }
            }
        }
        return best;
    }

    /** Knocked down: everything on it falls. */
    public void spill() {
        if (level != null) {
            Containers.dropContents(level, worldPosition, places);
        }
    }

    // ------------------------------------------------------------ holding

    @Override
    public int slots() {
        return PLACES;
    }

    @Override
    public ItemStack at(int slot) {
        return slot >= 0 && slot < PLACES ? places.get(slot) : ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public UUID layerOf(int slot) {
        return slot >= 0 && slot < PLACES ? layers[slot] : null;
    }

    @Override
    public String layerNameOf(int slot) {
        return slot >= 0 && slot < PLACES ? names[slot] : "";
    }

    @Override
    public int markOf(int slot) {
        return slot >= 0 && slot < PLACES ? marks[slot] : ToolPileBlockEntity.FOR_EVERYONE;
    }

    @Override
    public void setMark(int slot, int mark) {
        if (slot >= 0 && slot < PLACES) {
            marks[slot] = Math.floorMod(mark, 3);
            changed();
        }
    }

    @Override
    public ItemStack takeSlot(int slot, ToolPileBlockEntity.Access access) {
        if (!mayTake(slot, access)) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = places.get(slot);
        places.set(slot, ItemStack.EMPTY);
        layers[slot] = null;
        names[slot] = "";
        marks[slot] = ToolPileBlockEntity.FOR_EVERYONE;
        changed();
        return taken;
    }

    @Nullable
    @Override
    public UUID firstBy() {
        return firstBy;
    }

    @Override
    public String firstName() {
        return firstName;
    }

    @Override
    public String holdingName() {
        return "A tool rack";
    }

    // ------------------------------------------------------------ saving and syncing

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, places, true, registries);
        ListTag who = new ListTag();
        for (int i = 0; i < PLACES; i++) {
            CompoundTag one = new CompoundTag();
            if (layers[i] != null) {
                one.putUUID("By", layers[i]);
            }
            one.putString("Name", names[i]);
            one.putInt("Mark", marks[i]);
            one.putBoolean("Side", sides[i]);
            who.add(one);
        }
        tag.put("Who", who);
        if (firstBy != null) {
            tag.putUUID("FirstBy", firstBy);
        }
        tag.putString("FirstName", firstName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < PLACES; i++) {
            places.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, places, registries);
        ListTag who = tag.getList("Who", Tag.TAG_COMPOUND);
        for (int i = 0; i < PLACES; i++) {
            CompoundTag one = i < who.size() ? who.getCompound(i) : new CompoundTag();
            layers[i] = one.hasUUID("By") ? one.getUUID("By") : null;
            names[i] = one.getString("Name");
            marks[i] = one.getInt("Mark");
            sides[i] = one.getBoolean("Side");
        }
        firstBy = tag.hasUUID("FirstBy") ? tag.getUUID("FirstBy") : null;
        firstName = tag.getString("FirstName");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
