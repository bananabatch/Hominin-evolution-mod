package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Digging for roots. Under a tree - dirt or grass with a trunk close by - a hand axe or a digging stick works the
 * ground loose and brings up roots: three strokes, and a handful or two. They keep a body going when there is
 * nothing else - but live on them, and the grit wears a tooth through (see {@link Diseases}).
 */
public final class Roots {
    private static final int STROKES = 3;
    /** A spot dug out is dug out for a while. */
    private static final long REST_TICKS = 6000L;

    private record Digging(BlockPos pos, int strokes, long lastAt) {
    }

    private static final Map<UUID, Digging> digging = new HashMap<>();
    private static final Map<BlockPos, Long> dugOut = new HashMap<>();

    public static boolean digsRoots(ItemStack stack) {
        return stack.is(ModTags.Items.HAND_AXE_TOOLS) || stack.is(ModItems.DIGGING_STICK.get());
    }

    /** Dirt under a tree: a trunk within three blocks across and a few up. */
    public static boolean underATree(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(BlockTags.DIRT) || !level.getBlockState(pos.above()).getCollisionShape(level,
                pos.above()).isEmpty()) {
            return false;
        }
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-3, 1, -3), pos.offset(3, 4, 3))) {
            if (level.getBlockState(near).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isShiftKeyDown() || !digsRoots(held)
                || !underATree(event.getLevel(), event.getPos())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        dig(player, event.getPos().immutable(), held);
    }

    private static void dig(ServerPlayer player, BlockPos pos, ItemStack tool) {
        Level level = player.level();
        long now = level.getGameTime();
        Long rested = dugOut.get(pos);
        if (rested != null && now < rested) {
            player.displayClientMessage(Component.literal("This ground is dug out. Try under another tree."), true);
            return;
        }
        Digging was = digging.get(player.getUUID());
        if (was != null && now - was.lastAt() < 4L) {
            return;
        }
        int strokes = was != null && was.pos().equals(pos) && now - was.lastAt() < 60L ? was.strokes() + 1 : 1;
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 0.8F, 0.9F);
        level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
        if (strokes < STROKES) {
            digging.put(player.getUUID(), new Digging(pos, strokes, now));
            player.displayClientMessage(Component.literal("Digging for roots... (" + strokes + "/" + STROKES + ")"), true);
            return;
        }
        digging.remove(player.getUUID());
        dugOut.put(pos, now + REST_TICKS);
        if (dugOut.size() > 512) {
            dugOut.values().removeIf(until -> until < now);
        }
        tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        int count = 1 + player.getRandom().nextInt(2);
        Block.popResource(level, pos.above(), new ItemStack(ModItems.ROOTS.get(), count));
        player.displayClientMessage(Component.literal("Roots - tough and gritty, but food."), true);
        dev.hominin.evolution.band.Territory.usedResource(player, pos);
    }

    public static void forget(UUID player) {
        digging.remove(player);
    }

    private Roots() {
    }
}
