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
    private static final int CHECK_INTERVAL_TICKS = 1800;
    private static final float SPAWN_CHANCE = 0.25F;
    /** No new band while one is already this close. Neighbours are neighbours, not a crowd. */
    private static final double CROWDING_RADIUS = 220.0D;
    private static final int MIN_DISTANCE = 64;
    private static final int MAX_DISTANCE = 150;
    private static final int MIN_SIZE = 3;

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 == 0) {
            checkArrivals(player);
        }
        if (player.isSpectator()) {
            return;
        }
        if (player.tickCount % 200 == 40) {
            adoptStrays(player);
            materialize(player);
        }
        if (player.tickCount % 1200 == 300) {
            moveNomads(player);
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        // A new band settles only where there is room for one: no more than three camps within 260 blocks.
        ServerLevel level = player.serverLevel();
        ResourceLocation era = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        long near = Bands.all(level).stream().filter(b -> !b.nomadic() && !isExtinctBy(b.species, era)
                && Bands.horizontal(b.home, player.blockPosition()) < 360.0D * 360.0D).count();
        if (near < 3 && player.getRandom().nextFloat() < SPAWN_CHANCE) {
            int least = Bands.radiusFor(era) * 2 + 8;
            spawnNear(player, least, least + 90);
        }
    }

    /** Bands whose people are not about - nobody near their ground until now - are there again when you come. */
    private static void materialize(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Moves.tickLeaving(player);
        WildCamps.tick(player);
        ResourceLocation era = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        for (Bands.Record band : Bands.all(level)) {
            if (Bands.horizontal(band.home, player.blockPosition()) > 150.0D * 150.0D || isExtinctBy(band.species, era)
                    || !level.hasChunk(band.home.getX() >> 4, band.home.getZ() >> 4) || band.size <= 0) {
                continue;
            }
            boolean about = !level.getEntities(ModEntities.BAND_MEMBER.get(),
                    m -> m.isAlive() && band.id.equals(m.getBandId())).isEmpty();
            if (!about) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, band.home.getX(), band.home.getZ());
                placeBand(level, new BlockPos(band.home.getX(), y, band.home.getZ()), band.species, band.size, band.id,
                        player.getRandom());
            }
        }
    }

    /** Bands already out there from before bands were remembered: taken into the registry as they are. */
    private static void adoptStrays(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        java.util.Map<UUID, Integer> counts = new java.util.HashMap<>();
        java.util.Map<UUID, BlockPos> where = new java.util.HashMap<>();
        java.util.Map<UUID, ResourceLocation> kinds = new java.util.HashMap<>();
        for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(96.0D),
                m -> m.isWild() && m.getBandId() != null)) {
            UUID band = member.getBandId();
            if (Bands.retired(level, band)) {
                // One of a band from an age that is over.
                member.discard();
                continue;
            }
            if (Bands.get(level, band) != null) {
                continue;
            }
            counts.merge(band, 1, Integer::sum);
            kinds.putIfAbsent(band, member.getStage());
            if (member.isAlpha() || !where.containsKey(band)) {
                BlockPos home = Territory.homeOf(band);
                where.put(band, home != null ? home : member.blockPosition());
            }
        }
        for (var entry : counts.entrySet()) {
            Bands.register(level, entry.getKey(), kinds.get(entry.getKey()), where.get(entry.getKey()), entry.getValue());
        }
    }

    /**
     * Paranthropus holds no ground: every day a troop moves on, to wherever there is food or water - and
     * in hard times, off any band's ground, because a band will not share what little there is with a
     * troop that strips the ground bare.
     */
    private static void moveNomads(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long day = level.getDayTime() / 24000L;
        boolean hard = dev.hominin.evolution.survival.Seasons.strained(level);
        for (Bands.Record troop : Bands.all(level)) {
            if (!troop.nomadic() || troop.movedDay >= day
                    || Bands.horizontal(troop.home, player.blockPosition()) > 400.0D * 400.0D) {
                continue;
            }
            Bands.Record owner = Bands.groundAt(level, troop.home);
            BlockPos next = null;
            for (int attempt = 0; attempt < 8 && next == null; attempt++) {
                BlockPos candidate = trySite(level, troop.home, 80, 160, player.getRandom(), false);
                if (candidate == null || worthOf(level, candidate, true) == 0) {
                    continue;
                }
                Bands.Record ground = Bands.groundAt(level, candidate);
                if (hard && ground != null) {
                    continue;
                }
                next = candidate;
            }
            troop.movedDay = day;
            if (next == null) {
                continue;
            }
            if (hard && owner != null && owner.knownTo(player.getUUID())
                    && Bands.horizontal(troop.home, player.blockPosition()) < 160.0D * 160.0D) {
                player.sendSystemMessage(Component.literal(dev.hominin.evolution.band.BandNames.capital(owner.name)
                        + " drive " + troop.name + " off their ground - there is not enough to share with Paranthropus.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            troop.home = next;
            Bands.changed(level);
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
        ResourceLocation stage = forcedStage != null ? forcedStage
                : speciesFor(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage(), random);
        BlockPos site = findSite(level, player.blockPosition(), minDistance, maxDistance, random, loadChunks, stage);
        if (site == null) {
            return 0;
        }
        int start = BandSizes.of(stage).start();
        int size = MIN_SIZE + random.nextInt(Math.max(1, start - MIN_SIZE + 1));
        UUID bandId = UUID.randomUUID();
        Bands.Record record = Bands.register(level, bandId, stage, site, size);
        // Near enough to be about now; further out, they will be there when someone comes.
        if (Bands.horizontal(site, player.blockPosition()) < 150.0D * 150.0D) {
            placeBand(level, site, stage, size, bandId, random);
        }
        Territory.settle(bandId, site);
        Relations.call(player, level, record, (int) Math.round(Math.sqrt(site.distSqr(player.blockPosition()))));
        arrivals.put(player.getUUID(), new Arrival(bandId, site, level.getGameTime() + ARRIVAL_MEMORY_TICKS));
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
    /** "north-east", and so on: which way this is from where the player stands. */
    public static String bearingTo(ServerPlayer player, BlockPos site) {
        return bearingFrom(player, site);
    }

    static String bearingFrom(ServerPlayer player, BlockPos site) {
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
    /**
     * Puts a whole band of this species down around a spot, alpha first. Used by the
     * spawner and by the spawn eggs for species that only exist as other bands.
     *
     * @return the alpha, or null if nobody could be made
     */
    @Nullable
    public static BandMember placeBand(ServerLevel level, BlockPos site, ResourceLocation stage, int size, UUID bandId,
            RandomSource random) {
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
            if (Paranthropus.STAGE.equals(stage)) {
                equipParanthropus(member, random);
            } else {
                equip(member, random);
            }
            Bands.Record held = Bands.get(level, bandId);
            if (held != null && held.haven != null) {
                // A haven's people: the best armed of their kind.
                dev.hominin.evolution.world.Havens.arm(member, random);
            }
            Bands.Record record = Bands.get(level, bandId);
            if (record != null && record.desperation >= 3) {
                for (int n = 0; n < record.desperation - 2 && member.hasFood(); n++) {
                    member.takeFood();
                }
            }
            level.addFreshEntity(member);
            if (alpha == null) {
                alpha = member;
            }
        }
        if (alpha != null) {
            Territory.settle(bandId, site);
            Bands.Record band = Bands.get(level, bandId);
            if (band != null && !band.nomadic() && Bands.horizontal(band.home, site) < 16.0D * 16.0D) {
                ToolPiles.stockCamp(level, bandId, band.home, stage, random);
            }
        }
        return alpha;
    }

    /** Sharpened sticks, long branches, and food: never stone, unless they have learned it. */
    private static void equipParanthropus(BandMember member, RandomSource random) {
        if (random.nextFloat() < 0.5F) {
            member.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.LONG_BRANCH.get()));
        } else if (random.nextFloat() < 0.6F) {
            member.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SHARPENED_STICK.get()));
        }
        if (random.nextFloat() < 0.5F) {
            member.getInventory().addItem(new ItemStack(ModItems.SHARPENED_STICK.get()));
        }
        member.getInventory().addItem(random.nextBoolean()
                ? new ItemStack(ModItems.GRUB.get(), 2 + random.nextInt(3))
                : new ItemStack(Items.SWEET_BERRIES, 3 + random.nextInt(3)));
    }

    private static void equip(BandMember member, RandomSource random) {
        if (random.nextFloat() < 0.3F) {
            member.getInventory().addItem(dev.hominin.evolution.item.StoneMaterial.stamp(
                    new ItemStack(ModItems.LOMEKWIAN_TOOL.get()), random.nextBoolean()
                            ? null : dev.hominin.evolution.item.StoneMaterial.QUARTZITE));
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
    /**
     * Where a band settles. A band needs a reason to live somewhere - water, food (termite mounds, berries,
     * a carcass) or good stone - and the more of them a place has, the likelier a band is to be there. Nobody
     * settles where there is none of the three. A hominin band's ground never runs into another band's, or
     * into ground a player's band is living on. Paranthropus holds no ground and only needs food or water.
     */
    @Nullable
    private static BlockPos findSite(ServerLevel level, BlockPos around, int minDistance, int maxDistance,
            RandomSource random, boolean loadChunks, ResourceLocation species) {
        boolean troop = Paranthropus.STAGE.equals(species);
        int radius = Bands.radiusFor(species);
        java.util.List<BlockPos> camps = new java.util.ArrayList<>();
        for (ServerPlayer player : level.players()) {
            camps.add(dev.hominin.evolution.hunt.Predation.campOf(player));
        }
        int playerRadius = Bands.radiusFor(level.players().isEmpty() ? species
                : level.players().get(0).getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        BlockPos best = null;
        int bestScore = 0;
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos candidate = trySite(level, around, minDistance, maxDistance, random, loadChunks);
            if (candidate == null || (!troop && !Bands.groundIsFree(level, candidate, radius, camps, playerRadius))) {
                continue;
            }
            int score = worthOf(level, candidate, troop);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
            if (score >= 3) {
                break;
            }
        }
        // One reason to be there is enough; more make it likelier.
        return best != null && random.nextFloat() < 0.4F + 0.2F * bestScore ? best : null;
    }

    /**
     * What a site offers: water, food, good stone - one point each. For Paranthropus, stone is nothing to
     * them: only food and water count.
     */
    static int worthOf(ServerLevel level, BlockPos site, boolean troop) {
        boolean water = false;
        boolean food = false;
        boolean stone = false;
        for (BlockPos pos : BlockPos.betweenClosed(site.offset(-16, -4, -16), site.offset(16, 5, 16))) {
            // Never load country to look at it: what is not loaded does not count.
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            if (!water && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
                water = true;
                continue;
            }
            var state = level.getBlockState(pos);
            if (!food && (state.is(dev.hominin.evolution.ModBlocks.TERMITE_MOUND.get())
                    || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                    || state.is(dev.hominin.evolution.ModBlocks.CARCASS.get()))) {
                food = true;
            } else if (!stone && !troop && (state.is(dev.hominin.evolution.ModBlocks.CHERT_DEPOSIT.get())
                    || state.is(dev.hominin.evolution.ModBlocks.FINE_CHERT_DEPOSIT.get())
                    || state.is(dev.hominin.evolution.ModBlocks.QUARTZITE_DEPOSIT.get())
                    || state.is(dev.hominin.evolution.ModBlocks.BASALT_DEPOSIT.get())
                    || state.is(dev.hominin.evolution.ModBlocks.CHERT_ROCK.get())
                    || state.is(dev.hominin.evolution.ModBlocks.GRANITE_ROCK.get())
                    || state.is(dev.hominin.evolution.ModBlocks.BASALT_ROCK.get()))) {
                stone = true;
            }
            if (water && food && (stone || troop)) {
                break;
            }
        }
        return (water ? 1 : 0) + (food ? 1 : 0) + (stone ? 1 : 0);
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
            // Paranthropus outlasted the other early forms, and was gone by antecessor.
            case "paranthropus_boisei" -> now >= order(stage("homo_heidelbergensis"));
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
        // Just past your own ground: the nearest another band's can start without the two running together.
        int least = Bands.radiusFor(era) * 2 + 8;
        for (int i = 0; i < bands; i++) {
            spawnNear(player, least, least + 80, era, true);
        }
    }

    private WildBands() {
    }
}
