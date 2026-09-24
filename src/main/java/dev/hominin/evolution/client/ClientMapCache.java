package dev.hominin.evolution.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The country you have walked through, as you saw it: the mental map's ground. Every chunk you pass near
 * is looked at once and remembered as sixteen patches of colour - four blocks to a patch, the way a map
 * would show it - and kept between sessions, per world, in the game folder.
 */
public final class ClientMapCache {
    /** Patches a chunk side: 4 x 4 blocks each. */
    public static final int PATCHES = 4;
    private static final int RADIUS = 6;
    private static final int VERSION = 1;

    private static final Map<Long, byte[]> chunks = new HashMap<>();
    @Nullable
    private static Path file;
    private static boolean dirty;
    private static int ticks;

    /** The map colour of the patch holding this block, or -1 if you have never been near it. */
    public static int colourAt(int x, int z) {
        byte[] patches = chunks.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (patches == null) {
            return -1;
        }
        int packed = patches[((x & 15) >> 2) + PATCHES * ((z & 15) >> 2)] & 0xFF;
        if (packed == 0) {
            return -1;
        }
        MapColor colour = MapColor.byId(packed >> 2);
        int modifier = MapColor.Brightness.byId(packed & 3).modifier;
        int r = (colour.col >> 16 & 255) * modifier / 255;
        int g = (colour.col >> 8 & 255) * modifier / 255;
        int b = (colour.col & 255) * modifier / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static boolean explored(int x, int z) {
        return chunks.containsKey(ChunkPos.asLong(x >> 4, z >> 4));
    }

    // ------------------------------------------------------------ looking about

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ++ticks % 10 != 0
                || mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        int cx = mc.player.blockPosition().getX() >> 4;
        int cz = mc.player.blockPosition().getZ() >> 4;
        int looked = 0;
        boolean refresh = ticks % 600 == 0;
        for (int dx = -RADIUS; dx <= RADIUS && looked < 12; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS && looked < 12; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                long key = ChunkPos.asLong(cx + dx, cz + dz);
                // New ground is looked at as it comes into view; ground close by is looked at again now and then.
                if (chunks.containsKey(key) && !(refresh && Math.abs(dx) <= 2 && Math.abs(dz) <= 2)) {
                    continue;
                }
                if (sample(mc, cx + dx, cz + dz)) {
                    looked++;
                }
            }
        }
        if (ticks % 6000 == 0 && dirty) {
            save();
        }
    }

    private static boolean sample(Minecraft mc, int cx, int cz) {
        if (!(mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)
                || chunk.isEmpty()) {
            return false;
        }
        byte[] patches = new byte[PATCHES * PATCHES];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int pz = 0; pz < PATCHES; pz++) {
            int previous = Integer.MIN_VALUE;
            for (int px = 0; px < PATCHES; px++) {
                int x = (cx << 4) + px * 4 + 2;
                int z = (cz << 4) + pz * 4 + 2;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                pos.set(x, y, z);
                BlockState state = chunk.getBlockState(pos);
                MapColor colour = state.getMapColor(mc.level, pos);
                int steps = 0;
                while (colour == MapColor.NONE && y > mc.level.getMinBuildHeight() && steps++ < 8) {
                    pos.setY(--y);
                    state = chunk.getBlockState(pos);
                    colour = state.getMapColor(mc.level, pos);
                }
                // Shade the way a map does: ground rising to the north is lit, falling away is dark.
                int north = previous == Integer.MIN_VALUE ? y
                        : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, Math.max(0, (z & 15) - 4));
                MapColor.Brightness brightness;
                if (!state.getFluidState().isEmpty()) {
                    int depth = 0;
                    while (depth < 10 && !chunk.getBlockState(pos.setY(y - depth - 1)).getFluidState().isEmpty()) {
                        depth++;
                    }
                    brightness = depth > 6 ? MapColor.Brightness.LOW : depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
                } else {
                    brightness = y > north ? MapColor.Brightness.HIGH : y < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                previous = y;
                patches[px + PATCHES * pz] = (byte) (colour.id << 2 | brightness.id);
            }
        }
        chunks.put(ChunkPos.asLong(cx, cz), patches);
        dirty = true;
        return true;
    }

    // ------------------------------------------------------------ keeping it

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        chunks.clear();
        file = fileFor(Minecraft.getInstance());
        load();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (dirty) {
            save();
        }
        chunks.clear();
        file = null;
    }

    @Nullable
    private static Path fileFor(Minecraft mc) {
        String key;
        if (mc.getSingleplayerServer() != null) {
            key = "sp_" + mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize().getFileName();
        } else if (mc.getCurrentServer() != null) {
            key = "mp_" + mc.getCurrentServer().ip;
        } else {
            return null;
        }
        String dimension = "overworld";
        key = (key + "_" + dimension).replaceAll("[^A-Za-z0-9._-]", "_");
        return mc.gameDirectory.toPath().resolve("hominin_evolution").resolve("maps").resolve(key + ".bin");
    }

    private static void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != VERSION) {
                return;
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long key = in.readLong();
                byte[] patches = new byte[PATCHES * PATCHES];
                in.readFully(patches);
                chunks.put(key, patches);
            }
        } catch (IOException e) {
            chunks.clear();
        }
        dirty = false;
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(VERSION);
                out.writeInt(chunks.size());
                for (var entry : chunks.entrySet()) {
                    out.writeLong(entry.getKey());
                    out.write(entry.getValue());
                }
            }
            dirty = false;
        } catch (IOException ignored) {
            // A map that cannot be written is only lost for next time.
        }
    }

    private ClientMapCache() {
    }
}
