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

    private static final float TROOP_CHANCE = 0.3F;
    private static final int TROOP_MIN = 14;
    private static final int TROOP_MAX = 20;

    private static final float HYENA_CHANCE = 0.15F;
    private static final float SABERTOOTH_CHANCE = 0.07F;

    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_INTERVAL_TICKS != 600 || player.isSpectator()
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        RandomSource random = player.getRandom();
        if (none(player, Baboon.class, 128.0D) && random.nextFloat() < TROOP_CHANCE) {
            spawnTroop(player);
        }
        if (none(player, Pachycrocuta.class, 128.0D) && random.nextFloat() < HYENA_CHANCE) {
            spawnGroup(player, ModEntities.PACHYCROCUTA.get(), random.nextFloat() < 0.3F ? 2 : 1, 40, 70);
        }
        if (none(player, Sabertooth.class, 160.0D) && random.nextFloat() < SABERTOOTH_CHANCE) {
            spawnGroup(player, ModEntities.SABERTOOTH.get(), 1, 50, 90);
        }
    }

    private static boolean none(ServerPlayer player, Class<? extends Mob> type, double radius) {
        return player.level().getEntitiesOfClass(type, player.getBoundingBox().inflate(radius)).isEmpty();
    }

    /** A whole troop, in the homeland only - baboons are savanna animals. */
    public static int spawnTroop(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        BlockPos site = findSite(level, player.blockPosition(), 48, 100, random, true);
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
        for (int attempt = 0; attempt < 12; attempt++) {
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
