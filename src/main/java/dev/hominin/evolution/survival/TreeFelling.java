package dev.hominin.evolution.survival;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A tree cut through at the trunk comes down. Everything of it above the cut topples the way you were facing - away
 * from you - and lands as a trunk lying along the ground, to be worked where it fell; the crown breaks up as it
 * hits, and leaves what leaves leave: sticks, saplings. Whatever it lands on, it lands hard.
 *
 * <p>Only a tree: logs with a living crown on them. Something built out of logs stays standing, and so does a tree
 * with another trunk still holding it up.
 */
public final class TreeFelling {
    private static final int MOST_LOGS = 160;
    private static final int MOST_LEAVES = 600;
    /** How far a crown reaches from its wood: vanilla leaves die past six. */
    private static final int LEAF_REACH = 6;
    private static final float DAMAGE_PER_BLOCK = 2.0F;
    private static final int MOST_DAMAGE = 16;

    private record Cut(ResourceKey<Level> dimension, BlockPos pos, Direction fall) {
    }

    /** Cuts waiting for the break to go through - the next tick, after anything else has had its say. */
    private static final List<Cut> pending = new ArrayList<>();

    /** A log broken by a player. If there is tree above it, it will come down. */
    public static void broke(ServerPlayer player, BlockPos pos, BlockState state) {
        if (player.isCreative() || !state.is(BlockTags.LOGS)) {
            return;
        }
        pending.add(new Cut(player.level().dimension(), pos.immutable(), player.getDirection()));
    }

    public static void tick(ServerLevel level) {
        if (pending.isEmpty()) {
            return;
        }
        List<Cut> now = new ArrayList<>();
        for (Iterator<Cut> it = pending.iterator(); it.hasNext();) {
            Cut cut = it.next();
            if (cut.dimension().equals(level.dimension())) {
                now.add(cut);
                it.remove();
            }
        }
        for (Cut cut : now) {
            // Something stopped the break: the log is still there, and so is the tree.
            if (!level.getBlockState(cut.pos()).is(BlockTags.LOGS)) {
                fell(level, cut);
            }
        }
    }

    private static void fell(ServerLevel level, Cut cut) {
        BlockPos base = cut.pos();
        // Another trunk beside this one at the cut still holds it up.
        for (BlockPos near : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 0, 1))) {
            if (!near.equals(base) && level.getBlockState(near).is(BlockTags.LOGS)) {
                return;
            }
        }
        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        for (BlockPos start : BlockPos.betweenClosed(base.offset(-1, 1, -1), base.offset(1, 1, 1))) {
            if (level.getBlockState(start).is(BlockTags.LOGS)) {
                open.add(start.immutable());
            }
        }
        while (!open.isEmpty()) {
            BlockPos at = open.poll();
            if (!logs.add(at)) {
                continue;
            }
            if (logs.size() > MOST_LOGS) {
                return;
            }
            for (BlockPos next : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
                if (next.getY() > base.getY() && !logs.contains(next) && level.getBlockState(next).is(BlockTags.LOGS)) {
                    open.add(next.immutable());
                }
            }
        }
        if (logs.isEmpty()) {
            return;
        }
        Set<BlockPos> leaves = crown(level, logs);
        if (leaves.isEmpty()) {
            // No living crown: a stack of logs somebody built, not a tree.
            return;
        }
        // The crown breaks up first, so the trunk has somewhere to fall.
        int shown = 0;
        for (BlockPos leaf : leaves) {
            BlockState state = level.getBlockState(leaf);
            Block.dropResources(state, level, leaf);
            level.removeBlock(leaf, false);
            if (shown++ % 5 == 0) {
                level.levelEvent(2001, leaf, Block.getId(state));
            }
        }
        List<BlockPos> order = new ArrayList<>(logs);
        order.sort(Comparator.comparingInt(BlockPos::getY));
        Direction fall = cut.fall();
        for (BlockPos log : order) {
            BlockState state = level.getBlockState(log);
            if (state.hasProperty(RotatedPillarBlock.AXIS)) {
                state = state.setValue(RotatedPillarBlock.AXIS, fall.getAxis());
            }
            int height = log.getY() - base.getY();
            FallingBlockEntity falling = FallingBlockEntity.fall(level, log, state);
            // Thrown out along the fall in proportion to the height, so it comes to rest lying the tree's length.
            double speed = Math.sqrt(height / 50.0D) * 1.1D;
            falling.setDeltaMovement(new Vec3(fall.getStepX() * speed, 0.05D, fall.getStepZ() * speed));
            falling.setHurtsEntities(DAMAGE_PER_BLOCK, MOST_DAMAGE);
        }
        level.playSound(null, base, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, base.relative(fall, 4), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(base, 16.0D)) {
                player.displayClientMessage(Component.literal("The tree cracks, leans, and comes down."), true);
            }
        }
    }

    /** The living leaves this wood holds up. Leaves someone placed never fall with it. */
    private static Set<BlockPos> crown(ServerLevel level, Set<BlockPos> logs) {
        Set<BlockPos> leaves = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        ArrayDeque<Integer> depth = new ArrayDeque<>();
        for (BlockPos log : logs) {
            for (Direction dir : Direction.values()) {
                open.add(log.relative(dir));
                depth.add(1);
            }
        }
        while (!open.isEmpty() && leaves.size() < MOST_LEAVES) {
            BlockPos at = open.poll();
            int d = depth.poll();
            if (leaves.contains(at) || logs.contains(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (!state.is(BlockTags.LEAVES) || state.hasProperty(LeavesBlock.PERSISTENT)
                    && state.getValue(LeavesBlock.PERSISTENT)) {
                continue;
            }
            // Nearer some other tree's wood than this one's: that tree's crown, and it stays.
            if (state.hasProperty(LeavesBlock.DISTANCE) && state.getValue(LeavesBlock.DISTANCE) < d) {
                continue;
            }
            leaves.add(at);
            if (d < LEAF_REACH) {
                for (Direction dir : Direction.values()) {
                    open.add(at.relative(dir));
                    depth.add(d + 1);
                }
            }
        }
        return leaves;
    }

    private TreeFelling() {
    }
}
