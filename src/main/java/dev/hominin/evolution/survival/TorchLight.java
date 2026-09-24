package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A burning torch in the hand lights the ground round you. The light is carried the only way the world has:
 * an invisible light where your head is, moved as you move, taken away the moment you put the torch down or
 * it burns out - and cleaned up when you log back in, in case the world was shut with it still lit.
 */
public final class TorchLight {
    private static final int LEVEL = 14;
    private static final String LX = "torchlight_x";
    private static final String LY = "torchlight_y";
    private static final String LZ = "torchlight_z";

    /** Where each player's light is, and in which world. */
    private record Lit(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> world, BlockPos pos) {
    }

    private static final Map<UUID, Lit> lit = new HashMap<>();

    private static boolean holding(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.LIT_TORCH.get()) || player.getOffhandItem().is(ModItems.LIT_TORCH.get());
    }

    /** Every other tick: the light follows the torch. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 2 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Lit old = lit.get(player.getUUID());
        if (old == null && player.tickCount < 40) {
            cleanUp(player);
        }
        BlockPos want = holding(player) && player.isAlive() && !player.isSpectator() ? spotFor(player) : null;
        if (want != null && old != null && want.equals(old.pos()) && old.world() == level.dimension()) {
            return;
        }
        if (old != null) {
            douse(player);
        }
        if (want != null) {
            level.setBlock(want, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LEVEL), 3);
            lit.put(player.getUUID(), new Lit(level.dimension(), want));
            var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
            counters.put(LX, want.getX());
            counters.put(LY, want.getY());
            counters.put(LZ, want.getZ());
        }
    }

    /** Where the light can go: at your head, else your feet - only ever into empty air. */
    @Nullable
    private static BlockPos spotFor(ServerPlayer player) {
        BlockPos head = BlockPos.containing(player.getEyePosition());
        ServerLevel level = player.serverLevel();
        Lit held = lit.get(player.getUUID());
        BlockPos old = held == null || held.world() != level.dimension() ? null : held.pos();
        for (BlockPos pos : new BlockPos[] {head, player.blockPosition(), head.above()}) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || (pos.equals(old) && state.is(Blocks.LIGHT))) {
                return pos;
            }
        }
        return null;
    }

    private static void unlight(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** Leaving, dying, or changing worlds: the light goes with you. */
    public static void douse(ServerPlayer player) {
        Lit old = lit.remove(player.getUUID());
        if (old == null) {
            return;
        }
        ServerLevel world = player.server.getLevel(old.world());
        if (world != null) {
            unlight(world, old.pos());
        }
    }

    /** A light left burning by a world that was shut with a torch in hand. */
    private static void cleanUp(ServerPlayer player) {
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        if (!counters.containsKey(LX)) {
            return;
        }
        BlockPos stale = new BlockPos(counters.get(LX), counters.get(LY), counters.get(LZ));
        counters.remove(LX);
        counters.remove(LY);
        counters.remove(LZ);
        if (player.serverLevel().isLoaded(stale)) {
            unlight(player.serverLevel(), stale);
        }
    }

    private TorchLight() {
    }
}
