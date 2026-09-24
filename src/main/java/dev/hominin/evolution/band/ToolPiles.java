package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.item.AcheuleanToolItem;
import dev.hominin.evolution.item.StoneMaterial;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Tools kept on the ground, for everyone. Lay a hammerstone, a flake, a chopper, a hand axe, a cleaver or a
 * multitool down on your own ground (sneak-use the ground with it in hand) and it stays there: the first one
 * laid down is where the band keeps its tools, and the rest go beside it, eight to a pile. Your band takes
 * what it needs from the pile when it has nothing to work with, and puts back what it has spare. Other bands
 * keep piles of their own at their camps - and raiders take what is lying about.
 *
 * <p>Piles are remembered by whose they are - a player (for their band), a wild band by its id - so a band's
 * people can find them, and the map can show where your tools are.
 */
public final class ToolPiles extends SavedData {
    private static final String NAME = "hominin_evolution_tool_piles";

    /** Every pile, by whose it is. The first in each list is where the band keeps its tools. */
    private final Map<UUID, List<BlockPos>> piles = new HashMap<>();
    /** Bands whose camp has been given its pile: once. Take it all and it is gone. */
    private final java.util.Set<UUID> stocked = new java.util.HashSet<>();

    private static ToolPiles of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ToolPiles::new, ToolPiles::load), NAME);
    }

    /** Whose tools a band member uses: its own band's, or its leader's. */
    @Nullable
    public static UUID ownerOf(BandMember member) {
        return member.isWild() ? member.getBandId() : member.getLeader();
    }

    // ------------------------------------------------------------ reading them

    /** Where this owner's piles are, first pile first - any that are no longer there dropped as they are found. */
    public static List<BlockPos> piles(ServerLevel level, UUID owner) {
        ToolPiles data = of(level);
        List<BlockPos> list = data.piles.get(owner);
        if (list == null) {
            return List.of();
        }
        boolean changed = list.removeIf(pos -> level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof ToolPileBlockEntity));
        if (changed) {
            data.setDirty();
        }
        return new ArrayList<>(list);
    }

    /** Where the band keeps its tools: in its store, if it has built one - otherwise the first pile laid down. */
    @Nullable
    public static BlockPos store(ServerLevel level, UUID owner) {
        List<BlockPos> list = piles(level, owner);
        for (BlockPos pos : list) {
            if (level.isLoaded(pos) && dev.hominin.evolution.build.Sites.isStore(level, pos)) {
                return pos;
            }
        }
        return list.isEmpty() ? null : list.get(0);
    }

    /** Bones, kept for their marrow: laid down wherever a tool can be, and cracked when someone is hungry. */
    public static boolean isBone(ItemStack stack) {
        return stack.is(ModItems.LONG_BONE.get()) || stack.is(ModItems.RIB.get())
                || stack.is(net.minecraft.world.item.Items.BONE);
    }

    /** What can be laid down anywhere on your own ground: stone tools, and bones. */
    public static boolean layable(ItemStack stack) {
        return stack.is(ModTags.Items.STONE_TOOLS) || isBone(stack);
    }

    /** A player takes what is for everyone, and what they marked for themselves. */
    public static ToolPileBlockEntity.Access access(net.minecraft.world.entity.player.Player player) {
        UUID id = player.getUUID();
        return (layer, mark) -> mark == ToolPileBlockEntity.FOR_EVERYONE || id.equals(layer)
                // Laid down by one of your band for those close to them: you, if you are.
                || mark == ToolPileBlockEntity.FOR_CLOSE && layer != null && player.level() instanceof ServerLevel level
                        && level.getEntity(layer) instanceof BandMember laid && laid.isLedBy(player)
                        && laid.getBond() >= dev.hominin.evolution.block.NestOwners.SHARE_BOND;
    }

    /**
     * One of the band takes what is for everyone - and what their leader marked for those close to them, if they
     * are (bond 4 and up). Never what was marked for the leader alone.
     */
    public static ToolPileBlockEntity.Access access(BandMember member) {
        return (layer, mark) -> mark == ToolPileBlockEntity.FOR_EVERYONE || member.getUUID().equals(layer)
                || mark == ToolPileBlockEntity.FOR_CLOSE && layer != null && (layer.equals(member.getLeader())
                        ? member.getBond() >= dev.hominin.evolution.block.NestOwners.SHARE_BOND
                        : member.affinityWith(layer) >= 3);
    }

    private static String nameOf(net.minecraft.world.entity.Entity who) {
        if (who instanceof BandMember member) {
            member.ensureName();
        }
        return who.getName().getString();
    }

    /** What can be put by in a store: anything you carry that is not a block to be placed. */
    public static boolean storable(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof net.minecraft.world.item.BlockItem)
                && !(stack.getItem() instanceof dev.hominin.evolution.item.PileBundleItem);
    }

    /** Whether this spot is in a store of this player's. */
    private static boolean ownStore(ServerLevel level, ServerPlayer player, BlockPos pos) {
        dev.hominin.evolution.build.Sites.Site site = dev.hominin.evolution.build.Sites.roomAt(level, pos);
        return site != null && site.use() == dev.hominin.evolution.build.Sites.Use.STORE
                && site.owner().equals(player.getUUID());
    }

    /** One tool; a whole heap of anything else. */
    private static ItemStack laid(ItemStack stack) {
        return stack.split(stack.is(ModTags.Items.STONE_TOOLS) || !stack.isStackable() ? 1 : stack.getCount());
    }

    /** A heap put down in a store by one of the band: onto a pile there, or a new one. */
    public static boolean layDown(ServerLevel level, BlockPos at, UUID owner, ItemStack stack, BandMember by) {
        if (level.getBlockEntity(at) instanceof ToolPileBlockEntity pile) {
            return pile.addStack(stack, by.getUUID(), nameOf(by)) > 0;
        }
        if (!placeable(level, at)) {
            return false;
        }
        newPile(level, at, owner, ItemStack.EMPTY, by.getUUID(), nameOf(by));
        if (level.getBlockEntity(at) instanceof ToolPileBlockEntity pile) {
            pile.addStack(stack, by.getUUID(), nameOf(by));
        }
        return true;
    }

    /** Somewhere clear on the floor of the store this spot is in, for another pile. */
    @Nullable
    public static BlockPos storeSpot(ServerLevel level, BlockPos near) {
        dev.hominin.evolution.build.Sites.Site site = dev.hominin.evolution.build.Sites.roomAt(level, near);
        dev.hominin.evolution.build.Footprint footprint = site != null ? site.footprint() : null;
        if (footprint == null) {
            return null;
        }
        BlockPos best = null;
        for (BlockPos pos : footprint.inside()) {
            if (pos.getY() == site.origin().getY() && placeable(level, pos)
                    && (best == null || pos.distSqr(near) < best.distSqr(near))) {
                best = pos;
            }
        }
        return best;
    }

    /** How many tools this owner has lying in piles that are loaded now. */
    public static int toolsIn(ServerLevel level, UUID owner) {
        int count = 0;
        for (BlockPos pos : piles(level, owner)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile) {
                count += pile.count();
            }
        }
        return count;
    }

    private static void register(ServerLevel level, UUID owner, BlockPos pos) {
        ToolPiles data = of(level);
        List<BlockPos> list = data.piles.computeIfAbsent(owner, k -> new ArrayList<>());
        if (!list.contains(pos)) {
            list.add(pos.immutable());
            data.setDirty();
        }
    }

    /** A pile is gone - broken up, or emptied. */
    public static void forgetPile(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        ToolPiles data = of(level);
        boolean changed = false;
        for (var entry : data.piles.entrySet()) {
            if (owner == null || entry.getKey().equals(owner)) {
                changed |= entry.getValue().remove(pos);
            }
        }
        data.piles.values().removeIf(List::isEmpty);
        if (changed) {
            data.setDirty();
        }
    }

    // ------------------------------------------------------------ the player

    /**
     * Sneak-use at the ground with a tool in hand. On your own ground it is laid down; off it, the ground is
     * searched as it always was, and nothing is left lying about for anyone to walk off with.
     */
    public static void placeRequest(ServerPlayer player, BlockPos clicked) {
        ItemStack stack = player.getMainHandItem();
        ServerLevel level = player.serverLevel();
        boolean tool = stack.is(ModTags.Items.STONE_TOOLS);
        if (!storable(stack) || player.distanceToSqr(clicked.getCenter()) > 49.0D || !level.isLoaded(clicked)) {
            return;
        }
        BlockState clickedState = level.getBlockState(clicked);
        BlockPos at = clickedState.is(ModBlocks.TOOL_PILE.get()) ? clicked : clicked.above();
        boolean store = ownStore(level, player, at);
        if (!layable(stack) && !store) {
            // Only a store takes anything but stone tools and bones.
            return;
        }
        if (clickedState.is(ModBlocks.TOOL_PILE.get())) {
            layOn(player, clicked, stack);
            return;
        }
        if (!store && !dev.hominin.evolution.hunt.Predation.onOwnGround(player, at)) {
            if (clickedState.is(ModTags.Blocks.FORAGING_GROUND)) {
                dev.hominin.evolution.event.EvolutionEventHandler.forageAt(player, clicked);
                return;
            }
            player.displayClientMessage(Component.literal("You only leave tools lying on your own ground - out here "
                    + "anyone could walk off with them."), true);
            return;
        }
        if (level.getBlockState(at).is(ModBlocks.TOOL_PILE.get())) {
            layOn(player, at, stack);
            return;
        }
        if (!placeable(level, at)) {
            player.displayClientMessage(Component.literal("There is no room to lay it down there."), true);
            return;
        }
        boolean first = tool && store(level, player.getUUID()) == null;
        ItemStack one = laid(stack);
        newPile(level, at, player.getUUID(), one, player.getUUID(), nameOf(player));
        PileEthics.returned(player, one);
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!tool) {
            storedFood(player, one);
        }
        if (first) {
            player.sendSystemMessage(Component.literal("You lay it down. This is where the band keeps its tools now: "
                    + "they take what they need from it and put back what they have spare, and so will you. It is "
                    + "on your map - and anyone who raids you will know where to look.").withStyle(ChatFormatting.AQUA));
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.TOOL_PILE);
        }
    }

    private static boolean placeable(ServerLevel level, BlockPos at) {
        return level.getBlockState(at).canBeReplaced() && level.getFluidState(at).isEmpty()
                && ModBlocks.TOOL_PILE.get().defaultBlockState().canSurvive(level, at);
    }

    private static BlockPos newPile(ServerLevel level, BlockPos at, @Nullable UUID owner, ItemStack first) {
        return newPile(level, at, owner, first, null, "");
    }

    private static BlockPos newPile(ServerLevel level, BlockPos at, @Nullable UUID owner, ItemStack first,
            @Nullable UUID by, String byName) {
        level.setBlock(at, ModBlocks.TOOL_PILE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(at) instanceof ToolPileBlockEntity pile) {
            pile.setOwner(owner);
            if (!first.isEmpty()) {
                pile.addStack(first.copy(), by, byName);
            }
        }
        if (owner != null) {
            register(level, owner, at);
        }
        level.playSound(null, at, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
        return at;
    }

    /** Somewhere beside a full pile for the next one: tools go together. */
    @Nullable
    private static BlockPos besides(ServerLevel level, BlockPos pile) {
        for (int ring = 1; ring <= 2; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy : new int[] {0, 1, -1}) {
                        BlockPos at = pile.offset(dx, dy, dz);
                        if (level.getBlockState(at).is(ModBlocks.TOOL_PILE.get())
                                && level.getBlockEntity(at) instanceof ToolPileBlockEntity other && !other.isFull()) {
                            return at;
                        }
                        if (placeable(level, at)) {
                            return at;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** One more on the pile - or, the pile being full, beside it. */
    public static void layOn(ServerPlayer player, BlockPos pos, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        boolean inStore = ownStore(level, player, pos);
        if (!(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile) || !layable(stack) && !(storable(stack) && inStore)) {
            return;
        }
        UUID owner = pile.owner();
        if (owner != null && !owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.literal("That is somebody else's pile."), true);
            return;
        }
        if (owner == null && (inStore || dev.hominin.evolution.hunt.Predation.onOwnGround(player, pos))) {
            // An old deposit on your ground: yours now.
            pile.setOwner(player.getUUID());
            register(level, player.getUUID(), pos);
            owner = player.getUUID();
        }
        String name = nameOf(player);
        // A tool at a time; a heap of bones or food all at once.
        ItemStack heap = stack.is(ModTags.Items.STONE_TOOLS) || !stack.isStackable() ? stack.copyWithCount(1) : stack.copy();
        int offered = heap.getCount();
        pile.addStack(heap, player.getUUID(), name);
        if (!heap.isEmpty()) {
            // The pile is full: the rest goes beside it - in a store, on its floor.
            BlockPos next = inStore ? storeSpot(level, pos) : besides(level, pos);
            if (next == null && heap.getCount() == offered) {
                player.displayClientMessage(Component.literal(inStore ? "The store is full - there is no floor left in it."
                        : "The pile is as big as it gets, and there is no room beside it."), true);
                return;
            }
            if (next != null && level.getBlockEntity(next) instanceof ToolPileBlockEntity beside) {
                beside.addStack(heap, player.getUUID(), name);
            } else if (next != null) {
                newPile(level, next, owner, heap, player.getUUID(), name);
                heap = ItemStack.EMPTY;
            }
        }
        int laid = offered - heap.getCount();
        ItemStack put = stack.copyWithCount(laid);
        stack.shrink(laid);
        PileEthics.returned(player, put);
        level.playSound(null, pos, put.is(ModTags.Items.STONE_TOOLS) ? SoundEvents.STONE_PLACE
                : SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
        player.swing(InteractionHand.MAIN_HAND, true);
        storedFood(player, put);
    }

    /** The top one, picked up. Taken from another band's pile under their eyes, it is theft. */
    public static void pickUp(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile)) {
            return;
        }
        if (pile.isEmpty()) {
            level.removeBlock(pos, false);
            return;
        }
        int slot = pile.topSlot(access(player));
        UUID layer = pile.layerOf(slot);
        ItemStack top = pile.takeSlot(slot, access(player));
        if (!top.isEmpty()) {
            taking(player, pile, layer, top);
        }
        if (top.isEmpty()) {
            player.displayClientMessage(Component.literal("What is left there is marked for someone else. "
                    + "(Left-click it, or look at it and press the work key, to see what is in it.)"), true);
            return;
        }
        UUID owner = pile.owner();
        if (!player.getInventory().add(top)) {
            player.drop(top, false);
        }
        level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.6F, 1.2F);
        if (pile.isEmpty()) {
            level.removeBlock(pos, false);
        }
        theft(player, level, owner);
    }

    /**
     * Something taken off your own band's pile that somebody else laid down: fine once, noticed if you keep two of a
     * kind - and if you have never laid anything on it yourself, somebody says so.
     */
    public static void taking(ServerPlayer player, ToolPileBlockEntity pile, @Nullable UUID layer, ItemStack taken) {
        if (!player.getUUID().equals(pile.owner()) || player.getUUID().equals(layer)) {
            return;
        }
        PileEthics.borrowed(player, taken);
        if (!pile.contributed(player.getUUID()) && pile.othersContributed(player.getUUID())) {
            PileEthics.freeloaded(player);
        }
    }

    /** Whether a pile could lie here. */
    public static boolean canPileAt(ServerLevel level, BlockPos at) {
        return placeable(level, at);
    }

    /** The whole pile gathered up, to be set down somewhere else. Your band's, or an old deposit on your ground. */
    public static ItemStack gather(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile)) {
            return ItemStack.EMPTY;
        }
        UUID owner = pile.owner();
        boolean ours = player.getUUID().equals(owner) || owner == null
                && (dev.hominin.evolution.hunt.Predation.onOwnGround(player, pos) || ownStore(level, player, pos));
        if (!ours) {
            player.displayClientMessage(Component.literal("That is not your band's to move."), true);
            return ItemStack.EMPTY;
        }
        int count = 0;
        int foreign = 0;
        for (int slot = 0; slot < ToolPileBlockEntity.MAX; slot++) {
            if (!pile.at(slot).isEmpty()) {
                count++;
                // Somebody's - not yours, and not left by nobody at all.
                if (pile.layerOf(slot) != null && !player.getUUID().equals(pile.layerOf(slot))) {
                    foreign++;
                }
            }
        }
        CompoundTag saved = pile.bundle(level.registryAccess());
        saved.putUUID("Owner", player.getUUID());
        pile.takeAll();
        level.removeBlock(pos, false);
        ItemStack bundle = dev.hominin.evolution.item.PileBundleItem.of(saved, count, foreign);
        level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 0.9F);
        player.displayClientMessage(Component.literal(foreign > 0
                ? "You gather the pile up. " + foreign + " of those things are other people's - set it down again soon, "
                        + "on your own ground."
                : "You gather your pile up. Set it down wherever it should be.").withStyle(ChatFormatting.AQUA), false);
        return bundle;
    }

    /** A gathered pile, set down: on your own ground, or in your store. */
    public static boolean setDown(ServerPlayer player, BlockPos at, ItemStack bundle) {
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(at.getCenter()) > 49.0D || !level.isLoaded(at)) {
            return false;
        }
        if (!placeable(level, at)) {
            player.displayClientMessage(Component.literal("There is no room to set it down there."), true);
            return false;
        }
        if (!ownStore(level, player, at) && !dev.hominin.evolution.hunt.Predation.onOwnGround(player, at)) {
            player.displayClientMessage(Component.literal("Set it down on your own ground - out here anyone could "
                    + "walk off with it."), true);
            return false;
        }
        level.setBlock(at, ModBlocks.TOOL_PILE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(at) instanceof ToolPileBlockEntity pile) {
            pile.unbundle(dev.hominin.evolution.item.PileBundleItem.contents(bundle).copy(), level.registryAccess());
            pile.setOwner(player.getUUID());
        }
        register(level, player.getUUID(), at);
        level.playSound(null, at, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.displayClientMessage(Component.literal("You set the pile down, everything in it as it lay.")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    /** Taken from another band's pile under their eyes: theft. */
    public static void theft(ServerPlayer player, ServerLevel level, @Nullable UUID owner) {
        if (owner != null && !owner.equals(player.getUUID())) {
            Bands.Record band = Bands.get(level, owner);
            if (band != null) {
                BandMember saw = Relations.nearestMember(level, band, player, 24.0D);
                if (saw != null) {
                    saw.ensureName();
                    player.sendSystemMessage(Component.literal("<" + saw.getName().getString() + "> ")
                            .withStyle(ChatFormatting.GOLD).append(Component.literal("Those are ours! Put it back.")
                                    .withStyle(ChatFormatting.RED)));
                    Relations.change(player, band, -6, "you took from their tools");
                }
            }
        }
    }

    // ------------------------------------------------------------ the band

    /** The nearest of an owner's piles, within reach, that has something wanted in it. */
    @Nullable
    public static BlockPos pileWith(ServerLevel level, UUID owner, BlockPos near, double within, Predicate<ItemStack> wanted) {
        return pileWith(level, owner, near, within, wanted, ToolPileBlockEntity.ANYONE);
    }

    /** The same, counting only what this taker may have. */
    @Nullable
    public static BlockPos pileWith(ServerLevel level, UUID owner, BlockPos near, double within, Predicate<ItemStack> wanted,
            ToolPileBlockEntity.Access access) {
        BlockPos best = null;
        double bestDistance = within * within;
        for (BlockPos pos : piles(level, owner)) {
            double distance = pos.distSqr(near);
            if (distance >= bestDistance || !level.isLoaded(pos)
                    || !(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile)) {
                continue;
            }
            if (pile.total(wanted, access) > 0) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The first-laid pile, if it is within reach: where spare tools go. */
    @Nullable
    public static BlockPos storeWithin(ServerLevel level, UUID owner, BlockPos near, double within) {
        BlockPos store = store(level, owner);
        return store != null && store.distSqr(near) <= within * within && level.isLoaded(store) ? store : null;
    }

    public static ItemStack takeFrom(ServerLevel level, BlockPos pos, Predicate<ItemStack> wanted) {
        return takeFrom(level, pos, wanted, ToolPileBlockEntity.ANYONE);
    }

    public static ItemStack takeFrom(ServerLevel level, BlockPos pos, Predicate<ItemStack> wanted,
            ToolPileBlockEntity.Access access) {
        if (!(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile)) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = pile.take(wanted, access);
        if (pile.isEmpty()) {
            level.removeBlock(pos, false);
        }
        return taken;
    }

    /** A spare tool put back: on the store, or beside it when it is full. */
    public static boolean putBack(ServerLevel level, UUID owner, BlockPos store, ItemStack tool) {
        return putBack(level, owner, store, tool, null, "");
    }

    /** The same, remembering which of the band put it there. */
    public static boolean putBack(ServerLevel level, UUID owner, BlockPos store, ItemStack tool, @Nullable BandMember by) {
        return putBack(level, owner, store, tool, by != null ? by.getUUID() : null, by != null ? nameOf(by) : "");
    }

    private static boolean putBack(ServerLevel level, UUID owner, BlockPos store, ItemStack tool, @Nullable UUID by,
            String byName) {
        if (!(level.getBlockEntity(store) instanceof ToolPileBlockEntity pile)) {
            return false;
        }
        if (!pile.isFull()) {
            level.playSound(null, store, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.5F, 1.3F);
            return pile.add(tool, by, byName);
        }
        for (BlockPos pos : piles(level, owner)) {
            if (pos.distSqr(store) < 36.0D && level.getBlockEntity(pos) instanceof ToolPileBlockEntity other
                    && !other.isFull()) {
                return other.add(tool, by, byName);
            }
        }
        BlockPos next = besides(level, store);
        if (next == null) {
            return false;
        }
        if (level.getBlockEntity(next) instanceof ToolPileBlockEntity beside) {
            return beside.add(tool, by, byName);
        }
        newPile(level, next, owner, tool, by, byName);
        return true;
    }

    // ------------------------------------------------------------ raids

    /** Raiders take what is lying about: up to this many tools from the owner's loaded piles. */
    public static List<ItemStack> plunder(ServerLevel level, UUID owner, int max) {
        List<ItemStack> taken = new ArrayList<>();
        for (BlockPos pos : piles(level, owner)) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile)) {
                continue;
            }
            while (taken.size() < max && !pile.isEmpty()) {
                taken.add(pile.takeTop());
            }
            if (pile.isEmpty()) {
                level.removeBlock(pos, false);
            }
            if (taken.size() >= max) {
                break;
            }
        }
        return taken;
    }

    /** Food put by in the store is food shared: the band sees it. A bone put by is marrow for later. */
    private static void storedFood(ServerPlayer player, ItemStack laid) {
        if (isBone(laid)) {
            player.displayClientMessage(Component.literal("Put by for later - whoever is hungry and has a stone "
                    + "cracks it for the marrow.").withStyle(ChatFormatting.GRAY), true);
        }
        if (laid.has(net.minecraft.core.component.DataComponents.FOOD)) {
            player.displayClientMessage(Component.literal("You put " + laid.getCount() + " "
                    + laid.getHoverName().getString().toLowerCase() + " by in the store, for anyone who is hungry.")
                    .withStyle(ChatFormatting.GREEN), true);
            dev.hominin.evolution.band.Cohesion.addLimited(player, "stored_food", 1, 12000L);
        }
    }

    /** "3 hand axes, a flake" - for telling someone what went. */
    public static String describe(List<ItemStack> tools) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (ItemStack tool : tools) {
            counts.merge(tool.getHoverName().getString().toLowerCase(), tool.getCount(), Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((name, n) -> parts.add(n == 1 ? "a " + name : n + " " + name + (name.endsWith("s") ? "" : "s")));
        return String.join(", ", parts);
    }

    // ------------------------------------------------------------ other bands, and the long dead

    /** Whether a species kept stone tools at all. */
    public static boolean usesStone(ResourceLocation species) {
        String path = species.getPath();
        return !(path.equals("ardipithecus") || path.startsWith("australopithecus") || path.startsWith("paranthropus"));
    }

    /** A band's camp has its own pile: laid down the first time its people are about. */
    public static void stockCamp(ServerLevel level, UUID band, BlockPos home, ResourceLocation species, RandomSource random) {
        ToolPiles data = of(level);
        if (!usesStone(species) || data.stocked.contains(band) || !piles(level, band).isEmpty() || !level.isLoaded(home)) {
            return;
        }
        data.stocked.add(band);
        data.setDirty();
        BlockPos at = null;
        for (int attempt = 0; attempt < 8 && at == null; attempt++) {
            int x = home.getX() + random.nextInt(7) - 3;
            int z = home.getZ() + random.nextInt(7) - 3;
            BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (placeable(level, spot)) {
                at = spot;
            }
        }
        if (at == null) {
            return;
        }
        int count = 2 + random.nextInt(3);
        BlockPos pile = newPile(level, at, band, tool(random, species, 0.0F));
        for (int i = 1; i < count; i++) {
            putBack(level, band, pile, tool(random, species, 0.0F));
        }
    }

    /**
     * An old deposit: tools somebody left and never came back for - weathered, some near worn out, but stone
     * is stone. Nobody's, so anybody's. Returns where the pile went, or null if there was nowhere to put it.
     */
    @Nullable
    public static BlockPos deposit(ServerLevel level, BlockPos at, RandomSource random, boolean acheulean) {
        BlockPos spot = null;
        for (int attempt = 0; attempt < 10 && spot == null; attempt++) {
            int x = at.getX() + (attempt == 0 ? 0 : random.nextInt(9) - 4);
            int z = at.getZ() + (attempt == 0 ? 0 : random.nextInt(9) - 4);
            BlockPos candidate = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (placeable(level, candidate)) {
                spot = candidate;
            }
        }
        if (spot == null) {
            return null;
        }
        int count = 3 + random.nextInt(4);
        newPile(level, spot, null, tool(random, acheulean, 0.4F + random.nextFloat() * 0.45F));
        if (level.getBlockEntity(spot) instanceof ToolPileBlockEntity pile) {
            for (int i = 1; i < count; i++) {
                pile.add(tool(random, acheulean, 0.4F + random.nextFloat() * 0.45F));
            }
        }
        // The waste of the work lies about it: broken cores, the odd cobble.
        for (int i = 0; i < 3; i++) {
            int x = spot.getX() + random.nextInt(7) - 3;
            int z = spot.getZ() + random.nextInt(7) - 3;
            BlockPos rock = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockState scatter = (random.nextBoolean() ? ModBlocks.CHERT_ROCK.get() : ModBlocks.BASALT_ROCK.get())
                    .defaultBlockState();
            if (level.getBlockState(rock).canBeReplaced() && level.getFluidState(rock).isEmpty()
                    && scatter.canSurvive(level, rock)) {
                level.setBlock(rock, scatter, 3);
            }
        }
        return spot;
    }

    /** A band gone: its piles are nobody's now - old deposits. Returns where its store was, if anywhere. */
    @Nullable
    public static BlockPos orphan(ServerLevel level, UUID owner) {
        ToolPiles data = of(level);
        List<BlockPos> list = data.piles.remove(owner);
        if (list == null || list.isEmpty()) {
            return null;
        }
        data.setDirty();
        for (BlockPos pos : list) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile) {
                pile.setOwner(null);
            }
        }
        return list.get(0);
    }

    /** One tool as this kind makes them: ergaster's crude hand axes, rudolfensis without the multi tool. */
    public static ItemStack tool(RandomSource random, ResourceLocation species, float worn) {
        ItemStack stack;
        do {
            stack = tool(random, Bands.erectusOn(species), worn);
        } while (stack.is(ModItems.OLDOWAN_MULTITOOL.get()) && !Species.makesMultitool(species));
        if (stack.getItem() instanceof AcheuleanToolItem made && Species.bestAcheuleanTier(species) > 0) {
            int quality = Species.capQuality(species, AcheuleanToolItem.qualityOf(stack));
            StoneMaterial material = StoneMaterial.of(stack);
            int damage = stack.getDamageValue();
            stack = StoneMaterial.stamp(made.make(quality), material);
            stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, damage));
        }
        return stack;
    }

    /** One tool of the era, of any good stone, worn down by this much. */
    public static ItemStack tool(RandomSource random, boolean acheulean, float worn) {
        Item[] oldowan = {ModItems.FLAKE.get(), ModItems.FLAKE.get(), ModItems.CHOPPER.get(), ModItems.HAMMERSTONE.get(),
                ModItems.OLDOWAN_MULTITOOL.get()};
        Item[] later = {ModItems.HAND_AXE.get(), ModItems.HAND_AXE.get(), ModItems.CLEAVER.get(), ModItems.FLAKE.get(),
                ModItems.HAMMERSTONE.get(), ModItems.CHOPPER.get()};
        Item item = acheulean ? later[random.nextInt(later.length)] : oldowan[random.nextInt(oldowan.length)];
        ItemStack stack = item instanceof AcheuleanToolItem acheuleanTool ? acheuleanTool.make(1 + random.nextInt(4))
                : new ItemStack(item);
        StoneMaterial[] stones = {StoneMaterial.QUARTZITE, StoneMaterial.QUARTZITE, StoneMaterial.BASALT, StoneMaterial.CHERT,
                StoneMaterial.LIMESTONE, StoneMaterial.OBSIDIAN};
        StoneMaterial.stamp(stack, stones[random.nextInt(stones.length)]);
        if (worn > 0.0F && stack.isDamageableItem()) {
            stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, (int) (stack.getMaxDamage() * worn)));
        }
        return stack;
    }

    /** Developer: a full pile of the player's band's tools, just in front of them. */
    public static boolean devPile(ServerPlayer player, List<ItemStack> tools) {
        ServerLevel level = player.serverLevel();
        BlockPos ahead = player.blockPosition().relative(player.getDirection(), 2);
        BlockPos at = new BlockPos(ahead.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ahead.getX(),
                ahead.getZ()), ahead.getZ());
        if (tools.isEmpty() || !placeable(level, at)) {
            return false;
        }
        newPile(level, at, player.getUUID(), tools.get(0).copy(), player.getUUID(), nameOf(player));
        if (level.getBlockEntity(at) instanceof ToolPileBlockEntity pile) {
            for (int i = 1; i < Math.min(ToolPileBlockEntity.MAX, tools.size()); i++) {
                pile.add(tools.get(i).copy(), player.getUUID(), nameOf(player));
            }
        }
        return true;
    }

    // ------------------------------------------------------------ saving

    private static ToolPiles load(CompoundTag tag, HolderLookup.Provider registries) {
        ToolPiles data = new ToolPiles();
        for (Tag entry : tag.getList("Piles", Tag.TAG_COMPOUND)) {
            CompoundTag owner = (CompoundTag) entry;
            if (!owner.hasUUID("Owner")) {
                continue;
            }
            List<BlockPos> list = new ArrayList<>();
            for (Tag pos : owner.getList("At", Tag.TAG_LONG)) {
                list.add(BlockPos.of(((LongTag) pos).getAsLong()));
            }
            if (!list.isEmpty()) {
                data.piles.put(owner.getUUID("Owner"), list);
            }
        }
        for (Tag entry : tag.getList("Stocked", Tag.TAG_COMPOUND)) {
            data.stocked.add(((CompoundTag) entry).getUUID("Band"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : piles.entrySet()) {
            CompoundTag owner = new CompoundTag();
            owner.putUUID("Owner", entry.getKey());
            ListTag at = new ListTag();
            for (BlockPos pos : entry.getValue()) {
                at.add(LongTag.valueOf(pos.asLong()));
            }
            owner.put("At", at);
            list.add(owner);
        }
        tag.put("Piles", list);
        ListTag stockedList = new ListTag();
        for (UUID band : stocked) {
            CompoundTag b = new CompoundTag();
            b.putUUID("Band", band);
            stockedList.add(b);
        }
        tag.put("Stocked", stockedList);
        return tag;
    }

    private ToolPiles() {
    }
}
