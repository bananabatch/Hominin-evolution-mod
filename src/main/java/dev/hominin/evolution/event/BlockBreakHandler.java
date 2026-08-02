package dev.hominin.evolution.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                    "You have no tool that can break this."));

    private static final long BLOCKED_MESSAGE_COOLDOWN_TICKS = 60L;

    private static final float LONG_BRANCH_LEAF_DROP_CHANCE = 0.06F;

    private static final Map<UUID, Long> lastBlockedMessageTick = new HashMap<>();

    private BlockBreakHandler() {
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

    private static boolean canBreak(Player player, BlockState state) {
        if (player.isCreative()) {
            return true;
        }
        ItemStack held = player.getMainHandItem();
        for (ToolGate gate : TOOL_GATES) {
            if (state.is(gate.blocks())) {
                // A chopper cannot fell a tree, but it can hack a branch off one,
                // so it has to be allowed to swing at logs. See choppedBranchOff.
                if (state.is(ModTags.Blocks.REQUIRES_HAND_AXE) && held.is(ModItems.CHOPPER.get())) {
                    return true;
                }
                return held.is(gate.tools());
            }
        }
        return true;
    }

    /**
     * Hacking at a tree with a chopper tears a branch loose but leaves the trunk
     * standing - a hand axe is what actually fells wood. Returns true if the break
     * should be cancelled so the log survives.
     */
    private static boolean choppedBranchOff(Player player, BlockState state, BlockPos pos) {
        if (player.isCreative() || !state.is(ModTags.Blocks.REQUIRES_HAND_AXE)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.CHOPPER.get())) {
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
        if (level.getRandom().nextFloat() >= LONG_BRANCH_LEAF_DROP_CHANCE) {
            return;
        }
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                new ItemStack(ModItems.LONG_BRANCH.get())));
    }

    private static void sendBlockedMessage(Player player, BlockState state) {
        long gameTime = player.level().getGameTime();
        Long lastTick = lastBlockedMessageTick.get(player.getUUID());
        if (lastTick != null && gameTime - lastTick < BLOCKED_MESSAGE_COOLDOWN_TICKS) {
            return;
        }
        lastBlockedMessageTick.put(player.getUUID(), gameTime);
        for (ToolGate gate : TOOL_GATES) {
            if (state.is(gate.blocks())) {
                player.sendSystemMessage(Component.literal(gate.denial()));
                return;
            }
        }
    }

    static void forget(UUID playerId) {
        lastBlockedMessageTick.remove(playerId);
    }
}
