package dev.hominin.evolution.band;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.guide.Alerts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Moving camp means moving what the band keeps. Settle new ground far from your piles and your people go back for
 * them: each walks to the old camp, gathers a pile up, and carries it - the whole of it, as it lay - to the new one.
 * More piles than people and they go back again, as many trips as it takes. It is a long walk; they are gone a while.
 *
 * <p>What is still lying at the old camp is anybody's to find. A band living near it may help itself - and you will
 * hear who did.
 */
public final class Haul {
    /** At most this many of your band go back for the piles at once; the rest of the band stays with you. */
    private static final int MOST_HAULERS = 4;
    /** Walking speed on a long carry, in blocks a second. */
    private static final double PACE = 3.5D;

    private static final class Trip {
        final BlockPos pile;
        final long arriveAt;
        final long backAt;
        @Nullable
        CompoundTag bundle;
        boolean fetched;

        Trip(BlockPos pile, long arriveAt, long backAt) {
            this.pile = pile;
            this.arriveAt = arriveAt;
            this.backAt = backAt;
        }
    }

    private static final class Job {
        final BlockPos to;
        final Deque<BlockPos> waiting = new ArrayDeque<>();
        final Map<UUID, Trip> trips = new HashMap<>();
        int total;
        int moved;
        int lost;

        Job(BlockPos to) {
            this.to = to;
        }
    }

    private static final Map<UUID, Job> jobs = new HashMap<>();

    public static boolean away(BandMember member) {
        return tripOf(member) != null;
    }

    @Nullable
    private static Trip tripOf(BandMember member) {
        for (Job job : jobs.values()) {
            Trip trip = job.trips.get(member.getUUID());
            if (trip != null) {
                return trip;
            }
        }
        return null;
    }

    /** Where a hauler is walking: to the old pile, then back to you. */
    @Nullable
    public static BlockPos headingFor(BandMember member) {
        Trip trip = tripOf(member);
        if (trip == null) {
            return null;
        }
        return !trip.fetched ? trip.pile : member.leaderPlayer() != null ? member.leaderPlayer().blockPosition() : null;
    }

    /** New ground: every pile of yours left outside it is to be fetched. */
    public static void begin(ServerPlayer player, BlockPos to) {
        ServerLevel level = player.serverLevel();
        int radius = dev.hominin.evolution.hunt.Predation.territoryRadius(player);
        Job job = new Job(to.immutable());
        Job old = jobs.get(player.getUUID());
        if (old != null) {
            // Moved again mid-carry: whoever is on the way keeps going - to the new camp.
            job.trips.putAll(old.trips);
            job.moved = old.moved;
            job.lost = old.lost;
        }
        for (BlockPos pos : ToolPiles.piles(level, player.getUUID())) {
            if (Bands.horizontal(pos, to) > (double) radius * radius
                    && job.trips.values().stream().noneMatch(t -> t.pile.equals(pos))) {
                job.waiting.add(pos);
            }
        }
        if (job.waiting.isEmpty() && job.trips.isEmpty()) {
            jobs.remove(player.getUUID());
            return;
        }
        if (job.waiting.isEmpty()) {
            jobs.put(player.getUUID(), job);
            return;
        }
        job.total = job.waiting.size() + job.trips.size() + job.moved + job.lost;
        jobs.put(player.getUUID(), job);
        List<String> names = new ArrayList<>();
        for (BandMember member : Band.ownNear(player, 48.0D)) {
            if (names.size() >= Math.min(MOST_HAULERS, job.total)) {
                break;
            }
            if (member.isBaby() || member.isInjured() || member.isPregnant() || Parties.away(member)) {
                continue;
            }
            if (send(level, job, member)) {
                member.ensureName();
                names.add(member.getName().getString());
            }
        }
        if (names.isEmpty()) {
            player.sendSystemMessage(Component.literal("Your piles are still at the old camp - " + job.total + " of them - "
                    + "and nobody fit to go back for them. When somebody is, they will.").withStyle(ChatFormatting.GOLD));
            return;
        }
        player.sendSystemMessage(Component.literal(String.join(", ", names) + " go back to the old camp for what the band "
                + "keeps there: " + job.total + (job.total == 1 ? " pile" : " piles") + ". "
                + (job.total > names.size() ? "More than they can carry at once - they will make more trips. " : "")
                + "Until it is here, anyone could walk off with it.").withStyle(ChatFormatting.AQUA));
    }

    /** Off to fetch the next pile. */
    private static boolean send(ServerLevel level, Job job, BandMember member) {
        BlockPos pile = job.waiting.poll();
        if (pile == null) {
            return false;
        }
        long travel = travel(member.blockPosition(), pile);
        long back = travel(pile, job.to);
        long now = level.getGameTime();
        member.setTarget(null);
        job.trips.put(member.getUUID(), new Trip(pile, now + travel, now + travel + back));
        return true;
    }

    private static long travel(BlockPos a, BlockPos b) {
        double distance = Math.sqrt(Bands.horizontal(a, b));
        return Math.max(200L, Math.min(4800L, (long) (distance * 20.0D / PACE)));
    }

    /** Every second: piles picked up, piles set down, and the next trip. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 7) {
            return;
        }
        if (player.tickCount % 1200 == 7) {
            leftBehind(player);
        }
        Job job = jobs.get(player.getUUID());
        if (job == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Trip>> it = job.trips.entrySet().iterator();
        List<BandMember> free = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Trip> entry = it.next();
            Trip trip = entry.getValue();
            BandMember member = level.getEntity(entry.getKey()) instanceof BandMember m && m.isAlive() ? m : null;
            if (!trip.fetched && now >= trip.arriveAt) {
                trip.fetched = true;
                trip.bundle = pickUp(level, player.getUUID(), trip.pile);
            }
            if (!trip.fetched || now < trip.backAt) {
                continue;
            }
            it.remove();
            if (trip.bundle != null) {
                BlockPos at = setDown(level, player.getUUID(), job.to, trip.bundle);
                job.moved++;
                if (member != null) {
                    member.ensureName();
                    if (member.distanceToSqr(player) > 48.0D * 48.0D) {
                        BlockPos spot = Band.standingSpotNear(level, at != null ? at : player.blockPosition(), 2,
                                member.getRandom().nextFloat() * 6.2831855F);
                        member.getNavigation().stop();
                        member.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
                    }
                    player.displayClientMessage(Component.literal(member.getName().getString() + " brings a pile from "
                            + "the old camp (" + job.moved + " of " + job.total + ").").withStyle(ChatFormatting.AQUA), true);
                }
            } else {
                job.lost++;
                if (member != null) {
                    member.ensureName();
                    Lines.say(member, "haul_gone", "");
                }
            }
            if (member != null) {
                free.add(member);
            }
        }
        for (BandMember member : free) {
            send(level, job, member);
        }
        // Nobody on it, piles still waiting: whoever is free and near goes.
        if (job.trips.isEmpty() && !job.waiting.isEmpty() && player.tickCount % 200 == 7) {
            for (BandMember member : Band.ownNear(player, 32.0D)) {
                if (!member.isBaby() && !member.isInjured() && !member.isPregnant() && !Parties.away(member)) {
                    send(level, job, member);
                    break;
                }
            }
        }
        if (job.trips.isEmpty() && job.waiting.isEmpty()) {
            jobs.remove(player.getUUID());
            Alerts.urgent(player, Alerts.Kind.BAND, Component.literal(job.lost == 0
                    ? "Everything from the old camp is here now - " + job.moved + (job.moved == 1 ? " pile." : " piles.")
                    : job.moved + " of your piles are here from the old camp. " + job.lost + " were gone when they got "
                            + "there - somebody helped themselves.").withStyle(ChatFormatting.AQUA));
        }
    }

    /** At the old camp: the whole pile gathered up, as it lay. Null if it is not there any more. */
    @Nullable
    private static CompoundTag pickUp(ServerLevel level, UUID owner, BlockPos pos) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (!(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile) || pile.isEmpty()
                || pile.kind() == ToolPileBlockEntity.Kind.SACRED || !owner.equals(pile.owner())) {
            return null;
        }
        CompoundTag bundle = pile.bundle(level.registryAccess());
        pile.takeAll();
        level.removeBlock(pos, false);
        ToolPiles.forgetPile(level, pos, owner);
        return bundle;
    }

    /** At the new camp: set down beside the others, everything in it as it lay. */
    @Nullable
    private static BlockPos setDown(ServerLevel level, UUID owner, BlockPos camp, CompoundTag bundle) {
        level.getChunk(camp.getX() >> 4, camp.getZ() >> 4);
        for (int ring = 2; ring <= 8; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = camp.getX() + dx;
                    int z = camp.getZ() + dz;
                    BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (!ToolPiles.canPileAt(level, at)) {
                        continue;
                    }
                    level.setBlock(at, ModBlocks.TOOL_PILE.get().defaultBlockState(), 3);
                    if (level.getBlockEntity(at) instanceof ToolPileBlockEntity pile) {
                        pile.unbundle(bundle.copy(), level.registryAccess());
                        pile.setOwner(owner);
                    }
                    ToolPiles.adopt(level, owner, at);
                    level.playSound(null, at, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 0.8F, 1.0F);
                    return at;
                }
            }
        }
        return null;
    }

    /**
     * Piles of yours outside your ground, that nobody is on the way for: once a day a band living near may find one
     * and take it. You hear who - and think less of them for it.
     */
    private static void leftBehind(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int day = (int) (level.getDayTime() / 24000L);
        if (counters.getOrDefault("left_behind_day", -1) == day) {
            return;
        }
        counters.put("left_behind_day", day);
        BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
        int radius = dev.hominin.evolution.hunt.Predation.territoryRadius(player);
        Job job = jobs.get(player.getUUID());
        for (BlockPos pos : ToolPiles.piles(level, player.getUUID())) {
            if (Bands.horizontal(pos, camp) <= (double) (radius + 16) * (radius + 16)
                    || job != null && (job.waiting.contains(pos) || job.trips.values().stream().anyMatch(t -> t.pile.equals(pos)))
                    || player.getRandom().nextFloat() >= 0.3F) {
                continue;
            }
            Bands.Record taker = null;
            double nearest = 250.0D * 250.0D;
            for (Bands.Record band : Bands.all(level)) {
                double d = Bands.horizontal(band.home, pos);
                if (!band.nomadic() && d < nearest && ToolPiles.usesStone(band.species)
                        && Relations.standing(player, band) < Relations.ALLIED) {
                    nearest = d;
                    taker = band;
                }
            }
            if (taker == null) {
                continue;
            }
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (!(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile)
                    || pile.kind() != ToolPileBlockEntity.Kind.TOOLS || pile.isEmpty()) {
                continue;
            }
            List<ItemStack> taken = pile.takeAll();
            level.removeBlock(pos, false);
            ToolPiles.forgetPile(level, pos, player.getUUID());
            BlockPos store = ToolPiles.store(level, taker.id);
            if (store != null) {
                for (ItemStack stack : taken) {
                    ToolPiles.putBack(level, taker.id, store, stack);
                }
            }
            Relations.meet(player, taker, "");
            player.sendSystemMessage(Component.literal("Word comes back: " + taker.name + " found the pile you left at "
                    + "your old camp, and took it - " + ToolPiles.describe(taken) + ".").withStyle(ChatFormatting.GOLD));
            Relations.change(player, taker, -3, "they took the tools you left behind");
            return;
        }
    }

    /** Leaving the world: whatever is being carried is set down at the new camp; the rest waits where it is. */
    public static void logout(ServerPlayer player) {
        Job job = jobs.remove(player.getUUID());
        if (job == null) {
            return;
        }
        for (Trip trip : job.trips.values()) {
            if (trip.fetched && trip.bundle != null) {
                setDown(player.serverLevel(), player.getUUID(), job.to, trip.bundle);
            }
        }
    }

    private Haul() {
    }
}
