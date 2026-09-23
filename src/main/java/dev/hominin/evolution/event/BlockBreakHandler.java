package dev.hominin.evolution.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class BlockBreakHandler {
    /**
     * A material the player cannot get through until they have the right tool.
     * Checked in order, so a block in more than one tag is governed by the first
     * rule that matches.
     */
    private record ToolGate(TagKey<Block> blocks, TagKey<Item> tools, String denial) {
    }

    private static final List<ToolGate> TOOL_GATES = List.of(
            new ToolGate(ModTags.Blocks.REQUIRES_DIGGING_STICK, ModTags.Items.DIGGING_TOOLS,
                    "You need a digging stick to break this."),
            new ToolGate(ModTags.Blocks.REQUIRES_STONE_TOOL, ModTags.Items.STONE_TOOLS,
                    "You need a stone tool to break this."),
            new ToolGate(ModTags.Blocks.REQUIRES_HAND_AXE, ModTags.Items.HAND_AXE_TOOLS,
                    "You cannot fell wood with your hands."),
            new ToolGate(ModTags.Blocks.REQUIRES_HAFTED_TOOL, ModTags.Items.HAFTED_TOOLS,
                    "You have no tool that can break this."),
            new ToolGate(ModTags.Blocks.REQUIRES_HAMMERSTONE, ModTags.Items.HAMMERSTONES,
                    "It is far too big to pull apart. Crack it open with a hammerstone."));

    private static final long BLOCKED_MESSAGE_COOLDOWN_TICKS = 60L;

    private static final float NESTING_MATERIAL_CHANCE = 0.5F;

    /**
     * Stripping foliage by hand almost never brings a usable branch down with it.
     * A chopper is the tool for the job and gets a real rate; the bare-hand number
     * is there so an early hominin is not hard-blocked, not as a supply.
     */
    private static final float LONG_BRANCH_LEAF_DROP_CHANCE = 0.015F;
    private static final float LONG_BRANCH_LEAF_CHOPPER_CHANCE = 0.2F;

    /** The rare fallen limb that is already heavy at one end. */
    private static final float WOODEN_CLUB_LEAF_DROP_CHANCE = 0.005F;

    private static final Map<UUID, Long> lastBlockedMessageTick = new HashMap<>();

    private BlockBreakHandler() {
    }

    /**
     * Grass cut with an edge rather than torn up by hand. Pulling a handful of stems out
     * of the ground gets you a handful of stems; cutting them low with a flake gets you
     * the length of the stalk, which is the part you can do anything with.
     */
    private static void cutThatch(Player player, BlockState state, BlockPos pos) {
        if (player.level().isClientSide() || !state.is(ModTags.Blocks.THATCH_SOURCE)
                || !player.getMainHandItem().is(ModTags.Items.CUTTING_EDGE)) {
            return;
        }
        if (player.getRandom().nextFloat() < THATCH_CHANCE) {
            Block.popResource(player.level(), pos,
                    new ItemStack(dev.hominin.evolution.ModItems.THATCH.get()));
        }
    }

    /** Not every handful of grass is worth keeping. */
    private static final float THATCH_CHANCE = 0.55F;

    /**
     * A deposit is a place, not a possession. They exist as items so worldgen and the
     * creative tab can place them, but a survival player holding one could put a quarry
     * down wherever they stood and work it forever - the seam was supposed to be the
     * thing you had to go and find.
     */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isCreative()
                || isDeveloperMode(player)) {
            return;
        }
        if (isWorkedNotMined(event.getPlacedBlock())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                    "Stone like this is not something you set down. You find it where it lies."));
        }
    }

    /**
     * Authoritative server-side enforcement: even if something bypasses the mining
     * speed check, the block still refuses to actually break.
     */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getPlayer();
        BlockState state = event.getState();
        if (!canBreak(player, state)) {
            event.setCanceled(true);
            sendBlockedMessage(player, state);
            return;
        }
        if (choppedBranchOff(player, state, event.getPos())) {
            event.setCanceled(true);
            return;
        }
        cutThatch(player, state, event.getPos());
        maybeDropLongBranch(player, state, event.getPos());
    }

    /**
     * Runs on both sides so the client never shows the block-cracking animation
     * for a block the player cannot break. Cancelling here means the break never
     * completes, so the explanatory chat message also has to come from this path
     * (server side only, rate-limited because this fires every tick while mining).
     */
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!canBreak(player, event.getState())) {
            event.setCanceled(true);
            if (!player.level().isClientSide()) {
                sendBlockedMessage(player, event.getState());
            }
        }
    }

    /**
     * Outcrops and mounds are landmarks, not blocks to be mined. A deposit is a face you
     * work with a hammerstone - over and over, because the seam outlasts you - and a
     * termite mound is a larder that never moves. Pulling either apart by hand skipped the
     * whole point of finding one, and carrying a quarry in your pocket skipped the walk.
     */
    private static boolean isWorkedNotMined(BlockState state) {
        return state.is(dev.hominin.evolution.ModBlocks.CHERT_DEPOSIT.get())
                || state.is(dev.hominin.evolution.ModBlocks.QUARTZITE_DEPOSIT.get())
                || state.is(dev.hominin.evolution.ModBlocks.LIMESTONE_DEPOSIT.get())
                || state.is(dev.hominin.evolution.ModBlocks.TERMITE_MOUND.get());
    }

    /**
     * The stages that have finally worked out how to take the world apart on purpose.
     * Until then the landscape is scenery you live in, not material you mine - which is
     * the whole point of the early game, and was being given away by a default that let
     * anything not explicitly gated be punched out with bare hands.
     */
    private static boolean mayMineFreely(Player player) {
        if (player.level().isClientSide()) {
            return false;
        }
        String stage = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA)
                .getStage().getPath();
        return stage.equals("homo_sapiens") || stage.equals("homo_neanderthalensis");
    }

    private static boolean canBreak(Player player, BlockState state) {
        if (player.isCreative() || isDeveloperMode(player)) {
            return true;
        }
        if (isWorkedNotMined(state)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        for (ToolGate gate : TOOL_GATES) {
            if (state.is(gate.blocks())) {
                // A chopper cannot fell a tree, but it can hack a branch off one,
                // so it has to be allowed to swing at logs. See choppedBranchOff.
                if (state.is(net.minecraft.tags.BlockTags.LOGS) && held.is(ModTags.Items.CHOPPERS)) {
                    return true;
                }
                return held.is(gate.tools());
            }
        }
        // Anything the tool gates do not name has to earn its way out by being soft
        // enough to pull up by hand. Stone, ore and worked wood are not.
        return mayMineFreely(player) || state.is(ModTags.Blocks.TAKEABLE_BY_HAND);
    }

    /**
     * Hacking at a tree with a chopper tears a branch loose but leaves the trunk
     * standing - a hand axe is what actually fells wood. Returns true if the break
     * should be cancelled so the log survives.
     */
    private static boolean choppedBranchOff(Player player, BlockState state, BlockPos pos) {
        // Only a trunk has branches to hack off - and a hand axe fells it outright instead.
        if (player.isCreative() || !state.is(net.minecraft.tags.BlockTags.LOGS)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModTags.Items.CHOPPERS) || held.is(ModTags.Items.HAND_AXE_TOOLS)) {
            return false;
        }
        Level level = player.level();
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                new ItemStack(ModItems.LONG_BRANCH.get())));
        held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        player.sendSystemMessage(Component.literal("You hack a branch loose. The trunk still stands."));
        return true;
    }

    /**
     * The occasional full-length branch that comes down with the foliage. A minor
     * bonus - chopping at a trunk is the dependable route.
     */
    private static void maybeDropLongBranch(Player player, BlockState state, BlockPos pos) {
        if (player.isCreative() || !state.is(BlockTags.LEAVES)) {
            return;
        }
        Level level = player.level();
        // Leafy twigs come away with almost every handful - the stuff of a night's nest.
        if (level.getRandom().nextFloat() < NESTING_MATERIAL_CHANCE) {
            dropAt(level, pos, ModItems.NESTING_MATERIAL.get());
        }
        // A club-shaped limb is rare enough that it does not undercut making one,
        // and it is checked first so it cannot be crowded out by the branch roll.
        if (level.getRandom().nextFloat() < WOODEN_CLUB_LEAF_DROP_CHANCE) {
            dropAt(level, pos, ModItems.WOODEN_CLUB.get());
            player.displayClientMessage(Component.literal(
                    "A dead limb comes down with the leaves - heavy at one end."), true);
            return;
        }
        float chance = player.getMainHandItem().is(ModTags.Items.CHOPPERS)
                ? LONG_BRANCH_LEAF_CHOPPER_CHANCE
                : LONG_BRANCH_LEAF_DROP_CHANCE;
        if (level.getRandom().nextFloat() >= chance) {
            return;
        }
        dropAt(level, pos, ModItems.LONG_BRANCH.get());
    }

    private static void dropAt(Level level, BlockPos pos, Item item) {
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                new ItemStack(item)));
    }

    private static void sendBlockedMessage(Player player, BlockState state) {
        long gameTime = player.level().getGameTime();
        Long lastTick = lastBlockedMessageTick.get(player.getUUID());
        if (lastTick != null && gameTime - lastTick < BLOCKED_MESSAGE_COOLDOWN_TICKS) {
            return;
        }
        lastBlockedMessageTick.put(player.getUUID(), gameTime);
        if (isWorkedNotMined(state)) {
            player.sendSystemMessage(Component.literal(
                    state.is(dev.hominin.evolution.ModBlocks.TERMITE_MOUND.get())
                            ? "The mound is baked hard as fired clay. Fish it with a stick instead."
                            : "You cannot pull the rock apart. Strike a face off it with a hammerstone."));
            return;
        }
        for (ToolGate gate : TOOL_GATES) {
            if (state.is(gate.blocks())) {
                player.sendSystemMessage(Component.literal(gate.denial()));
                return;
            }
        }
        player.sendSystemMessage(Component.literal(
                "Your hands are not for this. Nothing you know how to make would get through it."));
    }

    /**
     * Developer mode is stored per-player rather than globally, so one tester can
     * wave through gates on a server without unlocking them for everyone else.
     */
    private static boolean isDeveloperMode(Player player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).isDeveloperMode();
    }

    static void forget(UUID playerId) {
        lastBlockedMessageTick.remove(playerId);
    }
}
