package dev.hominin.evolution.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.hominin.evolution.network.RevealPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Country you have only been told about, drawn onto your map. Somebody who knows a place tells you the lie of the
 * land around it - water, open grass, forest, hills - and it goes on the map in the colours of the ground, darker
 * than what you have seen yourself (it is hearsay), without anybody having to walk it. Worked out from the shape of
 * the world alone: nothing out there is generated to do it.
 */
public final class LandReveal {
    private static final int PATCHES = 4;
    /** How much of the country round a place a telling covers. */
    public static final int AROUND_PLACE = 40;
    public static final int MOST_GROUND = 96;

    /** Collects patches to send in one go. */
    public static final class Telling {
        private final Map<Long, byte[]> chunks = new LinkedHashMap<>();

        public Telling around(ServerLevel level, BlockPos centre, int radius) {
            int r = Math.min(radius, MOST_GROUND);
            int cx0 = (centre.getX() - r) >> 4;
            int cx1 = (centre.getX() + r) >> 4;
            int cz0 = (centre.getZ() - r) >> 4;
            int cz1 = (centre.getZ() + r) >> 4;
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    int mx = (cx << 4) + 8 - centre.getX();
                    int mz = (cz << 4) + 8 - centre.getZ();
                    long key = ChunkPos.asLong(cx, cz);
                    if (mx * mx + mz * mz > r * r || chunks.containsKey(key) || chunks.size() >= 1024) {
                        continue;
                    }
                    chunks.put(key, chunk(level, cx, cz));
                }
            }
            return this;
        }

        public void send(ServerPlayer player) {
            if (chunks.isEmpty()) {
                return;
            }
            List<Long> keys = new ArrayList<>(chunks.keySet());
            byte[] data = new byte[keys.size() * PATCHES * PATCHES];
            for (int i = 0; i < keys.size(); i++) {
                System.arraycopy(chunks.get(keys.get(i)), 0, data, i * PATCHES * PATCHES, PATCHES * PATCHES);
            }
            PacketDistributor.sendToPlayer(player, new RevealPayload(keys, data));
        }
    }

    public static Telling telling() {
        return new Telling();
    }

    private static byte[] chunk(ServerLevel level, int cx, int cz) {
        byte[] patches = new byte[PATCHES * PATCHES];
        for (int pz = 0; pz < PATCHES; pz++) {
            for (int px = 0; px < PATCHES; px++) {
                int x = (cx << 4) + px * 4 + 2;
                int z = (cz << 4) + pz * 4 + 2;
                Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                        QuartPos.fromBlock(x), QuartPos.fromBlock(level.getSeaLevel()), QuartPos.fromBlock(z),
                        level.getChunkSource().randomState().sampler());
                MapColor colour = colourOf(biome);
                // Hearsay is drawn dark: known of, not seen.
                patches[px + PATCHES * pz] = (byte) (colour.id << 2 | MapColor.Brightness.LOW.id);
            }
        }
        return patches;
    }

    /** What a place looks like from above, as far as its kind of country goes. */
    private static MapColor colourOf(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
            return MapColor.WATER;
        }
        if (biome.is(BiomeTags.IS_BEACH)) {
            return MapColor.SAND;
        }
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            return MapColor.COLOR_ORANGE;
        }
        if (biome.is(net.minecraft.world.level.biome.Biomes.DESERT)) {
            return MapColor.SAND;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
            return MapColor.STONE;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_TAIGA)) {
            return MapColor.PLANT;
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return MapColor.COLOR_YELLOW;
        }
        if (biome.value().getBaseTemperature() < 0.15F) {
            return MapColor.SNOW;
        }
        return MapColor.GRASS;
    }

    private LandReveal() {
    }
}
