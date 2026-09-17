package dev.hominin.evolution.climb;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The rules of being up a tree, shared by both sides.
 *
 * <p>The client decides when a climb starts and stops, because only it knows which
 * keys are held; the server keeps a copy of the flag so leaf collision agrees on both
 * ends, and so it can take the climb away when rotten wood gives out. Everything the
 * two sides both need to judge - what counts as a grip, what counts as a tree - lives
 * here so they cannot disagree about it.
 */
public final class Climbing {
    /**
     * How fast each stage goes up a trunk, in blocks per tick. Australopithecus still
     * had curved fingers and a climber's shoulders; erectus had traded both for
     * long-distance legs, and climbs like it.
     */
    private static final double AUSTRALOPITHECUS_SPEED = 0.2D;
    private static final double HABILIS_SPEED = 0.16D;
    private static final double ERECTUS_SPEED = 0.1D;

    /** Letting yourself down hand over hand, not dropping. */
    public static final double DESCEND_SPEED = 0.15D;

    /**
     * How far through a canopy a climber can move from the nearest trunk. Past this
     * they are in a hedge, not a tree, and let go.
     */
    public static final int CANOPY_REACH = 3;

    /**
     * How high any wall can be climbed. Enough to get out of a pit or up a small cliff -
     * with no building, there is no other way out - but not a way up a mountain.
     */
    public static final int WALL_CLIMB_LIMIT = 4;

    /** How far out from the body a hand can reach a trunk. */
    private static final double ARM_REACH = 0.3D;

    private static final ResourceLocation AUSTRALOPITHECUS =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "australopithecus");
    private static final ResourceLocation ARDIPITHECUS =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "ardipithecus");
    private static final ResourceLocation HABILIS =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "homo_habilis");

    public static boolean isClimbing(Player player) {
        // hasData first, so a query never creates the attachment on a player who has
        // never climbed - this is asked for every leaf block every player touches.
        return player.hasData(Attachments.CLIMBING) && player.getData(Attachments.CLIMBING);
    }

    public static void setClimbing(Player player, boolean climbing) {
        if (climbing || player.hasData(Attachments.CLIMBING)) {
            player.setData(Attachments.CLIMBING, climbing);
        }
    }

    public static double climbSpeed(@Nullable ResourceLocation stage) {
        if (AUSTRALOPITHECUS.equals(stage) || ARDIPITHECUS.equals(stage)) {
            return AUSTRALOPITHECUS_SPEED;
        }
        return HABILIS.equals(stage) ? HABILIS_SPEED : ERECTUS_SPEED;
    }

    /** A log within arm's reach of the body, or null. Sideways only - not the one underfoot. */
    @Nullable
    public static BlockPos grippedLog(Player player) {
        AABB box = player.getBoundingBox().inflate(ARM_REACH, 0.0D, ARM_REACH);
        Level level = player.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY + 0.01D), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY - 0.01D), Mth.floor(box.maxZ))) {
            if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                return pos.immutable();
            }
        }
        return null;
    }

    /** Any solid block within reach at body height that is not a tree - rock, earth, a wall. */
    @Nullable
    public static BlockPos grippedWall(Player player, double reach) {
        AABB box = player.getBoundingBox().inflate(reach, 0.0D, reach);
        Level level = player.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY + 0.01D), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY - 0.01D), Mth.floor(box.maxZ))) {
            var state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                    && !state.getCollisionShape(level, pos).isEmpty()) {
                return pos.immutable();
            }
        }
        return null;
    }

    @Nullable
    public static BlockPos grippedWall(Player player) {
        return grippedWall(player, ARM_REACH);
    }

    /** Holding on by a wall alone, which is the kind of climb with a height limit. */
    public static boolean onlyWall(Player player) {
        return grippedLog(player) == null
                && !(inLeaves(player) && trunkNearby(player.level(), player.blockPosition(), CANOPY_REACH))
                && grippedWall(player) != null;
    }

    /** Whether any part of the body is inside a leaf block. */
    public static boolean inLeaves(Player player) {
        AABB box = player.getBoundingBox().inflate(0.05D);
        Level level = player.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }

    /** A trunk somewhere close by - above, level with, or a little below. */
    public static boolean trunkNearby(Level level, BlockPos center, int reach) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-reach, -reach - 1, -reach), center.offset(reach, 2, reach))) {
            if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    /** Something to hold on to: a trunk in reach, or branches with a trunk behind them. */
    public static boolean canHold(Player player) {
        return grippedLog(player) != null
                || (inLeaves(player) && trunkNearby(player.level(), player.blockPosition(), CANOPY_REACH))
                || grippedWall(player) != null;
    }

    /** Stood on top of a tree's leaves - where a climber rests, and where they climb back down from. */
    public static boolean onCanopy(Player player) {
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1D, player.getZ());
        return player.onGround()
                && player.level().getBlockState(below).is(BlockTags.LEAVES)
                && trunkNearby(player.level(), below, CANOPY_REACH);
    }

    private Climbing() {
    }
}
