package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Leaves torn off a tree grow back. Every handful stripped for sticks, twigs and nesting is remembered, and five to
 * ten minutes later - if nothing has been put in its place, and the tree is still standing - it is back. A tree cut
 * down stays down: with no trunk near, nothing grows.
 */
public final class LeafRegrowth extends SavedData {
    private static final String NAME = "hominin_leaf_regrowth";
    private static final long MIN_TICKS = 6000L;
    private static final int SPREAD_TICKS = 6000;
    /** A trunk within this much, or it was a felled tree's crown and it is not coming back. */
    private static final int TRUNK_REACH = 5;
    private static final int MOST = 8192;

    private record Leaf(BlockState state, long due) {
    }

    private final Map<BlockPos, Leaf> leaves = new HashMap<>();

    private static LeafRegrowth of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(LeafRegrowth::new, LeafRegrowth::load), NAME);
    }

    /** Leaves broken here: they come back in a while. */
    public static void broken(ServerLevel level, BlockPos pos, BlockState state) {
        LeafRegrowth data = of(level);
        if (data.leaves.size() >= MOST) {
            return;
        }
        data.leaves.put(pos.immutable(), new Leaf(state, level.getGameTime() + MIN_TICKS + level.random.nextInt(SPREAD_TICKS)));
        data.setDirty();
    }

    /** Every ten seconds: what is due grows back, where it still can. */
    public static void tickLevel(ServerLevel level) {
        if (level.getGameTime() % 200L != 37L) {
            return;
        }
        LeafRegrowth data = of(level);
        if (data.leaves.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean changed = false;
        for (Iterator<Map.Entry<BlockPos, Leaf>> it = data.leaves.entrySet().iterator(); it.hasNext();) {
            Map.Entry<BlockPos, Leaf> entry = it.next();
            if (entry.getValue().due() > now) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos)) {
                continue;
            }
            it.remove();
            changed = true;
            if (!level.getBlockState(pos).isAir() || !trunkNear(level, pos)) {
                continue;
            }
            BlockState grown = Block.updateFromNeighbourShapes(entry.getValue().state(), level, pos);
            level.setBlock(pos, grown, Block.UPDATE_ALL);
        }
        if (changed) {
            data.setDirty();
        }
    }

    private static boolean trunkNear(ServerLevel level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-TRUNK_REACH, -TRUNK_REACH, -TRUNK_REACH),
                pos.offset(TRUNK_REACH, TRUNK_REACH, TRUNK_REACH))) {
            if (level.getBlockState(near).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    private static LeafRegrowth load(CompoundTag tag, HolderLookup.Provider registries) {
        LeafRegrowth data = new LeafRegrowth();
        var blocks = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        for (Tag entry : tag.getList("Leaves", Tag.TAG_COMPOUND)) {
            CompoundTag leaf = (CompoundTag) entry;
            data.leaves.put(BlockPos.of(leaf.getLong("Pos")),
                    new Leaf(NbtUtils.readBlockState(blocks, leaf.getCompound("State")), leaf.getLong("Due")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        leaves.forEach((pos, leaf) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos.asLong());
            entry.put("State", NbtUtils.writeBlockState(leaf.state()));
            entry.putLong("Due", leaf.due());
            list.add(entry);
        });
        tag.put("Leaves", list);
        return tag;
    }
}
