package dev.hominin.evolution.stage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.network.CutsceneArrivalPayload;
import dev.hominin.evolution.network.CutsceneStartPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Evolving, as it looks from the inside: the screen goes dark, deep time passes, and
 * a descendant opens their eyes somewhere else.
 *
 * <p>The move happens while the screen is black. A stage change is hundreds of
 * thousands of years - the band has wandered, the old camp is gone, and nothing the
 * ancestor was carrying survived. So the player lands a few hundred blocks away, in
 * savanna where the country allows, with only what that stage starts out holding.
 */
public final class Arrival {
    /** Ticks of fade before the move. The client's cutscene is timed to match. */
    public static final int FADE_TICKS = 20;

    /** How long the player is held still and unharmed while the screen is dark. */
    private static final int PROTECTED_TICKS = 160;

    private static final int MIN_DISTANCE = 400;
    private static final int MAX_DISTANCE = 500;

    /** Random bearings tried inside the ring before falling back to a wider search. */
    private static final int RING_ATTEMPTS = 24;

    /** The same search {@code /locate biome} runs. */
    private static final int LOCATE_RADIUS = 6400;
    private static final int LOCATE_HORIZONTAL_STEP = 32;
    private static final int LOCATE_VERTICAL_STEP = 64;

    /** How far around a located point to hunt for somewhere safe to stand. */
    private static final int SAFE_SPOT_SEARCH = 24;

    private static final ResourceLocation GUIDE_ITEM = ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book");

    private record Pending(long arriveAt, StageDefinition to) {
    }

    private static final Map<UUID, Pending> pending = new HashMap<>();

    /** Starts the cutscene. The move itself happens on a later tick, once the screen is black. */
    public static void begin(ServerPlayer player, StageDefinition from, StageDefinition to) {
        dev.hominin.evolution.stage.CutsceneGuard.tryStart(player, PROTECTED_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, PROTECTED_TICKS, 4, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PROTECTED_TICKS, 6, false, false, false));
        String age = StageAge.ago(to.yearsAgo());
        PacketDistributor.sendToPlayer(player, new CutsceneStartPayload(
                StageAge.later(from.yearsAgo(), to.yearsAgo()),
                age.isEmpty() ? to.displayName() : to.displayName() + " - " + age));
        pending.put(player.getUUID(), new Pending(player.level().getGameTime() + FADE_TICKS, to));
    }

    /** Called every player tick; does nothing unless this player is mid-cutscene. */
    public static void tick(ServerPlayer player) {
        Pending due = pending.get(player.getUUID());
        if (due == null || player.level().getGameTime() < due.arriveAt()) {
            return;
        }
        pending.remove(player.getUUID());
        arrive(player, due.to());
    }

    public static void forget(ServerPlayer player) {
        // Logging out mid-cutscene skips the move rather than stranding it half-done.
        pending.remove(player.getUUID());
    }

    private static void arrive(ServerPlayer player, StageDefinition to) {
        ServerLevel level = player.serverLevel();
        BlockPos from = player.blockPosition();
        BlockPos destination = findDestination(level, from, player.getRandom());

        replaceInventory(player, to);

        int blocks = 0;
        if (destination != null) {
            player.teleportTo(level, destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            player.resetFallDistance();
            Band.bringAlong(player, from);
            Band.formNewBand(player);
            // A new species does not arrive alone in the world: other bands of it are nearby.
            dev.hominin.evolution.band.WildBands.onArrival(player);
            // Home is where the band is now. Dying should not send a descendant back
            // to a camp nobody has lived in for a hundred thousand years.
            player.setRespawnPosition(level.dimension(), destination, player.getYRot(), true, false);
            blocks = (int) Math.round(Math.sqrt(from.distSqr(destination)));
        } else {
            HomininEvolutionMod.LOGGER.warn("No safe arrival point found for {} - leaving them in place",
                    player.getGameProfile().getName());
        }
        PacketDistributor.sendToPlayer(player, new CutsceneArrivalPayload(blocks));
        if (blocks > 0) {
            player.sendSystemMessage(Component.literal(
                    "Your descendants wake " + blocks + " blocks from where their ancestors walked.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Nothing survives the generations but the guide. Everything else is replaced by
     * what the new stage starts out with - which, for some stages, is nothing.
     */
    private static void replaceInventory(ServerPlayer player, StageDefinition to) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && !isGuide(stack)) {
                inventory.removeItemNoUpdate(slot);
            }
        }
        java.util.List<ItemStack> items = to.arrivalItems();
        // Habilis sometimes arrives with stone instead: a hammerstone, and three chert or six quartzite.
        if (player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage().getPath().equals("homo_habilis") && player.getRandom().nextFloat() < 0.5F) {
            boolean chert = player.getRandom().nextBoolean();
            items = java.util.List.of(new ItemStack(dev.hominin.evolution.ModItems.HAMMERSTONE.get()),
                    chert ? new ItemStack(dev.hominin.evolution.ModItems.CHERT_ROCK.get(), 3)
                            : new ItemStack(dev.hominin.evolution.ModItems.GRANITE_ROCK.get(), 6));
        }
        for (ItemStack item : items) {
            ItemStack copy = item.copy();
            if (!inventory.add(copy)) {
                player.drop(copy, false);
            }
        }
        inventory.setChanged();
    }

    private static boolean isGuide(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(GUIDE_ITEM);
    }

    /**
     * Savanna somewhere in the 400-500 block ring first; failing that, the nearest
     * savanna anywhere, searched outward from a point out in that ring so the move
     * still carries the player away; failing that, any safe ground in the ring.
     */
    @Nullable
    private static BlockPos findDestination(ServerLevel level, BlockPos from, RandomSource random) {
        for (int attempt = 0; attempt < RING_ATTEMPTS; attempt++) {
            BlockPos point = ringPoint(from, random);
            if (isHomelandCore(level, point)) {
                BlockPos safe = safeSpotNear(level, point);
                if (safe != null) {
                    return safe;
                }
            }
        }
        Pair<BlockPos, Holder<Biome>> located = level.findClosestBiome3d(
                biome -> biome.is(ModTags.Biomes.HOMININ_HOMELAND), ringPoint(from, random),
                LOCATE_RADIUS, LOCATE_HORIZONTAL_STEP, LOCATE_VERTICAL_STEP);
        if (located != null) {
            BlockPos safe = safeSpotNear(level, located.getFirst());
            if (safe != null) {
                return safe;
            }
        }
        for (int attempt = 0; attempt < RING_ATTEMPTS; attempt++) {
            BlockPos point = ringPoint(from, random);
            // Checking a spot for safety generates its terrain; ruling out open water
            // from the biome noise first keeps an ocean-side search from stalling.
            if (isOpenWater(level, point)) {
                continue;
            }
            BlockPos safe = safeSpotNear(level, point);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private static BlockPos ringPoint(BlockPos from, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
        return new BlockPos(from.getX() + (int) Math.round(Math.cos(angle) * distance), from.getY(),
                from.getZ() + (int) Math.round(Math.sin(angle) * distance));
    }

    /** Savanna here and for a good way around, so the band arrives in the middle of it, not at its edge. */
    private static boolean isHomelandCore(ServerLevel level, BlockPos point) {
        if (!isHomeland(level, point)) {
            return false;
        }
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (!isHomeland(level, point.relative(direction, CORE_CHECK_DISTANCE))) {
                return false;
            }
        }
        return true;
    }

    private static final int CORE_CHECK_DISTANCE = 96;

    private static boolean isHomeland(ServerLevel level, BlockPos point) {
        return noiseBiome(level, point).is(ModTags.Biomes.HOMININ_HOMELAND);
    }

    private static boolean isOpenWater(ServerLevel level, BlockPos point) {
        Holder<Biome> biome = noiseBiome(level, point);
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER);
    }

    /** Reads biome noise directly, so ruling a point out never generates terrain for it. */
    private static Holder<Biome> noiseBiome(ServerLevel level, BlockPos point) {
        return level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(point.getX()), QuartPos.fromBlock(level.getSeaLevel()),
                QuartPos.fromBlock(point.getZ()), level.getChunkSource().randomState().sampler());
    }

    /** The point itself if it is safe, otherwise the first safe column found spiralling out a little. */
    @Nullable
    private static BlockPos safeSpotNear(ServerLevel level, BlockPos point) {
        BlockPos direct = safeSurface(level, point.getX(), point.getZ());
        if (direct != null) {
            return direct;
        }
        for (int step = 4; step <= SAFE_SPOT_SEARCH; step += 4) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos safe = safeSurface(level, point.getX() + direction.getStepX() * step,
                        point.getZ() + direction.getStepZ() * step);
                if (safe != null) {
                    return safe;
                }
            }
        }
        return null;
    }

    /**
     * Solid, dry, open ground: something sturdy underfoot that is not a hazard, and
     * two clear blocks of air with no water in them. Leaves are skipped by the
     * heightmap, so a canopy never counts as the ground.
     */
    @Nullable
    private static BlockPos safeSurface(ServerLevel level, int x, int z) {
        // Level#getHeight does not load anything: for a chunk that is not already
        // loaded it quietly returns the bottom of the world. Every destination is
        // hundreds of blocks out, so the chunk has to be loaded - generated if need
        // be, as /tp does - and its heightmap read directly.
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) {
            return null;
        }
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos ground = feet.below();
        BlockState floor = level.getBlockState(ground);
        if (!floor.getFluidState().isEmpty() || !floor.isFaceSturdy(level, ground, Direction.UP)
                || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || floor.is(BlockTags.FIRE)
                || floor.is(BlockTags.LEAVES)) {
            return null;
        }
        return isClear(level, feet) && isClear(level, feet.above()) ? feet : null;
    }

    private static boolean isClear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && (state.isAir() || state.canBeReplaced())
                && !state.is(BlockTags.FIRE) && !state.is(Blocks.POWDER_SNOW);
    }

    private Arrival() {
    }
}
