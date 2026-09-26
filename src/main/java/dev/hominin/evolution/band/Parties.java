package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.guide.Alerts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Sending a party to another band, from The others: three of your band at least, more if you like, with a purpose -
 * a gift, a demand for tribute, a request for their ground, a message, a trade, or a raid - and whatever you give
 * them to carry. They walk there (the further, the longer), it goes how it goes, and they walk back with the news:
 * what they got, who is hurt, and - from a fight - who is not coming back. Two at most can die on one errand.
 */
public final class Parties {
    public static final int GIFT = 0;
    public static final int DEMAND = 1;
    public static final int ACCESS = 2;
    public static final int MESSAGE = 3;
    public static final int TRADE = 4;
    public static final int RAID = 5;
    public static final String[] INTENTS = {"Take them a gift", "Demand tribute", "Ask for their ground",
            "Send a message", "Ask for a trade", "Raid them"};

    private static final int MIN_PARTY = 3;
    private static final long ACCESS_TICKS = 5 * 24000L;

    private static final class Mission {
        final UUID player;
        final UUID band;
        final int intent;
        final List<UUID> members;
        final List<ItemStack> cargo;
        final List<Goods.Entry> wanted;
        final List<Integer> wantedCounts;
        final int option;
        @Nullable
        final UUID about;
        final BlockPos target;
        final long arriveAt;
        final long returnAt;
        boolean resolved;
        final List<ItemStack> haul = new ArrayList<>();
        String outcome = "";

        Mission(UUID player, UUID band, int intent, List<UUID> members, List<ItemStack> cargo, List<Goods.Entry> wanted,
                List<Integer> wantedCounts, int option, @Nullable UUID about, BlockPos target, long arriveAt, long returnAt) {
            this.player = player;
            this.band = band;
            this.intent = intent;
            this.members = members;
            this.cargo = cargo;
            this.wanted = wanted;
            this.wantedCounts = wantedCounts;
            this.option = option;
            this.about = about;
            this.target = target;
            this.arriveAt = arriveAt;
            this.returnAt = returnAt;
        }
    }

    private static final List<Mission> missions = new ArrayList<>();
    /** "player|band" -> the band they promised to help fight. */
    private static final Map<String, UUID> fightPledges = new HashMap<>();
    /** "player|band" -> until when they will join your hunt. */
    private static final Map<String, Long> huntPledges = new HashMap<>();

    // ------------------------------------------------------------ who is away

    @Nullable
    private static Mission missionOf(BandMember member) {
        for (Mission mission : missions) {
            if (mission.members.contains(member.getUUID())) {
                return mission;
            }
        }
        return null;
    }

    /** How many of this player's parties are out. */
    public static int awayFrom(ServerPlayer player) {
        int n = 0;
        for (Mission mission : missions) {
            if (mission.player.equals(player.getUUID())) {
                n++;
            }
        }
        return n;
    }

    public static boolean away(BandMember member) {
        return missionOf(member) != null || Haul.away(member);
    }

    /** Where someone on an errand is walking: there, then back to whoever sent them. */
    @Nullable
    public static BlockPos headingFor(BandMember member) {
        Mission mission = missionOf(member);
        if (mission == null) {
            return Haul.headingFor(member);
        }
        if (!mission.resolved) {
            return mission.target;
        }
        return member.leaderPlayer() != null ? member.leaderPlayer().blockPosition() : null;
    }

    // ------------------------------------------------------------ setting it up

    /** Grown, well members near you who could go. */
    private static List<BandMember> available(ServerPlayer player) {
        List<BandMember> list = new ArrayList<>();
        for (BandMember member : Band.ownNear(player, 32.0D)) {
            if (!member.isBaby() && !member.isInjured() && !member.isPregnant() && !away(member)) {
                list.add(member);
            }
        }
        list.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player)));
        return list;
    }

    /** Picked from The others: what they will carry, and how many go. */
    public static void open(ServerPlayer player, Bands.Record band, int intent) {
        if (intent < 0 || intent >= INTENTS.length || band.nomadic()) {
            return;
        }
        int max = available(player).size();
        List<Goods.Entry> left = intent == GIFT || intent == ACCESS || intent == TRADE || intent == RAID
                ? Goods.yours(player) : List.of();
        List<Goods.Entry> right = intent == TRADE ? Goods.theirs(player.serverLevel(), band, null) : List.of();
        List<String> options = new ArrayList<>();
        if (intent == MESSAGE) {
            for (Bands.Record other : Bands.all(player.serverLevel())) {
                if (other != band && !other.nomadic() && other.knownTo(player.getUUID())) {
                    options.add("Help us fight " + BandNames.capital(other.name));
                }
            }
            options.add("Join us on our next hunt");
        }
        String detail = switch (intent) {
            case GIFT -> "Pick what they take to " + band.name + ". The more it is worth to them, the more they will think of you.";
            case DEMAND -> "They go and lean on " + band.name + ". Strong enough, and they pay; if not, they may say no - or fight. "
                    + "More of you is safer.";
            case ACCESS -> "Pick what they offer " + band.name + " for five days on their ground.";
            case MESSAGE -> "Pick what they are to say. The button cycles through it.";
            case TRADE -> "Left: what they carry to offer. Right: what you want of " + band.name + "'s. A fair trade is taken.";
            default -> "Pick what they take with them - weapons count. Against " + band.name + ": up to two may not come back.";
        };
        if (max < MIN_PARTY) {
            detail = "You need " + MIN_PARTY + " of your band near you, grown and well, to send a party - you have " + max + ".";
        }
        Goods.open(player, Goods.PARTY, band, intent, INTENTS[intent] + " - " + BandNames.capital(band.name), detail, left,
                right, max, options);
    }

    /** Off they go. */
    public static void send(ServerPlayer player, Bands.Record band, int intent, int size, int option,
            List<Goods.Entry> left, List<Integer> leftCounts, List<Goods.Entry> right, List<Integer> rightCounts) {
        ServerLevel level = player.serverLevel();
        List<BandMember> free = available(player);
        if (free.size() < MIN_PARTY || size < MIN_PARTY) {
            player.displayClientMessage(Component.literal("You need " + MIN_PARTY + " of your band, grown and well, to send "
                    + "a party."), true);
            return;
        }
        List<BandMember> party = free.subList(0, Math.min(size, free.size()));
        List<ItemStack> cargo = new ArrayList<>();
        for (int i = 0; i < left.size() && i < leftCounts.size(); i++) {
            ItemStack taken = Goods.take(level, player, left.get(i), leftCounts.get(i));
            if (!taken.isEmpty()) {
                cargo.add(taken);
            }
        }
        UUID about = null;
        if (intent == MESSAGE) {
            int index = 0;
            for (Bands.Record other : Bands.all(level)) {
                if (other != band && !other.nomadic() && other.knownTo(player.getUUID())) {
                    if (index == option) {
                        about = other.id;
                    }
                    index++;
                }
            }
        }
        BlockPos target = Relations.whereIs(level, band);
        double distance = Math.sqrt(Bands.horizontal(target, player.blockPosition()));
        long travel = Math.max(400L, Math.min(6000L, (long) (distance * 20.0D / 3.0D)));
        long now = level.getGameTime();
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (BandMember member : party) {
            ids.add(member.getUUID());
            member.ensureName();
            names.add(member.getName().getString());
            member.setTarget(null);
        }
        missions.add(new Mission(player.getUUID(), band.id, intent, ids, cargo, new ArrayList<>(right),
                new ArrayList<>(rightCounts), option, about, target, now + travel, now + travel * 2));
        Relations.meet(player, band, "");
        player.sendSystemMessage(Component.literal(String.join(", ", names) + " set off for " + band.name + " - "
                + (int) distance + " blocks " + WildBands.bearingFrom(player, target) + ", "
                + INTENTS[intent].toLowerCase() + (cargo.isEmpty() ? "" : ", carrying " + ToolPiles.describe(cargo))
                + ". Back in about " + Math.max(1, travel * 2 / 1200L) + " minutes.").withStyle(ChatFormatting.AQUA));
    }

    // ------------------------------------------------------------ how it goes

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 13 || missions.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Iterator<Mission> it = missions.iterator();
        while (it.hasNext()) {
            Mission mission = it.next();
            if (!mission.player.equals(player.getUUID())) {
                continue;
            }
            Bands.Record band = Bands.get(level, mission.band);
            if (!mission.resolved && now >= mission.arriveAt) {
                mission.resolved = true;
                if (band == null) {
                    mission.outcome = "They found nobody there - the band is gone.";
                    mission.haul.addAll(mission.cargo);
                } else {
                    resolve(player, level, band, mission);
                }
            }
            if (mission.resolved && now >= mission.returnAt) {
                it.remove();
                homecoming(player, level, mission);
            }
        }
    }

    private static int strength(ServerLevel level, ServerPlayer player, Mission mission) {
        int strength = mission.members.size() * 5 + Presence.get(player) / 2;
        for (UUID id : mission.members) {
            if (level.getEntity(id) instanceof BandMember member) {
                strength += (member.bestWeaponRank() + 1) * 2;
            }
        }
        for (ItemStack stack : mission.cargo) {
            if (BandMember.isWeapon(stack) || stack.is(dev.hominin.evolution.ModTags.Items.STONE_TOOLS)) {
                strength += 3 * stack.getCount();
            }
        }
        return strength;
    }

    private static int defence(Bands.Record band) {
        return band.size * 4 + band.cohesion / 3 + band.presence * 5 / 6 - (band.desperation >= 4 ? 4 : 0);
    }

    private static void resolve(ServerPlayer player, ServerLevel level, Bands.Record band, Mission mission) {
        var random = player.getRandom();
        String them = BandNames.capital(band.name);
        // The party talks as well as the best talker in it.
        int talk = Negotiation.partyLevel(level, mission.members);
        switch (mission.intent) {
            case GIFT -> {
                int worth = 0;
                for (ItemStack stack : mission.cargo) {
                    worth += Trading.valueOf(stack, band.species) * stack.getCount();
                }
                worth += Math.round(worth * Negotiation.edge(talk));
                Relations.change(player, band, Math.min(12, 2 + worth / 6), "your party brought them gifts");
                if (worth >= 20 && band.desperation >= 3) {
                    band.desperation--;
                }
                mission.outcome = them + " took the gifts - " + (worth >= 20 ? "gladly." : "politely.");
            }
            case ACCESS -> {
                int worth = 0;
                for (ItemStack stack : mission.cargo) {
                    worth += Trading.valueOf(stack, band.species) * stack.getCount();
                }
                int standing = Relations.standing(player, band);
                int wanted = Math.round((12 + (standing < Relations.FRIENDLY ? 8 : 0) - band.desperation * 2)
                        * Negotiation.asking(talk));
                if (worth >= wanted) {
                    band.openTo.put(player.getUUID(), level.getGameTime() + ACCESS_TICKS);
                    Relations.change(player, band, 2, "you asked properly, and paid");
                    mission.outcome = them + " took what was offered: your band may use their ground for five days.";
                } else {
                    Relations.change(player, band, 1, "you asked for their ground");
                    mission.outcome = them + " kept the gifts, but their ground stays theirs. It was not enough.";
                }
            }
            case MESSAGE -> {
                int standing = Relations.standing(player, band);
                if (mission.about != null) {
                    Bands.Record enemy = Bands.get(level, mission.about);
                    if (enemy != null && standing >= Relations.FRIENDLY && !band.allies.contains(enemy.id)) {
                        fightPledges.put(player.getUUID() + "|" + band.id, enemy.id);
                        mission.outcome = them + " will stand with you against " + enemy.name + " - raid them, and they come.";
                    } else {
                        mission.outcome = them + " will not be drawn into a fight with " + (enemy == null ? "them" : enemy.name) + ".";
                    }
                } else if (standing >= Relations.NEUTRAL) {
                    huntPledges.put(player.getUUID() + "|" + band.id, level.getGameTime() + 24000L);
                    mission.outcome = them + " will send hunters on your next hunt, if it is today.";
                } else {
                    mission.outcome = them + " have no wish to hunt with you.";
                }
                Relations.change(player, band, 1, "you sent word");
            }
            case TRADE -> {
                int give = 0;
                for (ItemStack stack : mission.cargo) {
                    give += Trading.valueOf(stack, band.species) * stack.getCount();
                }
                int want = Goods.worth(mission.wanted, mission.wantedCounts, band.species);
                float ratio = (Relations.standing(player, band) >= Relations.FRIENDLY ? 1.0F : 1.3F) * Negotiation.asking(talk);
                if (band.desperation >= 4) {
                    ratio *= 0.8F;
                }
                if (want > 0 && give >= want * ratio) {
                    for (int i = 0; i < mission.wanted.size() && i < mission.wantedCounts.size(); i++) {
                        ItemStack got = Goods.take(level, null, mission.wanted.get(i), mission.wantedCounts.get(i));
                        if (!got.isEmpty()) {
                            mission.haul.add(got);
                        }
                    }
                    Relations.change(player, band, 1, "a fair trade");
                    mission.outcome = them + " took the trade.";
                } else {
                    mission.haul.addAll(mission.cargo);
                    mission.outcome = them + " would not give that for what you sent. The party brings it back.";
                }
            }
            case DEMAND -> {
                // A good talker makes paying up sound like the sensible thing.
                float roll = strength(level, player, mission) * (0.75F + random.nextFloat() * 0.5F) - defence(band)
                        + Negotiation.edge(talk) * 12.0F;
                Relations.revenge(player, band);
                if (roll > 0.0F) {
                    mission.haul.add(new ItemStack(ModItems.MEAT_CHUNK.get(), 3 + mission.members.size()));
                    Relations.change(player, band, -6, "your party made them pay");
                    Presence.add(player, 1, "a band paid your party");
                    mission.outcome = them + " looked at your party, and paid.";
                } else if (random.nextBoolean()) {
                    Relations.change(player, band, -4, "your party tried to make them pay");
                    mission.outcome = them + " told your party to go home - and they did.";
                } else {
                    Relations.change(player, band, -10, "your party fought them");
                    mission.outcome = them + " would not pay, and it came to a fight. "
                            + casualties(level, mission, Math.min(0.5F, 0.15F - roll / 100.0F));
                }
            }
            default -> {
                float roll = strength(level, player, mission) * (0.75F + random.nextFloat() * 0.5F) - defence(band);
                Relations.revenge(player, band);
                Relations.change(player, band, -15, "your party raided them");
                if (roll > 0.0F) {
                    mission.haul.addAll(ToolPiles.plunder(level, band.id, 2 + mission.members.size() / 2));
                    mission.haul.add(new ItemStack(ModItems.MEAT_CHUNK.get(), 4 + mission.members.size()));
                    band.cohesion = Math.max(0, band.cohesion - 6);
                    Presence.add(player, 2, "your party raided " + band.name);
                    mission.outcome = "The raid on " + band.name + " worked. " + casualties(level, mission, 0.08F);
                } else {
                    mission.outcome = them + " were ready. The raid failed. " + casualties(level, mission, 0.3F);
                }
            }
        }
        Bands.changed(level);
    }

    /** Who does not come back, and who comes back hurt. Two dead at most. */
    private static String casualties(ServerLevel level, Mission mission, float deadChance) {
        List<String> dead = new ArrayList<>();
        List<String> hurt = new ArrayList<>();
        var random = level.random;
        for (UUID id : new ArrayList<>(mission.members)) {
            if (!(level.getEntity(id) instanceof BandMember member)) {
                continue;
            }
            member.ensureName();
            if (dead.size() < 2 && random.nextFloat() < deadChance) {
                dead.add(member.getName().getString());
                mission.members.remove(id);
                member.kill();
            } else if (random.nextFloat() < 0.5F) {
                hurt.add(member.getName().getString());
                member.injure();
                member.setHealth(Math.max(2.0F, member.getHealth() * 0.4F));
            }
        }
        return (dead.isEmpty() ? "Nobody was killed." : String.join(" and ", dead) + (dead.size() == 1 ? " was" : " were")
                + " killed.") + (hurt.isEmpty() ? "" : " " + String.join(", ", hurt) + " came back hurt.");
    }

    private static void homecoming(ServerPlayer player, ServerLevel level, Mission mission) {
        for (UUID id : mission.members) {
            if (level.getEntity(id) instanceof BandMember member && member.distanceToSqr(player) > 48.0D * 48.0D) {
                BlockPos spot = Band.standingSpotNear(level, player.blockPosition(), 3 + player.getRandom().nextInt(3),
                        player.getRandom().nextFloat() * 6.2831855F);
                member.getNavigation().stop();
                member.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
            }
        }
        for (ItemStack stack : mission.haul) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        Alerts.urgent(player, Alerts.Kind.BAND, Component.literal("Your party is back from " + (Bands.get(level, mission.band)
                instanceof Bands.Record band ? band.name : "the others") + ". " + mission.outcome
                + (mission.haul.isEmpty() ? "" : " They bring " + ToolPiles.describe(mission.haul) + "."))
                .withStyle(ChatFormatting.AQUA));
    }

    // ------------------------------------------------------------ promises

    /** Whether this band promised to join your hunt today. */
    public static boolean pledgedHunt(ServerPlayer player, Bands.Record band) {
        return huntPledges.getOrDefault(player.getUUID() + "|" + band.id, 0L) > player.level().getGameTime();
    }

    /** Bands that promised to stand with you against this one - taken up now, once. */
    public static List<Bands.Record> pledgedAgainst(ServerPlayer player, Bands.Record target) {
        List<Bands.Record> list = new ArrayList<>();
        for (Bands.Record band : Bands.all(player.serverLevel())) {
            String key = player.getUUID() + "|" + band.id;
            if (target.id.equals(fightPledges.get(key))) {
                fightPledges.remove(key);
                list.add(band);
            }
        }
        return list;
    }

    private Parties() {
    }
}
