package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** What two players are to each other: for now, mates. Kept for the whole world, on the overworld. */
public final class PlayerTies extends SavedData {
    private static final String NAME = "hominin_evolution_player_ties";

    /** Each player's mate - both ways round. */
    private final Map<UUID, UUID> mates = new HashMap<>();

    private static PlayerTies of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerTies::new, PlayerTies::load), NAME);
    }

    /** The player this one has taken as a mate, if any. */
    @Nullable
    public static UUID mateOf(MinecraftServer server, UUID player) {
        return of(server).mates.get(player);
    }

    public static boolean areMates(MinecraftServer server, UUID a, UUID b) {
        return b.equals(mateOf(server, a));
    }

    /** The two of them, and nobody else: whoever either had before is let go. */
    public static void pair(MinecraftServer server, UUID a, UUID b) {
        PlayerTies data = of(server);
        unpair(data, a);
        unpair(data, b);
        data.mates.put(a, b);
        data.mates.put(b, a);
        data.setDirty();
    }

    private static void unpair(PlayerTies data, UUID player) {
        UUID was = data.mates.remove(player);
        if (was != null) {
            data.mates.remove(was);
        }
    }

    private static PlayerTies load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerTies data = new PlayerTies();
        for (Tag entry : tag.getList("Mates", Tag.TAG_COMPOUND)) {
            CompoundTag pair = (CompoundTag) entry;
            data.mates.put(pair.getUUID("A"), pair.getUUID("B"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : mates.entrySet()) {
            CompoundTag pair = new CompoundTag();
            pair.putUUID("A", entry.getKey());
            pair.putUUID("B", entry.getValue());
            list.add(pair);
        }
        tag.put("Mates", list);
        return tag;
    }

    private PlayerTies() {
    }
}
