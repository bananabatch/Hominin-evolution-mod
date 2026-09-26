package dev.hominin.evolution.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Bands;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Oases - and havens, which are kept the same way. A big pool in dry country, in a horseshoe of hill open on one side,
 * gravel at its edges and good stone lying about; and at first light, everything comes to drink: grazers, the big
 * herds, a Paranthropus troop (who will trade while they are there) - and now and then a hunter, which drinks its
 * share and takes a small animal or two, and leaves hominins be. Kill six of what comes at dawn and they stay away
 * for two days.
 */
public final class Oases {
    /** Visitors of the morning: tagged, so a hunter among them keeps the truce and kills can be counted. */
    public static final String TRUCE = "hominin_truce";
    private static final String VISITOR = "hominin_visitor";
    private static final int KILLS_TO_SCARE = 6;
    private static final int DAYS_AWAY = 2;

    /** Which place each visitor came to, for counting kills. */
    private static final Map<UUID, String> visitorOf = new HashMap<>();
    /** Place, day: kills among its dawn visitors. */
    private static final Map<String, Integer> killed = new HashMap<>();
    /** Place: the first day the animals come back. */
    private static final Map<String, Integer> awayUntil = new HashMap<>();
    /** Hunters keeping the truce, and when the morning is over for them. */
    private static final Map<UUID, Long> truceUntil = new HashMap<>();

    private static int day(ServerLevel level) {
        return (int) ((level.getDayTime() + 1000L) / 24000L);
    }

    /** First light at an oasis or a haven: the animals come. */
    public static void dawn(ServerPlayer player, Pois.Poi poi) {
        ServerLevel level = player.serverLevel();
        if (day(level) < awayUntil.getOrDefault(poi.id(), 0) || !level.hasChunk(poi.pos().getX() >> 4, poi.pos().getZ() >> 4)) {
            return;
        }
        RandomSource random = player.getRandom();
        boolean later = dev.hominin.evolution.entity.WildAnimals.erectusOrLater(player);
        BlockPos at = poi.pos();
        int grazers = 3 + random.nextInt(3);
        for (int i = 0; i < grazers; i++) {
            EntityType<? extends Mob> type = later ? ModEntities.RUSINGORYX.get()
                    : random.nextBoolean() ? EntityType.HORSE : EntityType.DONKEY;
            visitor(level, type, at, 13 + random.nextInt(5), poi, false);
        }
        // The big herds.
        int big = 1 + random.nextInt(2);
        for (int i = 0; i < big; i++) {
            EntityType<? extends Mob> type = random.nextBoolean() ? ModEntities.MEGALOTRAGUS.get() : ModEntities.PELOROVIS.get();
            visitor(level, type, at, 14 + random.nextInt(5), poi, false);
        }
        // Paranthropus, down to drink: they will trade while they are here.
        var era = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        Bands.Record troop = nearestTroop(level, at);
        if (troop != null && dev.hominin.evolution.band.Paranthropus.stillAround(era) && random.nextFloat() < 0.6F) {
            for (int i = 0; i < 2 + random.nextInt(2); i++) {
                BandMember one = ModEntities.BAND_MEMBER.get().create(level);
                if (one == null) {
                    break;
                }
                BlockPos spot = Band.standingSpotNear(level, at, 15 + random.nextInt(3), random.nextFloat() * Mth.TWO_PI);
                one.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                one.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
                one.setStage(troop.species);
                one.joinWildBand(troop.id, null);
                one.addTag(VISITOR);
                level.addFreshEntity(one);
            }
        }
        // Now and then a hunter comes to drink - and keeps the truce of the water.
        if (random.nextFloat() < 0.3F) {
            EntityType<? extends Mob> type = later && random.nextBoolean() ? ModEntities.SABERTOOTH.get()
                    : ModEntities.CROCUTA.get();
            Mob hunter = visitor(level, type, at, 16 + random.nextInt(4), poi, true);
            if (hunter != null) {
                truceUntil.put(hunter.getUUID(), level.getGameTime() + 4000L);
            }
        }
        if (Bands.horizontal(at, player.blockPosition()) < 160.0D * 160.0D) {
            player.sendSystemMessage(Component.literal("First light at the " + (poi.kind() == Pois.Kind.OASIS ? "oasis"
                    : "haven") + ": the animals are coming down to drink.").withStyle(ChatFormatting.GRAY));
        }
    }

    @Nullable
    private static Bands.Record nearestTroop(ServerLevel level, BlockPos at) {
        Bands.Record best = null;
        double bestDistance = 700.0D * 700.0D;
        for (Bands.Record band : Bands.all(level)) {
            if (!band.nomadic()) {
                continue;
            }
            double d = Bands.horizontal(dev.hominin.evolution.band.Relations.whereIs(level, band), at);
            if (d < bestDistance) {
                bestDistance = d;
                best = band;
            }
        }
        return best;
    }

    @Nullable
    private static Mob visitor(ServerLevel level, EntityType<? extends Mob> type, BlockPos at, int distance, Pois.Poi poi,
            boolean truce) {
        Mob mob = type.create(level);
        if (mob == null) {
            return null;
        }
        BlockPos spot = Band.standingSpotNear(level, at, distance, level.random.nextFloat() * Mth.TWO_PI);
        mob.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        mob.addTag(VISITOR);
        if (truce) {
            mob.addTag(TRUCE);
        }
        level.addFreshEntity(mob);
        visitorOf.put(mob.getUUID(), poi.id());
        // Down to the water.
        mob.getNavigation().moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 1.0D);
        return mob;
    }

    /** One of the morning's visitors killed. Six in a day, and the rest keep away for two days. */
    public static void died(LivingEntity dead, @Nullable net.minecraft.world.entity.Entity killer) {
        String place = visitorOf.remove(dead.getUUID());
        truceUntil.remove(dead.getUUID());
        if (place == null || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        String key = place + "|" + day(level);
        int count = killed.merge(key, 1, Integer::sum);
        if (count == KILLS_TO_SCARE) {
            awayUntil.put(place, day(level) + DAYS_AWAY + 1);
            if (killer instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Too much killing at the water. The animals will keep away from "
                        + "here for the next two days.").withStyle(ChatFormatting.GOLD));
            }
        }
        if (killed.size() > 256) {
            killed.clear();
        }
    }

    /** Whether this hunter is keeping the truce of the water - it leaves hominins alone. */
    public static boolean keepsTruce(Mob mob) {
        return mob.getTags().contains(TRUCE);
    }

    /** Every so often: hunters whose morning is over slip away, out of sight. */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 200L != 0L || truceUntil.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        truceUntil.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            if (!(level.getEntity(entry.getKey()) instanceof Mob hunter) || !hunter.isAlive()) {
                return true;
            }
            if (level.getNearestPlayer(hunter, 48.0D) == null) {
                hunter.discard();
                visitorOf.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    // ------------------------------------------------------------ laying one out

    /**
     * The oasis itself: a big pool, two deep, with a sand shore and gravel along it and salt crusted at the water here
     * and there; loose good stone and a little outcrop of it; and a horseshoe of hill round it, open on one side.
     */
    static BlockPos layOut(ServerLevel level, BlockPos ground, RandomSource random) {
        int cx = ground.getX();
        int cz = ground.getZ();
        int y = ground.getY();
        float opening = random.nextFloat() * Mth.TWO_PI;
        for (int dx = -25; dx <= 25; dx++) {
            for (int dz = -25; dz <= 25; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                double d = Math.sqrt(dx * dx * 0.8D + dz * dz);
                BlockPos floor = new BlockPos(x, y, z);
                if (d <= 11.0D) {
                    // The pool: cleared to the sky, two deep in the middle, one at the edge.
                    int depth = d <= 7.5D ? 2 : 1;
                    for (int up = 1; up <= 6; up++) {
                        level.setBlock(floor.above(up), Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(floor.below(depth), d <= 7.5D ? Blocks.CLAY.defaultBlockState()
                            : Blocks.SAND.defaultBlockState(), 2);
                    for (int down = 0; down < depth; down++) {
                        level.setBlock(floor.below(down), Blocks.WATER.defaultBlockState(), 3);
                    }
                } else if (d <= 14.5D) {
                    // The shore: sand, gravel in patches along it - the gravel is worth sifting - and salt, here and
                    // there, crusted at the water's edge.
                    for (int up = 1; up <= 4; up++) {
                        BlockState above = level.getBlockState(floor.above(up));
                        if (!above.isAir() && above.canBeReplaced()) {
                            level.setBlock(floor.above(up), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                    boolean gravel = Math.floorMod((int) (Math.atan2(dz, dx) * 4.0D), 3) == 0 || d <= 12.0D && random.nextFloat() < 0.3F;
                    boolean salt = !gravel && d <= 12.5D && Math.floorMod((int) (Math.atan2(dz, dx) * 5.0D), 4) == 1
                            && random.nextFloat() < 0.45F;
                    level.setBlock(floor, salt ? ModBlocks.SALT_BLOCK.get().defaultBlockState()
                            : gravel ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState(), 2);
                    level.setBlock(floor.below(), Blocks.SAND.defaultBlockState(), 2);
                    BlockState reed = random.nextBoolean() ? Blocks.SUGAR_CANE.defaultBlockState()
                            : Blocks.SHORT_GRASS.defaultBlockState();
                    if (!gravel && !salt && d <= 12.0D && random.nextFloat() < 0.25F && level.getBlockState(floor.above()).isAir()
                            && reed.canSurvive(level, floor.above())) {
                        level.setBlock(floor.above(), reed, 2);
                    }
                } else if (d >= 17.0D && d <= 25.0D) {
                    // The horseshoe: a ring of hill, highest in the middle of its span, open on one side.
                    float angle = (float) Math.atan2(dz, dx);
                    float off = Math.abs(Mth.wrapDegrees((angle - opening) * Mth.RAD_TO_DEG));
                    if (off < 30.0F) {
                        continue;
                    }
                    double across = 1.0D - Math.abs(d - 21.0D) / 4.0D;
                    int height = (int) Math.round(across * (4.0D + Math.min(2.0D, (off - 30.0F) / 30.0D)));
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (height <= 0 || Math.abs(top - y) > 4) {
                        continue;
                    }
                    for (int up = 1; up <= height; up++) {
                        BlockPos at = new BlockPos(x, top + up, z);
                        BlockState was = level.getBlockState(at);
                        if (was.isAir() || was.canBeReplaced()) {
                            level.setBlock(at, up == height ? Blocks.GRASS_BLOCK.defaultBlockState()
                                    : Blocks.DIRT.defaultBlockState(), 2);
                        }
                    }
                    level.setBlock(new BlockPos(x, top, z), Blocks.DIRT.defaultBlockState(), 2);
                }
            }
        }
        // Good stone lying about the shore, and a little outcrop of it.
        BlockState[] rocks = {ModBlocks.CHERT_ROCK.get().defaultBlockState(), ModBlocks.GRANITE_ROCK.get().defaultBlockState(),
                ModBlocks.BASALT_ROCK.get().defaultBlockState()};
        for (int i = 0; i < 10; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            int reach = 12 + random.nextInt(3);
            int x = cx + Math.round(Mth.cos(angle) * reach);
            int z = cz + Math.round(Mth.sin(angle) * reach);
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockState rock = rocks[random.nextInt(rocks.length)];
            if (level.getBlockState(at).isAir() && rock.canSurvive(level, at)) {
                level.setBlock(at, rock, 2);
            }
        }
        float stoneAngle = opening + Mth.PI;
        int sx = cx + Math.round(Mth.cos(stoneAngle) * 14.0F);
        int sz = cz + Math.round(Mth.sin(stoneAngle) * 14.0F);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 1 && random.nextBoolean()) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx + dx, sz + dz);
                level.setBlock(new BlockPos(sx + dx, top, sz + dz), ModBlocks.QUARTZITE_DEPOSIT.get().defaultBlockState(), 2);
            }
        }
        return ground;
    }

    /** Hominins like an oasis as much as anyone: two in five have a band living at them already. */
    static void maybeInhabit(ServerLevel level, BlockPos centre, @Nullable ServerPlayer near) {
        if (near == null || Bands.groundAt(level, centre) != null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (dev.hominin.evolution.hunt.Predation.settled(player)
                    && Bands.horizontal(dev.hominin.evolution.hunt.Predation.campOf(player), centre) < 120.0D * 120.0D) {
                return;
            }
        }
        RandomSource random = RandomSource.create(centre.asLong() ^ level.getSeed());
        if (random.nextFloat() >= 0.4F) {
            return;
        }
        var era = near.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        var species = dev.hominin.evolution.band.WildBands.speciesFor(era, random);
        if (dev.hominin.evolution.band.Paranthropus.STAGE.equals(species)) {
            species = era;
        }
        UUID id = UUID.randomUUID();
        BlockPos camp = centre.offset(19, 0, 0);
        Bands.register(level, id, species, camp, dev.hominin.evolution.band.BandSizes.of(species).start() + 1
                + random.nextInt(3));
        dev.hominin.evolution.band.Territory.settle(id, camp);
    }

    /** The oases and havens within reach of somewhere, for a band thinking of moving: any nobody holds. */
    public static List<BlockPos> emptyNear(ServerLevel level, BlockPos around, int radius) {
        List<BlockPos> list = new ArrayList<>();
        for (Pois.Poi poi : Pois.near(level, around, radius, true)) {
            if (poi.kind() == Pois.Kind.OASIS && Bands.groundAt(level, poi.pos()) == null) {
                list.add(poi.pos());
            }
        }
        return list;
    }

    private Oases() {
    }
}
