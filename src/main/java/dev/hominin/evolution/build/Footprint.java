package dev.hominin.evolution.build;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A blueprint laid out on the ground: where each of its blocks falls in the world, facing a given way. The same
 * arithmetic on both sides - the ghost a client draws is the one the server checks against.
 *
 * <p>{@code origin} is the middle of the front row at ground level - the doorway - and {@code forward} is the way
 * you face walking in. Columns run to your right as you stand outside looking at the door.
 */
public final class Footprint {
    private final Blueprint blueprint;
    private final BlockPos origin;
    private final Direction forward;
    private final Map<BlockPos, Blueprint.Cell> cells = new LinkedHashMap<>();
    private final Set<BlockPos> inside = new HashSet<>();
    private final Set<BlockPos> clear = new HashSet<>();
    private final Set<BlockPos> ground = new HashSet<>();
    private final BoundingBox box;

    public Footprint(Blueprint blueprint, BlockPos origin, Direction forward) {
        this.blueprint = blueprint;
        this.origin = origin.immutable();
        this.forward = forward.getAxis().isHorizontal() ? forward : Direction.NORTH;
        for (Blueprint.Cell cell : blueprint.cells()) {
            cells.put(world(cell.local()), cell);
        }
        for (BlockPos local : blueprint.inside()) {
            inside.add(world(local));
        }
        for (BlockPos local : blueprint.clear()) {
            clear.add(world(local));
        }
        for (BlockPos local : blueprint.ground()) {
            ground.add(world(local));
        }
        BoundingBox bounds = null;
        for (BlockPos pos : all()) {
            bounds = bounds == null ? new BoundingBox(pos) : bounds.encapsulate(pos);
        }
        this.box = bounds;
    }

    /** A place in the blueprint, in the world. */
    public BlockPos world(BlockPos local) {
        Direction right = forward.getClockWise();
        return origin.offset(right.getStepX() * local.getX() + forward.getStepX() * local.getZ(), local.getY(),
                right.getStepZ() * local.getX() + forward.getStepZ() * local.getZ());
    }

    public Blueprint blueprint() {
        return blueprint;
    }

    public BlockPos origin() {
        return origin;
    }

    public Direction forward() {
        return forward;
    }

    /** Every block of the build, where it goes. */
    public Map<BlockPos, Blueprint.Cell> cells() {
        return cells;
    }

    /** The room: kept clear, and under the roof. */
    public Set<BlockPos> inside() {
        return inside;
    }

    public BoundingBox box() {
        return box;
    }

    private Set<BlockPos> all() {
        Set<BlockPos> all = new HashSet<>(cells.keySet());
        all.addAll(inside);
        all.addAll(clear);
        return all;
    }

    /** Whether this spot is part of it at all - a block of it, the room or the doorway. */
    public boolean contains(BlockPos pos) {
        return box.isInside(pos) && (cells.containsKey(pos) || inside.contains(pos) || clear.contains(pos));
    }

    public boolean isInside(BlockPos pos) {
        return box.isInside(pos) && inside.contains(pos);
    }

    /** Whether the right block is in this spot already. */
    public boolean filled(Level level, BlockPos pos) {
        Blueprint.Cell cell = cells.get(pos);
        return cell != null && level.getBlockState(pos).is(cell.block());
    }

    /** How many of its blocks are in place. */
    public int placed(Level level) {
        int n = 0;
        for (BlockPos pos : cells.keySet()) {
            if (level.isLoaded(pos) && filled(level, pos)) {
                n++;
            }
        }
        return n;
    }

    public int total() {
        return cells.size();
    }

    /** Whether it can be marked out here, and if not, why not. */
    public record Fit(boolean ok, String reason, int placed) {
    }

    /**
     * Only on solid ground, with nothing in the way: every block of it, the room and the doorway free - grass and
     * the like are fine, they get trampled - and something solid under every part of the bottom layer.
     */
    public Fit fit(Level level, Predicate<BlockPos> otherPlans) {
        int placed = 0;
        for (BlockPos pos : all()) {
            if (!level.isLoaded(pos)) {
                return new Fit(false, "Too far off to mark out.", 0);
            }
            if (otherPlans.test(pos)) {
                return new Fit(false, "Another build is already marked out there.", 0);
            }
            BlockState state = level.getBlockState(pos);
            if (!level.getFluidState(pos).isEmpty()) {
                return new Fit(false, "Not in water.", 0);
            }
            Blueprint.Cell cell = cells.get(pos);
            if (cell != null && state.is(cell.block())) {
                placed++;
                continue;
            }
            if (!free(level, pos, state, cell == null)) {
                return new Fit(false, "Something is in the way.", 0);
            }
        }
        for (BlockPos pos : ground) {
            BlockPos below = pos.below();
            if (!cells.containsKey(below) && !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return new Fit(false, "It needs solid, level ground under all of it.", 0);
            }
        }
        return new Fit(true, "", placed);
    }

    /**
     * Air, grass, a flower: nothing that would have to be dug out. In the room, a bed, a nest or a pile of tools
     * are welcome too - that is what the room is for.
     */
    static boolean free(Level level, BlockPos pos, BlockState state, boolean room) {
        if (state.isAir() || state.canBeReplaced()) {
            return true;
        }
        boolean ours = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(HomininEvolutionMod.MODID);
        if (room && (state.is(ModBlocks.NEST.get()) || state.is(ModBlocks.THATCH_BEDDING.get())
                || state.is(ModBlocks.TOOL_PILE.get()) || state.is(ModBlocks.PLACED_TORCH.get()))) {
            return true;
        }
        return !ours && !state.hasBlockEntity() && state.getCollisionShape(level, pos).isEmpty();
    }
}
