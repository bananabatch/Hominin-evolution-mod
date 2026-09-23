package dev.hominin.evolution.band;

import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.data.Ancestors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * What survives a species.
 *
 * <p>Evolving takes everything: the camp, the tools, the band, the body. The one thing that
 * can cross that gap is a name - said often enough, by enough people, that the children who
 * never met its owner go on saying it. So when a band is left behind in the deep past, the
 * one you were closest to is remembered, and somebody in the band that wakes around your
 * descendants is called after them.
 *
 * <p>Nobody in that new band knows who the name belonged to. You do.
 */
public final class Remembrance {
    /** How many remembered names have already been given to a new band. */
    private static final String CARRIED = EvolutionManager.SKILL_PREFIX + "names_carried";

    /** The bond a name is worth: they grow up being told they are named for somebody. */
    private static final int NAMESAKE_BOND = 2;

    /**
     * Called while the old band is still standing, at the moment of evolving: whoever you were
     * closest to is the one the name comes from - your mate first, then whoever thought most of
     * you, then whoever is there.
     */
    public static void keep(ServerPlayer player, @Nullable String species) {
        List<BandMember> band = Band.all(player);
        if (band.isEmpty()) {
            return;
        }
        BandMember closest = Mating.mateOf(player);
        if (closest == null || closest.isBaby()) {
            closest = null;
            for (BandMember member : band) {
                if (member.isBaby()) {
                    continue;
                }
                if (closest == null || member.getBond() > closest.getBond()) {
                    closest = member;
                }
            }
        }
        if (closest == null) {
            closest = band.get(0);
        }
        closest.ensureName();
        Ancestors ancestors = player.getData(Attachments.ANCESTORS);
        ancestors.remember(closest.getName().getString(), species == null ? "your own kind" : species);
        player.setData(Attachments.ANCESTORS, ancestors);
    }

    /**
     * Called as a new band forms around a descendant. If a name is waiting to be passed on, one
     * of them is carrying it - and thinks a little more of you for it, because they have been
     * told all their life who they were named for.
     */
    public static void carryInto(ServerPlayer player) {
        Ancestors ancestors = player.getData(Attachments.ANCESTORS);
        Ancestors.Ancestor ancestor = ancestors.last();
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        if (ancestor == null || counters.getOrDefault(CARRIED, 0) >= ancestors.size()) {
            return;
        }
        BandMember namesake = grownMember(player);
        if (namesake == null) {
            return;
        }
        counters.put(CARRIED, ancestors.size());
        namesake.setCustomName(Component.literal(ancestor.name()));
        namesake.addBond(NAMESAKE_BOND);
        player.sendSystemMessage(Component.literal("One of them is called " + ancestor.name()
                + ". Nobody here knows why - it is only a name their people use. You know why.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.literal("(" + ancestor.name() + " walked with you as "
                + ancestor.species() + ". The name came down.)").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Nullable
    private static BandMember grownMember(ServerPlayer player) {
        BandMember best = null;
        for (BandMember member : Band.all(player)) {
            if (member.isBaby()) {
                continue;
            }
            if (best == null || member.distanceToSqr(player) < best.distanceToSqr(player)) {
                best = member;
            }
        }
        return best;
    }

    /** The line, oldest first, for the journal. */
    public static List<String> lines(ServerPlayer player) {
        List<String> out = new java.util.ArrayList<>();
        for (Ancestors.Ancestor ancestor : player.getData(Attachments.ANCESTORS).line()) {
            out.add("   " + ancestor.name() + " - " + ancestor.species());
        }
        return out;
    }

    private Remembrance() {
    }
}
