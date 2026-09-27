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

    /**
     * How long the player is held still and unharmed while the screen is dark: the counted-down cutscene runs ten and
     * a half seconds, covered with a little to spare.
     */
    private static final int PROTECTED_TICKS = 220;

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

    private static final Map<UUID, Pending> pending = dev.hominin.evolution.ServerState.track(new HashMap<>());
    /**
     * Players the world carries along with a band that evolved: they wake near it, not somewhere of their own - and
     * those who did not evolve themselves keep what they carried.
     */
    private static final Map<UUID, UUID> alongside = dev.hominin.evolution.ServerState.track(new HashMap<>());
    private static final java.util.Set<UUID> keepsThings = dev.hominin.evolution.ServerState.track(new java.util.HashSet<>());
    /** How far from the evolved band a band carried along with it wakes: near, but its own ground. */
    private static final int ALONGSIDE_MIN = 24;
    private static final int ALONGSIDE_MAX = 48;

    /** When this player evolves - or is carried along - they wake near this band's leader. */
    public static void alongside(ServerPlayer player, UUID leader) {
        alongside.put(player.getUUID(), leader);
    }

    /**
     * The world moves on and this player's band with it, though it has not evolved: the same dark, the same deep time
     * passing, and waking near the band that did - with their own kind, their own things, their own band.
     */
    public static void carryAlong(ServerPlayer player, UUID leader, StageDefinition now, StageDefinition worldInto,
            int fromYearsAgo) {
        alongside.put(player.getUUID(), leader);
        keepsThings.add(player.getUUID());
        dev.hominin.evolution.stage.CutsceneGuard.tryStart(player, PROTECTED_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, PROTECTED_TICKS, 4, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PROTECTED_TICKS, 6, false, false, false));
        int from = Math.max(fromYearsAgo, worldInto.yearsAgo());
        String age = StageAge.ago(worldInto.yearsAgo());
        PacketDistributor.sendToPlayer(player, new CutsceneStartPayload(StageAge.later(from, worldInto.yearsAgo()),
                age.isEmpty() ? now.displayName() : now.displayName() + " - " + age, from, worldInto.yearsAgo()));
        pending.put(player.getUUID(), new Pending(player.level().getGameTime() + FADE_TICKS, now));
    }

    /** Starts the cutscene. The move itself happens on a later tick, once the screen is black. */
    public static void begin(ServerPlayer player, StageDefinition from, StageDefinition to) {
        dev.hominin.evolution.stage.CutsceneGuard.tryStart(player, PROTECTED_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, PROTECTED_TICKS, 4, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PROTECTED_TICKS, 6, false, false, false));
        String age = StageAge.ago(to.yearsAgo());
        PacketDistributor.sendToPlayer(player, new CutsceneStartPayload(
                StageAge.later(from.yearsAgo(), to.yearsAgo()),
                age.isEmpty() ? to.displayName() : to.displayName() + " - " + age, from.yearsAgo(), to.yearsAgo()));
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
        alongside.remove(player.getUUID());
        keepsThings.remove(player.getUUID());
    }

    private static void arrive(ServerPlayer player, StageDefinition to) {
        ServerLevel level = player.serverLevel();
        // A co-leader wakes where the band does: beside its leader, once the leader is there.
        java.util.UUID hostId = dev.hominin.evolution.band.Newcomers.hostOf(player);
        if (hostId != null && level.getServer().getPlayerList().getPlayer(hostId) instanceof ServerPlayer host
                && host != player && host.level() == level) {
            if (pending.containsKey(hostId)) {
                pending.put(player.getUUID(), new Pending(level.getGameTime() + 5L, to));
                return;
            }
            alongside.remove(player.getUUID());
            besideLeader(player, host, to, keepsThings.remove(player.getUUID()));
            return;
        }
        BlockPos from = player.blockPosition();
        var data = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA);
        // Carried along with a band that evolved: wait for it to wake, then wake near it.
        UUID with = alongside.get(player.getUUID());
        ServerPlayer leader = with == null ? null : level.getServer().getPlayerList().getPlayer(with);
        if (leader != null && leader != player && leader.level() == level && pending.containsKey(with)) {
            pending.put(player.getUUID(), new Pending(level.getGameTime() + 5L, to));
            return;
        }
        alongside.remove(player.getUUID());
        boolean keep = keepsThings.remove(player.getUUID());
        // Where the line splits, the people go their own way: east, or north - and a long way.
        int lineage = Lineage.branches(data.getStage()) ? Lineage.of(data) : Lineage.NONE;
        if (leader != null && leader != player && leader.level() == level) {
            BlockPos near = nearBand(level, leader.blockPosition(), player.getRandom());
            if (near != null) {
                wakeNear(player, leader, near, from, to, keep);
                return;
            }
        }
        // Whether the ancestors ever put a roof up: if they did, their descendants more often wake somewhere camped.
        boolean built = dev.hominin.evolution.build.Sites.ownedBy(level, player.getUUID()).stream()
                .anyMatch(site -> site.built() && dev.hominin.evolution.build.Building.shelter(site));
        BlockPos destination = lineage == Lineage.NONE ? findDestination(level, from, player.getRandom())
                : findDestination(level, from, player.getRandom(), Lineage.bearing(lineage), REGION_MIN, REGION_MAX);

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
            if (lineage != Lineage.NONE) {
                wakeIn(player, destination, lineage, built);
            }
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

    /** Somewhere safe a little way off from where the evolved band woke: ground of their own, within sight of it. */
    @Nullable
    private static BlockPos nearBand(ServerLevel level, BlockPos centre, RandomSource random) {
        for (int attempt = 0; attempt < RING_ATTEMPTS; attempt++) {
            BlockPos point = ringPoint(centre, random, Double.NaN, ALONGSIDE_MIN, ALONGSIDE_MAX);
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

    /**
     * Waking near the band the world moved on with. Their own band comes with them; a player who evolved starts afresh
     * as anyone evolving does, and one who did not keeps what they had.
     */
    private static void wakeNear(ServerPlayer player, ServerPlayer leader, BlockPos at, BlockPos from, StageDefinition to,
            boolean keep) {
        ServerLevel level = player.serverLevel();
        if (!keep) {
            replaceInventory(player, to);
        }
        player.teleportTo(level, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        Band.bringAlong(player, from);
        if (!keep) {
            Band.formNewBand(player);
        } else {
            dev.hominin.evolution.hunt.Predation.settle(player, at);
        }
        player.setRespawnPosition(level.dimension(), at, player.getYRot(), true, false);
        int blocks = (int) Math.round(Math.sqrt(from.distSqr(at)));
        PacketDistributor.sendToPlayer(player, new CutsceneArrivalPayload(blocks));
        player.sendSystemMessage(Component.literal((keep ? "The world has moved on, and your band with it. " : "")
                + "You wake not far from " + leader.getGameProfile().getName() + "'s band - " + blocks
                + " blocks from where your ancestors walked.").withStyle(ChatFormatting.GOLD));
    }

    /** The co-leader's waking: their own things replaced, beside the leader, at the band's camp. */
    private static void besideLeader(ServerPlayer player, ServerPlayer host, StageDefinition to, boolean keep) {
        ServerLevel level = host.serverLevel();
        BlockPos from = player.blockPosition();
        if (!keep) {
            replaceInventory(player, to);
        }
        BlockPos spot = Band.standingSpotNear(level, host.blockPosition(), 3, player.getRandom().nextFloat() * 6.2831855F);
        player.teleportTo(level, spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, host.getYRot(), 0.0F);
        player.resetFallDistance();
        dev.hominin.evolution.hunt.Predation.settle(player, dev.hominin.evolution.hunt.Predation.campOf(host));
        player.setRespawnPosition(level.dimension(), spot, host.getYRot(), true, false);
        int blocks = (int) Math.round(Math.sqrt(from.distSqr(spot)));
        PacketDistributor.sendToPlayer(player, new CutsceneArrivalPayload(blocks));
        player.sendSystemMessage(Component.literal("You wake beside " + host.getGameProfile().getName()
                + ", among the band you lead together.").withStyle(ChatFormatting.GOLD));
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
            items = java.util.List.of(dev.hominin.evolution.item.StoneMaterial.stamp(
                    new ItemStack(dev.hominin.evolution.ModItems.HAMMERSTONE.get()),
                    chert ? dev.hominin.evolution.item.StoneMaterial.CHERT : dev.hominin.evolution.item.StoneMaterial.QUARTZITE),
                    chert ? new ItemStack(dev.hominin.evolution.ModItems.CHERT_ROCK.get(), 3)
                            : new ItemStack(dev.hominin.evolution.ModItems.GRANITE_ROCK.get(), 6));
        }
        for (ItemStack item : items) {
            ItemStack copy = item.copy();
            if (copy.getItem() instanceof dev.hominin.evolution.item.AcheuleanToolItem tool
                    && !copy.has(dev.hominin.evolution.ModDataComponents.QUALITY.get())) {
                // A tool handed down is a decent one: strong, of chert.
                copy = dev.hominin.evolution.item.StoneMaterial.stamp(tool.make(2),
                        dev.hominin.evolution.item.StoneMaterial.CHERT);
            }
            if (!inventory.add(copy)) {
                player.drop(copy, false);
            }
        }
        inventory.setChanged();
    }

    /**
     * Where the line splits, the descendants wake either in a camp the band keeps - a small hut and a fire pit -
     * or out on the open plains with nothing. A people whose ancestors built are likelier to have kept building.
     */
    private static void wakeIn(ServerPlayer player, BlockPos at, int lineage, boolean built) {
        boolean camped = player.getRandom().nextFloat() < (built ? 0.6F : 0.4F)
                && dev.hominin.evolution.build.Building.raiseCamp(player, at);
        String region = Lineage.region(lineage);
        player.sendSystemMessage(Component.literal(camped ? "You wake up in a familiar camp."
                : "You wake up venturing the open plains.").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(region + ". Your people are on the road to " + Lineage.people(lineage)
                + (camped ? " - and a roof and a fire pit are already here." : " - with nothing yet but what you carry."))
                .withStyle(ChatFormatting.GRAY));
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
        return findDestination(level, from, random, Double.NaN, MIN_DISTANCE, MAX_DISTANCE);
    }

    /** A long way off where the line splits: a new country, one day its own land. */
    private static final int REGION_MIN = 900;
    private static final int REGION_MAX = 1200;
    /** How far either side of the bearing a region's arrival may fall. */
    private static final double REGION_SPREAD = Math.toRadians(35.0D);

    /**
     * The same search, along a bearing - east for Africa's east, north for its north - when one is given (NaN for
     * any direction at all), and out at a given distance.
     */
    @Nullable
    private static BlockPos findDestination(ServerLevel level, BlockPos from, RandomSource random, double bearing,
            int min, int max) {
        for (int attempt = 0; attempt < RING_ATTEMPTS; attempt++) {
            BlockPos point = ringPoint(from, random, bearing, min, max);
            if (isHomelandCore(level, point)) {
                BlockPos safe = safeSpotNear(level, point);
                if (safe != null) {
                    return safe;
                }
            }
        }
        Pair<BlockPos, Holder<Biome>> located = level.findClosestBiome3d(
                biome -> biome.is(ModTags.Biomes.HOMININ_HOMELAND), ringPoint(from, random, bearing, min, max),
                LOCATE_RADIUS, LOCATE_HORIZONTAL_STEP, LOCATE_VERTICAL_STEP);
        if (located != null) {
            BlockPos safe = safeSpotNear(level, located.getFirst());
            if (safe != null) {
                return safe;
            }
        }
        for (int attempt = 0; attempt < RING_ATTEMPTS; attempt++) {
            BlockPos point = ringPoint(from, random, bearing, min, max);
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

    private static BlockPos ringPoint(BlockPos from, RandomSource random, double bearing, int min, int max) {
        double angle = Double.isNaN(bearing) ? random.nextDouble() * Math.PI * 2.0D
                : bearing + (random.nextDouble() * 2.0D - 1.0D) * REGION_SPREAD;
        int distance = min + random.nextInt(max - min + 1);
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
