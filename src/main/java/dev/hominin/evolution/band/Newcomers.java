package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Somebody else comes into the world. When a player joins a world where others already walk, they choose before they
 * wake: walk with one of the bands already out there, or make their own - a long way off, two hundred and fifty
 * blocks from anyone, where their band will have its own ground.
 *
 * <p>Walking with another player's band, you are its <b>co-leader</b>: its members count you as their own, stand with
 * you, pull their blows with you and you with them, and you live at its camp. You are the same kind as them. You lead
 * it together - you hear what the band needs and what other bands demand as the leader does, either of you can
 * answer, meet a need or give the band an order - and the band talks about the two of you.
 *
 * <p>Any time, from the H menu, a player can ask to lead another player's band with them (bringing their own people
 * into it), or a co-leader can split off and start a band of their own where they stand.
 */
public final class Newcomers extends SavedData {
    public static final int ACTION = 66;
    /** The H menu's list of other players' bands. */
    public static final int ACTION_PLAYERS = 69;
    /** A band's leader answering someone who asked to lead it with them. */
    public static final int ACTION_REQUEST = 70;
    private static final int OWN_BAND = 99;
    private static final int SPLIT_OFF = 98;
    private static final int ACCEPT = 0;
    /** How far from everyone else a new band starts. */
    private static final int OWN_BAND_DISTANCE = 250;
    private static final String NAME = "hominin_newcomers";

    /** Players walking with somebody else's band: player, and whose band. */
    private final Map<UUID, UUID> hosts = new HashMap<>();

    /** Asked, and not yet answered: the players they were offered, and when to ask (again). */
    private record Asking(List<UUID> offered, long askAt) {
    }

    private static final Map<UUID, Asking> asking = new HashMap<>();
    /** Who asked to lead whose band with them: leader, and who asked. */
    private static final Map<UUID, UUID> requests = new HashMap<>();

    public Newcomers() {
    }

    private static Newcomers load(CompoundTag tag, HolderLookup.Provider registries) {
        Newcomers data = new Newcomers();
        CompoundTag list = tag.getCompound("Hosts");
        for (String key : list.getAllKeys()) {
            try {
                data.hosts.put(UUID.fromString(key), list.getUUID(key));
            } catch (IllegalArgumentException ignored) {
                // A bad key is dropped.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag list = new CompoundTag();
        hosts.forEach((player, host) -> list.putUUID(player.toString(), host));
        tag.put("Hosts", list);
        return tag;
    }

    private static Newcomers of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Newcomers::new, Newcomers::load), NAME);
    }

    /** Whose band this player walks with - or null, for a player with a band of their own. Server side only. */
    @Nullable
    public static UUID hostOf(Player player) {
        return player instanceof ServerPlayer server ? of(server.server).hosts.get(player.getUUID()) : null;
    }

    /** Whoever leads this player's band: the band's leader, if they are a co-leader and it is about; else themselves. */
    public static ServerPlayer leadOf(ServerPlayer player) {
        UUID host = hostOf(player);
        return host != null && player.server.getPlayerList().getPlayer(host) instanceof ServerPlayer leader
                && leader.level() == player.level() ? leader : player;
    }

    /** Whether two players lead the same band: one is the other's co-leader, or both lead with the same leader. */
    public static boolean sameBand(ServerPlayer player, Player other) {
        if (player == other) {
            return true;
        }
        UUID mine = hostOf(player);
        UUID theirs = hostOf(other);
        UUID myLeader = mine != null ? mine : player.getUUID();
        UUID theirLeader = theirs != null ? theirs : other.getUUID();
        return myLeader.equals(theirLeader);
    }

    /** The co-leaders of this player's band who are about. */
    public static List<ServerPlayer> coLeaders(ServerPlayer leader) {
        List<ServerPlayer> co = new ArrayList<>();
        Newcomers data = of(leader.server);
        if (data.hosts.isEmpty()) {
            return co;
        }
        for (ServerPlayer other : leader.server.getPlayerList().getPlayers()) {
            if (other != leader && leader.getUUID().equals(data.hosts.get(other.getUUID()))) {
                co.add(other);
            }
        }
        return co;
    }

    /** Now one of this band's leaders - with whatever band they had before gone into it. */
    public static void join(ServerPlayer player, ServerPlayer host) {
        Newcomers data = of(player.server);
        data.hosts.put(player.getUUID(), host.getUUID());
        // Anyone who led with them leads with the new band now.
        data.hosts.replaceAll((co, was) -> was.equals(player.getUUID()) ? host.getUUID() : was);
        data.setDirty();
        Band.markFormed(player);
        Band.forgetBand(player);
        Chatter.news(host, "news_coleader", player.getGameProfile().getName());
    }

    /** No longer a co-leader: a band of their own, from here. */
    public static void split(ServerPlayer player) {
        Newcomers data = of(player.server);
        if (data.hosts.remove(player.getUUID()) != null) {
            data.setDirty();
        }
    }

    // ------------------------------------------------------------ the first time in

    /**
     * A player's first time in. With nobody else about, they simply wake among their band. With others already out
     * there, they choose first - and are asked a moment after they arrive, once the world has loaded round them.
     */
    public static boolean ask(ServerPlayer player) {
        if (others(player).isEmpty()) {
            return false;
        }
        asking.put(player.getUUID(), new Asking(List.of(), player.level().getGameTime() + 60L));
        return true;
    }

    private static List<ServerPlayer> others(ServerPlayer player) {
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player && !other.isSpectator() && other.level() == player.level()) {
                others.add(other);
            }
        }
        return others;
    }

    /** Bands a newcomer could walk with: every other player leading one. */
    private static List<ServerPlayer> hosts(ServerPlayer player) {
        List<ServerPlayer> hosts = new ArrayList<>();
        for (ServerPlayer other : others(player)) {
            if (hostOf(other) == null && !Band.all(other).isEmpty() && hosts.size() < 4) {
                hosts.add(other);
            }
        }
        return hosts;
    }

    /** Until they answer, asked every ten seconds - closing the question does not make it go away. */
    public static void tick(ServerPlayer player) {
        Asking waiting = asking.get(player.getUUID());
        if (waiting == null || player.level().getGameTime() < waiting.askAt()) {
            return;
        }
        List<ServerPlayer> hosts = hosts(player);
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<UUID> offered = new ArrayList<>();
        for (ServerPlayer host : hosts) {
            labels.add("Lead " + host.getGameProfile().getName() + "'s band with them");
            values.add(offered.size());
            offered.add(host.getUUID());
        }
        labels.add("Make my own band, far off");
        values.add(OWN_BAND);
        asking.put(player.getUUID(), new Asking(offered, player.level().getGameTime() + 200L));
        String names = String.join(", ", others(player).stream().map(p -> p.getGameProfile().getName()).toList());
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION,
                "Others already walk this country (" + names + "). Walk with one of their bands - or make your own, "
                        + OWN_BAND_DISTANCE + " blocks from anyone?", labels, values));
    }

    public static void choose(ServerPlayer player, int value) {
        Asking waiting = asking.remove(player.getUUID());
        if (waiting == null) {
            return;
        }
        if (value >= 0 && value < waiting.offered().size()
                && player.server.getPlayerList().getPlayer(waiting.offered().get(value)) instanceof ServerPlayer host
                && hostOf(host) == null && !Band.all(host).isEmpty()) {
            walkWith(player, host);
            return;
        }
        ownBandFarOff(player);
    }

    /** One of theirs: same kind, their camp, their people. */
    private static void walkWith(ServerPlayer player, ServerPlayer host) {
        ServerLevel level = host.serverLevel();
        join(player, host);
        var mine = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        mine.setStage(host.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        dev.hominin.evolution.stage.StageSync.sync(player);
        BlockPos spot = Band.standingSpotNear(level, host.blockPosition(), 3, player.getRandom().nextFloat() * Mth.TWO_PI);
        player.teleportTo(level, spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, host.getYRot(), 0.0F);
        dev.hominin.evolution.hunt.Predation.settle(player, dev.hominin.evolution.hunt.Predation.campOf(host));
        player.sendSystemMessage(Component.literal("You lead " + host.getGameProfile().getName() + "'s band with them "
                + "now - its people are yours and you are theirs. What the band needs and what other bands want, you "
                + "hear too, and either of you can answer.").withStyle(ChatFormatting.GOLD));
        host.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " leads your band with you now: "
                + "your people count them as one of their own, and they share the band's demands with you.")
                .withStyle(ChatFormatting.GOLD));
    }

    /** Their own band, a long way from everyone - their own ground, with nobody else's on it. */
    private static void ownBandFarOff(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<ServerPlayer> others = others(player);
        double cx = 0.0D;
        double cz = 0.0D;
        for (ServerPlayer other : others) {
            cx += other.getX() / others.size();
            cz += other.getZ() / others.size();
        }
        if (others.isEmpty()) {
            cx = player.getX();
            cz = player.getZ();
        }
        BlockPos found = null;
        float start = player.getRandom().nextFloat() * Mth.TWO_PI;
        for (int attempt = 0; attempt < 16 && found == null; attempt++) {
            float angle = start + attempt * (Mth.TWO_PI / 16.0F);
            int distance = OWN_BAND_DISTANCE + (attempt / 8) * 40;
            int x = Mth.floor(cx + Mth.cos(angle) * distance);
            int z = Mth.floor(cz + Mth.sin(angle) * distance);
            level.getChunk(x >> 4, z >> 4);
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!Band.dry(level, at)) {
                continue;
            }
            boolean clear = true;
            for (ServerPlayer other : others) {
                if (Bands.horizontal(other.blockPosition(), at) < (OWN_BAND_DISTANCE - 30.0D) * (OWN_BAND_DISTANCE - 30.0D)) {
                    clear = false;
                }
            }
            if (clear) {
                found = at;
            }
        }
        if (found != null) {
            player.teleportTo(level, found.getX() + 0.5D, found.getY(), found.getZ() + 0.5D, player.getYRot(), 0.0F);
            player.setRespawnPosition(level.dimension(), found, 0.0F, true, false);
        }
        Band.formFirstBand(player);
        player.sendSystemMessage(Component.literal("Your own band, on its own ground - the others are a long walk "
                + "away. They are on your map.").withStyle(ChatFormatting.GOLD));
        for (ServerPlayer other : others) {
            other.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " has a band of their own now, "
                    + "a long way off. They are on your map.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Dead, and nobody of your own to carry on as: you come round beside the band you walk with. */
    public static void respawned(ServerPlayer player) {
        UUID host = hostOf(player);
        if (host != null && player.server.getPlayerList().getPlayer(host) instanceof ServerPlayer leader
                && leader.level() == player.level()) {
            BlockPos spot = Band.standingSpotNear(leader.serverLevel(), leader.blockPosition(), 4,
                    player.getRandom().nextFloat() * Mth.TWO_PI);
            player.teleportTo(leader.serverLevel(), spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                    player.getYRot(), 0.0F);
        }
    }

    // ------------------------------------------------------------ later: asking to lead a band together

    /** The H menu: other players' bands to ask to lead with them - and, for a co-leader, splitting off. */
    public static void openMenu(ServerPlayer player) {
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<ServerPlayer> hosts = hosts(player);
        UUID mine = hostOf(player);
        for (int i = 0; i < hosts.size(); i++) {
            ServerPlayer host = hosts.get(i);
            if (host.getUUID().equals(mine)) {
                continue;
            }
            labels.add("Ask to lead " + host.getGameProfile().getName() + "'s band with them");
            values.add(i);
        }
        if (mine != null) {
            labels.add("Split off: a band of my own, here");
            values.add(SPLIT_OFF);
        }
        if (labels.isEmpty()) {
            player.displayClientMessage(Component.literal("No other player leads a band just now."), true);
            return;
        }
        asking.put(player.getUUID(), new Asking(hosts.stream().map(ServerPlayer::getUUID).toList(), Long.MAX_VALUE));
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION_PLAYERS,
                "Other players' bands. Lead one with them - your own people come into it - or go your own way.",
                labels, values));
    }

    public static void choosePlayer(ServerPlayer player, int value) {
        Asking menu = asking.remove(player.getUUID());
        if (value == SPLIT_OFF) {
            splitOff(player);
            return;
        }
        if (menu == null || value < 0 || value >= menu.offered().size()
                || !(player.server.getPlayerList().getPlayer(menu.offered().get(value)) instanceof ServerPlayer host)) {
            return;
        }
        var ours = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        if (!ours.equals(host.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage())) {
            player.displayClientMessage(Component.literal(host.getGameProfile().getName() + "'s people are not your "
                    + "kind. Only a band of your own species will take you."), true);
            return;
        }
        requests.put(host.getUUID(), player.getUUID());
        int bringing = Band.all(player).size();
        PacketDistributor.sendToPlayer(host, new ChoicesPayload(player.getId(), ACTION_REQUEST,
                player.getGameProfile().getName() + " asks to lead your band with you" + (bringing > 0 ? " - and " + bringing
                        + " of their people would come into it." : ".") + " Take them in?",
                List.of("Take them in", "No"), List.of(ACCEPT, 1)));
        player.displayClientMessage(Component.literal("You ask " + host.getGameProfile().getName() + ". They are "
                + "thinking it over."), true);
    }

    /** The leader's answer. Yes: the asker is a co-leader, and their people are the band's - as many as it has room for. */
    public static void answerRequest(ServerPlayer host, int entityId, int value) {
        UUID asked = requests.remove(host.getUUID());
        if (asked == null || !(host.serverLevel().getEntity(entityId) instanceof ServerPlayer player)
                || !player.getUUID().equals(asked)) {
            return;
        }
        if (value != ACCEPT) {
            player.sendSystemMessage(Component.literal(host.getGameProfile().getName() + " would sooner keep their band "
                    + "as it is.").withStyle(ChatFormatting.GRAY));
            return;
        }
        List<String> came = new ArrayList<>();
        List<String> went = new ArrayList<>();
        for (BandMember member : Band.all(player)) {
            member.ensureName();
            if (Band.hasRoomFor(host)) {
                member.setLeader(host.getUUID());
                member.getNavigation().moveTo(host, 1.0D);
                came.add(member.getName().getString());
            } else {
                // No room: they go their own way.
                member.discard();
                went.add(member.getName().getString());
            }
        }
        join(player, host);
        dev.hominin.evolution.hunt.Predation.settle(player, dev.hominin.evolution.hunt.Predation.campOf(host));
        String people = came.isEmpty() ? "" : " " + String.join(", ", came) + " came with them.";
        String gone = went.isEmpty() ? "" : " There was no room for " + String.join(", ", went) + ": they go their own way.";
        host.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " leads your band with you now."
                + people + gone).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("You lead " + host.getGameProfile().getName() + "'s band with them "
                + "now: what it needs and what others want of it, you hear too." + people + gone)
                .withStyle(ChatFormatting.GOLD));
    }

    /** A co-leader goes their own way: a band of their own, from those who would follow them, where they stand. */
    public static void splitOff(ServerPlayer player) {
        UUID host = hostOf(player);
        if (host == null) {
            return;
        }
        split(player);
        Band.formNewBand(player);
        player.sendSystemMessage(Component.literal("You split off with a band of your own.").withStyle(ChatFormatting.GOLD));
        if (player.server.getPlayerList().getPlayer(host) instanceof ServerPlayer leader) {
            leader.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " has split off with a band of "
                    + "their own.").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void forget(UUID player) {
        asking.remove(player);
        requests.remove(player);
    }
}
