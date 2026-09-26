package dev.hominin.evolution.stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.Newcomers;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * With other players in the world, nobody evolves alone. When a band evolves, everyone gets fifteen seconds - an
 * intermission - to choose, and everyone sees who chose what:
 *
 * <ul>
 * <li><b>The band's co-leaders</b> go with it and become the same species - or split off, into a new band of their
 * own of that species. The band that evolved stays the band it was.</li>
 * <li><b>Everyone else</b> keeps their own band - or joins the evolving one, as a co-leader. A band that keeps to
 * itself and is behind is <b>bumped up</b> with the rest of the world: to the fallback of the species the others
 * have become, with some of its road already walked (a few of its hard requirements met, a few of its optional ones,
 * and more half-done - never all of it).</li>
 * <li><b>Where the line splits</b> (heidelbergensis) everyone chooses their route. A co-leader who chooses a
 * different way from the band's leader is asked: split off and go that way with a band of your own - or go with
 * the band after all.</li>
 * </ul>
 */
public final class Intermission {
    public static final int ACTION = 71;
    public static final int ACTION_SPLIT = 72;
    private static final long LENGTH = 15 * 20L;
    /** Time left for a co-leader asked whether to split, however late in the intermission it comes. */
    private static final long SPLIT_ANSWER = 10 * 20L;
    private static final long ASK_AGAIN = 10 * 20L;

    private static final int WITH_BAND = 0;
    private static final int SPLIT = 1;
    private static final int KEEP_OWN = 2;
    private static final int JOIN = 3;
    /** A route, as {@link Lineage#SAPIENS} or {@link Lineage#NEANDERTHAL} on top of this. */
    private static final int ROUTE = 10;
    /** Keeping their own band, and going this way. */
    private static final int KEEP_ROUTE = 20;

    private enum Role {
        EVOLVER, CO_LEADER, OTHER,
        /** Co-leads some other band: goes wherever that band's leader takes it. */
        FOLLOWER
    }

    private static final class Session {
        final UUID evolver;
        final ResourceLocation into;
        final boolean branch;
        long endsAt;
        long askedEvolverAt;
        final Map<UUID, Role> roles = new LinkedHashMap<>();
        final Map<UUID, Integer> picked = new HashMap<>();
        final Map<UUID, Integer> route = new HashMap<>();
        final Set<UUID> askedSplit = new HashSet<>();
        final Map<UUID, Boolean> splits = new HashMap<>();

        Session(UUID evolver, ResourceLocation into, boolean branch, long endsAt) {
            this.evolver = evolver;
            this.into = into;
            this.branch = branch;
            this.endsAt = endsAt;
        }
    }

    @Nullable
    private static Session session;
    /** Somebody else evolving while an intermission runs: they wait their turn. */
    private static final List<Map.Entry<UUID, ResourceLocation>> queued = new ArrayList<>();
    private static long lastTick = -1L;

    // ------------------------------------------------------------ starting one

    /**
     * Called as a player evolves. Returns true when the evolve is held for an intermission (or queued behind one) and
     * will go through at its end; false, alone in the world, to evolve at once.
     */
    public static boolean start(ServerPlayer player, ResourceLocation into) {
        MinecraftServer server = player.server;
        ServerPlayer evolver = Newcomers.leadOf(player);
        List<ServerPlayer> everyone = new ArrayList<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (!other.isSpectator() && other.level() == evolver.level()) {
                everyone.add(other);
            }
        }
        if (everyone.size() < 2) {
            return false;
        }
        if (session != null) {
            queued.add(Map.entry(evolver.getUUID(), into));
            player.sendSystemMessage(Component.literal("Another band is evolving - yours will, as soon as they have.")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }
        long now = evolver.level().getGameTime();
        Session s = new Session(evolver.getUUID(), into, Lineage.branches(into), now + LENGTH);
        s.askedEvolverAt = now;
        List<ServerPlayer> co = Newcomers.coLeaders(evolver);
        for (ServerPlayer one : everyone) {
            s.roles.put(one.getUUID(), one == evolver ? Role.EVOLVER : co.contains(one) ? Role.CO_LEADER
                    : Newcomers.hostOf(one) != null ? Role.FOLLOWER : Role.OTHER);
        }
        session = s;
        String name = evolver.getGameProfile().getName();
        String species = speciesName(into);
        for (ServerPlayer one : everyone) {
            one.sendSystemMessage(Component.literal(name + "'s band is evolving into " + species + ". Fifteen seconds "
                    + "to choose.").withStyle(ChatFormatting.LIGHT_PURPLE));
            ask(one, s);
        }
        return true;
    }

    private static String speciesName(ResourceLocation stage) {
        StageDefinition def = StageRegistry.get(stage);
        return def == null ? stage.getPath() : def.displayName();
    }

    private static void ask(ServerPlayer player, Session s) {
        Role role = s.roles.get(player.getUUID());
        ServerPlayer evolver = player.server.getPlayerList().getPlayer(s.evolver);
        String who = evolver == null ? "the" : evolver.getGameProfile().getName() + "'s";
        String species = speciesName(s.into);
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        String title;
        if (role == Role.FOLLOWER) {
            player.sendSystemMessage(Component.literal("Your band's leader chooses for your band: you go where they go.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (role == Role.EVOLVER) {
            if (!s.branch) {
                player.sendSystemMessage(Component.literal("The others are choosing: go with your band, split off, or "
                        + "keep to their own.").withStyle(ChatFormatting.GRAY));
                return;
            }
            title = "Your people are becoming " + species + ". Which way do they go? Everyone else is choosing too.";
            labels.add("Towards Homo sapiens - East Africa");
            values.add(ROUTE + Lineage.SAPIENS);
            labels.add("Towards the Neanderthals - North Africa");
            values.add(ROUTE + Lineage.NEANDERTHAL);
        } else if (role == Role.CO_LEADER) {
            if (s.branch) {
                title = who + " band is becoming " + species + ". Which way do you go? If it is not the band's way, you "
                        + "can split off.";
                labels.add("Towards Homo sapiens");
                values.add(ROUTE + Lineage.SAPIENS);
                labels.add("Towards the Neanderthals");
                values.add(ROUTE + Lineage.NEANDERTHAL);
            } else {
                title = who + " band - yours - is becoming " + species + ". Go with it, or split off into a band of "
                        + "your own?";
                labels.add("Go with the band");
                values.add(WITH_BAND);
                labels.add("Split off: a band of my own");
                values.add(SPLIT);
            }
        } else {
            ResourceLocation bump = bumpTarget(player, s.into);
            boolean behind = bump != null;
            String keep = behind ? "Keep my band (it becomes " + speciesName(bump) + ")" : "Keep my band";
            title = who + " band is becoming " + species + "." + (behind ? " The world is moving on: keep your band, "
                    + "and it moves on with it - to " + speciesName(bump) + ", some of the way already walked." : "")
                    + " Or join theirs, and lead it with them.";
            if (behind && Lineage.branches(bump)) {
                labels.add("Keep my band - towards Homo sapiens");
                values.add(KEEP_ROUTE + Lineage.SAPIENS);
                labels.add("Keep my band - towards the Neanderthals");
                values.add(KEEP_ROUTE + Lineage.NEANDERTHAL);
            } else {
                labels.add(keep);
                values.add(KEEP_OWN);
            }
            if (rank(stageOf(player)) <= rank(s.into)) {
                labels.add("Join " + who + " band (become " + species + ")");
                values.add(JOIN);
            }
        }
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION, title, labels, values));
    }

    // ------------------------------------------------------------ choosing

    public static void choose(ServerPlayer player, int value) {
        Session s = session;
        if (s == null || !s.roles.containsKey(player.getUUID())) {
            return;
        }
        s.picked.put(player.getUUID(), value);
        if (value >= KEEP_ROUTE) {
            s.route.put(player.getUUID(), value - KEEP_ROUTE);
        } else if (value >= ROUTE) {
            s.route.put(player.getUUID(), value - ROUTE);
        }
        announce(player, label(value, s));
        // Different ways: whoever differs from the band's leader is asked whether to split.
        Integer leaderRoute = s.route.get(s.evolver);
        if (s.branch && leaderRoute != null) {
            for (Map.Entry<UUID, Role> entry : s.roles.entrySet()) {
                Integer theirs = s.route.get(entry.getKey());
                if (entry.getValue() == Role.CO_LEADER && theirs != null && !theirs.equals(leaderRoute)
                        && s.askedSplit.add(entry.getKey())
                        && player.server.getPlayerList().getPlayer(entry.getKey()) instanceof ServerPlayer co) {
                    askSplit(co, s, leaderRoute, theirs);
                }
            }
        }
    }

    private static void askSplit(ServerPlayer co, Session s, int leaderRoute, int theirs) {
        s.endsAt = Math.max(s.endsAt, co.level().getGameTime() + SPLIT_ANSWER);
        ServerPlayer evolver = co.server.getPlayerList().getPlayer(s.evolver);
        String who = evolver == null ? "The band" : evolver.getGameProfile().getName() + "'s band";
        PacketDistributor.sendToPlayer(co, new ChoicesPayload(co.getId(), ACTION_SPLIT,
                who + " goes towards " + Lineage.people(leaderRoute) + "; you chose " + Lineage.people(theirs)
                        + ". Split off, with a band of your own going your way - or go with them?",
                List.of("Split off - my own band, towards " + Lineage.people(theirs),
                        "Go with them - towards " + Lineage.people(leaderRoute)),
                List.of(1, 0)));
    }

    public static void chooseSplit(ServerPlayer player, int value) {
        Session s = session;
        if (s == null || !s.askedSplit.contains(player.getUUID())) {
            return;
        }
        s.splits.put(player.getUUID(), value == 1);
        announce(player, value == 1 ? "splits off, towards " + Lineage.people(s.route.getOrDefault(player.getUUID(), 0))
                : "goes with the band after all");
    }

    private static String label(int value, Session s) {
        if (value >= KEEP_ROUTE) {
            return "keeps their band, towards " + Lineage.people(value - KEEP_ROUTE);
        }
        if (value >= ROUTE) {
            return "towards " + Lineage.people(value - ROUTE);
        }
        return switch (value) {
            case WITH_BAND -> "goes with the band";
            case SPLIT -> "splits off into a band of their own";
            case JOIN -> "joins the band";
            default -> "keeps their own band";
        };
    }

    private static void announce(ServerPlayer player, String what) {
        Session s = session;
        if (s == null) {
            return;
        }
        for (UUID id : s.roles.keySet()) {
            if (player.server.getPlayerList().getPlayer(id) instanceof ServerPlayer one) {
                one.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " " + what + ".")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    // ------------------------------------------------------------ the count, and the end

    /** Called from every player's tick; runs once a server tick. */
    public static void tick(ServerPlayer any) {
        long now = any.server.overworld().getGameTime();
        if (now == lastTick) {
            return;
        }
        lastTick = now;
        Session s = session;
        if (s == null) {
            if (!queued.isEmpty()) {
                Map.Entry<UUID, ResourceLocation> next = queued.remove(0);
                if (any.server.getPlayerList().getPlayer(next.getKey()) instanceof ServerPlayer waiting
                        && !start(waiting, next.getValue())) {
                    EvolutionManager.become(waiting, next.getValue());
                }
            }
            return;
        }
        ServerPlayer evolver = any.server.getPlayerList().getPlayer(s.evolver);
        if (evolver == null) {
            // Gone before it happened: nothing happens. Their milestone waits for them.
            tellAll(any.server, s, "The evolving band's leader has gone. Nothing changes - yet.");
            session = null;
            return;
        }
        long left = s.endsAt - evolver.level().getGameTime();
        if (left % 20 == 0) {
            board(any.server, s, Math.max(0, left / 20));
        }
        if (left > 0) {
            return;
        }
        if (s.branch && !s.route.containsKey(s.evolver)) {
            // Where the line splits, it waits for its leader's choice.
            if (evolver.level().getGameTime() - s.askedEvolverAt >= ASK_AGAIN) {
                s.askedEvolverAt = evolver.level().getGameTime();
                ask(evolver, s);
            }
            return;
        }
        session = null;
        resolve(evolver, s);
    }

    /** Everyone's choice so far, over the hotbar, with the seconds left. */
    private static void board(MinecraftServer server, Session s, long seconds) {
        StringBuilder text = new StringBuilder("Evolving in " + seconds + "s");
        for (Map.Entry<UUID, Role> entry : s.roles.entrySet()) {
            ServerPlayer one = server.getPlayerList().getPlayer(entry.getKey());
            if (one == null) {
                continue;
            }
            Integer picked = s.picked.get(entry.getKey());
            String choice = picked != null ? label(picked, s)
                    : entry.getValue() == Role.EVOLVER && !s.branch ? "evolving"
                    : entry.getValue() == Role.FOLLOWER ? "with their band" : "choosing...";
            text.append("  |  ").append(one.getGameProfile().getName()).append(": ").append(choice);
        }
        Component line = Component.literal(text.toString()).withStyle(ChatFormatting.LIGHT_PURPLE);
        for (UUID id : s.roles.keySet()) {
            if (server.getPlayerList().getPlayer(id) instanceof ServerPlayer one) {
                one.displayClientMessage(line, true);
            }
        }
    }

    private static void tellAll(MinecraftServer server, Session s, String text) {
        for (UUID id : s.roles.keySet()) {
            if (server.getPlayerList().getPlayer(id) instanceof ServerPlayer one) {
                one.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void resolve(ServerPlayer evolver, Session s) {
        MinecraftServer server = evolver.server;
        int leaderRoute = s.route.getOrDefault(s.evolver, Lineage.NONE);
        if (s.branch) {
            Lineage.chosen(evolver, leaderRoute);
        }
        EvolutionManager.become(evolver, s.into);
        for (Map.Entry<UUID, Role> entry : s.roles.entrySet()) {
            ServerPlayer one = server.getPlayerList().getPlayer(entry.getKey());
            if (one == null || one == evolver || entry.getValue() == Role.FOLLOWER) {
                continue;
            }
            Integer picked = s.picked.get(one.getUUID());
            if (entry.getValue() == Role.CO_LEADER) {
                Integer theirs = s.route.get(one.getUUID());
                boolean splits = s.branch ? theirs != null && theirs != leaderRoute
                        && Boolean.TRUE.equals(s.splits.get(one.getUUID()))
                        : picked != null && picked == SPLIT;
                if (splits) {
                    // A band of their own, of the new kind: the band that evolved stays the band it was.
                    Newcomers.split(one);
                    if (s.branch) {
                        Lineage.chosen(one, theirs);
                    }
                    EvolutionManager.become(one, s.into);
                } else {
                    if (s.branch) {
                        Lineage.chosen(one, leaderRoute);
                    }
                    EvolutionManager.become(one, s.into);
                }
                continue;
            }
            if (picked != null && picked == JOIN) {
                Newcomers.join(one, evolver);
                if (s.branch) {
                    Lineage.chosen(one, leaderRoute);
                }
                EvolutionManager.become(one, s.into);
                continue;
            }
            ResourceLocation bump = bumpTarget(one, s.into);
            if (bump == null) {
                continue;
            }
            if (Lineage.branches(bump)) {
                Integer route = s.route.get(one.getUUID());
                Lineage.chosen(one, route != null ? route
                        : one.getRandom().nextBoolean() ? Lineage.SAPIENS : Lineage.NEANDERTHAL);
            }
            one.sendSystemMessage(Component.literal("The world has moved on, and your band with it: "
                    + speciesName(bump) + ".").withStyle(ChatFormatting.GOLD));
            EvolutionManager.become(one, bump);
            headStart(one);
        }
        // Last, whoever co-leads another band: they become whatever their leader has just become.
        for (Map.Entry<UUID, Role> entry : s.roles.entrySet()) {
            ServerPlayer one = server.getPlayerList().getPlayer(entry.getKey());
            UUID hostId = one == null || entry.getValue() != Role.FOLLOWER ? null : Newcomers.hostOf(one);
            if (hostId == null || !(server.getPlayerList().getPlayer(hostId) instanceof ServerPlayer host)) {
                continue;
            }
            ResourceLocation theirs = stageOf(host);
            if (theirs.equals(stageOf(one))) {
                continue;
            }
            Lineage.chosen(one, Lineage.of(host));
            EvolutionManager.become(one, theirs);
            if (!host.getUUID().equals(s.evolver)) {
                headStart(one);
            }
        }
    }

    // ------------------------------------------------------------ who is behind

    private static ResourceLocation stageOf(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
    }

    /** Where a band that keeps to itself goes, if it is behind: the fallback of the new species - or the species. */
    @Nullable
    private static ResourceLocation bumpTarget(ServerPlayer player, ResourceLocation into) {
        if (rank(stageOf(player)) >= rank(into)) {
            return null;
        }
        ResourceLocation fallback = Fallbacks.of(into);
        return fallback != null && StageRegistry.get(fallback) != null ? fallback : into;
    }

    /**
     * How far along the line a species is: ardipithecus 0, then one for each step - and a fallback counts as the
     * species it stands behind.
     */
    static int rank(@Nullable ResourceLocation stage) {
        if (stage == null) {
            return 0;
        }
        Map<ResourceLocation, Integer> ranks = new HashMap<>();
        ResourceLocation at = Band.ARDIPITHECUS;
        int step = 0;
        while (at != null && !ranks.containsKey(at)) {
            ranks.put(at, step++);
            StageDefinition def = StageRegistry.get(at);
            at = def == null ? null : def.nextStage().orElse(null);
        }
        for (Map.Entry<ResourceLocation, Integer> main : new ArrayList<>(ranks.entrySet())) {
            ResourceLocation fallback = Fallbacks.of(main.getKey());
            if (fallback != null) {
                ranks.putIfAbsent(fallback, main.getValue());
            }
        }
        Integer known = ranks.get(stage);
        if (known != null) {
            return known;
        }
        StageDefinition def = StageRegistry.get(stage);
        ResourceLocation next = def == null ? null : def.nextStage().orElse(null);
        return next != null && ranks.containsKey(next) ? ranks.get(next) - 1 : 0;
    }

    /**
     * Some of the road already walked: of the hard requirements, fewer than half met outright and one more half-way;
     * of the optional ones, one short of enough met, and one more half-way. Never the whole of it.
     */
    private static void headStart(ServerPlayer player) {
        var data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition def = StageRegistry.get(data.getStage());
        if (def == null) {
            return;
        }
        Map<String, Integer> counters = data.getCriterionCounters();
        List<GateCriterion> required = new ArrayList<>(def.gate().required());
        Collections.shuffle(required, new java.util.Random(player.getRandom().nextLong()));
        int full = Math.max(0, (required.size() - 1) / 2);
        for (int i = 0; i < required.size(); i++) {
            GateCriterion c = required.get(i);
            if (i < full) {
                counters.put(c.id(), c.requiredCount());
            } else if (i == full && c.requiredCount() > 1) {
                counters.put(c.id(), c.requiredCount() / 2);
            }
        }
        List<GateCriterion> optional = new ArrayList<>(def.gate().optionalPool());
        Collections.shuffle(optional, new java.util.Random(player.getRandom().nextLong()));
        int fullOptional = Math.max(0, Math.min(optional.size(), def.gate().chooseCount() - 1));
        for (int i = 0; i < optional.size(); i++) {
            GateCriterion c = optional.get(i);
            if (i < fullOptional) {
                counters.put(c.id(), c.requiredCount());
            } else if (i == fullOptional && c.requiredCount() > 1) {
                counters.put(c.id(), c.requiredCount() / 2);
            }
        }
        StageSync.sync(player);
        player.sendSystemMessage(Component.literal("Your people did not start from nothing: some of what "
                + def.displayName() + " asks of you is done already, and more of it half-done.")
                .withStyle(ChatFormatting.GRAY));
    }

    public static void forget(ServerPlayer player) {
        queued.removeIf(e -> e.getKey().equals(player.getUUID()));
    }

    private Intermission() {
    }
}
