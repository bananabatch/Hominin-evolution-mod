package dev.hominin.evolution.hunt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What the predators of a place learn about you.
 *
 * <p>A band that sleeps in the same spot every night, with bones and meat about it, is a
 * larder with a routine - and everything out there has time to learn routines. Pressure
 * builds while you stay put and falls away when you move, which is the whole argument for
 * a nomadic life: not that walking is safe, but that staying still is not.
 *
 * <p>It also holds the other half of the story. An australopithecine is prey and nothing
 * else; habilis can make a cat think about it; by erectus the primate is the dangerous
 * thing on the plain, taking kills off animals that once took them off us.
 */
public final class Predation {
    /**
     * Ordinary game. A big cat does not live on hominins - it lives on whatever grazes
     * near the water, and hominins are an occasional and badly-behaved substitute.
     * Leaving them out made every predator a thing that only ever came for you.
     */
    public static boolean isGame(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.animal.Animal
                && !(entity instanceof dev.hominin.evolution.band.BandMember)
                && !entity.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS);
    }

    /** How often camp pressure is weighed. */
    private static final int CHECK_TICKS = 200;
    /** How far you can drift and still count as camped in the same place. */
    private static final double CAMP_RADIUS = 28.0D;
    /** Moving this far resets the camp, and most of what was circling it. */
    private static final double MOVED_ON = 64.0D;

    private static final float PRESSURE_WARN = 40.0F;
    private static final float PRESSURE_VISIT = 75.0F;
    private static final float PRESSURE_MAX = 120.0F;

    private record Camp(BlockPos anchor, float pressure, boolean warned) {
    }

    private static final Map<UUID, Camp> camps = new HashMap<>();

    // ------------------------------------------------------------ what a hominin looks like

    /** 1 for the earliest hominins, 3 for erectus and later: how formidable your kind looks. */
    public static int standing(Player player) {
        String stage = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage().getPath();
        return switch (stage) {
            case "ardipithecus", "australopithecus" -> 1;
            case "homo_habilis" -> 2;
            default -> 3;
        };
    }

    /** Whether the thing in your hands would give an animal pause. */
    public static boolean armed(Player player) {
        ItemStack held = player.getMainHandItem();
        return held.is(ModTags.Items.STONE_TOOLS) || BandMember.isWeapon(held);
    }

    /**
     * Whether a predator still fancies its chances. An erectus with a spear and a band at
     * its back is not prey, and the animals around it have worked that out.
     */
    public static boolean looksWorthAttacking(Player player) {
        int nerve = standing(player);
        if (nerve >= 3 && armed(player)) {
            return false;
        }
        if (nerve >= 2 && armed(player) && Band.companionsNear(player, 12.0D).size() >= 3) {
            return false;
        }
        return true;
    }

    /** How much better a threat display works for a later hominin: they read us, and we have changed. */
    public static float displayBonus(Player player) {
        return switch (standing(player)) {
            case 1 -> 0.0F;
            case 2 -> 0.1F;
            default -> 0.3F;
        };
    }

    // ------------------------------------------------------------ camp pressure

    /** Once every ten seconds, per player. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_TICKS != 120 || player.isSpectator() || player.isCreative()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Camp camp = camps.get(player.getUUID());
        if (camp == null) {
            camps.put(player.getUUID(), new Camp(player.blockPosition(), 0.0F, false));
            return;
        }
        double distance = Math.sqrt(camp.anchor().distSqr(player.blockPosition()));
        if (distance > MOVED_ON) {
            // Camp moved. Whatever had learned this place has to start again somewhere else.
            float left = camp.pressure() * 0.25F;
            camps.put(player.getUUID(), new Camp(player.blockPosition(), left, false));
            if (camp.pressure() >= PRESSURE_WARN) {
                player.sendSystemMessage(Component.literal(
                        "You move on. Whatever had been circling the old camp is welcome to it.")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        if (distance > CAMP_RADIUS) {
            // Ranging out from camp, but still living off the same ground.
            camps.put(player.getUUID(), new Camp(camp.anchor(), Math.max(0.0F, camp.pressure() - 1.0F), camp.warned()));
            return;
        }
        if (dev.hominin.evolution.entity.Bonobo.sanctuary(level, player.blockPosition())) {
            // Camped among bonobos: nothing out there is learning this place.
            camps.put(player.getUUID(), new Camp(camp.anchor(), Math.max(0.0F, camp.pressure() - 3.0F), camp.warned()));
            return;
        }
        float gain = 3.0F;
        if (dev.hominin.evolution.survival.Kuru.has(player)) {
            // Stumbling, shaking, easy: everything out there can tell.
            gain += 8.0F;
        }
        if (level.isNight()) {
            gain += 2.0F;
        }
        gain += carcassesNear(level, player.blockPosition()) * 2.5F;
        if (player.getHealth() < player.getMaxHealth() * 0.5F) {
            gain += 2.0F;
        }
        // A big band is harder to creep up on, and knows it.
        gain -= Math.min(3.0F, Band.ownNear(player, 16.0D).size() * 0.5F);
        float pressure = Mth.clamp(camp.pressure() + Math.max(0.5F, gain), 0.0F, PRESSURE_MAX);
        boolean warned = camp.warned();
        if (!warned && pressure >= PRESSURE_WARN) {
            warned = true;
            player.sendSystemMessage(Component.literal(
                    "Something has been about the camp. There are tracks you did not make.")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (pressure >= PRESSURE_VISIT && sendVisitor(player, level)) {
            pressure -= 45.0F;
        }
        camps.put(player.getUUID(), new Camp(camp.anchor(), pressure, warned));
    }

    private static int carcassesNear(ServerLevel level, BlockPos around) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-12, -4, -12), around.offset(12, 4, 12))) {
            if (level.getBlockState(pos).is(ModBlocks.CARCASS.get())) {
                count++;
            }
        }
        return Math.min(4, count);
    }

    /** Something comes to look the camp over. What comes depends on the hour and your era. */
    private static boolean sendVisitor(ServerPlayer player, ServerLevel level) {
        EntityType<? extends Mob> type = chooseVisitor(player, level);
        BlockPos site = siteNear(level, player.blockPosition(), 18, 30);
        if (site == null || dev.hominin.evolution.entity.Bonobo.sanctuary(level, site)) {
            return false;
        }
        Mob visitor = type.create(level);
        if (visitor == null) {
            return false;
        }
        visitor.moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        visitor.finalizeSpawn(level, level.getCurrentDifficultyAt(site), MobSpawnType.EVENT, null);
        level.addFreshEntity(visitor);
        dev.hominin.evolution.band.Paranthropus.warn(player, site);
        // A pair, for the animals that hunt in pairs.
        if (type == ModEntities.HOMOTHERIUM.get() && level.random.nextBoolean()) {
            Mob mate = type.create(level);
            if (mate != null) {
                mate.moveTo(site.getX() + 2.5D, site.getY(), site.getZ() + 1.5D, 0.0F, 0.0F);
                mate.finalizeSpawn(level, level.getCurrentDifficultyAt(site), MobSpawnType.EVENT, null);
                level.addFreshEntity(mate);
            }
        }
        player.sendSystemMessage(Component.literal(level.isNight()
                ? "Something is moving out there in the dark, and it knows where you sleep."
                : "Something has followed the smell of this place in.").withStyle(ChatFormatting.RED));
        return true;
    }

    private static EntityType<? extends Mob> chooseVisitor(ServerPlayer player, ServerLevel level) {
        float roll = level.random.nextFloat();
        if (level.isDay()) {
            // Daylight in the open belongs to the scimitar cat.
            return roll < 0.6F ? ModEntities.HOMOTHERIUM.get() : ModEntities.PACHYCROCUTA.get();
        }
        if (roll < 0.45F) {
            return ModEntities.PACHYCROCUTA.get();
        }
        return standing(player) >= 2 ? ModEntities.SABERTOOTH.get() : ModEntities.HOMOTHERIUM.get();
    }

    private static BlockPos siteNear(ServerLevel level, BlockPos around, int min, int max) {
        for (int attempt = 0; attempt < 10; attempt++) {
            float angle = level.random.nextFloat() * Mth.TWO_PI;
            int distance = min + level.random.nextInt(max - min + 1);
            int x = around.getX() + Math.round(Mth.cos(angle) * distance);
            int z = around.getZ() + Math.round(Mth.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (level.getFluidState(pos.below()).isEmpty() && level.getBlockState(pos.below()).isSolid()) {
                return pos;
            }
        }
        return null;
    }

    /** How settled this camp has become, for anything that wants to read it. */
    public static float pressureOf(Player player) {
        Camp camp = camps.get(player.getUUID());
        return camp == null ? 0.0F : camp.pressure();
    }

    public static void forget(UUID player) {
        camps.remove(player);
    }

    private Predation() {
    }
}
