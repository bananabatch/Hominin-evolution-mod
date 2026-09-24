package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What people say when you are about - your own band, and everyone else's.
 *
 * <p>Another band's people talk when you come near: about you, to you, among themselves. Their names are
 * not the gold of your own band's but the colour of where you stand with them - blue for allies, green for
 * friends, grey for strangers, red for a band that does not want you there - and what they say follows
 * the same line. Heard from a way off, you hear how far, and which way.
 *
 * <p>Your own band talk to you as well as they feel about you: somebody who hardly trusts you says so;
 * somebody whose bond with you is deep says that too.
 */
public final class Voices {
    /** Another band's people are heard this far off. */
    private static final double HEARD = 40.0D;
    /** Closer than this there is no need to say where they are. */
    private static final double CLOSE = 12.0D;
    private static final long BAND_GAP = 45 * 20L;
    private static final long OWN_GAP = 4 * 60 * 20L;

    private static final Map<String, Long> lastHeard = new HashMap<>();

    private static final String[] ALLIED = {"There they are! Come and sit with us.", "You are always welcome at our fire.",
            "We saw a big herd down by the water - we'll show you.", "Our young ones keep asking about you.",
            "Whatever you need, just say it.", "Your people and ours - one band, near enough.",
            "Stay close tonight. Two bands are safer than one.", "We kept some meat back for you."};
    private static final String[] FRIENDLY = {"Good to see you again.", "The water is low upstream - go easy on it.",
            "Trade later? We have good stone.", "Keep an eye out, the hyenas have been about.",
            "You are welcome here. Just don't take too much.", "Your band looks well fed. Good.",
            "Hunting today? Luck to you."};
    private static final String[] NEUTRAL = {"Who is that?", "Keep your distance, and we'll keep ours.",
            "They are watching us.", "What do they want?", "Strangers. Stay close to me.", "Don't stare. Let them pass.",
            "Is that all of them?"};
    private static final String[] UNFRIENDLY = {"You again. Keep walking.", "This isn't your ground.",
            "Watch them. They take things.", "Nobody asked you here.", "Go back to your own water.",
            "If they come any closer, we drive them off."};
    private static final String[] HOSTILE = {"Thieves! Get away from here!", "We remember what you did.",
            "Come one step closer. Go on.", "Next time we take everything you have.",
            "Look at them. Weak. We could take it all.", "You won't sleep easy tonight."};
    private static final String[] HARD = {"There's not enough for us, never mind them.",
            "Dry days make people desperate. Watch them.", "Nobody shares in a season like this."};
    private static final String[] QUARREL = {"That was mine! Put it back!", "Stop taking the best pieces for yourself!",
            "Who ate the last of the meat?", "Why do we even follow you?", "Do it yourself, then."};
    private static final String[] PROUD = {"Nothing hunts near our camp. Nothing dares.",
            "The cats know better than to come here.", "Let them come. We are ready."};
    private static final String[] TROOP = {"A Paranthropus chews, and stares at you without stopping.",
            "One of the Paranthropus grunts, and the rest look up.", "The Paranthropus troop crunches through a mouthful of roots.",
            "A big Paranthropus male turns his back on you, pointedly."};

    private static final String[] COLD = {"Hm.", "You again.", "I do my share. Don't look at me like that.",
            "Why should I listen to you?", "Some leader.", "I don't need you watching me."};
    private static final String[] EASY = {"Where are we going next?", "I found good roots back there.",
            "Stay where I can see you.", "My feet ache. Yours?", "Quiet today. I don't trust quiet."};
    private static final String[] WARM = {"Glad you're with us.", "I'll watch your back today.",
            "You picked a good spot last night.", "Want me to carry something?", "I like it when you lead."};
    private static final String[] CLOSE_BOND = {"I would follow you anywhere. You know that.", "You're the best of us.",
            "I saved you the good part. Don't tell the others.", "Whatever comes, we face it together.",
            "When I'm old I'll still be listening to you."};

    /** Every five seconds per player. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 100 != 43 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        others(player, level, now);
        own(player, now);
    }

    // ------------------------------------------------------------ other bands

    private static void others(ServerPlayer player, ServerLevel level, long now) {
        if (player.getRandom().nextInt(3) != 0) {
            return;
        }
        List<BandMember> heard = level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(HEARD),
                m -> m.isAlive() && m.isWild() && !m.isBaby() && !m.isGuestOf(player) && m.getBandId() != null);
        if (heard.isEmpty()) {
            return;
        }
        BandMember speaker = heard.get(player.getRandom().nextInt(heard.size()));
        String key = player.getUUID() + "|" + speaker.getBandId();
        if (now - lastHeard.getOrDefault(key, -99999L) < BAND_GAP) {
            return;
        }
        if (lastHeard.size() > 2048) {
            lastHeard.clear();
        }
        lastHeard.put(key, now);
        Bands.Record band = Bands.get(level, speaker.getBandId());
        speaker.ensureName();
        if (Paranthropus.is(speaker)) {
            player.sendSystemMessage(Component.literal(Lines.pickFrom("troop|" + player.getUUID(), List.of(TROOP),
                    player.getRandom()) + where(player, speaker)).withStyle(ChatFormatting.GRAY));
            return;
        }
        int standing = band == null ? Relations.NEUTRAL : Relations.standing(player, band);
        List<String> pool = new ArrayList<>(List.of(standing >= Relations.ALLIED ? ALLIED : standing >= Relations.FRIENDLY
                ? FRIENDLY : standing > Relations.UNFRIENDLY ? NEUTRAL : standing > Relations.HOSTILE ? UNFRIENDLY : HOSTILE));
        boolean hard = dev.hominin.evolution.survival.Seasons.strained(level);
        if (hard && standing < Relations.FRIENDLY) {
            pool.addAll(List.of(HARD));
        }
        if (band != null && band.presence >= 35 && standing < Relations.FRIENDLY) {
            pool.addAll(List.of(PROUD));
        }
        boolean quarrel = band != null && band.cohesion < 20 && player.getRandom().nextInt(3) == 0;
        String line = Lines.pickFrom("voice|" + player.getUUID(), quarrel ? List.of(QUARREL) : pool, player.getRandom());
        MutableComponent name = Component.literal(speaker.getName().getString()
                + (band != null ? " of " + BandNames.capital(band.name) : "")).withStyle(colour(standing));
        player.sendSystemMessage(Component.literal("<").withStyle(colour(standing)).append(name)
                .append(Component.literal(">").withStyle(colour(standing)))
                .append(Component.literal(where(player, speaker)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" " + (quarrel ? "(to their own) " : "")).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(line).withStyle(quarrel ? ChatFormatting.GRAY : ChatFormatting.WHITE)));
    }

    /** Blue for allies, green for friends, grey for strangers, red for bad blood. */
    public static ChatFormatting colour(int standing) {
        return standing >= Relations.ALLIED ? ChatFormatting.AQUA : standing >= Relations.FRIENDLY ? ChatFormatting.GREEN
                : standing > Relations.UNFRIENDLY ? ChatFormatting.GRAY : standing > Relations.HOSTILE ? ChatFormatting.RED
                : ChatFormatting.DARK_RED;
    }

    /** " (28 blocks NE)", or nothing when they are right here. */
    private static String where(ServerPlayer player, BandMember speaker) {
        double distance = Math.sqrt(speaker.distanceToSqr(player));
        if (distance < CLOSE) {
            return "";
        }
        return " (" + (int) distance + " blocks " + WildBands.bearingFrom(player, speaker.blockPosition()) + ")";
    }

    // ------------------------------------------------------------ your own band

    private static void own(ServerPlayer player, long now) {
        String key = player.getUUID() + "|own";
        if (now - lastHeard.getOrDefault(key, -99999L) < OWN_GAP || player.getRandom().nextInt(4) != 0) {
            return;
        }
        List<BandMember> near = Band.ownNear(player, 12.0D);
        near.removeIf(m -> m.isBaby() || m.inDanger() || m.getTarget() != null);
        if (near.isEmpty()) {
            return;
        }
        lastHeard.put(key, now);
        BandMember speaker = near.get(player.getRandom().nextInt(near.size()));
        String line = lineFor(player, speaker);
        if (line == null) {
            return;
        }
        speaker.ensureName();
        player.sendSystemMessage(Component.literal("<" + speaker.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    /** What this member says to you, by how they feel about you. */
    @Nullable
    private static String lineFor(ServerPlayer player, BandMember member) {
        int bond = member.getBond();
        String[] pool = bond <= 0 ? COLD : bond < Wants.GIFT_BOND ? EASY : bond < 6 ? WARM : CLOSE_BOND;
        if (member.isAntisocial() && bond < 6) {
            pool = COLD;
        }
        return Lines.pickFrom("bond|" + player.getUUID(), List.of(pool), player.getRandom());
    }

    public static void forget(UUID player) {
        lastHeard.keySet().removeIf(key -> key.startsWith(player.toString()));
    }

    private Voices() {
    }
}
