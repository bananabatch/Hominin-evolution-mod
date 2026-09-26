package dev.hominin.evolution.band;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.block.FirePitBlockEntity;
import dev.hominin.evolution.block.ThatchBlock;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.build.Blueprint;
import dev.hominin.evolution.build.Blueprints;
import dev.hominin.evolution.build.Footprint;
import dev.hominin.evolution.build.Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Other people's camps look lived in. From erectus on a band keeps a fire burning at its camp - you can see the smoke
 * from a long way off - and often a knapping station by it, and now and then a hut of its own. Their piles are there
 * already. The same pieces make a lone camp: a fire, a station, perhaps a hut, and the dead.
 */
public final class WildCamps {
    private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath(
            dev.hominin.evolution.HomininEvolutionMod.MODID, "small_hut");
    /** Kept fires are topped up once they have less than this left. */
    private static final long TOP_UP = 6000L;

    // ------------------------------------------------------------ the pieces

    /** Open, dry, level ground at this column, near this height - or null. */
    @Nullable
    public static BlockPos openGround(ServerLevel level, int x, int z, int nearY) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos at = new BlockPos(x, y, z);
        if (Math.abs(y - nearY) > 3 || !level.getBlockState(at).canBeReplaced() || !level.getFluidState(at).isEmpty()
                || !level.getFluidState(at.below()).isEmpty()
                || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
                || Sites.containing(level, at) != null) {
            return null;
        }
        return at;
    }

    /** Somewhere on open ground in a ring round a spot. */
    @Nullable
    private static BlockPos spotNear(ServerLevel level, BlockPos near, RandomSource random, int min, int max) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int r = min + random.nextInt(max - min + 1);
            BlockPos at = openGround(level, near.getX() + (int) Math.round(Math.cos(angle) * r),
                    near.getZ() + (int) Math.round(Math.sin(angle) * r), near.getY());
            if (at != null) {
                return at;
            }
        }
        return null;
    }

    /** A fire pit, laid and lit. */
    @Nullable
    public static BlockPos fire(ServerLevel level, BlockPos at) {
        if (!level.getBlockState(at).canBeReplaced()) {
            return null;
        }
        level.setBlock(at, ModBlocks.FIRE_PIT.get().defaultBlockState(), Block.UPDATE_ALL);
        tend(level, at);
        return at.immutable();
    }

    /** A fire pit somewhere near, laid and lit. */
    @Nullable
    public static BlockPos fireNear(ServerLevel level, BlockPos near, RandomSource random) {
        BlockPos at = spotNear(level, near, random, 2, 5);
        return at == null ? null : fire(level, at);
    }

    /** A fire of theirs kept going: fed before it gets low, lit again if it went out. */
    public static void tend(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof FirePitBlockEntity pit)) {
            return;
        }
        if (level.isRainingAt(pos.above())) {
            // Nobody gets a fire going in the rain; they let it burn down, and light it again after.
            return;
        }
        if (!pit.isLit()) {
            pit.stoke(FirePitBlockEntity.Fuel.STICK, 4);
            pit.stoke(FirePitBlockEntity.Fuel.THATCH, 1);
            pit.kindle();
        } else if (pit.ticksLeft() < TOP_UP) {
            pit.stoke(FirePitBlockEntity.Fuel.BRANCH, 2);
        }
    }

    /** A knapping station or a work station, set down on open ground near a spot, facing it. */
    @Nullable
    public static BlockPos station(ServerLevel level, BlockPos near, RandomSource random, boolean knapping) {
        BlockPos at = spotNear(level, near, random, 2, 4);
        if (at == null) {
            return null;
        }
        BlockState state = knapping ? ModBlocks.KNAPPING_STATION.get().defaultBlockState()
                : ModBlocks.WORK_STATION.get().defaultBlockState();
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            Direction facing = Direction.getNearest(near.getX() - at.getX(), 0, near.getZ() - at.getZ());
            state = state.setValue(HorizontalDirectionalBlock.FACING,
                    facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
        }
        level.setBlock(at, state, Block.UPDATE_ALL);
        return at;
    }

    /**
     * A small hut, finished, standing a few blocks off - hide over the top of it, which keeps every wall under it. Nobody's build: nothing asks what it is for. Returns where it stands, or null if nowhere near fits.
     */
    @Nullable
    public static BoundingBox hut(ServerLevel level, BlockPos near, RandomSource random) {
        Blueprint blueprint = Blueprints.get(HUT);
        if (blueprint == null) {
            return null;
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int r = 5 + random.nextInt(5);
            int x = near.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = near.getZ() + (int) Math.round(Math.sin(angle) * r);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - near.getY()) > 3) {
                continue;
            }
            // The doorway faces the fire.
            Direction forward = Direction.getNearest(near.getX() - x, 0, near.getZ() - z).getOpposite();
            if (!forward.getAxis().isHorizontal()) {
                forward = Direction.NORTH;
            }
            Footprint footprint = new Footprint(blueprint, new BlockPos(x, y, z), forward);
            if (footprint.contains(near) || !footprint.fit(level, pos -> Sites.containing(level, pos) != null).ok()) {
                continue;
            }
            for (var entry : footprint.cells().entrySet()) {
                BlockState look = entry.getValue().look();
                Blueprint.Cell above = footprint.cells().get(entry.getKey().above());
                if (look.hasProperty(ThatchBlock.CURED)) {
                    // Hide on the top of each stack: it keeps the wall under it too.
                    look = look.setValue(ThatchBlock.CURED, above == null || !(above.block() instanceof ThatchBlock));
                }
                if (!level.getBlockState(entry.getKey()).isAir()) {
                    level.destroyBlock(entry.getKey(), false);
                }
                level.setBlock(entry.getKey(), look, Block.UPDATE_ALL);
            }
            return footprint.box();
        }
        return null;
    }

    /** What an earlier camp left here that a new one replaces: its fire, stations, dead and nobody's piles. */
    public static void clearOld(ServerLevel level, BlockPos centre, int reach) {
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -3, -reach), centre.offset(reach, 4, reach))) {
            BlockState state = level.getBlockState(pos);
            boolean old = state.is(ModBlocks.FIRE_PIT.get()) || state.is(ModBlocks.KNAPPING_STATION.get())
                    || state.is(ModBlocks.WORK_STATION.get()) || state.is(ModBlocks.HOMININ_CARCASS.get())
                    || state.is(ModBlocks.TOOL_PILE.get()) && level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile
                            && pile.owner() == null;
            if (old) {
                level.removeBlock(pos, false);
            }
        }
    }

    // ------------------------------------------------------------ a band's camp

    /** From erectus on a band's camp has a fire, most likely a station, and sometimes a hut. */
    private static void furnish(ServerLevel level, Bands.Record band, RandomSource random) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, band.home.getX(), band.home.getZ());
        BlockPos home = new BlockPos(band.home.getX(), y, band.home.getZ());
        BlockPos fire = fireNear(level, home, random);
        if (fire == null) {
            // Nowhere to put it today: they try again later.
            return;
        }
        band.fire = fire;
        band.furnished = true;
        if (random.nextFloat() < 0.5F) {
            station(level, fire, random, random.nextFloat() < 0.7F);
        }
        if (random.nextFloat() < 0.3F) {
            hut(level, fire, random);
        }
        Bands.changed(level);
    }

    /** Every thirty seconds round a player: camps near enough to be about are furnished, and their fires kept up. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 600 != 40) {
            return;
        }
        ServerLevel level = player.serverLevel();
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || !Bands.erectusOn(band.species)
                    || Bands.horizontal(band.home, player.blockPosition()) > 256.0D * 256.0D) {
                continue;
            }
            if (!band.furnished) {
                if (level.isLoaded(band.home) && Bands.horizontal(band.home, player.blockPosition()) < 150.0D * 150.0D) {
                    furnish(level, band, player.getRandom());
                }
                continue;
            }
            if (band.fire == null || !level.isLoaded(band.fire)) {
                continue;
            }
            if (!level.getBlockState(band.fire).is(ModBlocks.FIRE_PIT.get())) {
                // Somebody broke it up: they make another.
                band.fire = null;
                band.furnished = false;
                Bands.changed(level);
                continue;
            }
            tend(level, band.fire);
        }
    }

    private WildCamps() {
    }
}
