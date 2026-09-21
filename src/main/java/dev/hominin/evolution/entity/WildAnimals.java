package dev.hominin.evolution.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Puts the savanna's other animals around a player: baboon troops, the occasional giant
 * hyena, and rarely a saber-toothed cat. Each is spawned out of sight, only where none
 * of its kind is already nearby, and only on loaded ground.
 */
public final class WildAnimals {
    private static final int CHECK_INTERVAL_TICKS = 1200;

    private static final float TROOP_CHANCE = 0.6F;
    /** Troops are checked for twice as often as the predators. */
    private static final int TROOP_CHECK_TICKS = 600;
    private static final int TROOP_MIN = 14;
    private static final int TROOP_MAX = 20;

    private static final float HYENA_CHANCE = 0.15F;
    private static final float SABERTOOTH_CHANCE = 0.07F;
    /** One bird over the country at a time, and only by day. */
    private static final float EAGLE_CHANCE = 0.12F;
    /** The scimitar cat hunts in the open, in daylight, usually in pairs. */
    private static final float HOMOTHERIUM_CHANCE = 0.14F;
    /** Rare on purpose: meeting one should be an event, not a feature of the landscape. */
    private static final float DINOPITHECUS_CHANCE = 0.09F;
    /** Common where the forest starts; a rare sight out on the grass, and only later on. */
    private static final float CHIMP_JUNGLE_CHANCE = 0.6F;
    /** Out of the trees but within reach of them: savanna woodland near a jungle. */
    private static final float CHIMP_EDGE_CHANCE = 0.3F;
    private static final int CHIMP_EDGE_DISTANCE = 90;
    private static final float CHIMP_SAVANNA_CHANCE = 0.03F;
    /** Bonobos: erectus onward, by rivers and at forest edges. */
    private static final float BONOBO_CHANCE = 0.3F;
    /** A crocodile in any warm water worth drinking from - and twice as likely in a drought. */
    private static final float CROCODILE_CHANCE = 0.3F;

    public static void tick(ServerPlayer player) {
        if (player.tickCount % TROOP_CHECK_TICKS == 300 && !player.isSpectator()
                && player.level().dimension() == Level.OVERWORLD
                && none(player, Baboon.class, 80.0D) && player.getRandom().nextFloat() < TROOP_CHANCE) {
            spawnTroop(player);
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 600 || player.isSpectator()
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        RandomSource random = player.getRandom();
        if (none(player, Pachycrocuta.class, 128.0D) && random.nextFloat() < HYENA_CHANCE) {
            spawnGroup(player, ModEntities.PACHYCROCUTA.get(), random.nextFloat() < 0.3F ? 2 : 1, 40, 70);
        }
        if (none(player, Sabertooth.class, 160.0D) && random.nextFloat() < SABERTOOTH_CHANCE) {
            spawnGroup(player, ModEntities.SABERTOOTH.get(), 1, 50, 90);
        }
        if (player.level().isDay() && none(player, dev.hominin.evolution.entity.CrownedEagle.class, 120.0D)
                && random.nextFloat() < EAGLE_CHANCE) {
            spawnGroup(player, ModEntities.CROWNED_EAGLE.get(), 1, 30, 60);
        }
        if (player.level().isDay() && none(player, dev.hominin.evolution.entity.Homotherium.class, 140.0D)
                && random.nextFloat() < HOMOTHERIUM_CHANCE) {
            spawnGroup(player, ModEntities.HOMOTHERIUM.get(), random.nextBoolean() ? 2 : 1, 45, 80);
        }
        if (none(player, dev.hominin.evolution.entity.Chimpanzee.class, 128.0D)) {
            spawnCommunity(player, random);
        }
        if (!stillAround(player) && none(player, Bonobo.class, 160.0D) && random.nextFloat() < BONOBO_CHANCE) {
            spawnBonobos(player, random);
        }
        float crocodile = dev.hominin.evolution.survival.Drought.isActive(player.level())
                ? CROCODILE_CHANCE * 2.0F : CROCODILE_CHANCE;
        if (none(player, Crocodile.class, 96.0D) && random.nextFloat() < crocodile) {
            spawnCrocodile(player, random);
        }
        // They did not last as long as the hominins did. Once you are erectus they are
        // simply no longer out there, which is the only monument they get.
        if (stillAround(player) && none(player, dev.hominin.evolution.entity.Dinopithecus.class, 150.0D)
                && random.nextFloat() < DINOPITHECUS_CHANCE) {
            spawnGroup(player, ModEntities.DINOPITHECUS.get(), 2 + random.nextInt(2), 40, 75);
        }
    }

    /**
     * A community settles where the trees are: the edge of a jungle. Out in the open savanna
     * they are a rare find, and only once erectus is walking far enough to meet them.
     */
    private static void spawnCommunity(ServerPlayer player, RandomSource random) {
        ServerLevel level = player.serverLevel();
        BlockPos site = findSite(level, player.blockPosition(), 40, 80, random, false);
        if (site == null) {
            return;
        }
        boolean jungle = level.getBiome(site).is(net.minecraft.tags.BiomeTags.IS_JUNGLE);
        boolean late = !stillAround(player);
        float chance = jungle ? CHIMP_JUNGLE_CHANCE
                : jungleWithin(level, site, CHIMP_EDGE_DISTANCE) ? CHIMP_EDGE_CHANCE
                : late ? CHIMP_SAVANNA_CHANCE : 0.0F;
        if (random.nextFloat() >= chance) {
            return;
        }
        UUID community = UUID.randomUUID();
        UUID alpha = null;
        int size = 4 + random.nextInt(4);
        for (int i = 0; i < size; i++) {
            dev.hominin.evolution.entity.Chimpanzee chimp = place(level, ModEntities.CHIMPANZEE.get(), site,
                    random.nextInt(5));
            if (chimp != null) {
                chimp.joinCommunity(community, alpha);
                if (alpha == null) {
                    alpha = chimp.getUUID();
                }
            }
        }
    }

    /** Erectus or anything after it: the era when the world opens up. */
    public static boolean erectusOrLater(net.minecraft.world.entity.player.Player player) {
        return !stillAround(player);
    }

    /**
     * A bonobo troop settles by a river or at the forest's edge. A troop met by someone whose
     * people have hunted bonobos already knows it, and is no refuge.
     */
    private static void spawnBonobos(ServerPlayer player, RandomSource random) {
        ServerLevel level = player.serverLevel();
        BlockPos site = findSite(level, player.blockPosition(), 40, 80, random, false);
        if (site == null) {
            return;
        }
        var biome = level.getBiome(site);
        boolean suits = biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE) || biome.is(net.minecraft.tags.BiomeTags.IS_FOREST)
                || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER) || waterNear(level, site, 10);
        if (!suits) {
            return;
        }
        UUID troop = UUID.randomUUID();
        boolean hunted = Bonobo.isBetrayer(player);
        int size = 6 + random.nextInt(5);
        for (int i = 0; i < size; i++) {
            Bonobo bonobo = place(level, ModEntities.BONOBO.get(), site, random.nextInt(6));
            if (bonobo != null) {
                bonobo.joinTroop(troop, hunted);
            }
        }
    }

    /** Whether there is jungle within this many blocks - sampled in rings, not searched block by block. */
    private static boolean jungleWithin(ServerLevel level, BlockPos site, int distance) {
        for (int ring = 15; ring <= distance; ring += 15) {
            for (int point = 0; point < 16; point++) {
                float angle = point * Mth.TWO_PI / 16.0F;
                BlockPos pos = site.offset(Math.round(Mth.cos(angle) * ring), 0, Math.round(Mth.sin(angle) * ring));
                if (level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_JUNGLE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean waterNear(ServerLevel level, BlockPos at, int radius) {
        for (int attempt = 0; attempt < 40; attempt++) {
            BlockPos pos = at.offset(level.random.nextInt(radius * 2 + 1) - radius, -1,
                    level.random.nextInt(radius * 2 + 1) - radius);
            if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** One crocodile, lying in water at least two deep, somewhere warm. */
    private static void spawnCrocodile(ServerPlayer player, RandomSource random) {
        ServerLevel level = player.serverLevel();
        BlockPos around = player.blockPosition();
        for (int attempt = 0; attempt < 32; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            int distance = 24 + random.nextInt(33);
            int x = around.getX() + Math.round(Mth.cos(angle) * distance);
            int z = around.getZ() + Math.round(Mth.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos surface = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
            if (!level.getFluidState(surface).is(net.minecraft.tags.FluidTags.WATER)
                    || !level.getFluidState(surface.below()).is(net.minecraft.tags.FluidTags.WATER)) {
                continue;
            }
            var biome = level.getBiome(surface);
            boolean warm = biome.is(ModTags.Biomes.HOMININ_HOMELAND) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)
                    || biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE)
                    || biome.is(net.minecraft.world.level.biome.Biomes.SWAMP)
                    || biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP);
            if (!warm || biome.value().getBaseTemperature() < 0.5F || Bonobo.sanctuary(level, surface)) {
                continue;
            }
            Crocodile crocodile = ModEntities.CROCODILE.get().create(level);
            if (crocodile == null) {
                return;
            }
            crocodile.moveTo(x + 0.5D, surface.getY() - 0.4D, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            crocodile.finalizeSpawn(level, level.getCurrentDifficultyAt(surface), MobSpawnType.EVENT, null);
            level.addFreshEntity(crocodile);
            return;
        }
    }

    /** Whether Dinopithecus is still a living animal in this player's era. */
    private static boolean stillAround(net.minecraft.world.entity.player.Player player) {
        String stage = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA)
                .getStage().getPath();
        return !stage.equals("homo_erectus") && !stage.equals("homo_ergaster")
                && !stage.equals("homo_heidelbergensis") && !stage.equals("homo_sapiens")
                && !stage.equals("homo_neanderthalensis");
    }

    private static boolean none(ServerPlayer player, Class<? extends Mob> type, double radius) {
        return player.level().getEntitiesOfClass(type, player.getBoundingBox().inflate(radius)).isEmpty();
    }

    /** A whole troop, in the homeland only - baboons are savanna animals. */
    public static int spawnTroop(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        BlockPos site = findSite(level, player.blockPosition(), 36, 72, random, true);
        if (site == null) {
            return 0;
        }
        int size = TROOP_MIN + random.nextInt(TROOP_MAX - TROOP_MIN + 1);
        UUID troop = UUID.randomUUID();
        UUID leader = null;
        for (int i = 0; i < size; i++) {
            Baboon baboon = place(level, ModEntities.BABOON.get(), site, random.nextInt(6));
            if (baboon != null) {
                baboon.joinTroop(troop, leader);
                if (leader == null) {
                    leader = baboon.getUUID();
                }
            }
        }
        return size;
    }

    public static <T extends Mob> int spawnGroup(ServerPlayer player, EntityType<T> type, int count, int min, int max) {
        ServerLevel level = player.serverLevel();
        BlockPos site = findSite(level, player.blockPosition(), min, max, player.getRandom(), false);
        // Nothing that hunts comes into bonobo country.
        if (site == null || Bonobo.sanctuary(level, site)) {
            return 0;
        }
        for (int i = 0; i < count; i++) {
            place(level, type, site, i * 2);
        }
        if (type.is(ModTags.EntityTypes.PREDATORS) || type == ModEntities.DINOPITHECUS.get()) {
            dev.hominin.evolution.band.Paranthropus.warn(player, site);
        }
        return count;
    }

    @Nullable
    private static <T extends Mob> T place(ServerLevel level, EntityType<T> type, BlockPos site, int spread) {
        T mob = type.create(level);
        if (mob == null) {
            return null;
        }
        BlockPos pos = Band.standingSpotNear(level, site, spread, level.getRandom().nextFloat() * Mth.TWO_PI);
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        level.addFreshEntity(mob);
        return mob;
    }

    @Nullable
    private static BlockPos findSite(ServerLevel level, BlockPos around, int min, int max, RandomSource random,
            boolean homelandOnly) {
        for (int attempt = 0; attempt < 24; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            int distance = min + random.nextInt(max - min + 1);
            int x = around.getX() + Math.round(Mth.cos(angle) * distance);
            int z = around.getZ() + Math.round(Mth.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getFluidState(pos.below()).isEmpty() || !level.getBlockState(pos.below()).isSolid()) {
                continue;
            }
            if (homelandOnly && !level.getBiome(pos).is(ModTags.Biomes.HOMININ_HOMELAND)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    private WildAnimals() {
    }
}
