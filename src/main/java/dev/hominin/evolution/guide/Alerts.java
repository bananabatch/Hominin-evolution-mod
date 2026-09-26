package dev.hominin.evolution.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.network.AlertPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What cannot wait. Most of what happens goes to chat and scrolls away; an alert also goes across the top of the
 * screen, and into the journal's Event log, so nothing urgent is lost: a need in the band, something coming for
 * you, the season turning.
 *
 * <p>The same alert twice within a minute is said once - a warning that keeps firing should not bury the chat.
 */
public final class Alerts {
    public enum Kind {
        NEED("Need", ChatFormatting.GOLD),
        WARNING("Warning", ChatFormatting.RED),
        DANGER("Danger", ChatFormatting.DARK_RED),
        SEASON("Season", ChatFormatting.GREEN),
        BAND("Band", ChatFormatting.LIGHT_PURPLE);

        private final String label;
        private final ChatFormatting colour;

        Kind(String label, ChatFormatting colour) {
            this.label = label;
            this.colour = colour;
        }

        public String label() {
            return label;
        }

        /** The one-letter code the journal's lines carry, so the screen can colour them. */
        public char code() {
            return name().charAt(0);
        }
    }

    /** How many alerts the journal keeps, newest first. */
    private static final int KEPT = 30;
    private static final long REPEAT_GAP = 60 * 20L;

    private static final Map<String, Long> lastSaid = new HashMap<>();

    /** Urgent: in chat, across the top of the screen, and kept in the journal. */
    public static void urgent(Player player, Kind kind, Component message) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        String text = message.getString();
        long now = server.level().getGameTime();
        String key = server.getUUID() + "|" + text;
        if (now - lastSaid.getOrDefault(key, -REPEAT_GAP) < REPEAT_GAP) {
            return;
        }
        if (lastSaid.size() > 4096) {
            lastSaid.clear();
        }
        lastSaid.put(key, now);
        if (kind != Kind.SEASON) {
            // The band's needs, dangers and troubles are every leader's: its co-leaders hear them as well.
            for (ServerPlayer co : dev.hominin.evolution.band.Newcomers.coLeaders(server)) {
                urgent(co, kind, message);
            }
        }
        // Side by side, not nested: the message keeps its own style rather than taking the label's bold.
        server.sendSystemMessage(Component.empty()
                .append(Component.literal("[" + kind.label + "] ").withStyle(kind.colour, ChatFormatting.BOLD))
                .append(message));
        PacketDistributor.sendToPlayer(server, new AlertPayload(kind.ordinal(), text));
        // Kept on the player, through death and logging out: newest first.
        List<String> kept = new ArrayList<>();
        // The day of this age, not the world's: evolving starts the count over.
        long stamp = dev.hominin.evolution.survival.Drought.dayOf(server.level()) * 24000L
                + server.level().getDayTime() % 24000L;
        kept.add(kind.code() + "|" + stamp + "|" + text);
        for (String old : server.getData(dev.hominin.evolution.Attachments.ALERTS)) {
            if (kept.size() >= KEPT) {
                break;
            }
            kept.add(old);
        }
        server.setData(dev.hominin.evolution.Attachments.ALERTS, List.copyOf(kept));
    }

    public static void urgent(Player player, Kind kind, String text, ChatFormatting... style) {
        urgent(player, kind, Component.literal(text).withStyle(style));
    }

    /** The journal's record: "W|Day 3, evening - ...", newest first. */
    public static List<String> recent(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        for (String entry : player.getData(dev.hominin.evolution.Attachments.ALERTS)) {
            String[] parts = entry.split("\\|", 3);
            if (parts.length == 3) {
                long dayTime;
                try {
                    dayTime = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                lines.add(parts[0] + "|" + when(dayTime) + " - " + parts[2]);
            }
        }
        return lines;
    }

    /** "Day 3, evening". */
    public static String when(long dayTime) {
        long day = dayTime / 24000L + 1;
        long time = dayTime % 24000L;
        String part = time < 6000L ? "morning" : time < 12000L ? "afternoon" : time < 13800L ? "evening" : "night";
        return "Day " + day + ", " + part;
    }

    public static void forget(UUID player) {
        lastSaid.keySet().removeIf(key -> key.startsWith(player.toString()));
    }

    private Alerts() {
    }
}
