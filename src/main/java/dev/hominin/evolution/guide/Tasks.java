package dev.hominin.evolution.guide;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.BandNames;
import dev.hominin.evolution.band.Bands;
import dev.hominin.evolution.band.Cohesion;
import dev.hominin.evolution.survival.Afflictions;
import dev.hominin.evolution.survival.Drought;
import dev.hominin.evolution.survival.Seasons;
import dev.hominin.evolution.survival.Thirst;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The journal's Event log, "right now": everything that wants seeing to at this moment, worked out fresh each time
 * the journal opens - your own body first, then the band, then other bands, then the season. Each line carries its
 * {@link Alerts.Kind} code for its colour.
 */
public final class Tasks {
    private static final int LOW = 6;

    public static List<String> of(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<String> tasks = new ArrayList<>();
        // You.
        int water = Thirst.get(player);
        if (water <= LOW) {
            add(tasks, Alerts.Kind.NEED, "Water " + water + "/" + Thirst.MAX + " - drink soon.");
        }
        int food = player.getFoodData().getFoodLevel();
        if (food <= LOW) {
            add(tasks, Alerts.Kind.NEED, "Hunger " + food + "/20 - eat something.");
        }
        Afflictions.Affliction affliction = Afflictions.current(player);
        if (affliction != null) {
            add(tasks, Alerts.Kind.WARNING, affliction.label() + " - you heal only to " + affliction.hearts(player)
                    + " hearts for " + Afflictions.secondsLeft(player) + "s more.");
        }
        if (dev.hominin.evolution.survival.FoodIllness.has(player)) {
            add(tasks, Alerts.Kind.WARNING, "Ill from bad meat: " + dev.hominin.evolution.survival.FoodIllness.describe(player));
        }
        if (dev.hominin.evolution.survival.Kuru.has(player)) {
            add(tasks, Alerts.Kind.DANGER, "Kuru: " + dev.hominin.evolution.survival.Kuru.describe(player));
        }
        if (dev.hominin.evolution.band.Mating.isPregnant(player)) {
            add(tasks, Alerts.Kind.BAND, "You are pregnant - " + dev.hominin.evolution.band.Mating.describePregnancy(player)
                    + ".");
        }
        // The band.
        String need = dev.hominin.evolution.band.Needs.describe(player);
        if (need != null) {
            add(tasks, Alerts.Kind.NEED, need);
        }
        int hungry = 0;
        for (BandMember member : Band.all(player)) {
            if (member.isHungry() && !member.isBaby()) {
                hungry++;
            }
            if (member.isPregnant()) {
                member.ensureName();
                add(tasks, Alerts.Kind.BAND, member.getName().getString() + " is expecting - " + member.dueIn() + ".");
            }
        }
        if (hungry > 0) {
            add(tasks, Alerts.Kind.NEED, hungry + (hungry == 1 ? " of the band is" : " of the band are")
                    + " hungry - share food (H: Share).");
        }
        if (Bands.erectusOn(player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage())) {
            for (String project : dev.hominin.evolution.band.ErectusWork.describeProjects(level, player.getUUID())) {
                add(tasks, Alerts.Kind.BAND, project);
            }
            int bedless = dev.hominin.evolution.band.ErectusWork.bedless(player);
            if (bedless > 0) {
                add(tasks, Alerts.Kind.BAND, bedless + (bedless == 1 ? " of the band still sleeps" : " of the band still sleep")
                        + " in a nest - they cut thatch and hunt for hides to make bedding (a bed is 3 hides, 3 thatch and "
                        + "10 twine, made at the work station).");
            }
        }
        String pile = dev.hominin.evolution.band.SacredPile.describe(player);
        if (pile != null) {
            add(tasks, Alerts.Kind.BAND, pile);
        }
        int cohesion = Cohesion.get(player);
        if (cohesion < 20) {
            add(tasks, Alerts.Kind.WARNING, "Band cohesion " + cohesion + "/" + Cohesion.MAX
                    + " - they are losing faith in you." + (cohesion <= 10 ? " One more failing could end it." : ""));
        }
        // Other bands.
        String demand = dev.hominin.evolution.band.Relations.describeDemand(player);
        if (demand != null) {
            add(tasks, Alerts.Kind.DANGER, demand);
        }
        for (String claim : dev.hominin.evolution.band.Claims.describe(player)) {
            add(tasks, Alerts.Kind.WARNING, claim);
        }
        for (Bands.Record band : Bands.all(level)) {
            int owed = band.owed.getOrDefault(player.getUUID(), 0);
            if (owed > 0 && band.knownTo(player.getUUID())) {
                add(tasks, Alerts.Kind.WARNING, "You have taken from " + BandNames.capital(band.name) + "'s ground "
                        + owed + (owed == 1 ? " time" : " times") + " - at 3 they come for tribute.");
            }
        }
        // The season: always there, last.
        String day = Drought.isActive(level) ? " A dry day today." : Drought.isProsperousDay(level)
                ? " A prosperous day today." : "";
        int left = Seasons.daysLeft(level);
        add(tasks, Alerts.Kind.SEASON, Seasons.label(level) + ": " + left + (left == 1 ? " day" : " days") + " left." + day);
        return tasks;
    }

    private static void add(List<String> tasks, Alerts.Kind kind, String text) {
        tasks.add(kind.code() + "|" + text);
    }

    private Tasks() {
    }
}
