package dev.hominin.evolution.block;

import dev.hominin.evolution.ModBlockEntities;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.food.Cooking;
import dev.hominin.evolution.food.Spoilage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What hangs from one block of spit: three hooks, a few pieces to a hook. Over a fire, raw meat cooks - and cooked
 * meat left there long enough chars. Without one, it just hangs, out of the dirt and away from the flies.
 */
public class CookingSpitBlockEntity extends BlockEntity implements Holding {
    public static final int HOOKS = 3;
    private static final int PER_HOOK = 4;

    private final NonNullList<ItemStack> hooks = NonNullList.withSize(HOOKS, ItemStack.EMPTY);
    /** Ticks each hook has spent over a fire, and how many it needs: to cook, or - cooked - to char. */
    private final int[] progress = new int[HOOKS];
    private final int[] needs = new int[HOOKS];
    /** Who hung each hook's meat, their name, and who it is for - as on a pile. */
    private final java.util.UUID[] layers = new java.util.UUID[HOOKS];
    private final String[] names = {"", "", ""};
    private final int[] marks = new int[HOOKS];
    @javax.annotation.Nullable
    private java.util.UUID firstBy;
    private String firstName = "";

    public CookingSpitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COOKING_SPIT.get(), pos, state);
    }

    /** Food of any kind hangs; water does not. */
    public static boolean hangable(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD) && !stack.is(ModItems.WATER_EGGSHELL.get());
    }

    public NonNullList<ItemStack> hooks() {
        return hooks;
    }

    private Direction.Axis axis() {
        return getBlockState().getValue(CookingSpitBlock.AXIS);
    }

    /** The hook nearest where the spit was touched. */
    private int hookAt(Vec3 hit) {
        double along = axis() == Direction.Axis.Z ? hit.z - worldPosition.getZ() : hit.x - worldPosition.getX();
        return Math.max(0, Math.min(HOOKS - 1, (int) Math.floor(along * HOOKS)));
    }

    private boolean fire() {
        return level != null && fireBelow(level, worldPosition);
    }

    public static boolean fireBelow(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return dev.hominin.evolution.survival.Hearths.isLitHearth(below) || below.is(BlockTags.FIRE);
    }

    // ------------------------------------------------------------ hanging and taking down

    /** Hangs what is in hand - one piece, or sneaking, a hookful. Returns false if it is nothing that hangs. */
    public boolean hang(ServerPlayer player, ItemStack stack, Vec3 hit) {
        if (!hangable(stack)) {
            return false;
        }
        int aimed = hookAt(hit);
        int slot = room(aimed, stack) ? aimed : -1;
        for (int i = 0; i < HOOKS && slot < 0; i++) {
            if (!hooks.get(i).isEmpty() && room(i, stack)) {
                slot = i;
            }
        }
        for (int i = 0; i < HOOKS && slot < 0; i++) {
            if (hooks.get(i).isEmpty()) {
                slot = i;
            }
        }
        if (slot < 0) {
            player.displayClientMessage(Component.literal("There is no room left on this part of the spit."), true);
            return true;
        }
        ItemStack on = hooks.get(slot);
        int count = Math.min(player.isShiftKeyDown() ? PER_HOOK : 1, stack.getCount());
        if (on.isEmpty()) {
            hooks.set(slot, stack.copyWithCount(count));
            progress[slot] = 0;
            needs[slot] = needed(hooks.get(slot));
            layers[slot] = player.getUUID();
            names[slot] = player.getName().getString();
            marks[slot] = ToolPileBlockEntity.FOR_EVERYONE;
            if (firstBy == null) {
                firstBy = player.getUUID();
                firstName = player.getName().getString();
            }
        } else {
            count = Math.min(count, PER_HOOK - on.getCount());
            on.grow(count);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(count);
        }
        level.playSound(null, worldPosition, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
        boolean cooks = !Cooking.isCooked(hooks.get(slot)) && needs[slot] > 0;
        player.displayClientMessage(Component.literal(cooks
                ? (fire() ? "Hung over the fire. It will cook slowly and evenly - take it down when it is done."
                        : "Hung up. Light a fire under it and it will cook.")
                : "Hung up out of the dirt. It will keep here."), true);
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.COOKING_RACK);
        changed();
        return true;
    }

    /** Whether a hookful of this would go on somewhere. */
    public boolean hasRoomFor(ItemStack stack) {
        if (!hangable(stack)) {
            return false;
        }
        for (int i = 0; i < HOOKS; i++) {
            if (room(i, stack)) {
                return true;
            }
        }
        return false;
    }

    /** One of the band hangs food up: as much as a hook takes. Returns how many went on. */
    public int hangFor(java.util.UUID by, String name, ItemStack stack) {
        if (!hangable(stack) || level == null) {
            return 0;
        }
        for (int slot = 0; slot < HOOKS; slot++) {
            if (!room(slot, stack)) {
                continue;
            }
            ItemStack on = hooks.get(slot);
            int count;
            if (on.isEmpty()) {
                count = Math.min(PER_HOOK, stack.getCount());
                hooks.set(slot, stack.split(count));
                progress[slot] = 0;
                needs[slot] = needed(hooks.get(slot));
                layers[slot] = by;
                names[slot] = name;
                marks[slot] = ToolPileBlockEntity.FOR_EVERYONE;
                if (firstBy == null) {
                    firstBy = by;
                    firstName = name;
                }
            } else {
                count = Math.min(PER_HOOK - on.getCount(), stack.getCount());
                on.grow(count);
                stack.shrink(count);
            }
            level.playSound(null, worldPosition, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
            changed();
            return count;
        }
        return 0;
    }

    /** Back on its hook: what was left of a hookful when one piece was taken off it. */
    public void putBack(int slot, ItemStack stack, java.util.UUID by, String name) {
        if (stack.isEmpty() || level == null) {
            return;
        }
        if (slot < 0 || slot >= HOOKS || !hooks.get(slot).isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY(),
                    worldPosition.getZ() + 0.5D, stack);
            return;
        }
        hooks.set(slot, stack);
        progress[slot] = 0;
        needs[slot] = needed(stack);
        layers[slot] = by;
        names[slot] = name;
        marks[slot] = ToolPileBlockEntity.FOR_EVERYONE;
        changed();
    }

    private boolean room(int slot, ItemStack stack) {
        ItemStack on = hooks.get(slot);
        return on.isEmpty() || ItemStack.isSameItemSameComponents(on, stack) && on.getCount() < PER_HOOK;
    }

    // ------------------------------------------------------------ holding

    @Override
    public int slots() {
        return HOOKS;
    }

    @Override
    public ItemStack at(int slot) {
        return slot >= 0 && slot < HOOKS ? hooks.get(slot) : ItemStack.EMPTY;
    }

    @javax.annotation.Nullable
    @Override
    public java.util.UUID layerOf(int slot) {
        return slot >= 0 && slot < HOOKS ? layers[slot] : null;
    }

    @Override
    public String layerNameOf(int slot) {
        return slot >= 0 && slot < HOOKS ? names[slot] : "";
    }

    @Override
    public int markOf(int slot) {
        return slot >= 0 && slot < HOOKS ? marks[slot] : ToolPileBlockEntity.FOR_EVERYONE;
    }

    @Override
    public void setMark(int slot, int mark) {
        if (slot >= 0 && slot < HOOKS) {
            marks[slot] = Math.floorMod(mark, 3);
            changed();
        }
    }

    @Override
    public ItemStack takeSlot(int slot, ToolPileBlockEntity.Access access) {
        if (!mayTake(slot, access)) {
            return ItemStack.EMPTY;
        }
        ItemStack down = hooks.get(slot);
        clearHook(slot);
        changed();
        return down;
    }

    private void clearHook(int slot) {
        hooks.set(slot, ItemStack.EMPTY);
        progress[slot] = 0;
        needs[slot] = 0;
        layers[slot] = null;
        names[slot] = "";
        marks[slot] = ToolPileBlockEntity.FOR_EVERYONE;
    }

    @javax.annotation.Nullable
    @Override
    public java.util.UUID firstBy() {
        return firstBy;
    }

    @Override
    public String firstName() {
        return firstName;
    }

    @Override
    public String holdingName() {
        return "A cooking rack";
    }

    @Override
    public String extra(int slot) {
        ItemStack stack = at(slot);
        if (stack.isEmpty() || needs[slot] <= 0) {
            return "";
        }
        if (Cooking.isCooked(stack)) {
            return fire() ? "Cooked - it will char" : "Cooked";
        }
        return fire() ? Math.round(100.0F * progress[slot] / needs[slot]) + "% cooked" : "Raw - no fire under it";
    }

    /** Empty-handed: take down the hook nearest where you reached - or whichever has something on it. */
    public void takeDown(ServerPlayer player, Vec3 hit) {
        ToolPileBlockEntity.Access access = dev.hominin.evolution.band.ToolPiles.access(player);
        int slot = hookAt(hit);
        if (!mayTake(slot, access)) {
            slot = -1;
            for (int i = HOOKS - 1; i >= 0; i--) {
                if (mayTake(i, access)) {
                    slot = i;
                    break;
                }
            }
        }
        if (slot < 0 && hooks.stream().anyMatch(s -> !s.isEmpty())) {
            player.displayClientMessage(Component.literal("What hangs here is not for you."), true);
            return;
        }
        if (slot < 0) {
            player.displayClientMessage(Component.literal(fire()
                    ? "Nothing on this part of the spit. Hang meat on it and the fire will cook it."
                    : "Nothing hanging here. Hang food on it to keep it off the ground."), true);
            return;
        }
        ItemStack down = hooks.get(slot);
        boolean raw = !Cooking.isCooked(down) && needs[slot] > 0;
        int left = raw ? Math.max(1, (needs[slot] - progress[slot]) / 20) : 0;
        clearHook(slot);
        if (!player.getInventory().add(down)) {
            player.drop(down, false);
        }
        level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
        if (raw && fire()) {
            player.displayClientMessage(Component.literal("That was not done yet - about " + left + " seconds more."),
                    true);
        }
        changed();
    }

    /** How long this needs over the fire from now: to cook, or to char if it is cooked already. */
    private int needed(ItemStack stack) {
        if (Cooking.isCooked(stack)) {
            return Cooking.chars(stack) ? Cooking.CHAR_TICKS : 0;
        }
        return level == null ? 0 : Cooking.cookTicks(level, stack);
    }

    // ------------------------------------------------------------ over the fire

    public static void serverTick(Level level, BlockPos pos, BlockState state, CookingSpitBlockEntity spit) {
        if (!fireBelow(level, pos)) {
            return;
        }
        boolean changed = false;
        boolean cooking = false;
        for (int i = 0; i < HOOKS; i++) {
            ItemStack stack = spit.hooks.get(i);
            if (stack.isEmpty() || spit.needs[i] <= 0) {
                continue;
            }
            cooking |= !Cooking.isCooked(stack);
            if (++spit.progress[i] < spit.needs[i]) {
                continue;
            }
            ItemStack done;
            if (Cooking.isCooked(stack)) {
                done = new ItemStack(ModItems.CHARRED_MEAT.get(), stack.getCount());
                Spoilage.carry(stack, done);
            } else {
                done = Cooking.cooked(level, stack);
            }
            if (done.isEmpty()) {
                spit.needs[i] = 0;
                continue;
            }
            spit.hooks.set(i, done);
            spit.progress[i] = 0;
            spit.needs[i] = spit.needed(done);
            changed = true;
        }
        if (cooking && level instanceof ServerLevel server && (level.getGameTime() + pos.asLong()) % 30L == 0L) {
            // Fat dripping into the fire, and the smell of it.
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D, 2,
                    0.15D, 0.05D, 0.15D, 0.005D);
            if (level.getRandom().nextInt(3) == 0) {
                level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.4F, 1.4F);
            }
        }
        if (changed) {
            level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.2F);
            spit.changed();
        }
    }

    /** Brought down: everything on it lands on the ground. */
    public void spill() {
        if (level != null) {
            Containers.dropContents(level, worldPosition, hooks);
        }
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
        ContainerHelper.saveAllItems(tag, hooks, true, registries);
        tag.putIntArray("Progress", progress);
        tag.putIntArray("Needs", needs);
        tag.putIntArray("Marks", marks);
        net.minecraft.nbt.ListTag who = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < HOOKS; i++) {
            CompoundTag one = new CompoundTag();
            if (layers[i] != null) {
                one.putUUID("By", layers[i]);
            }
            one.putString("Name", names[i]);
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
        for (int i = 0; i < HOOKS; i++) {
            hooks.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, hooks, registries);
        int[] savedProgress = tag.getIntArray("Progress");
        int[] savedNeeds = tag.getIntArray("Needs");
        int[] savedMarks = tag.getIntArray("Marks");
        net.minecraft.nbt.ListTag who = tag.getList("Who", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < HOOKS; i++) {
            progress[i] = i < savedProgress.length ? savedProgress[i] : 0;
            needs[i] = i < savedNeeds.length ? savedNeeds[i] : 0;
            marks[i] = i < savedMarks.length ? savedMarks[i] : 0;
            CompoundTag one = i < who.size() ? who.getCompound(i) : new CompoundTag();
            layers[i] = one.hasUUID("By") ? one.getUUID("By") : null;
            names[i] = one.getString("Name");
        }
        firstBy = tag.hasUUID("FirstBy") ? tag.getUUID("FirstBy") : null;
        firstName = tag.getString("FirstName");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, hooks, true, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
