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
        // They did not last as long as the hominins did. Once you are erectus they are
        // simply no longer out there, which is the only monument they get.
        if (stillAround(player) && none(player, dev.hominin.evolution.entity.Dinopithecus.class, 150.0D)
                && random.nextFloat() < DINOPITHECUS_CHANCE) {
            spawnGroup(player, ModEntities.DINOPITHECUS.get(), 2 + random.nextInt(2), 40, 75);
        }
    }

    /** Whether Dinopithecus is still a living animal in this player's era. */
    private static boolean stillAround(ServerPlayer player) {
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
        if (site == null) {
            return 0;
        }
        for (int i = 0; i < count; i++) {
            place(level, type, site, i * 2);
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
