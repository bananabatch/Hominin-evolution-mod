package dev.hominin.evolution.band;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What your own people think of the other bands - each their own mind, -10 to 10 - and of what you do about them.
 *
 * <p>An opinion is made by what happens: a band that trades fairly, sends gifts or comes to help is liked; one that
 * raids you, squeezes you for tribute or drives you off is not. Watching you deal with a band, a member who likes it
 * says so when you turn on it, and one who does not says so when you go soft on it.
 *
 * <p>And when times are hard, they have their own ideas. A hungry band with weapons looks at a band with food and
 * does the sum out loud - "We're hungry. We have spears. They have food. It's us or them." - or, at a band using your
 * ground for nothing, wants it made to pay.
 */
public final class Opinions {
    private static final String KEY = "HomininOpinions";
    private static final int MOST = 10;
    /** A push from one of them at most this often. */
    private static final int PUSH_TICKS = 5 * 60 * 20;

    private static CompoundTag tag(BandMember member) {
        CompoundTag data = member.getPersistentData();
        if (!data.contains(KEY)) {
            data.put(KEY, new CompoundTag());
        }
        return data.getCompound(KEY);
    }

    public static int of(BandMember member, Bands.Record band) {
        CompoundTag tag = tag(member);
        String key = band.id.toString();
        if (!tag.contains(key)) {
            // Everybody starts somewhere of their own.
            tag.putInt(key, member.getRandom().nextInt(5) - 2);
        }
        return tag.getInt(key);
    }

    public static void add(BandMember member, Bands.Record band, int delta) {
        tag(member).putInt(band.id.toString(), Math.max(-MOST, Math.min(MOST, of(member, band) + delta)));
    }

    /** In words, for a member's info. */
    public static String describe(BandMember member, Bands.Record band) {
        int o = of(member, band);
        return o >= 6 ? "likes " + band.name + " a lot" : o >= 2 ? "thinks well of " + band.name
                : o <= -6 ? "hates " + band.name : o <= -2 ? "has no time for " + band.name : "does not mind " + band.name;
    }

    // ------------------------------------------------------------ what you do about them

    /**
     * Standing with a band has changed by something you did. Those of your people who were there take it in: it moves
     * their own view a little the same way - and whoever feels strongly says so.
     */
    public static void judge(ServerPlayer player, Bands.Record band, int delta) {
        if (band.nomadic()) {
            return;
        }
        List<BandMember> there = Band.ownNear(player, 32.0D);
        boolean spoken = false;
        for (BandMember member : there) {
            if (member.isBaby()) {
                continue;
            }
            int before = of(member, band);
            if (member.getRandom().nextBoolean()) {
                add(member, band, Integer.signum(delta));
            }
            if (spoken || member.getRandom().nextInt(3) != 0) {
                continue;
            }
            String kind = delta < 0 ? (before >= 3 ? "opinion_against_friend" : before <= -3 ? "opinion_against_foe" : null)
                    : (before <= -3 ? "opinion_for_foe" : before >= 3 ? "opinion_for_friend" : null);
            if (kind != null) {
                Lines.say(member, kind, " (" + BandNames.capital(band.name) + ")");
                spoken = true;
                if (kind.equals("opinion_against_friend") && member.getRandom().nextInt(3) == 0) {
                    // Turning on people they liked costs you with them, a little.
                    member.addBond(-1);
                }
            }
        }
    }

    /** They raided you: nobody forgets that. */
    public static void raided(ServerPlayer player, Bands.Record band) {
        for (BandMember member : Band.all(player)) {
            add(member, band, -3);
        }
    }

    /** They came to help: remembered too. */
    public static void helped(ServerPlayer player, Bands.Record band) {
        for (BandMember member : Band.ownNear(player, 48.0D)) {
            add(member, band, 2);
        }
    }

    // ------------------------------------------------------------ their own ideas

    private static final String[] RAID = {"We're hungry. We have spears. {b} have food. It's us or them.",
            "{b} have meat drying on racks and we have nothing. We should take it.",
            "The little ones are hungry, and {b} are fat. You see where I'm going.",
            "Hit {b} tonight, while they sleep. We'd eat for days.",
            "Why are we starving when {b} have plenty? Take it."};
    private static final String[] TRIBUTE = {"{b} forage our ground and pay nothing. Make them pay.",
            "{b} owe us for that water. Go and tell them so.", "Why do we let {b} walk our land for free?",
            "Lean on {b}. They'll pay - they know what we are."};

    /** Every so often: somebody with a grudge and an empty belly says what they think should be done. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 1200 != 611 || player.isSpectator()) {
            return;
        }
        var counters = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int minute = (int) (player.level().getGameTime() / 1200L);
        if (minute < counters.getOrDefault("opinion_push_next", 0)) {
            return;
        }
        int desperation = Claims.ownDesperation(player);
        ServerLevel level = player.serverLevel();
        List<BandMember> near = Band.ownNear(player, 24.0D);
        if (near.isEmpty()) {
            return;
        }
        BandMember speaker = null;
        Bands.Record target = null;
        boolean raid = false;
        for (BandMember member : near) {
            if (member.isBaby()) {
                continue;
            }
            boolean hungry = member.isHungry() || desperation >= 3;
            for (Bands.Record band : Bands.all(level)) {
                if (band.nomadic() || !band.knownTo(player.getUUID())
                        || Relations.standing(player, band) >= Relations.ALLIED
                        || Bands.horizontal(band.home, player.blockPosition()) > 320.0D * 320.0D) {
                    continue;
                }
                int opinion = of(member, band);
                if (hungry && member.carriesWeapon() && opinion <= 0 && (desperation >= 3 || opinion <= -4)) {
                    speaker = member;
                    target = band;
                    raid = true;
                    break;
                }
                // A weaker band close by that they have no time for: make it pay for living next to us.
                if (opinion <= -2 && Relations.standing(player, band) < Relations.FRIENDLY
                        && band.presence < Presence.get(player)
                        && Bands.horizontal(band.home, dev.hominin.evolution.hunt.Predation.campOf(player)) < 200.0D * 200.0D) {
                    speaker = member;
                    target = band;
                }
            }
            if (raid) {
                break;
            }
        }
        if (speaker == null || target == null || player.getRandom().nextInt(raid ? 2 : 3) != 0) {
            return;
        }
        counters.put("opinion_push_next", minute + PUSH_TICKS / 1200);
        speaker.ensureName();
        String[] pool = raid ? RAID : TRIBUTE;
        String line = pool[player.getRandom().nextInt(pool.length)].replace("{b}", BandNames.capital(target.name));
        player.sendSystemMessage(Component.literal("<" + speaker.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(raid ? "  (they want a raid)" : "  (they want tribute demanded)")
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    @Nullable
    public static Bands.Record strongestFeeling(BandMember member, ServerLevel level) {
        Bands.Record best = null;
        int strongest = 1;
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || !tag(member).contains(band.id.toString())) {
                continue;
            }
            int o = Math.abs(of(member, band));
            if (o > strongest) {
                strongest = o;
                best = band;
            }
        }
        return best;
    }

    private Opinions() {
    }
}
