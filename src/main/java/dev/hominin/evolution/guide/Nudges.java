package dev.hominin.evolution.guide;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.stage.GateCriterion;
import dev.hominin.evolution.stage.StageDefinition;
import dev.hominin.evolution.stage.StageRegistry;
import net.minecraft.server.level.ServerPlayer;

/**
 * A word in your ear about what your kind still has to do - but only once it has been left alone a while. A
 * requirement on the list that has not moved in fifteen minutes of play gets a tip on how to go about it (and a
 * second, later, if it still has not); so does the milestone act, once everything else is done and it is still
 * not. Never more than one every five minutes, and never while tips are off.
 */
public final class Nudges {
    /** Fifteen minutes of play without a step towards it. */
    private static final long STALL_TICKS = 18000L;
    /** One nudge in this long, whatever is stalled. */
    private static final long GAP_TICKS = 6000L;
    /** Twice per requirement, per kind: after that it is a choice. */
    private static final int MOST = 2;
    private static final String NUDGED = "nudged_";

    private record Hint(String title, String text, String entry, int page) {
    }

    private static final Map<String, Hint> HINTS = new HashMap<>();

    private static void hint(String id, String title, String text, String entry, int page) {
        HINTS.put(id, new Hint(title, text, entry, page));
    }

    static {
        hint("think_times", "Thinking", "Hold {K} to think - with a full stomach and nothing hurting. Empty hands let "
                + "the mind wander; something held gives it an idea to chew on.", "hands/thinking", 0);
        hint("climb_trees", "Climbing", "Walk into a trunk and hold {jump} to climb; {sneak} lets you down. Every new "
                + "tree you climb counts.", "body/trees", 1);
        hint("scavenge_bones", "Marrow", "Break a carcass for its bones, then right-click a bone with a flake in your "
                + "pack - or crack a long bone on a rock. Old bone beds out in the country hold several.",
                "hunting/sharing", 2);
        hint("forage_biomes", "Foraging in new places", "Sneak and right-click grass or soil to forage. Each new "
                + "place counts - walk on into country you have not searched yet.", "body/foraging", 0);
        hint("notice_stone_deposit", "Workable stone", "Look for bare rock breaking through the grass. Use a "
                + "workable stone deposit to take note of it.", "stone/finding", 0);
        hint("water_sources", "Water in more than one place", "Sneak and right-click water to drink. Drink at a "
                + "different water from the last - each new place counts.", "body/water", 1);
        hint("stun_predator", "Stunning a predator", "Hold a long branch and strike a predator on the head - or drop "
                + "onto one from above. Heads do not like it.", "hunting/blunt", 0);
        hint("hunt_with_stick", "Hunting with a stick", "Gnaw a stick sharp ({P} with a bare stick in hand) and kill "
                + "something small with it still in your hand.", "kinds/habilis", 1);
        hint("hunt_with_spear", "Hunting with a spear", "A long branch and a flake, one in each hand, {P}: a "
                + "sharpened spear. Kill something with it in your hand - or thrown.", "kinds/habilis", 3);
        hint("craft_oldowan_tools", "Oldowan tools", "A rock in hand, a hammerstone in the other, {P}: a flake, a "
                + "chopper, a grinding stone, or a multi tool. Different tools count, not the same one twice.",
                "kinds/habilis", 2);
        hint("notice_fire_source", "Finding fire", "Fire comes from lightning and from lava. Find a natural fire and "
                + "sneak-use it to take note of it - carefully.", "body/fire", 0);
        hint("cold_biome_edge", "The cold", "Walk until the country turns cold - snow, or high ground - and turn back.",
                "kinds/habilis", 0);
        hint("ground_night_survival", "A night on the ground", "Spend a whole night with no tree over you, and live. "
                + "Stay close to the band, and keep a weapon.", "body/trees", 0);
        hint("make_acheulean_tool", "A hand axe", "Four sticks and a hide, {P}: a knapping station. Lay stone on its "
                + "mat and work it into a hand axe or a cleaver.", "stone/acheulean", 0);
        hint("knapping_level_2", "Better hands", "Every Acheulean tool you make takes your knapping up: one tool off "
                + "level 4, two more off 3. Turning a good stone over in your mind ({K}) helps too.", "stone/acheulean", 0);
        hint("hearth_night", "A night by the fire", "At the work station: three sticks along the bottom, a log in the "
                + "middle, nothing in the slot - a fire pit. Put three things in it, drill it lit, and spend the night "
                + "beside it.", "body/fire", 0);
        hint("hunt_megafauna", "Megafauna", "A Pelorovis herd, or a great predator. Take the band: {H}, Let's hunt - "
                + "and for megafauna, split into attackers and chasers.", "hunting/megafauna", 0);
        hint("build_structure", "Building", "Press {O} for what you know how to build. Plan it, mark it out with {P}, "
                + "and fill the ghost with thatch blocks and building branches from the work station.",
                "hands/building", 0);
        hint("big_band", "A bigger band", "A band grows by children. Keep them fed and whole and they pair off by "
                + "themselves; ask your mate for a child. Strangers sitting by a carcass will come with you, and "
                + "another band's people sometimes ask to join.", "living/mates", 2);
        hint("eat_cooked_meat", "Cooked meat", "Lay meat on a burning fire pit, or hang it on a cooking rack over one "
                + "- then eat it.", "body/fire", 3);
        hint("persistence_kill", "Running it down", "Wound big game with an edge and keep following it - it cannot "
                + "outlast you. Lose sight of it, and think ({K}) to pick the tracks up.", "hunting/edges", 1);
        hint("take_kill", "Taking a kill", "Walk straight up to hyenas or a giant hyena on a kill. With a band behind "
                + "you, they give it up.", "hunting/sharing", 3);
        hint("range_far", "The long walk", "Pack up and walk: a thousand blocks from where your kind began.",
                "kinds/erectus", 0);
        hint("trade_erectus", "Trading", "Find another erectus band, pick one of them out, {H} and Trade: offer "
                + "something of the same worth or better.", "beginnings/band", 4);
        hint("adopt_norm", "A rule to live by", "{H}, Culture: pick a way for your people to live by.",
                "living/culture", 0);
        hint("make_levallois_tool", "Levallois tools", "At the knapping station, cycle the industry to Levallois: "
                + "flakes (2 a stone), blades, or a hand axe. Any hands can strike the flakes and blades.",
                "kinds/heidelbergensis", 2);
        hint("levallois_hand_axe", "A Levallois hand axe", "Lay a hammerstone of the stone you want among the stone "
                + "at a knapping station - your own hammer in its slot, and a bone - and take it down (Levallois).",
                "kinds/heidelbergensis", 3);
        hint("make_knife", "A knife", "A Levallois blade at the work station, with four twine in the slot - or four "
                + "twine laid round the blade.", "kinds/heidelbergensis", 4);
        hint("super_weapon", "A great weapon", "Once two hard requirements and three tasks are done and two nights "
                + "survived, hold {K} with a workable shaft in hand (a blade does too, in East Africa).",
                "kinds/heidelbergensis", 5);
        hint("fire_harden_spear", "Hardening spears", "Right-click a burning fire with a sharpened spear four times, "
                + "or hold it there.", "body/fire", 7);
        hint("trade_band", "Trading", "Find another band, pick one of them out, {H} and Trade: offer something of the "
                + "same worth or better.", "beginnings/band", 4);
        hint("hold_feast", "A feast", "{H}, Culture: take up the Feast. The next big thing that happens, the band "
                + "gathers for two days and feasts - be at the fire when it begins.", "living/culture", 9);
        hint("demand_tribute", "Strangers on your ground", "Now and then another band forages on your ground - they "
                + "glow. Walk up to them and make them pay for it.", "living/others", 19);
        hint("choose_lineage", "Which way", "Choose which way your people go: the question comes back every half "
                + "minute until you do.", "kinds/heidelbergensis", 0);
        // The milestone acts.
        hint("milestone:walk_upright", "Walking upright", "Everything else is done. Hold {K} with empty hands and "
                + "think about walking on the ground.", "hands/thinking", 2);
        hint("milestone:strike_flake", "The first flake", "Everything else is done. A knappable rock in hand, a "
                + "hammerstone in your off hand, {P} - strike a flake.", "stone/knapping", 0);
        hint("milestone:fire_transfer", "Keeping fire", "Everything else is done. Right-click a natural fire to carry "
                + "it off - or, if your kind can, drill a fire of your own.", "body/fire", 0);
        hint("milestone:fine_acheulean", "A fine tool", "Everything else is done. Make a tier 2 or better hand axe or "
                + "cleaver - not from obsidian. Chert and basalt take the best edges; better hands make better tiers.",
                "stone/acheulean", 0);
    }

    private record Seen(int count, long since) {
    }

    private static final Map<UUID, Map<String, Seen>> seen = new HashMap<>();
    private static final Map<UUID, Long> lastNudge = new HashMap<>();

    /** Every ten seconds. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 200 != 77 || player.isSpectator()) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.current(data);
        if (stage == null || player.getData(Attachments.TIPS).isOff()) {
            return;
        }
        long now = player.level().getGameTime();
        Map<String, Seen> mine = seen.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Map<String, Integer> counters = data.getCriterionCounters();
        String due = null;
        for (GateCriterion criterion : stage.gate().required()) {
            due = check(mine, counters, data, criterion, now, due);
        }
        boolean optionalsWanted = EvolutionManager.optionalSatisfiedCount(data, stage) < stage.gate().chooseCount();
        if (optionalsWanted) {
            for (GateCriterion criterion : stage.gate().optionalPool()) {
                due = check(mine, counters, data, criterion, now, due);
            }
        }
        if (EvolutionManager.isGateReady(data, stage) && !data.isDeveloperMode()) {
            String key = "milestone:" + stage.milestone().type().getPath();
            Seen ready = mine.get(key);
            if (ready == null) {
                mine.put(key, new Seen(0, now));
            } else if (due == null && now - ready.since() >= STALL_TICKS && counters.getOrDefault(NUDGED + key, 0) < MOST) {
                due = key;
            }
        }
        if (due == null || now - lastNudge.getOrDefault(player.getUUID(), -GAP_TICKS) < GAP_TICKS) {
            return;
        }
        Hint hint = HINTS.get(due);
        if (hint != null && Tips.showHint(player, hint.title(), hint.text(), hint.entry(), hint.page())) {
            counters.merge(NUDGED + due, 1, Integer::sum);
            lastNudge.put(player.getUUID(), now);
            Seen old = mine.get(due);
            mine.put(due, new Seen(old == null ? 0 : old.count(), now));
        }
    }

    /** Tracks one requirement's progress; returns it if it has stalled long enough to nudge, else what was due. */
    @Nullable
    private static String check(Map<String, Seen> mine, Map<String, Integer> counters, PlayerEvolutionData data,
            GateCriterion criterion, long now, @Nullable String due) {
        String id = criterion.id();
        if (EvolutionManager.isCriterionSatisfied(data, criterion) || !HINTS.containsKey(id)) {
            return due;
        }
        int count = counters.getOrDefault(id, 0);
        Seen was = mine.get(id);
        if (was == null || was.count() != count) {
            mine.put(id, new Seen(count, now));
            return due;
        }
        if (due == null && now - was.since() >= STALL_TICKS && counters.getOrDefault(NUDGED + id, 0) < MOST) {
            return id;
        }
        return due;
    }

    public static void forget(UUID player) {
        seen.remove(player);
        lastNudge.remove(player);
    }

    private Nudges() {
    }
}
