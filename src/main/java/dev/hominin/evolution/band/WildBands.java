package dev.hominin.evolution.band;

import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Other bands of the player's own species, out in the same country. They keep to
 * themselves and will not join you - but they carry things, and will trade.
 *
 * <p>A band is spawned out of sight near a player who has none nearby, every so often.
 * Wild bands are not kept when nobody is near them, so the world does not fill up.
 */
public final class WildBands {
    private static final int CHECK_INTERVAL_TICKS = 600;
    private static final float SPAWN_CHANCE = 0.6F;
    /** No new band while one is already this close: roughly one band every 100-200 blocks. */
    private static final double CROWDING_RADIUS = 100.0D;
    private static final int MIN_DISTANCE = 64;
    private static final int MAX_DISTANCE = 150;
    private static final int MIN_SIZE = 3;

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 == 0) {
            checkArrivals(player);
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0 || player.isSpectator()) {
            return;
        }
        boolean crowded = !player.level().getEntitiesOfClass(BandMember.class,
                player.getBoundingBox().inflate(CROWDING_RADIUS), BandMember::isWild).isEmpty();
        if (!crowded && player.getRandom().nextFloat() < SPAWN_CHANCE) {
            spawnNear(player, MIN_DISTANCE, MAX_DISTANCE);
        }
    }

    /** Spawns a band somewhere between the two distances. Returns how many came, 0 if nowhere fit. */
    public static int spawnNear(ServerPlayer player, int minDistance, int maxDistance) {
        return spawnNear(player, minDistance, maxDistance, null, false);
    }

    /**
     * @param forcedStage the species to spawn, or null to pick one for the player's era
     * @param loadChunks  whether a site may load terrain - only on arrival, when nothing is loaded yet
     */
    public static int spawnNear(ServerPlayer player, int minDistance, int maxDistance,
            @Nullable ResourceLocation forcedStage, boolean loadChunks) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        BlockPos site = findSite(level, player.blockPosition(), minDistance, maxDistance, random, loadChunks);
        if (site == null) {
            return 0;
        }
        ResourceLocation stage = forcedStage != null ? forcedStage
                : speciesFor(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage(), random);
        int start = BandSizes.of(stage).start();
        int size = MIN_SIZE + random.nextInt(Math.max(1, start - MIN_SIZE + 1));
        UUID bandId = UUID.randomUUID();
        BandMember alpha = null;
        for (int i = 0; i < size; i++) {
            BlockPos pos = Band.standingSpotNear(level, site, random.nextInt(3), random.nextFloat() * Mth.TWO_PI);
            BandMember member = ModEntities.BAND_MEMBER.get().create(level);
            if (member == null) {
                continue;
            }
            member.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            member.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            member.setStage(stage);
            member.joinWildBand(bandId, alpha == null ? null : alpha.getUUID());
            member.ensureName();
            equip(member, random);
            level.addFreshEntity(member);
            if (alpha == null) {
                alpha = member;
            }
        }
        if (alpha != null) {
            Territory.settle(bandId, site);
            level.playSound(null, alpha.blockPosition(), ModSounds.BAND_PANT_HOOT.get(), SoundSource.NEUTRAL, 3.0F, 0.9F);
            // A call carries, and a call tells you which way to walk. The glow is no use from
            // here - they are further off than anything renders - so it waits until you are close.
            int distance = (int) Math.round(Math.sqrt(site.distSqr(player.blockPosition())));
            player.sendSystemMessage(Component.literal("Another band is calling, " + bearingFrom(player, site)
                    + ", about " + distance + " blocks off.").withStyle(net.minecraft.ChatFormatting.GOLD));
            arrivals.put(player.getUUID(), new Arrival(bandId, site, level.getGameTime() + ARRIVAL_MEMORY_TICKS));
        }
        return size;
    }

    /** How long a call is worth following up before the band has moved on. */
    private static final long ARRIVAL_MEMORY_TICKS = 12000L;
    /** How close you have to get before you can pick them out of the country. */
    private static final double SIGHTING_RANGE = 64.0D;
    private static final int SIGHTING_GLOW_TICKS = 30 * 20;

    private record Arrival(UUID band, BlockPos site, long expiresAt) {
    }

    private static final java.util.Map<UUID, Arrival> arrivals = new java.util.HashMap<>();

    /** Walking towards a call: once they are in reach, they light up so you can actually find them. */
    private static void checkArrivals(ServerPlayer player) {
        Arrival arrival = arrivals.get(player.getUUID());
        if (arrival == null) {
            return;
        }
        if (player.level().getGameTime() > arrival.expiresAt()) {
            arrivals.remove(player.getUUID());
            return;
        }
        if (player.blockPosition().distSqr(arrival.site()) > SIGHTING_RANGE * SIGHTING_RANGE) {
            return;
        }
        int seen = 0;
        for (BandMember member : Band.near(player, SIGHTING_RANGE)) {
            if (arrival.band().equals(member.getBandId())) {
                member.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, SIGHTING_GLOW_TICKS, 0, false, false));
                seen++;
            }
        }
        if (seen == 0) {
            return;
        }
        arrivals.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("You pick them out of the grass: " + seen
                + (seen == 1 ? " of them." : " of them.")).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** Which way to walk, in words. */
    private static String bearingFrom(ServerPlayer player, BlockPos site) {
        double dx = site.getX() - player.getX();
        double dz = site.getZ() - player.getZ();
        String northSouth = Math.abs(dz) < Math.abs(dx) / 2.0D ? "" : dz < 0 ? "north" : "south";
        String eastWest = Math.abs(dx) < Math.abs(dz) / 2.0D ? "" : dx < 0 ? "west" : "east";
        return "to the " + (northSouth + eastWest).replace("northeast", "north-east")
                .replace("northwest", "north-west").replace("southeast", "south-east")
                .replace("southwest", "south-west");
    }

    public static void forget(UUID player) {
        arrivals.remove(player);
    }

    /** What a wild band carries: some of it worth trading for. */
    private static void equip(BandMember member, RandomSource random) {
        if (random.nextFloat() < 0.3F) {
            member.getInventory().addItem(new ItemStack(ModItems.LOMEKWIAN_TOOL.get()));
        }
        if (random.nextFloat() < 0.3F) {
            member.getInventory().addItem(new ItemStack(ModItems.TERMITE_STICK.get()));
        }
        if (random.nextFloat() < 0.25F) {
            member.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.LONG_BRANCH.get()));
        }
        if (random.nextFloat() < 0.5F) {
            member.getInventory().addItem(random.nextBoolean()
                    ? new ItemStack(ModItems.GRUB.get(), 2)
                    : new ItemStack(Items.SWEET_BERRIES, 3));
        }
    }

    /**
     * Where a band settles. Bands live where the water and the stone are, and best of all
     * where both are - so sites are scored for what is around them, and the best of a
     * dozen tries wins.
     */
    @Nullable
    private static BlockPos findSite(ServerLevel level, BlockPos around, int minDistance, int maxDistance,
            RandomSource random, boolean loadChunks) {
        BlockPos best = null;
        int bestScore = -1;
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos candidate = trySite(level, around, minDistance, maxDistance, random, loadChunks);
            if (candidate == null) {
                continue;
            }
            int score = worthOf(level, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
            // Water and stone together is as good as it gets; stop looking.
            if (score >= 3) {
                break;
            }
        }
        return best;
    }

    /** What a site is worth: water nearby, stone nearby, both together best of all. */
    private static int worthOf(ServerLevel level, BlockPos site) {
        boolean water = false;
        boolean stone = false;
        for (BlockPos pos : BlockPos.betweenClosed(site.offset(-12, -4, -12), site.offset(12, 4, 12))) {
            if (!water && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
                water = true;
            } else if (!stone && (level.getBlockState(pos).is(dev.hominin.evolution.ModTags.Blocks.WORKABLE_STONE_DEPOSIT)
                    || level.getBlockState(pos).getBlock() instanceof dev.hominin.evolution.block.LooseRockBlock)) {
                stone = true;
            }
            if (water && stone) {
                return 3;
            }
        }
        return water ? 2 : stone ? 1 : 0;
    }

    @Nullable
    private static BlockPos trySite(ServerLevel level, BlockPos around, int minDistance, int maxDistance,
            RandomSource random, boolean loadChunks) {
        for (int attempt = 0; attempt < 4; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            int distance = minDistance + random.nextInt(Math.max(1, maxDistance - minDistance + 1));
            int x = around.getX() + Math.round(Mth.cos(angle) * distance);
            int z = around.getZ() + Math.round(Mth.sin(angle) * distance);
            // Only in chunks already loaded: a spawn should never force the world to generate.
            if (loadChunks) {
                level.getChunk(x >> 4, z >> 4);
            } else if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos.below()).isEmpty() && level.getBlockState(pos.below()).isSolid()
                    && level.getFluidState(pos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ species through time

    private static ResourceLocation stage(String path) {
        return ResourceLocation.fromNamespaceAndPath(dev.hominin.evolution.HomininEvolutionMod.MODID, path);
    }

    /**
     * Which species another band belongs to, given the player's era. Older species linger
     * for a while as rarer neighbours, then die out: Australopithecus is gone by the time
     * of erectus, habilis thins out under erectus, and erectus itself hangs on until
     * behaviourally modern sapiens.
     */
    public static ResourceLocation speciesFor(ResourceLocation era, RandomSource random) {
        // The marginal forms are out there whether or not you have ever been one: anamensis
        // beside Australopithecus, rudolfensis beside habilis, ergaster beside erectus.
        float roll = random.nextFloat();
        ResourceLocation cousin = switch (era.getPath()) {
            case "australopithecus", "australopithecus_anamensis" -> stage("australopithecus_anamensis");
            case "homo_habilis", "homo_rudolfensis" -> stage("homo_rudolfensis");
            case "homo_erectus", "homo_ergaster" -> stage("homo_ergaster");
            default -> null;
        };
        if (cousin != null && roll < 0.25F) {
            return cousin;
        }
        // A fallback's own era is otherwise the species it stands behind.
        ResourceLocation standard = switch (era.getPath()) {
            case "australopithecus_anamensis" -> stage("australopithecus");
            case "homo_rudolfensis" -> stage("homo_habilis");
            case "homo_ergaster" -> stage("homo_erectus");
            default -> era;
        };
        era = standard;
        return switch (era.getPath()) {
            case "homo_habilis" -> random.nextFloat() < 0.25F ? stage("australopithecus") : era;
            case "homo_erectus" -> random.nextFloat() < 0.2F ? stage("homo_habilis") : era;
            case "homo_heidelbergensis", "homo_neanderthalensis" ->
                    random.nextFloat() < 0.3F ? stage("homo_erectus") : era;
            default -> era;
        };
    }

    /** Whether a species has died out by this era. */
    public static boolean isExtinctBy(ResourceLocation species, ResourceLocation era) {
        int born = order(species);
        int now = order(era);
        return switch (species.getPath()) {
            case "australopithecus" -> now >= order(stage("homo_erectus"));
            case "homo_habilis" -> now >= order(stage("homo_heidelbergensis"));
            case "homo_erectus" -> now >= order(stage("homo_sapiens"));
            default -> born >= 0 && now >= 0 && now - born >= 3;
        };
    }

    private static int order(ResourceLocation stage) {
        return switch (stage.getPath()) {
            case "ardipithecus" -> 0;
            case "australopithecus_anamensis" -> 1;
            case "australopithecus" -> 1;
            case "homo_rudolfensis" -> 2;
            case "homo_habilis" -> 2;
            case "homo_ergaster" -> 3;
            case "homo_erectus" -> 3;
            case "homo_heidelbergensis", "homo_neanderthalensis" -> 4;
            case "homo_sapiens" -> 5;
            default -> -1;
        };
    }

    /** After evolving, the other bands of species that have now died out are gone. */
    public static void cullExtinct(ServerPlayer player) {
        ResourceLocation era = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        for (BandMember member : player.serverLevel().getEntities(ModEntities.BAND_MEMBER.get(),
                m -> m.isWild() && isExtinctBy(m.getStage(), era))) {
            member.discard();
        }
    }

    /** A new species arrives among others of its kind: a few bands near where the player wakes. */
    public static void onArrival(ServerPlayer player) {
        ResourceLocation era = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        int bands = 2 + player.getRandom().nextInt(2);
        for (int i = 0; i < bands; i++) {
            spawnNear(player, 40, 110, era, true);
        }
    }

    private WildBands() {
    }
}
