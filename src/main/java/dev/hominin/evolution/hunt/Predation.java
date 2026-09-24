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
import dev.hominin.evolution.entity.Bonobo;
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
        tickRange(player, level);
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
        // A band that has lived here for days is a routine everything knows.
        float gain = overstayed(player) ? 6.0F : 3.0F;
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
        // A big band is harder to creep up on, and knows it; a band the country knows to leave alone, more so.
        gain -= Math.min(3.0F, Band.ownNear(player, 16.0D).size() * 0.5F);
        gain -= Math.max(0, dev.hominin.evolution.band.Presence.get(player) - 20) * 0.08F;
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
            return roll < 0.8F ? ModEntities.HOMOTHERIUM.get() : ModEntities.PACHYCROCUTA.get();
        }
        // The giant hyena is a rare, dreadful visitor now, not the usual one.
        if (roll < 0.25F) {
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

    // ------------------------------------------------------------ the home range

    /**
     * The long view of the same argument. Camp pressure is a night or two in one spot; the home
     * range is days on the same country. Stay within 200 blocks of where you have been living
     * for four and a half days and everything that hunts there has learned you: it comes more often,
     * it takes food out of the band's hands, and now and then it takes one of you - and the longer you
     * stay, the more of them come. A band with a strong presence ({@link dev.hominin.evolution.band.Presence})
     * turns most of that away. But nothing stops the ground itself wearing out: the foraging dries up
     * after three days and the big herds stop coming, so holding ground is possible, and never free.
     */
    private static final String RANGE_X = "range_x";
    private static final String RANGE_Z = "range_z";
    private static final String RANGE_SINCE = "range_since_minute";
    private static final String RANGE_TOLD = "range_told";
    private static final String RANGE_NEXT = "range_next_minute";
    /** 1 while the band is packed up and on the move, with no ground of its own. */
    private static final String RANGE_PACKED = "range_packed";
    private static final String RANGE_FAR_TOLD = "range_far_told";
    /** Packed up, the camp goes where you go: this far and it is new country. */
    private static final int RANGE_RADIUS = 200;
    /** Settled: this far from your ground and you are told; this far and the band settles wherever you are. */
    public static final int FAR_WARN = 150;
    public static final int FAR_MOVE = 250;
    private static final long WARN_TICKS = 96000L;
    private static final long OVERSTAY_TICKS = 108000L;
    /** Minutes between the country's reminders once you have overstayed. */
    private static final int INCIDENT_MINUTES = 3;

    private static java.util.Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    /** Days lived off the country you are in now. */
    public static float daysOnGround(ServerPlayer player) {
        Integer since = counters(player).get(RANGE_SINCE);
        if (since == null) {
            return 0.0F;
        }
        return (player.level().getGameTime() / 1200L - since) * 1200L / 24000.0F;
    }

    /**
     * How much the ground still gives: nothing lost for three days, then less each day after - roots
     * dug, bushes stripped, grubs taken - down to four tenths. Moving on is the only cure.
     */
    public static float groundFactor(ServerPlayer player) {
        float days = daysOnGround(player);
        return days <= 3.0F ? 1.0F : Math.max(0.4F, 1.0F - (days - 3.0F) * 0.15F);
    }

    /** The heart of the ground your band is living on: your camp, and the middle of your territory. */
    public static BlockPos campOf(ServerPlayer player) {
        var counters = counters(player);
        if (!counters.containsKey(RANGE_X)) {
            return player.blockPosition();
        }
        return new BlockPos(counters.get(RANGE_X), player.getBlockY(), counters.get(RANGE_Z));
    }

    public static boolean overstayed(ServerPlayer player) {
        return daysOnGround(player) * 24000.0F >= OVERSTAY_TICKS;
    }

    /** Whether the band has ground of its own right now, rather than being packed up and on the move. */
    public static boolean settled(ServerPlayer player) {
        return counters(player).getOrDefault(RANGE_PACKED, 0) == 0;
    }

    /** How far your ground runs from its heart. */
    public static int territoryRadius(ServerPlayer player) {
        return dev.hominin.evolution.band.Bands.radiusFor(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
    }

    /** Whether this is your band's ground. */
    public static boolean onOwnGround(ServerPlayer player, BlockPos pos) {
        if (!settled(player) || !counters(player).containsKey(RANGE_X)) {
            return false;
        }
        BlockPos camp = campOf(player);
        double dx = pos.getX() - camp.getX();
        double dz = pos.getZ() - camp.getZ();
        int radius = territoryRadius(player);
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    /**
     * The band makes this its ground: a new band waking here, a territory set from the map, or a band that
     * went too far from the old one. Nothing here knows it yet.
     */
    public static void settle(ServerPlayer player, BlockPos at) {
        var counters = counters(player);
        counters.put(RANGE_X, at.getX());
        counters.put(RANGE_Z, at.getZ());
        counters.put(RANGE_SINCE, (int) (player.level().getGameTime() / 1200L));
        counters.put(RANGE_TOLD, 0);
        counters.remove(RANGE_NEXT);
        counters.remove(RANGE_PACKED);
        counters.remove(RANGE_FAR_TOLD);
        dev.hominin.evolution.band.Presence.newGround(player);
        // Settling on somebody else's ground is noticed.
        dev.hominin.evolution.band.Bands.Record theirs = dev.hominin.evolution.band.Bands.groundAt(player.serverLevel(), at);
        if (theirs != null && !dev.hominin.evolution.band.Claims.takeGround(player, theirs)
                && dev.hominin.evolution.band.Relations.standing(player, theirs) < dev.hominin.evolution.band.Relations.ALLIED) {
            dev.hominin.evolution.band.Relations.change(player, theirs, -5, "you made camp on their ground");
        }
    }

    /** The band packs up: no ground of its own until it chooses some. The camp goes where you go. */
    public static void packUp(ServerPlayer player) {
        if (!settled(player)) {
            player.displayClientMessage(Component.literal("You are already on the move."), true);
            return;
        }
        var counters = counters(player);
        counters.put(RANGE_PACKED, 1);
        counters.put(RANGE_X, player.getBlockX());
        counters.put(RANGE_Z, player.getBlockZ());
        counters.remove(RANGE_FAR_TOLD);
        player.sendSystemMessage(Component.literal("The band packs up. You are on the move now, with no ground of your "
                + "own - and no presence building anywhere. When you find somewhere, open the map (J) and set your "
                + "territory there.").withStyle(ChatFormatting.GOLD));
    }

    /** From the map: here is our ground now. */
    public static void settleHere(ServerPlayer player) {
        settle(player, player.blockPosition());
        var land = dev.hominin.evolution.world.Land.ofPlayer(player);
        player.sendSystemMessage(Component.literal("Your band makes this its ground: " + territoryRadius(player)
                + " blocks round where you stand. Keep a fire and build here and the country learns to leave you alone.")
                .withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("Pressure " + land.total() + "/10 - "
                + dev.hominin.evolution.world.Land.label(land.total()) + ": " + String.join(", ", land.describe(player)) + ".")
                .withStyle(ChatFormatting.GRAY));
        if (land.total() >= 5) {
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.LAND_PRESSURE);
        }
        if (land.hidesSomethingFrom(player)) {
            dev.hominin.evolution.mind.Insights.hint(player, "settle_" + player.blockPosition().asLong());
        }
    }

    private static void tickRange(ServerPlayer player, ServerLevel level) {
        var counters = counters(player);
        int minute = (int) (level.getGameTime() / 1200L);
        BlockPos here = player.blockPosition();
        if (!counters.containsKey(RANGE_SINCE)) {
            settle(player, here);
            return;
        }
        int cx = counters.get(RANGE_X);
        int cz = counters.get(RANGE_Z);
        double dx = here.getX() - cx;
        double dz = here.getZ() - cz;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (settled(player)) {
            // Your ground stays where you made it. Wander far and you are told; too far and the band settles again.
            if (distance > FAR_MOVE) {
                settle(player, here);
                player.sendSystemMessage(Component.literal("You have come too far from your ground to go back to it. "
                        + "The band settles here instead - new ground, and nothing here knows you yet.")
                        .withStyle(ChatFormatting.GOLD));
                return;
            }
            if (distance > FAR_WARN && counters.getOrDefault(RANGE_FAR_TOLD, 0) == 0) {
                counters.put(RANGE_FAR_TOLD, 1);
                player.sendSystemMessage(Component.literal("You are " + (int) distance + " blocks from your ground - too "
                        + "far. Turn back, or go on past " + FAR_MOVE + " and the band will settle wherever you stop.")
                        .withStyle(ChatFormatting.YELLOW));
                dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.TERRITORY);
            } else if (distance < FAR_WARN - 30) {
                counters.remove(RANGE_FAR_TOLD);
            }
        } else {
            if (distance > RANGE_RADIUS) {
                if (counters.getOrDefault(RANGE_TOLD, 0) > 0) {
                    player.sendSystemMessage(Component.literal(
                            "New country. Nothing here knows your band yet.").withStyle(ChatFormatting.GREEN));
                }
                counters.put(RANGE_X, here.getX());
                counters.put(RANGE_Z, here.getZ());
                counters.put(RANGE_SINCE, minute);
                counters.put(RANGE_TOLD, 0);
                counters.remove(RANGE_NEXT);
                dev.hominin.evolution.band.Presence.newGround(player);
                return;
            }
            // On the move, the camp's heart drifts to wherever you actually spend your time.
            counters.put(RANGE_X, cx + (int) Math.round(dx / 40.0D));
            counters.put(RANGE_Z, cz + (int) Math.round(dz / 40.0D));
        }
        long stayed = (long) (minute - counters.get(RANGE_SINCE)) * 1200L;
        int told = counters.getOrDefault(RANGE_TOLD, 0);
        if (stayed >= WARN_TICKS && told < 1) {
            counters.put(RANGE_TOLD, 1);
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.HOME_RANGE);
            player.sendSystemMessage(Component.literal("Four days on this ground. The things that hunt here "
                    + "are starting to know your band's ways. Half a day more and they will start testing you - make "
                    + "your band felt here (presence " + dev.hominin.evolution.band.Presence.get(player) + ") - keep a fire, "
                    + "build, kill what hunts here - or pack up and move on.")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (stayed < OVERSTAY_TICKS) {
            return;
        }
        if (told < 2) {
            counters.put(RANGE_TOLD, 2);
            counters.put(RANGE_NEXT, minute + 1);
            player.sendSystemMessage(Component.literal("Four and a half days on the same ground. Everything that hunts "
                    + "here knows your band now - where you sleep, where you eat, who lags behind. Hold it if you can: "
                    + "the ground is wearing out under you all the same.").withStyle(ChatFormatting.RED));
        }
        if (minute < counters.getOrDefault(RANGE_NEXT, 0) || Bonobo.sanctuary(level, here)) {
            return;
        }
        // The longer you stay, the more of them there are, and the sooner they come again.
        float over = daysOnGround(player) - OVERSTAY_TICKS / 24000.0F;
        int gap = Math.max(1, INCIDENT_MINUTES + level.random.nextInt(3) - (int) over);
        counters.put(RANGE_NEXT, minute + gap);
        int presence = dev.hominin.evolution.band.Presence.get(player);
        float turnedAway = presence >= 45 ? 0.7F : presence >= dev.hominin.evolution.band.Presence.STRONG ? 0.5F : 0.0F;
        if (level.random.nextFloat() < turnedAway) {
            if (level.random.nextInt(3) == 0) {
                player.displayClientMessage(Component.literal("Something came to test the camp, saw whose it was, and "
                        + "thought better of it.").withStyle(ChatFormatting.DARK_GREEN), true);
            }
            return;
        }
        incident(player, level);
        if (over >= 1.5F && level.random.nextBoolean()) {
            sendVisitor(player, level);
        }
    }

    /** The country reminds you: a visitor, a theft, or an ambush on whoever strays. */
    private static void incident(ServerPlayer player, ServerLevel level) {
        float roll = level.random.nextFloat();
        java.util.List<BandMember> band = Band.ownNear(player, 64.0D);
        if (roll < 0.35F && !band.isEmpty()) {
            java.util.List<BandMember> fed = band.stream().filter(BandMember::hasFood).toList();
            if (!fed.isEmpty()) {
                BandMember robbed = fed.get(level.random.nextInt(fed.size()));
                ItemStack taken = robbed.takeFood();
                if (level.random.nextBoolean()) {
                    robbed.takeFood();
                }
                robbed.ensureName();
                player.sendSystemMessage(Component.literal("While nobody watched, something got into "
                        + robbed.getName().getString() + "'s food and made off with " + taken.getHoverName().getString()
                        + ". It knows this camp.").withStyle(ChatFormatting.RED));
                dev.hominin.evolution.band.Presence.add(player, -2, "food stolen from the camp");
                return;
            }
        }
        if (roll > 0.8F && !band.isEmpty()) {
            // Whoever is furthest out: the one a hunter that knows you waits for.
            BandMember straggler = band.get(0);
            for (BandMember member : band) {
                if (member.distanceToSqr(player) > straggler.distanceToSqr(player)) {
                    straggler = member;
                }
            }
            BlockPos site = siteNear(level, straggler.blockPosition(), 8, 14);
            EntityType<? extends Mob> type = chooseVisitor(player, level);
            Mob hunter = site == null ? null : type.create(level);
            if (hunter != null) {
                hunter.moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(site), MobSpawnType.EVENT, null);
                level.addFreshEntity(hunter);
                hunter.setTarget(straggler);
                straggler.ensureName();
                player.sendSystemMessage(Component.literal("Something has been waiting for one of you to stray - and "
                        + straggler.getName().getString() + " has.").withStyle(ChatFormatting.DARK_RED));
                return;
            }
        }
        sendVisitor(player, level);
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
