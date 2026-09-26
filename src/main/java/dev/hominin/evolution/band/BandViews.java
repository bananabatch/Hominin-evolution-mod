package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * How a band sees another player's band - set by hand, by its leader or a co-leader, since no standing is kept between
 * players' bands the way it is with the others. <b>Hostile</b>: its people are driven off your ground - your band goes
 * for them. <b>Allied</b>: when one of them is attacked near your people, your band stands with them. Friendly, neutral
 * and unfriendly are said, and known to both sides.
 */
public final class BandViews extends SavedData {
    public static final int ACTION_LIST = 89;
    public static final int ACTION_SET = 90;
    private static final String NAME = "hominin_evolution_band_views";
    /** How near one of theirs your people have to be to go for them, or to stand with them. */
    private static final double REACH = 24.0D;
    private static final long TOLD_TICKS = 5 * 60 * 20L;

    public enum View {
        ALLIED("allied", ChatFormatting.AQUA),
        FRIENDLY("friendly", ChatFormatting.GREEN),
        NEUTRAL("neutral", ChatFormatting.GRAY),
        UNFRIENDLY("unfriendly", ChatFormatting.RED),
        HOSTILE("hostile", ChatFormatting.DARK_RED);

        private final String label;
        private final ChatFormatting colour;

        View(String label, ChatFormatting colour) {
            this.label = label;
            this.colour = colour;
        }

        public String label() {
            return label;
        }
    }

    /** By a band's leader: how it sees each other band, by that band's leader. Missing: neutral. */
    private final Map<UUID, Map<UUID, View>> views = new HashMap<>();
    /** The bands listed in each player's open menu: a pick is an index into it. */
    private static final Map<UUID, List<UUID>> listed = new HashMap<>();
    /** When each band was last told its people are going for someone's: "leader/other". */
    private static final Map<String, Long> told = new HashMap<>();

    private static BandViews of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BandViews::new, BandViews::load), NAME);
    }

    /** The leader of the band this player is in: whoever they lead with, or themselves. */
    public static UUID bandOf(Player player) {
        UUID host = Newcomers.hostOf(player);
        return host != null ? host : player.getUUID();
    }

    /** How the band led by one player sees the band led by another. */
    public static View view(MinecraftServer server, UUID band, UUID other) {
        Map<UUID, View> seen = of(server).views.get(band);
        return seen == null ? View.NEUTRAL : seen.getOrDefault(other, View.NEUTRAL);
    }

    /** "Their band: allied with yours. Yours: hostile to theirs." - for the player menu's info. */
    public static String describe(ServerPlayer viewer, ServerPlayer other) {
        UUID mine = bandOf(viewer);
        UUID theirs = bandOf(other);
        if (mine.equals(theirs)) {
            return "";
        }
        return " Your band sees theirs as " + view(viewer.server, mine, theirs).label() + "; theirs sees yours as "
                + view(viewer.server, theirs, mine).label() + ".";
    }

    // ------------------------------------------------------------ the menu

    /** The other players' bands, each with how yours sees it. */
    public static void open(ServerPlayer player) {
        if (!BandRoles.check(player, Newcomers.Role.CO_LEADER, "decide how the band sees other bands")) {
            return;
        }
        UUID mine = bandOf(player);
        Map<UUID, String> bands = new LinkedHashMap<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            UUID theirs = bandOf(other);
            if (theirs.equals(mine)) {
                continue;
            }
            ServerPlayer leader = player.server.getPlayerList().getPlayer(theirs);
            bands.putIfAbsent(theirs, (leader != null ? leader : other).getGameProfile().getName() + "'s band");
        }
        List<UUID> order = new ArrayList<>(bands.keySet());
        listed.put(player.getUUID(), order);
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            labels.add(bands.get(order.get(i)) + ": " + view(player.server, mine, order.get(i)).label());
            values.add(i);
        }
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION_LIST, order.isEmpty()
                ? "There is no other player's band about." : "How your band sees other players' bands. Hostile: your "
                        + "people go for theirs on your ground. Allied: yours stand with theirs when they are attacked.",
                labels, values));
    }

    public static void chooseBand(ServerPlayer player, int index) {
        List<UUID> order = listed.getOrDefault(player.getUUID(), List.of());
        if (index < 0 || index >= order.size()) {
            return;
        }
        UUID other = order.get(index);
        View now = view(player.server, bandOf(player), other);
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (View view : View.values()) {
            labels.add((view == now ? "[x] " : "") + view.label());
            values.add(index * 8 + view.ordinal());
        }
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION_SET, "How does your band see "
                + "them?", labels, values));
    }

    public static void set(ServerPlayer player, int value) {
        List<UUID> order = listed.getOrDefault(player.getUUID(), List.of());
        int index = value / 8;
        int ordinal = value % 8;
        View[] all = View.values();
        if (index < 0 || index >= order.size() || ordinal < 0 || ordinal >= all.length
                || !BandRoles.check(player, Newcomers.Role.CO_LEADER, "decide how the band sees other bands")) {
            return;
        }
        UUID mine = bandOf(player);
        UUID other = order.get(index);
        BandViews data = of(player.server);
        View view = all[ordinal];
        if (view == View.NEUTRAL) {
            data.views.getOrDefault(mine, new HashMap<>()).remove(other);
        } else {
            data.views.computeIfAbsent(mine, k -> new HashMap<>()).put(other, view);
        }
        data.setDirty();
        String ours = player.getGameProfile().getName();
        String theirs = player.server.getPlayerList().getPlayer(other) instanceof ServerPlayer leader
                ? leader.getGameProfile().getName() + "'s band" : "their band";
        player.sendSystemMessage(Component.literal("Your band sees " + theirs + " as " + view.label() + " now.")
                .withStyle(view.colour));
        // They hear of it: every player of theirs about.
        for (ServerPlayer one : player.server.getPlayerList().getPlayers()) {
            if (bandOf(one).equals(other)) {
                one.sendSystemMessage(Component.literal(ours + "'s band sees yours as " + view.label() + " now.")
                        .withStyle(view.colour));
            }
        }
        open(player);
    }

    // ------------------------------------------------------------ what it means

    /** Every two seconds, for a band's leader: anyone of a hostile band on your ground, your people go for. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 40 != 23 || Newcomers.hostOf(player) != null) {
            return;
        }
        Map<UUID, View> seen = of(player.server).views.get(player.getUUID());
        if (seen == null || !seen.containsValue(View.HOSTILE)) {
            return;
        }
        var level = player.serverLevel();
        UUID mine = player.getUUID();
        List<BandMember> ours = new ArrayList<>(level.getEntities(ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && !m.isBaby() && mine.equals(m.getLeader())));
        if (ours.isEmpty()) {
            return;
        }
        for (var entry : seen.entrySet()) {
            if (entry.getValue() != View.HOSTILE) {
                continue;
            }
            UUID other = entry.getKey();
            List<LivingEntity> intruders = new ArrayList<>();
            for (ServerPlayer one : player.server.getPlayerList().getPlayers()) {
                if (one.level() == level && one.isAlive() && !one.isSpectator() && bandOf(one).equals(other)
                        && dev.hominin.evolution.hunt.Predation.onOwnGround(player, one.blockPosition())) {
                    intruders.add(one);
                }
            }
            intruders.addAll(level.getEntities(ModEntities.BAND_MEMBER.get(), m -> m.isAlive() && !m.isBaby()
                    && other.equals(m.getLeader()) && dev.hominin.evolution.hunt.Predation.onOwnGround(player,
                            m.blockPosition())));
            boolean went = false;
            for (LivingEntity intruder : intruders) {
                for (BandMember member : ours) {
                    if (member.getTarget() == null && member.distanceToSqr(intruder) < REACH * REACH) {
                        member.defendAgainst(intruder);
                        went = true;
                    }
                }
            }
            String key = mine + "/" + other;
            if (went && level.getGameTime() - told.getOrDefault(key, -TOLD_TICKS) >= TOLD_TICKS) {
                told.put(key, level.getGameTime());
                player.displayClientMessage(Component.literal("Your band goes for the people of a hostile band on your "
                        + "ground.").withStyle(ChatFormatting.DARK_RED), true);
            }
        }
    }

    /** One of a band's players has been attacked: any band that sees theirs as allied, with people close by, comes. */
    public static void allyAttacked(ServerPlayer hurt, Entity attacker) {
        UUID theirs = bandOf(hurt);
        UUID attackers = attacker instanceof Player p ? bandOf(p)
                : attacker instanceof BandMember m ? m.getLeader() : null;
        BandViews data = of(hurt.server);
        for (var entry : data.views.entrySet()) {
            UUID band = entry.getKey();
            if (band.equals(theirs) || band.equals(attackers) || entry.getValue().get(theirs) != View.ALLIED
                    || !(attacker instanceof LivingEntity target)) {
                continue;
            }
            for (BandMember member : hurt.serverLevel().getEntities(ModEntities.BAND_MEMBER.get(),
                    m -> m.isAlive() && !m.isBaby() && band.equals(m.getLeader())
                            && m.distanceToSqr(hurt) < REACH * REACH)) {
                member.defendAgainst(target);
            }
        }
    }

    public static void forget(UUID player) {
        listed.remove(player);
    }

    private static BandViews load(CompoundTag tag, HolderLookup.Provider registries) {
        BandViews data = new BandViews();
        for (Tag entry : tag.getList("Views", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) entry;
            try {
                data.views.computeIfAbsent(one.getUUID("Band"), k -> new HashMap<>())
                        .put(one.getUUID("Other"), View.valueOf(one.getString("View")));
            } catch (IllegalArgumentException ignored) {
                // An unknown view is dropped: neutral.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var band : views.entrySet()) {
            for (var seen : band.getValue().entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putUUID("Band", band.getKey());
                one.putUUID("Other", seen.getKey());
                one.putString("View", seen.getValue().name());
                list.add(one);
            }
        }
        tag.put("Views", list);
        return tag;
    }

    private BandViews() {
    }
}
