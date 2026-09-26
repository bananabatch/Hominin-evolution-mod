package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Things in a pile marked "for players only": no band member takes them, only people. Whoever marked them can name
 * exactly which players - with nobody named, any player may. The list is the marker's, and goes for everything they
 * mark that way.
 */
public final class PilePlayers extends SavedData {
    public static final int ACTION = 84;
    private static final String NAME = "hominin_evolution_pile_players";
    private static final int DONE = -1;
    private static final int ANYONE = -2;

    /** Who each player lets take what they marked for players only. Empty or missing: any player. */
    private final Map<UUID, Set<UUID>> allowed = new HashMap<>();
    /** The players offered in each player's open list, in order: a pick is an index into it. */
    private static final Map<UUID, List<UUID>> offered = new HashMap<>();

    private static PilePlayers of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PilePlayers::new, PilePlayers::load), NAME);
    }

    /** Whether this player may take what the layer marked for players only. */
    public static boolean allows(MinecraftServer server, @Nullable UUID layer, UUID taker) {
        if (layer == null || layer.equals(taker)) {
            return true;
        }
        Set<UUID> named = of(server).allowed.get(layer);
        return named == null || named.isEmpty() || named.contains(taker);
    }

    /** The list, to tick players on and off: every other player about, and "anyone" to clear it. */
    public static void open(ServerPlayer player) {
        Set<UUID> named = of(player.server).allowed.getOrDefault(player.getUUID(), Set.of());
        List<UUID> others = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player) {
                continue;
            }
            labels.add((named.contains(other.getUUID()) ? "[x] " : "[ ] ") + other.getGameProfile().getName());
            values.add(others.size());
            others.add(other.getUUID());
        }
        offered.put(player.getUUID(), others);
        labels.add(named.isEmpty() ? "[x] Any player" : "Any player (clear the list)");
        values.add(ANYONE);
        labels.add("Done");
        values.add(DONE);
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION, "What you mark for players "
                + "only: which players may take it? " + (named.isEmpty() ? "Any player can, as it is."
                : named.size() + " named."), labels, values));
    }

    public static void choose(ServerPlayer player, int value) {
        if (value == DONE) {
            offered.remove(player.getUUID());
            return;
        }
        PilePlayers data = of(player.server);
        Set<UUID> named = data.allowed.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        if (value == ANYONE) {
            named.clear();
        } else {
            List<UUID> others = offered.getOrDefault(player.getUUID(), List.of());
            if (value < 0 || value >= others.size()) {
                return;
            }
            UUID picked = others.get(value);
            if (!named.remove(picked)) {
                named.add(picked);
            }
        }
        data.setDirty();
        open(player);
    }

    public static void forget(UUID player) {
        offered.remove(player);
    }

    private static PilePlayers load(CompoundTag tag, HolderLookup.Provider registries) {
        PilePlayers data = new PilePlayers();
        for (Tag entry : tag.getList("Allowed", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) entry;
            Set<UUID> named = new HashSet<>();
            for (Tag id : one.getList("Players", Tag.TAG_INT_ARRAY)) {
                named.add(net.minecraft.nbt.NbtUtils.loadUUID(id));
            }
            data.allowed.put(one.getUUID("Layer"), named);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : allowed.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Layer", entry.getKey());
            ListTag players = new ListTag();
            for (UUID id : entry.getValue()) {
                players.add(net.minecraft.nbt.NbtUtils.createUUID(id));
            }
            one.put("Players", players);
            list.add(one);
        }
        tag.put("Allowed", list);
        return tag;
    }

    private PilePlayers() {
    }
}
