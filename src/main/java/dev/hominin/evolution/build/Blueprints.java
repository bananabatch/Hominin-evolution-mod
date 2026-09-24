package dev.hominin.evolution.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Every blueprint there is. The mod's own are data ({@code data/hominin_evolution/hominin/blueprints}); a file of
 * the same name in {@code config/hominin_evolution/blueprints} replaces one, and {@code /hominin blueprint capture}
 * writes such a file from something actually built in the world - so a build can be made by hand, exactly as
 * wanted, and then be the blueprint.
 *
 * <p>The server sends what it has to each client ({@link dev.hominin.evolution.network.BlueprintsPayload}), so the
 * ghosts a client draws are always the server's.
 */
public final class Blueprints extends SimpleJsonResourceReloadListener {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile Map<ResourceLocation, Blueprint> all = Map.of();
    /** What the data packs had, before any override - so an override can be taken away again. */
    private static volatile Map<ResourceLocation, String> bundled = Map.of();

    public Blueprints() {
        super(new Gson(), "hominin/blueprints");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> found, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, String> texts = new LinkedHashMap<>();
        found.forEach((id, json) -> texts.put(id, json.toString()));
        bundled = Map.copyOf(texts);
        rebuild();
    }

    /** The data packs' blueprints, with the config folder's on top. */
    public static void rebuild() {
        Map<ResourceLocation, String> texts = new LinkedHashMap<>(bundled);
        Path dir = overrideDir();
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String name = file.getFileName().toString();
                    ResourceLocation id = ResourceLocation.tryBuild(HomininEvolutionMod.MODID,
                            name.substring(0, name.length() - 5));
                    if (id != null) {
                        texts.put(id, Files.readString(file, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException e) {
                HomininEvolutionMod.LOGGER.error("Could not read blueprint overrides in {}", dir, e);
            }
        }
        Map<ResourceLocation, Blueprint> parsed = new LinkedHashMap<>();
        texts.forEach((id, text) -> {
            try {
                parsed.put(id, Blueprint.parse(id, text));
            } catch (RuntimeException e) {
                HomininEvolutionMod.LOGGER.error("Blueprint {} is broken: {}", id, e.getMessage());
            }
        });
        set(parsed);
        HomininEvolutionMod.LOGGER.info("Loaded {} blueprints", parsed.size());
    }

    private static void set(Map<ResourceLocation, Blueprint> parsed) {
        List<Blueprint> sorted = new ArrayList<>(parsed.values());
        sorted.sort(Comparator.comparingInt(Blueprint::order).thenComparing(b -> b.id().toString()));
        Map<ResourceLocation, Blueprint> map = new LinkedHashMap<>();
        for (Blueprint blueprint : sorted) {
            map.put(blueprint.id(), blueprint);
        }
        all = java.util.Collections.unmodifiableMap(map);
    }

    /** What the server sent: on the client, the only blueprints there are. */
    public static void fromServer(List<String> ids, List<String> texts) {
        Map<ResourceLocation, Blueprint> parsed = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(ids.size(), texts.size()); i++) {
            ResourceLocation id = ResourceLocation.parse(ids.get(i));
            // Unchanged ones keep their object, so nothing built from them has to be worked out again.
            Blueprint known = all.get(id);
            if (known != null && known.json().equals(texts.get(i))) {
                parsed.put(id, known);
                continue;
            }
            try {
                parsed.put(id, Blueprint.parse(id, texts.get(i)));
            } catch (RuntimeException e) {
                HomininEvolutionMod.LOGGER.error("Blueprint {} from the server is broken: {}", id, e.getMessage());
            }
        }
        set(parsed);
    }

    /** In order: the first you can build first. */
    public static List<Blueprint> list() {
        return List.copyOf(all.values());
    }

    @Nullable
    public static Blueprint get(ResourceLocation id) {
        return all.get(id);
    }

    public static Path overrideDir() {
        return FMLPaths.CONFIGDIR.get().resolve(HomininEvolutionMod.MODID).resolve("blueprints");
    }

    // ------------------------------------------------------------ capturing a build

    /**
     * Writes what stands between two corners as a blueprint, with its door on the given side, over the one of
     * that name. Air under something of the build is its room; air with nothing over it is left out. Returns
     * what it made, or throws with what went wrong.
     */
    public static Blueprint capture(ServerLevel level, BlockPos a, BlockPos b, Direction door, String name) throws IOException {
        Direction forward = door.getOpposite();
        Direction right = forward.getClockWise();
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());
        int width = right.getAxis() == Direction.Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
        int depth = forward.getAxis() == Direction.Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
        int height = maxY - minY + 1;
        if (width * depth * height > 32 * 32 * 24) {
            throw new IOException("That is too big to be a blueprint (32 by 32 by 24 at most).");
        }
        char[][][] grid = new char[height][depth][width];
        Map<Block, Character> letters = new LinkedHashMap<>();
        Map<Character, BlockState> key = new LinkedHashMap<>();
        String spare = "ABCEFGHIJKLMNOQRSUVWXYZabcdefghijklmnopqrstuvwxyz";
        int nextSpare = 0;
        for (int y = 0; y < height; y++) {
            for (int row = 0; row < depth; row++) {
                for (int col = 0; col < width; col++) {
                    BlockPos pos = at(minX, maxX, minZ, maxZ, minY + y, forward, right, row, col);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.canBeReplaced()) {
                        grid[y][row][col] = ' ';
                        continue;
                    }
                    Character letter = letters.get(state.getBlock());
                    if (letter == null) {
                        letter = state.is(ModBlocks.THATCH_BLOCK.get()) ? 'T'
                                : state.is(ModBlocks.BUILDING_BRANCH.get()) ? 'P' : spare.charAt(nextSpare++);
                        letters.put(state.getBlock(), letter);
                        key.put(letter, state);
                    }
                    grid[y][row][col] = letter;
                }
            }
        }
        // The room: anything clear with the build over it.
        for (int row = 0; row < depth; row++) {
            for (int col = 0; col < width; col++) {
                for (int y = 0; y < height; y++) {
                    if (grid[y][row][col] != ' ') {
                        continue;
                    }
                    for (int above = y + 1; above < height; above++) {
                        if (grid[above][row][col] != ' ' && grid[above][row][col] != '.') {
                            grid[y][row][col] = '.';
                            break;
                        }
                    }
                }
            }
        }
        int top = height;
        while (top > 1 && isEmpty(grid[top - 1])) {
            top--;
        }
        if (letters.isEmpty()) {
            throw new IOException("There is nothing built between those corners.");
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, name);
        Blueprint old = get(id);
        JsonObject json = new JsonObject();
        json.addProperty("name", old != null ? old.name() : name.replace('_', ' '));
        json.addProperty("order", old != null ? old.order() : 100);
        json.addProperty("description", old != null ? old.description() : "");
        if (old != null && old.after() != null) {
            json.addProperty("after", old.after().toString());
        }
        JsonObject keyJson = new JsonObject();
        key.forEach((letter, state) -> keyJson.addProperty(String.valueOf(letter), text(state)));
        json.add("key", keyJson);
        JsonArray layers = new JsonArray();
        for (int y = 0; y < top; y++) {
            JsonArray rows = new JsonArray();
            for (int row = 0; row < depth; row++) {
                rows.add(new String(grid[y][row]));
            }
            layers.add(rows);
        }
        json.add("layers", layers);
        String text = PRETTY.toJson(json);
        Blueprint made = Blueprint.parse(id, text);
        Path dir = overrideDir();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".json"), text, StandardCharsets.UTF_8);
        rebuild();
        return made;
    }

    /** Takes a captured blueprint away again: the mod's own is back. Whether there was one to take away. */
    public static boolean reset(String name) throws IOException {
        boolean gone = Files.deleteIfExists(overrideDir().resolve(name + ".json"));
        rebuild();
        return gone;
    }

    private static boolean isEmpty(char[][] layer) {
        for (char[] row : layer) {
            for (char c : row) {
                if (c != ' ' && c != '.') {
                    return false;
                }
            }
        }
        return true;
    }

    /** The world position of a row and column, counting rows in from the door and columns to your right. */
    private static BlockPos at(int minX, int maxX, int minZ, int maxZ, int y, Direction forward, Direction right, int row,
            int col) {
        int x;
        int z;
        if (forward.getAxis() == Direction.Axis.X) {
            x = forward.getStepX() > 0 ? minX + row : maxX - row;
            z = right.getStepZ() > 0 ? minZ + col : maxZ - col;
        } else {
            z = forward.getStepZ() > 0 ? minZ + row : maxZ - row;
            x = right.getStepX() > 0 ? minX + col : maxX - col;
        }
        return new BlockPos(x, y, z);
    }

    /** "hominin_evolution:thatch_block[cured=true]". */
    private static String text(BlockState state) {
        return net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state);
    }
}
