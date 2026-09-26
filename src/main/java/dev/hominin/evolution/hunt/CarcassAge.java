package dev.hominin.evolution.hunt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Carcasses rot. A kill lies two days before there is nothing left worth having; a giant's, three. Past halfway,
 * nobody claimed it - nobody butchered it - and what meat is still on it has turned. At the end it is gone, and a
 * few bones lie where it was.
 *
 * <p>Counted from when anyone first came near enough for the world to be keeping time there (the first random tick),
 * which for a fresh kill is as good as the moment it fell.
 */
public final class CarcassAge extends SavedData {
    private static final String NAME = "hominin_evolution_carcass_ages";
    public static final int CARCASS_DAYS = 2;
    public static final int GIANT_DAYS = 3;
    private static final long DAY = 24000L;

    private final Map<Long, Long> since = new HashMap<>();

    private static CarcassAge of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CarcassAge::new, CarcassAge::load), NAME);
    }

    private static CarcassAge load(CompoundTag tag, HolderLookup.Provider registries) {
        CarcassAge data = new CarcassAge();
        for (Tag entry : tag.getList("Carcasses", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            data.since.put(c.getLong("Pos"), c.getLong("Since"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        since.forEach((pos, time) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos);
            c.putLong("Since", time);
            list.add(c);
        });
        tag.put("Carcasses", list);
        return tag;
    }

    /** Game ticks this carcass has lain, starting the clock if nobody had. */
    private static long lain(ServerLevel level, BlockPos pos) {
        CarcassAge data = of(level);
        long now = level.getGameTime();
        Long start = data.since.get(pos.asLong());
        if (start == null) {
            data.since.put(pos.asLong(), now);
            data.setDirty();
            return 0L;
        }
        return now - start;
    }

    /** A random tick: past its days, the carcass is gone - a few bones where it lay. */
    public static void age(ServerLevel level, BlockPos pos, int days) {
        if (lain(level, pos) < days * DAY) {
            return;
        }
        forget(level, pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 0.6F, 0.8F);
        Block.popResource(level, pos, new ItemStack(Items.BONE, 1 + level.random.nextInt(days + 1)));
    }

    /** Butchered late: past halfway, the raw meat on it has turned. */
    public static void spoilIfLeft(ServerLevel level, BlockPos pos, List<ItemStack> drops, int days) {
        if (lain(level, pos) < days * DAY / 2) {
            return;
        }
        for (ItemStack stack : drops) {
            if (dev.hominin.evolution.food.Spoilage.isRawMeat(stack) || stack.is(Items.BEEF)) {
                dev.hominin.evolution.food.Spoilage.spoil(stack);
            }
        }
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        CarcassAge data = of(level);
        if (data.since.remove(pos.asLong()) != null) {
            data.setDirty();
        }
    }
}
