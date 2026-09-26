package dev.hominin.evolution.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.block.BuildingBranchBlock;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Something to build: which block goes where, layer by layer, and what has to be left clear. Read from JSON
 * ({@code data/<ns>/hominin/blueprints/<name>.json}, or an override in {@code config/hominin_evolution/blueprints}):
 *
 * <pre>
 * "key":    {"T": "hominin_evolution:thatch_block[cured=true]", "P": "hominin_evolution:building_branch"},
 * "layers": [ [" T_T ", "T...T", ...],   the ground layer, rows from the front (the door) to the back,
 *             [ ... ] ]                  columns left to right as you stand outside looking at the door
 * </pre>
 *
 * A key letter is a block to put there - only the block counts, the state in brackets is how its ghost looks.
 * {@code .} is inside: kept clear, and it is the room. {@code _} is kept clear too - a doorway. A space is
 * whatever happens to be there.
 */
public final class Blueprint {
    public static final char ANY = ' ';
    public static final char INSIDE = '.';
    public static final char CLEAR = '_';

    /** One block of the build, at its place in the blueprint (x across from the middle, y up, z in from the door). */
    public record Cell(BlockPos local, Block block, BlockState look) {
    }

    private final ResourceLocation id;
    private final String name;
    private final String description;
    private final int order;
    @Nullable
    private final ResourceLocation after;
    private final List<Cell> cells;
    private final List<BlockPos> inside;
    private final List<BlockPos> clear;
    /** Every column that stands on the ground: whatever is in the bottom layer. */
    private final List<BlockPos> ground;
    private final int width;
    private final int height;
    private final int depth;
    private final String json;

    private Blueprint(ResourceLocation id, String name, String description, int order, @Nullable ResourceLocation after,
            List<Cell> cells, List<BlockPos> inside, List<BlockPos> clear, List<BlockPos> ground, int width, int height,
            int depth, String json) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.order = order;
        this.after = after;
        this.cells = cells;
        this.inside = inside;
        this.clear = clear;
        this.ground = ground;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.json = json;
    }

    public ResourceLocation id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int order() {
        return order;
    }

    /** A blueprint that has to have been built first, if any. */
    @Nullable
    public ResourceLocation after() {
        return after;
    }

    public List<Cell> cells() {
        return cells;
    }

    public List<BlockPos> inside() {
        return inside;
    }

    /** Somewhere to be inside of - a hut, a tent - rather than a thing that stands in the open, like a rack. */
    public boolean shelter() {
        return !inside.isEmpty();
    }

    public List<BlockPos> clear() {
        return clear;
    }

    public List<BlockPos> ground() {
        return ground;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    /** The text it was read from - what the server sends a client, so both build the same thing. */
    public String json() {
        return json;
    }

    /** What it takes: "32 thatch blocks, 2 building branches". */
    public Map<Block, Integer> materials() {
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (Cell cell : cells) {
            counts.merge(cell.block(), 1, Integer::sum);
        }
        return counts;
    }

    public String materialsText() {
        // What it takes in hand, not in blocks: a two-tall post is one post, and a bar across two is a branch.
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Cell cell : cells) {
            if (twoTall(cell.block()) && cell.look().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && cell.look().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                            == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                continue;
            }
            String name = cell.block() == ModBlocks.COOKING_SPIT.get() || cell.block() == ModBlocks.TOOL_RACK_BAR.get()
                    ? "workable branch" : cell.block().getName().getString().toLowerCase();
            counts.merge(name, 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((name, n) -> parts.add(n + " " + name
                + (n == 1 || name.endsWith("s") ? "" : name.endsWith("h") ? "es" : "s")));
        return String.join(", ", parts);
    }

    /** A block that stands two high from one item: a rack's post. */
    public static boolean twoTall(Block block) {
        return block instanceof dev.hominin.evolution.block.CookingRackBlock;
    }

    // ------------------------------------------------------------ reading

    public static Blueprint parse(ResourceLocation id, String text) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        String name = root.has("name") ? root.get("name").getAsString() : id.getPath().replace('_', ' ');
        String description = root.has("description") ? root.get("description").getAsString() : "";
        int order = root.has("order") ? root.get("order").getAsInt() : 100;
        ResourceLocation after = root.has("after") && !root.get("after").getAsString().isEmpty()
                ? ResourceLocation.parse(root.get("after").getAsString()) : null;

        Map<Character, BlockState> key = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("key").entrySet()) {
            if (entry.getKey().length() != 1) {
                throw new IllegalArgumentException("key '" + entry.getKey() + "' is not one character");
            }
            char c = entry.getKey().charAt(0);
            if (c == ANY || c == INSIDE || c == CLEAR) {
                throw new IllegalArgumentException("'" + c + "' is reserved and cannot be a key");
            }
            key.put(c, state(entry.getValue().getAsString()));
        }

        JsonArray layers = root.getAsJsonArray("layers");
        int height = layers.size();
        int depth = 0;
        int width = 0;
        for (JsonElement layer : layers) {
            JsonArray rows = layer.getAsJsonArray();
            depth = Math.max(depth, rows.size());
            for (JsonElement row : rows) {
                width = Math.max(width, row.getAsString().length());
            }
        }
        int centre = (width - 1) / 2;
        List<Cell> cells = new ArrayList<>();
        List<BlockPos> inside = new ArrayList<>();
        List<BlockPos> clear = new ArrayList<>();
        List<BlockPos> ground = new ArrayList<>();
        Map<BlockPos, BlockState> placed = new HashMap<>();
        for (int y = 0; y < height; y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z).getAsString();
                for (int col = 0; col < row.length(); col++) {
                    char c = row.charAt(col);
                    if (c == ANY) {
                        continue;
                    }
                    BlockPos local = new BlockPos(col - centre, y, z);
                    if (y == 0) {
                        ground.add(local);
                    }
                    if (c == INSIDE) {
                        inside.add(local);
                    } else if (c == CLEAR) {
                        clear.add(local);
                    } else {
                        BlockState state = key.get(c);
                        if (state == null) {
                            throw new IllegalArgumentException("'" + c + "' at layer " + y + ", row " + z + " is not in the key");
                        }
                        placed.put(local, state);
                    }
                }
            }
        }
        // Bottom up, so a ghost is filled in the order it can be built.
        placed.entrySet().stream()
                .sorted((a, b) -> a.getKey().getY() != b.getKey().getY() ? Integer.compare(a.getKey().getY(), b.getKey().getY())
                        : a.getKey().getZ() != b.getKey().getZ() ? Integer.compare(a.getKey().getZ(), b.getKey().getZ())
                        : Integer.compare(a.getKey().getX(), b.getKey().getX()))
                .forEach(entry -> cells.add(new Cell(entry.getKey(), entry.getValue().getBlock(),
                        look(entry.getKey(), entry.getValue(), placed))));
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("nothing to build");
        }
        return new Blueprint(id, name, description, order, after, List.copyOf(cells), List.copyOf(inside),
                List.copyOf(clear), List.copyOf(ground), width, height, depth, text);
    }

    /** A post stands in its ring of rocks only at the bottom; the ones stacked on it are bare. */
    private static BlockState look(BlockPos local, BlockState state, Map<BlockPos, BlockState> placed) {
        if (state.is(ModBlocks.BUILDING_BRANCH.get())) {
            BlockState below = placed.get(local.below());
            return state.setValue(BuildingBranchBlock.BASE, below == null || !below.is(ModBlocks.BUILDING_BRANCH.get()));
        }
        return state;
    }

    private static BlockState state(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException("bad block '" + text + "': " + e.getMessage());
        }
    }
}
