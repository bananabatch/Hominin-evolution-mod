package dev.hominin.evolution.survival;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Deep time. Every evolution is an age later, and the count of days starts again from the first: day one, the start
 * of the rains. The world's own clock runs on; this is the day the current age began, and every day and season is
 * counted from it.
 */
public final class Era extends SavedData {
    private static final String NAME = "hominin_evolution_era";

    /** The world day this age began on. */
    private long startDay;

    private static Era of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Era::new, Era::load), NAME);
    }

    /** The day the current age began - zero on the client, which does not count days. */
    public static long startDay(Level level) {
        return level instanceof ServerLevel server ? of(server).startDay : 0L;
    }

    /** A new age: today is day one again. */
    public static void begin(ServerLevel level) {
        Era era = of(level);
        era.startDay = level.getDayTime() / 24000L;
        era.setDirty();
    }

    private static Era load(CompoundTag tag, HolderLookup.Provider registries) {
        Era era = new Era();
        era.startDay = tag.getLong("StartDay");
        return era;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("StartDay", startDay);
        return tag;
    }

    private Era() {
    }
}
