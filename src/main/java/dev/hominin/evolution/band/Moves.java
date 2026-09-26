package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.world.Havens;
import dev.hominin.evolution.world.Pois;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Bands move. Now and then - more often when they are hungry - a band gives up its ground for better: somewhere with
 * a spring, a seam of good stone, a salt lick, the things that make ground worth holding. An empty haven draws them
 * most of all. What they cannot carry they leave behind: their old camp's pile is anybody's.
 */
public final class Moves {
    private static final String LEAVING = "HomininLeaving";

    /** How far a band will go looking for better ground. */
    private static final int LOOK = 420;

    /** Once a day, for every band: does it move? */
    public static void daily(ServerLevel level) {
        List<BlockPos> camps = new ArrayList<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (dev.hominin.evolution.hunt.Predation.settled(player)) {
                camps.add(dev.hominin.evolution.hunt.Predation.campOf(player));
            }
        }
        RandomSource random = level.random;
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || band.haven != null || band.size <= 0) {
                continue;
            }
            // Threat built up on ground they cannot hold: they go, and soon.
            boolean fleeing = band.threat() >= 8 && band.presence < Presence.STRONG;
            float chance = 0.05F + (band.desperation >= 4 ? 0.06F : 0.0F) + (band.cohesion < 15 ? 0.02F : 0.0F)
                    + (fleeing ? 0.45F : 0.0F);
            if (random.nextFloat() >= chance) {
                continue;
            }
            List<Havens.Site> empty = Havens.emptyNear(level, band.home, 800);
            if (!empty.isEmpty() && random.nextFloat() < 0.5F) {
                Havens.Site site = empty.get(random.nextInt(empty.size()));
                if (clearOfPlayers(site.centre(), band.radius(), camps)) {
                    relocate(level, band, site.centre(), "into the haven");
                    Havens.occupy(level, site, band);
                    continue;
                }
            }
            List<BlockPos> oases = dev.hominin.evolution.world.Oases.emptyNear(level, band.home, 600);
            if (!oases.isEmpty() && random.nextFloat() < 0.4F) {
                BlockPos oasis = oases.get(random.nextInt(oases.size()));
                BlockPos camp = oasis.offset(19, 0, 0);
                if (clearOfPlayers(camp, band.radius(), camps)) {
                    relocate(level, band, camp, "to the oasis");
                    continue;
                }
            }
            int here = worth(level, band.home, band.radius());
            BlockPos best = null;
            // Fleeing, anywhere nearly as good will do.
            int bestWorth = fleeing ? here - 3 : here + 2;
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                int distance = band.radius() * 2 + 40 + random.nextInt(LOOK - band.radius() * 2 - 40);
                BlockPos at = band.home.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
                var biome = level.getBiome(at);
                if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)
                        || biome.is(net.minecraft.tags.BiomeTags.IS_MOUNTAIN)) {
                    continue;
                }
                if (!Bands.groundIsFree(level, at, band.radius(), camps,
                        dev.hominin.evolution.band.Bands.ERECTUS_RADIUS)) {
                    continue;
                }
                int value = worth(level, at, band.radius());
                if (value > bestWorth) {
                    bestWorth = value;
                    best = at;
                }
            }
            if (best != null) {
                relocate(level, band, best, fleeing ? "- too much was hunting round their camp" : "to richer ground");
            }
        }
    }

    private static boolean clearOfPlayers(BlockPos site, int radius, List<BlockPos> camps) {
        for (BlockPos camp : camps) {
            double reach = radius + Bands.ERECTUS_RADIUS + 8.0D;
            if (Bands.horizontal(site, camp) < reach * reach) {
                return false;
            }
        }
        return true;
    }

    /** What a band can tell of a stretch of ground from what it knows of it: the places in it. */
    private static int worth(ServerLevel level, BlockPos centre, int radius) {
        int total = 0;
        for (Pois.Poi poi : Pois.inGround(level, centre, radius)) {
            total += poi.kind().pressure;
        }
        return total;
    }

    /**
     * The band moves its camp. Its old pile is left where it was - anybody's now - and it makes a new one where it
     * settles. Those of them out of anyone's sight are simply gone ahead; the rest walk.
     */
    public static void relocate(ServerLevel level, Bands.Record band, BlockPos to, String why) {
        BlockPos from = band.home;
        if (band.haven != null) {
            Havens.vacate(level, band);
        }
        ToolPiles.leftBehind(level, band.id);
        // The old camp's fire and huts stay where they were; the new camp gets its own.
        band.fire = null;
        band.furnished = false;
        // New ground: nothing has learned it yet.
        band.threatBuild = 0;
        Claims.moveBand(level, band, to);
        for (BandMember member : level.getEntities(dev.hominin.evolution.ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && band.id.equals(m.getBandId()))) {
            if (level.getNearestPlayer(member, 64.0D) == null) {
                // Out of sight: they are there already when anyone looks.
                member.discard();
            } else {
                // In sight: they walk off, and are gone once nobody is watching.
                member.getPersistentData().putBoolean(LEAVING, true);
                walkOff(member, to);
            }
        }
        int distance = (int) Math.sqrt(Bands.horizontal(from, to));
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!band.knownTo(player.getUUID()) || Bands.horizontal(player.blockPosition(), to) > 600.0D * 600.0D
                    && Bands.horizontal(player.blockPosition(), from) > 600.0D * 600.0D) {
                continue;
            }
            player.sendSystemMessage(Component.literal("Word comes that " + band.name + " have moved their camp " + why
                    + " - " + distance + " blocks from where it was; now "
                    + (int) Math.sqrt(Bands.horizontal(player.blockPosition(), to)) + " blocks "
                    + dev.hominin.evolution.mind.MentalMap.bearing(player, to) + ". Whatever they could not carry is "
                    + "still at the old camp.").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void walkOff(BandMember member, BlockPos to) {
        double dx = to.getX() - member.getX();
        double dz = to.getZ() - member.getZ();
        double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
        double step = Math.min(24.0D, length);
        member.getNavigation().moveTo(member.getX() + dx / length * step, member.getY(), member.getZ() + dz / length * step,
                1.0D);
    }

    /** Every ten seconds round a player: those of a band that moved keep walking, and are gone when out of sight. */
    public static void tickLeaving(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(96.0D),
                m -> m.isAlive() && m.isWild() && m.getPersistentData().getBoolean(LEAVING))) {
            Bands.Record band = member.getBandId() == null ? null : Bands.get(level, member.getBandId());
            if (band == null || level.getNearestPlayer(member, 40.0D) == null
                    || Bands.horizontal(member.blockPosition(), band.home) < 24.0D * 24.0D) {
                if (band != null && Bands.horizontal(member.blockPosition(), band.home) < 24.0D * 24.0D) {
                    // Made it: home again.
                    member.getPersistentData().remove(LEAVING);
                } else {
                    member.discard();
                }
                continue;
            }
            walkOff(member, band.home);
        }
    }

    private Moves() {
    }
}
