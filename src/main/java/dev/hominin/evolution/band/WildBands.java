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
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        BlockPos site = findSite(level, player.blockPosition(), minDistance, maxDistance, random);
        if (site == null) {
            return 0;
        }
        ResourceLocation stage = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
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
            level.playSound(null, alpha.blockPosition(), ModSounds.BAND_PANT_HOOT.get(), SoundSource.NEUTRAL, 3.0F, 0.9F);
            player.displayClientMessage(Component.literal("Somewhere nearby, another band is calling."), true);
        }
        return size;
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

    @Nullable
    private static BlockPos findSite(ServerLevel level, BlockPos around, int minDistance, int maxDistance,
            RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            int distance = minDistance + random.nextInt(Math.max(1, maxDistance - minDistance + 1));
            int x = around.getX() + Math.round(Mth.cos(angle) * distance);
            int z = around.getZ() + Math.round(Mth.sin(angle) * distance);
            // Only in chunks already loaded: a spawn should never force the world to generate.
            if (!level.hasChunk(x >> 4, z >> 4)) {
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

    private WildBands() {
    }
}
