package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.world.Pois;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tide pools: pockets of sea water left in the rock and sand along a shore. Crouch and work a stick through one and
 * something comes up: an oyster, a clam, a small fish - and now and then an octopus. All of it eaten raw or cooked.
 * Each pocket gives up three things a day.
 */
public final class TidePools {
    private static final int PER_POCKET = 3;
    /** Pocket, day: how much it has given up. */
    private static final Map<Long, Integer> worked = new HashMap<>();

    /** A stick worked through water: returns true if it was a tide pool, and it has been searched. */
    public static boolean search(ServerPlayer player) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().is(Items.STICK)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getViewVector(1.0F).scale(5.0D));
        BlockHitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY,
                player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos water = hit.getBlockPos();
        if (!level.getFluidState(water).is(FluidTags.WATER)) {
            water = water.above();
            if (!level.getFluidState(water).is(FluidTags.WATER)) {
                return false;
            }
        }
        if (!isPool(level, water)) {
            return false;
        }
        int day = (int) (level.getDayTime() / 24000L);
        long key = water.asLong() * 31L + day;
        int done = worked.getOrDefault(key, 0);
        level.playSound(null, water, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.5F, 1.3F);
        if (done >= PER_POCKET) {
            player.displayClientMessage(Component.literal("Nothing more in this pocket today. Try another - or come back "
                    + "tomorrow, when the tide has been in."), true);
            return true;
        }
        if (worked.size() > 2048) {
            worked.clear();
        }
        worked.put(key, done + 1);
        float roll = player.getRandom().nextFloat();
        ItemStack found = roll < 0.3F ? new ItemStack(ModItems.OYSTER.get())
                : roll < 0.6F ? new ItemStack(ModItems.CLAM.get())
                : roll < 0.88F ? new ItemStack(ModItems.SMALL_FISH.get())
                : roll < 0.96F ? new ItemStack(ModItems.OCTOPUS.get()) : ItemStack.EMPTY;
        if (found.isEmpty()) {
            player.displayClientMessage(Component.literal("Weed and sand - nothing this time."), true);
            return true;
        }
        if (!player.getInventory().add(found.copy())) {
            player.drop(found.copy(), false);
        }
        player.displayClientMessage(Component.literal("You work the stick along the bottom and come up with ")
                .append(found.getHoverName()).append(Component.literal(".")).withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /**
     * Water in a tide pool: shallow, and held in by the wet sand round it - or, at a tide pool place, any shallow
     * pocket near its heart.
     */
    public static boolean isPool(ServerLevel level, BlockPos water) {
        if (level.getFluidState(water.below()).is(FluidTags.WATER) && level.getFluidState(water.below(2)).is(FluidTags.WATER)) {
            // Too deep: that is the sea, not a pool.
            return false;
        }
        if (level.getBlockState(water.below()).isAir()) {
            return false;
        }
        for (BlockPos near : BlockPos.betweenClosed(water.offset(-3, -1, -3), water.offset(3, 1, 3))) {
            if (level.getBlockState(near).is(dev.hominin.evolution.ModBlocks.WET_SAND.get())) {
                return true;
            }
        }
        for (Pois.Poi poi : Pois.near(level, water, 32, true)) {
            if (poi.kind() == Pois.Kind.TIDE_POOL && poi.pos().distSqr(water) <= 24 * 24) {
                return true;
            }
        }
        return false;
    }

    /**
     * Laying the place out: five to eight pockets of water up the beach from the sea - never touching it - each
     * rimmed round with wet sand and gravel that hold the water in, with loose rocks and a stone or two on the edge.
     */
    public static BlockPos layOut(ServerLevel level, BlockPos ground, net.minecraft.util.RandomSource random) {
        java.util.List<BlockPos> pockets = new java.util.ArrayList<>();
        int want = 5 + random.nextInt(4);
        for (int attempt = 0; attempt < 60 && pockets.size() < want; attempt++) {
            int x = ground.getX() + random.nextInt(25) - 12;
            int z = ground.getZ() + random.nextInt(25) - 12;
            if (!level.hasChunk(x >> 4, z >> 4) || !level.hasChunk((x + 3) >> 4, (z + 3) >> 4)
                    || !level.hasChunk((x - 3) >> 4, (z - 3) >> 4)) {
                continue;
            }
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos floor = new BlockPos(x, top, z);
            if (Math.abs(top - ground.getY()) > 4 || !level.getFluidState(floor).isEmpty()
                    || !level.getBlockState(floor).isSolid() || nearWater(level, floor, 3)) {
                continue;
            }
            boolean apart = true;
            for (BlockPos other : pockets) {
                if (other.distSqr(floor) < 6 * 6) {
                    apart = false;
                    break;
                }
            }
            if (!apart) {
                continue;
            }
            pockets.add(floor);
            pocket(level, floor, random);
        }
        return ground;
    }

    /** One pocket: water a block deep over sand, a ring of wet sand and gravel round it, rocks on the edge. */
    private static void pocket(ServerLevel level, BlockPos floor, net.minecraft.util.RandomSource random) {
        float rx = 1.2F + random.nextFloat() * 1.3F;
        float rz = 1.2F + random.nextFloat() * 1.3F;
        BlockState wet = dev.hominin.evolution.ModBlocks.WET_SAND.get().defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        java.util.List<BlockPos> rim = new java.util.ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt((dx / rx) * (dx / rx) + (dz / rz) * (dz / rz));
                BlockPos at = floor.offset(dx, 0, dz);
                if (d <= 1.0D) {
                    // The water, held over sand, open to the sky.
                    level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(at.below(), Blocks.SAND.defaultBlockState(), 2);
                    level.setBlock(at, Blocks.WATER.defaultBlockState(), 3);
                } else if (d <= 1.0D + 1.5D / Math.min(rx, rz)) {
                    // The rim: wet sand, some gravel - and under it, nothing for the water to run out through.
                    level.setBlock(at, random.nextFloat() < 0.7F ? wet : gravel, 2);
                    if (!level.getBlockState(at.below()).isSolid()) {
                        level.setBlock(at.below(), wet, 2);
                    }
                    BlockState above = level.getBlockState(at.above());
                    if (!above.isAir() && above.canBeReplaced()) {
                        level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 2);
                    }
                    rim.add(at);
                }
            }
        }
        // Rocks on the edge: loose stones, and now and then a stone set in the rim.
        java.util.Collections.shuffle(rim, new java.util.Random(random.nextLong()));
        BlockState[] rocks = {dev.hominin.evolution.ModBlocks.GRANITE_ROCK.get().defaultBlockState(),
                dev.hominin.evolution.ModBlocks.BASALT_ROCK.get().defaultBlockState(),
                dev.hominin.evolution.ModBlocks.CHERT_ROCK.get().defaultBlockState()};
        int placed = 0;
        for (BlockPos at : rim) {
            if (placed >= 2 + random.nextInt(3)) {
                break;
            }
            if (random.nextFloat() < 0.3F) {
                level.setBlock(at, random.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState()
                        : Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
                placed++;
                continue;
            }
            BlockState rock = rocks[random.nextInt(rocks.length)];
            if (level.getBlockState(at.above()).isAir() && rock.canSurvive(level, at.above())) {
                level.setBlock(at.above(), rock, 2);
                placed++;
            }
        }
    }

    private static boolean nearWater(ServerLevel level, BlockPos at, int reach) {
        for (BlockPos near : BlockPos.betweenClosed(at.offset(-reach, -1, -reach), at.offset(reach, 1, reach))) {
            if (!level.getFluidState(near).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private TidePools() {
    }
}
