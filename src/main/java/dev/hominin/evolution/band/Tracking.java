package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.network.TrackPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Members you keep an eye on. Right-click a name in tribe stats and they are tracked: a line each in the corner of
 * the screen - which way and how far, their health, whether they are hungry, what they are doing, and their skills.
 * Up to four at once; right-click the name again to stop.
 */
public final class Tracking {
    public static final int ACTION_TRACK = 60;
    private static final int MOST = 4;

    private static final Map<String, String> DOING = Map.ofEntries(
            Map.entry("BuildHelpGoal", "building"), Map.entry("LabourGoal", "working"),
            Map.entry("ErectusCraftGoal", "making something"), Map.entry("CraftGoal", "making something"),
            Map.entry("TinkerGoal", "knapping"), Map.entry("GatherThatchGoal", "cutting thatch"),
            Map.entry("HideHuntGoal", "hunting for a hide"), Map.entry("ForageGoal", "foraging"),
            Map.entry("QuarryGoal", "looking for stone"), Map.entry("TermiteFishGoal", "fishing termites"),
            Map.entry("NestBuildGoal", "making a bed"), Map.entry("GroomGoal", "grooming"),
            Map.entry("StoreGoal", "at the store"), Map.entry("StockGoal", "at the pile"),
            Map.entry("ToolPileGoal", "at the tool pile"), Map.entry("ScavengeGoal", "scavenging"),
            Map.entry("SentryGoal", "keeping watch"), Map.entry("BatheGoal", "bathing"),
            Map.entry("PlayGoal", "playing"), Map.entry("WrestleGoal", "wrestling"),
            Map.entry("SharpenStickGoal", "sharpening a stick"), Map.entry("FetchGoal", "fetching for you"),
            Map.entry("MissionGoal", "away with a party"), Map.entry("GrieveGoal", "grieving"),
            Map.entry("FleeToTreeGoal", "fleeing"), Map.entry("FreezeGoal", "frozen with fear"),
            Map.entry("ExcursionGoal", "off on their own"), Map.entry("GatherItemsGoal", "picking something up"),
            Map.entry("ArmSelfGoal", "finding a weapon"), Map.entry("GuideGoal", "showing you the way"),
            Map.entry("SleepInNestGoal", "going to bed"), Map.entry("FollowLeaderGoal", "following you"));

    private static List<String> tracked(ServerPlayer player) {
        return player.getData(Attachments.TRACKED);
    }

    public static boolean isTracked(ServerPlayer player, BandMember member) {
        return tracked(player).contains(member.getUUID().toString());
    }

    /** A name right-clicked in tribe stats: tracked, or no longer. */
    public static void toggle(ServerPlayer player, int entityId) {
        if (!(player.level().getEntity(entityId) instanceof BandMember member) || !member.isLedBy(player)) {
            player.displayClientMessage(Component.literal("They are too far off to keep an eye on from here."), true);
            return;
        }
        member.ensureName();
        List<String> list = new ArrayList<>(tracked(player));
        String id = member.getUUID().toString();
        if (list.remove(id)) {
            player.displayClientMessage(Component.literal("No longer keeping an eye on " + member.getName().getString() + "."),
                    true);
        } else {
            if (list.size() >= MOST) {
                list.remove(0);
            }
            list.add(id);
            player.displayClientMessage(Component.literal("Keeping an eye on " + member.getName().getString() + " ("
                    + list.size() + "/" + MOST + "). Right-click their name again to stop.").withStyle(ChatFormatting.GOLD),
                    true);
        }
        player.setData(Attachments.TRACKED, List.copyOf(list));
        send(player);
    }

    /** Gone for good: dead, or left. */
    public static void forget(ServerPlayer player, UUID member) {
        List<String> list = new ArrayList<>(tracked(player));
        if (list.remove(member.toString())) {
            player.setData(Attachments.TRACKED, List.copyOf(list));
            send(player);
        }
    }

    /** Once a second. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 == 11 && !tracked(player).isEmpty()) {
            send(player);
        }
    }

    private static void send(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        for (String id : tracked(player)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (player.serverLevel().getEntity(uuid) instanceof BandMember member && member.isAlive()) {
                lines.add(line(player, member));
                lines.add(skills(member));
            } else {
                lines.add("? - too far off to know");
                lines.add("");
            }
        }
        PacketDistributor.sendToPlayer(player, new TrackPayload(lines));
    }

    private static String line(ServerPlayer player, BandMember member) {
        member.ensureName();
        double dx = member.getX() - player.getX();
        double dz = member.getZ() - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String[] compass = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        String way = distance < 3 ? "here" : distance + "m " + compass[Math.floorMod(
                Math.round((float) (Math.toDegrees(Math.atan2(-dx, dz)) / 45.0D)), 8)];
        return member.getName().getString() + " - " + way + " - " + Math.round(member.getHealth()) + "/"
                + Math.round(member.getMaxHealth()) + " hp" + (member.isHungry() ? ", hungry" : "")
                + (member.isInjured() ? ", injured" : "") + " - " + doing(member);
    }

    private static String skills(BandMember member) {
        return "  knap " + member.getKnapLevel() + " / hunt " + member.getHuntLevel() + " / talk "
                + member.getNegotiateLevel() + " / fight " + member.getFightSkill() + " / bond " + member.getBond();
    }

    /** What they are up to, in a word or two. */
    public static String doing(BandMember member) {
        if (member.isSleeping()) {
            return "asleep";
        }
        if (Parties.away(member)) {
            return "away with a party";
        }
        if (member.getTarget() != null && member.getTarget().isAlive()) {
            return (member.isHunting() ? "hunting " : "fighting ") + member.getTarget().getName().getString().toLowerCase();
        }
        for (String goal : member.runningGoals()) {
            String what = DOING.get(goal);
            if (what != null) {
                return what;
            }
        }
        return "idle";
    }

    private Tracking() {
    }
}
