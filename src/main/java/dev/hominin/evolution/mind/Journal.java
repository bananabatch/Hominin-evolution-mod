package dev.hominin.evolution.mind;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.network.JournalPayload;
import dev.hominin.evolution.stage.StageDefinition;
import dev.hominin.evolution.stage.StageRegistry;
import dev.hominin.evolution.survival.Afflictions;
import dev.hominin.evolution.survival.Infestation;
import dev.hominin.evolution.survival.Thirst;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Everything the J screen shows: who you are right now, and what you know.
 *
 * <p>Built on the server and sent over whole, because nearly all of it - ticks, what is
 * keeping you from healing, what the band thinks of you - only exists there.
 */
public final class Journal {
    private static final String GENDER = "gender";
    private static final int MAX_MEMBERS_LISTED = 8;

    public static void send(ServerPlayer player) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        backfill(player, data);
        StageDefinition stage = StageRegistry.get(data.getStage());
        String stageName = stage != null ? stage.displayName() : data.getStage().getPath();

        List<String> stats = new ArrayList<>();
        stats.add("Species: " + stageName);
        stats.add("Sex: " + (gender(player, data) == 2 ? "Female" : "Male"));
        stats.add("Health: " + Math.round(player.getHealth()) + " / " + Math.round(player.getMaxHealth())
                + "    Water: " + Thirst.get(player) + " / " + Thirst.MAX);
        stats.add("Ticks: " + Infestation.describe(player));
        stats.add("Knapping: " + dev.hominin.evolution.knapping.Acheulean.describe(player));
        stats.add("Persistence hunting: " + dev.hominin.evolution.hunt.Persistence.describe(player));
        if (dev.hominin.evolution.survival.FoodIllness.has(player)) {
            stats.add("Illness: " + dev.hominin.evolution.survival.FoodIllness.describe(player));
        }
        if (dev.hominin.evolution.survival.Kuru.has(player)) {
            stats.add("Illness: " + dev.hominin.evolution.survival.Kuru.describe(player));
        }
        if (dev.hominin.evolution.band.Mating.isPregnant(player)) {
            stats.add("Pregnant: " + dev.hominin.evolution.band.Mating.describePregnancy(player));
        }
        BandMember mate = dev.hominin.evolution.band.Mating.mateOf(player);
        if (mate != null) {
            mate.ensureName();
            stats.add("Mate: " + mate.getName().getString());
        }
        if (dev.hominin.evolution.band.Mortuary.hasNorm(player)) {
            stats.add("Norm: your dead stay with you");
        }
        Afflictions.Affliction affliction = Afflictions.current(player);
        stats.add("Healing: " + (affliction == null ? "nothing is holding it back"
                : "only to " + affliction.hearts(player) + " hearts - " + affliction.label().toLowerCase() + " ("
                        + Afflictions.secondsLeft(player) + "s)"));
        String body = player.getData(dev.hominin.evolution.Attachments.MIND).bodyName();
        if (!body.isEmpty()) {
            stats.add("You are: " + body);
        }
        stats.add("");

        List<BandMember> band = Band.all(player);
        int left = Band.bandsLeft(player);
        stats.add("Band: " + band.size() + (band.size() == 1 ? " member" : " members")
                + "    Cohesion: " + dev.hominin.evolution.band.Cohesion.get(player) + "/"
                + dev.hominin.evolution.band.Cohesion.MAX);
        stats.add(left == 0 ? "This band is the last one your species has."
                : "You can lose " + left + " more " + (left == 1 ? "band" : "bands") + " before your species dies out.");
        stats.add("Your band: " + dev.hominin.evolution.band.BandNames.capital(dev.hominin.evolution.band.Relations.ownName(player))
                + "    Presence here: " + dev.hominin.evolution.band.Presence.get(player) + "/50");
        if (dev.hominin.evolution.hunt.Predation.settled(player)) {
            var land = dev.hominin.evolution.world.Land.ofPlayer(player);
            stats.add("Your ground: pressure " + land.total() + "/10 - " + dev.hominin.evolution.world.Land.label(land.total()));
        } else {
            stats.add("Your ground: none - packed up and on the move");
        }
        int desperate = dev.hominin.evolution.band.Claims.ownDesperation(player);
        stats.add("Desperation: " + desperate + "/5 - " + dev.hominin.evolution.band.Claims.desperationLabel(desperate));
        int feared = dev.hominin.evolution.band.Claims.feared(player);
        if (feared > 0) {
            stats.add("Your name: " + feared + "/10 - bands think twice before trying you");
        }
        if (dev.hominin.evolution.band.Bands.desperateTimes(player.serverLevel())) {
            stats.add("Desperate times: nearly every band out there is going hungry");
        }
        MindData mind = MentalMap.mind(player);
        stats.add("Places held in mind: " + mind.memories().size() + "/" + mind.slots() + " (see Map)");
        int places = dev.hominin.evolution.world.Pois.known(player).size();
        int handed = dev.hominin.evolution.world.Pois.passedDown(player);
        stats.add("Places the band knows: " + places + (handed > 0 ? " - handed down " + handed
                + (handed == 1 ? " time" : " times") : "") + " (diamonds on the map)");
        if (dev.hominin.evolution.band.ToolPiles.store(player.serverLevel(), player.getUUID()) != null) {
            stats.add("The band's tools: " + dev.hominin.evolution.band.ToolPiles.toolsIn(player.serverLevel(),
                    player.getUUID()) + " lying in its piles");
        }
        if (!band.isEmpty()) {
            int total = 0;
            for (BandMember member : band) {
                total += member.getBond();
            }
            stats.add("Standing with the band: " + standing(total / (float) band.size()));
            band.sort((a, b) -> Integer.compare(b.getBond(), a.getBond()));
            for (int i = 0; i < Math.min(MAX_MEMBERS_LISTED, band.size()); i++) {
                BandMember member = band.get(i);
                member.ensureName();
                stats.add("   " + member.getName().getString() + " - bond " + member.getBond()
                        + (member.isBaby() ? " (child)" : ""));
            }
            if (band.size() > MAX_MEMBERS_LISTED) {
                stats.add("   and " + (band.size() - MAX_MEMBERS_LISTED) + " more");
            }
        }
        java.util.List<String> remembered = dev.hominin.evolution.band.Remembrance.lines(player);
        if (!remembered.isEmpty()) {
            stats.add("");
            stats.add("Remembered - names your line carries:");
            stats.addAll(remembered);
        }
        stats.add("");
        var counters = data.getCriterionCounters();
        stats.add("Practised at running: " + Math.min(3, counters.getOrDefault("play_tag", 0)) + "/3"
                + "    at fighting: " + Math.min(3, counters.getOrDefault("play_wrestle", 0)) + "/3");

        List<String> titles = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        List<Integer> flags = new ArrayList<>();
        for (Skills.Skill skill : Skills.Skill.values()) {
            titles.add(skill.title());
            bodies.add(skill.about() + "\n" + skill.howTo() + "\n" + skill.effect());
            flags.add((Skills.knows(player, skill) ? 1 : 0) | (skill.carriesOver() ? 2 : 0));
        }
        PacketDistributor.sendToPlayer(player, new JournalPayload(stageName, stats, titles, bodies, flags));
    }

    private static String standing(float averageBond) {
        if (averageBond >= 5) {
            return "trusted - they would follow you anywhere";
        }
        if (averageBond >= 3) {
            return "well liked";
        }
        if (averageBond >= 1) {
            return "accepted";
        }
        return "tolerated - they do not know you yet";
    }

    /**
     * Each individual is one sex or the other. Stored as a plain counter, so it is drawn
     * again when you evolve: the descendant who wakes up is somebody new.
     */
    public static int gender(ServerPlayer player, PlayerEvolutionData data) {
        return data.getCriterionCounters().computeIfAbsent(GENDER, key -> 1 + player.getRandom().nextInt(2));
    }

    /** The body you are in now is this sex: waking as a member, or living as one for a while. */
    public static void setFemale(ServerPlayer player, boolean female) {
        player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters()
                .put(GENDER, female ? 2 : 1);
    }

    /** Skills earned before the journal existed are credited the first time it opens. */
    private static void backfill(ServerPlayer player, PlayerEvolutionData data) {
        boolean lomekwian = data.getUnlockedRecipes().stream().anyMatch(id -> id.getPath().contains("lomekwian"));
        if (lomekwian && !Skills.knows(player, Skills.Skill.LOMEKWIAN)) {
            data.getCriterionCounters().put(Skills.Skill.LOMEKWIAN.key(), 1);
        }
    }

    private Journal() {
    }
}
