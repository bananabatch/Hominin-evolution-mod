package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.network.GiftStockPayload;
import dev.hominin.evolution.network.OthersActionPayload;
import dev.hominin.evolution.network.OthersPayload;
import dev.hominin.evolution.survival.Seasons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Where you stand with the other bands: a standing with each, 0 to 50, the way cohesion is with your own.
 *
 * <ul>
 * <li><b>10 and under - hostile.</b> They will take advantage of you when things go badly: at night, in
 * a dry season, when your band is thin, they come raiding.</li>
 * <li><b>20 and under - unfriendly.</b> Catch you on their ground and they come straight at you: leave,
 * or pay, or be driven off.</li>
 * <li><b>30 - neutral.</b> Where every band starts, unless it saw you taking from its ground before it
 * ever met you.</li>
 * <li><b>35 to 44 - friendly.</b> Welcome on their ground, their water and stone yours to use - except in
 * hard times, when only friends of 40 and up are. They will travel with you.</li>
 * <li><b>45 and up - allies.</b> They stand with you against other bands, join you when you go after big
 * game on their ground, come when something hunts you there, and now and then bring you something.</li>
 * </ul>
 *
 * <p>Standing moves with what you do: taking from their ground unasked costs it, trades and gifts earn
 * it, killing a predator beside them earns it, striking one of them costs a great deal. From erectus on,
 * nobody is friendly to a stranger: travelling together has to be earned.
 */
public final class Relations {
    public static final int MAX = 50;
    public static final int HOSTILE = 10;
    public static final int UNFRIENDLY = 20;
    public static final int NEUTRAL = 30;
    public static final int FRIENDLY = 35;
    public static final int ALLIED = 45;

    /** Members this close see what you do. */
    private static final double SIGHT = 64.0D;
    /** Close enough to deal with face to face. */
    private static final double NEAR = 32.0D;
    private static final long DEMAND_TICKS = 30 * 20L;
    private static final long RAID_GAP = 36000L;
    /** The most standing a day of trades and gifts can buy with one band. */
    private static final int DAILY_GAIN = 12;
    /** How long a raid of yours on another band lasts before your people give it up. */
    private static final long RAID_TICKS = 60 * 20L;

    private static final Map<String, Long> cooldowns = new HashMap<>();
    private static final Map<String, Integer> gainedToday = new HashMap<>();

    // ------------------------------------------------------------ reading it

    public static int standing(ServerPlayer player, Bands.Record band) {
        return band.standing.getOrDefault(player.getUUID(), NEUTRAL);
    }

    public static String tier(int standing) {
        return standing <= HOSTILE ? "hostile - they would take everything you have"
                : standing <= UNFRIENDLY ? "unfriendly - they chase you off their ground"
                : standing < FRIENDLY ? "neutral"
                : standing < ALLIED ? "friendly - welcome on their ground" : "allies - they would stand with you";
    }

    private static ChatFormatting colour(int standing) {
        return standing <= HOSTILE ? ChatFormatting.DARK_RED : standing <= UNFRIENDLY ? ChatFormatting.RED
                : standing < FRIENDLY ? ChatFormatting.GRAY : standing < ALLIED ? ChatFormatting.GREEN
                : ChatFormatting.AQUA;
    }

    @Nullable
    private static Bands.Record bandOf(BandMember member) {
        return member.level() instanceof ServerLevel level ? Bands.get(level, member.getBandId()) : null;
    }

    private static boolean erectusOn(ResourceLocation stage) {
        String era = stage.getPath();
        return !era.startsWith("australopithecus") && !era.equals("ardipithecus") && !era.equals("homo_habilis")
                && !era.equals("homo_rudolfensis");
    }

    // ------------------------------------------------------------ meeting

    /**
     * The player comes to know a band - hears it, sees it, is told of it. If it had already watched them
     * take from its ground, it starts out thinking less of them.
     */
    public static void meet(ServerPlayer player, Bands.Record band, String how) {
        meet(player, band, how, false);
    }

    private static void meet(ServerPlayer player, Bands.Record band, String how, boolean quiet) {
        if (!band.known.add(player.getUUID())) {
            return;
        }
        int trespass = band.trespass.getOrDefault(player.getUUID(), 0);
        band.trespass.remove(player.getUUID());
        int rumours = band.rumours.getOrDefault(player.getUUID(), 0);
        band.rumours.remove(player.getUUID());
        int start = Math.max(UNFRIENDLY - 2, NEUTRAL - trespass * 3 - rumours * 3);
        band.standing.put(player.getUUID(), start);
        Bands.changed(player.serverLevel());
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.OTHER_BANDS);
        if (quiet) {
            return;
        }
        MutableComponent line = Component.literal(how + " You know them now: " + band.name + " - "
                + (band.nomadic() ? "a Paranthropus troop, always moving on" : speciesName(band.species) + ", " + band.size
                        + " of them") + ".").withStyle(ChatFormatting.GOLD);
        if (rumours > 0 && !band.nomadic()) {
            line.append(Component.literal(" Someone who knew you got here first - they have heard things about you. "
                    + "(Standing " + start + ")").withStyle(ChatFormatting.RED));
        } else if (trespass > 0 && !band.nomadic()) {
            line.append(Component.literal(" They had already watched you taking from their ground, and think less of you "
                    + "for it. (Standing " + start + ")").withStyle(ChatFormatting.RED));
        }
        line.append(Component.literal(" ").append(leadLink(band)));
        player.sendSystemMessage(line);
    }

    public static String speciesName(ResourceLocation species) {
        return switch (species.getPath()) {
            case "australopithecus" -> "Australopithecus";
            case "australopithecus_anamensis" -> "Australopithecus anamensis";
            case "homo_habilis" -> "Homo habilis";
            case "homo_rudolfensis" -> "Homo rudolfensis";
            case "homo_erectus" -> "Homo erectus";
            case "homo_ergaster" -> "Homo ergaster";
            case "homo_heidelbergensis" -> "Homo heidelbergensis";
            case "paranthropus_boisei" -> "Paranthropus";
            default -> species.getPath().replace('_', ' ');
        };
    }

    /** "[Lead me there]" - one click and the pointer at the top of the screen leads the way. */
    public static Component leadLink(Bands.Record band) {
        return Component.literal("[Lead me there]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hominin lead band " + band.id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Follow the pointer at the top of your screen to " + band.name + "."))));
    }

    /** Standing moves: said once, quietly, and louder when it crosses into a new tier. */
    public static void change(ServerPlayer player, Bands.Record band, int delta, String why) {
        if (delta == 0) {
            return;
        }
        if (!band.knownTo(player.getUUID())) {
            meet(player, band, "");
        }
        int before = standing(player, band);
        int after = Mth.clamp(before + delta, 0, MAX);
        if (after == before) {
            return;
        }
        band.standing.put(player.getUUID(), after);
        Bands.changed(player.serverLevel());
        player.displayClientMessage(Component.literal(BandNames.capital(band.name) + ": standing " + (delta > 0 ? "+" : "")
                + delta + " (" + why + ") - " + after + "/" + MAX).withStyle(colour(after)), true);
        int[] lines = {HOSTILE, UNFRIENDLY, NEUTRAL - 1, FRIENDLY - 1, ALLIED - 1};
        for (int line : lines) {
            boolean up = before <= line && after > line;
            boolean down = before > line && after <= line;
            if (up || down) {
                player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " are " + tier(after)
                        + " now. (Standing " + after + "/" + MAX + ")").withStyle(colour(after)));
                break;
            }
        }
    }

    // ------------------------------------------------------------ their ground

    /** Friends are welcome on a band's ground; in hard times only good friends. */
    public static boolean welcomes(ServerPlayer player, Bands.Record band) {
        int standing = standing(player, band);
        if (standing >= ALLIED) {
            return true;
        }
        return standing >= FRIENDLY && (!Seasons.strained(player.level()) || standing >= 40);
    }

    private static int membersNear(ServerLevel level, Bands.Record band, LivingEntity around, double radius) {
        return level.getEntitiesOfClass(BandMember.class, around.getBoundingBox().inflate(radius),
                m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby()).size();
    }

    /**
     * The player foraged, drank or took stone here. If this is a band's ground, and one of them saw - or
     * found the signs later - it counts against the player: before meeting them, as a bad first
     * impression; after, as standing lost.
     */
    public static void usedResource(ServerPlayer player, BlockPos where) {
        ServerLevel level = player.serverLevel();
        Bands.Record band = Bands.groundAt(level, where);
        if (band == null) {
            return;
        }
        boolean seen = membersNear(level, band, player, SIGHT) > 0;
        if (!seen && player.getRandom().nextFloat() > 0.25F) {
            return;
        }
        String key = player.getUUID() + "/use/" + band.id;
        long now = level.getGameTime();
        if (now < cooldowns.getOrDefault(key, 0L)) {
            return;
        }
        cooldowns.put(key, now + 600L);
        if (!band.knownTo(player.getUUID())) {
            int trespass = band.trespass.merge(player.getUUID(), 1, Integer::sum);
            Bands.changed(level);
            if (seen) {
                meet(player, band, "Somebody has been watching you take from their ground.");
            } else if (trespass >= 3) {
                player.displayClientMessage(Component.literal("There are signs of people here - somebody's ground.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        if (welcomes(player, band)) {
            return;
        }
        change(player, band, -1, "you took from their ground");
        if (seen) {
            BandMember speaker = nearestOf(level, band, player, SIGHT);
            if (speaker != null && player.getRandom().nextInt(3) == 0) {
                speaker.ensureName();
                boolean hard = Seasons.strained(level);
                say(player, speaker, hard ? "There is barely enough here for us. Take no more of it."
                        : player.getRandom().nextBoolean() ? "This is our water. You drink it, you owe us for it."
                        : "Everything here is ours. Go and find your own.");
            }
        }
    }

    // ------------------------------------------------------------ travelling together

    /** Whether this band will walk with the player today. From erectus on, only friends will. */
    public static boolean willTravel(ServerPlayer player, BandMember member) {
        Bands.Record band = bandOf(member);
        if (band == null) {
            return true;
        }
        int standing = standing(player, band);
        int needed = erectusOn(member.getStage()) ? FRIENDLY : NEUTRAL;
        if (Seasons.strained(player.level())) {
            needed = Math.max(needed, 40);
        }
        return standing >= needed;
    }

    public static String travelRefusal(ServerPlayer player, BandMember member) {
        Bands.Record band = bandOf(member);
        int standing = band == null ? NEUTRAL : standing(player, band);
        boolean strained = Seasons.strained(player.level());
        return (band == null ? "They" : BandNames.capital(band.name)) + " will not walk with a band they "
                + (standing < NEUTRAL ? "do not trust." : "hardly know.")
                + " (Standing " + standing + " - they need " + (strained ? 40 : erectusOn(member.getStage()) ? FRIENDLY : NEUTRAL)
                + (strained ? " in hard times" : "") + ". Trade and give to earn it.)";
    }

    public static void travelled(ServerPlayer player, BandMember member) {
        Bands.Record band = bandOf(member);
        if (band != null) {
            earn(player, band, 2, "you travelled together");
        }
    }

    // ------------------------------------------------------------ trades, gifts and help

    /** Standing earned by giving: a day's worth at most, per band - it cannot be bought all at once. */
    private static void earn(ServerPlayer player, Bands.Record band, int amount, String why) {
        String key = player.getUUID() + "/" + band.id + "/" + player.level().getDayTime() / 24000L;
        int gained = gainedToday.getOrDefault(key, 0);
        int allowed = Math.min(amount, DAILY_GAIN - gained);
        if (gainedToday.size() > 1024) {
            gainedToday.clear();
        }
        if (allowed <= 0) {
            player.displayClientMessage(Component.literal(BandNames.capital(band.name)
                    + " are pleased - but it takes more than a day to earn a band's trust.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        gainedToday.put(key, gained + allowed);
        change(player, band, allowed, why);
    }

    /** A fair exchange: something worth having earns a little. */
    public static void traded(ServerPlayer player, BandMember member, ItemStack taken) {
        Bands.Record band = bandOf(member);
        if (band != null) {
            earn(player, band, Trading.tierOf(taken, member.getStage()) >= 3 ? 3 : 2, "a fair trade");
        }
    }

    /** A predator killed near a band's people - they saw who did it. */
    public static void helpedAgainst(ServerPlayer player, LivingEntity predator) {
        ServerLevel level = player.serverLevel();
        for (Bands.Record band : Bands.all(level)) {
            if (membersNear(level, band, predator, NEAR) > 0) {
                earn(player, band, 3, "you killed a " + predator.getName().getString().toLowerCase() + " beside them");
            }
        }
    }

    /**
     * Any hurt to a band member. A player - or their band - striking another band's people costs a great
     * deal; and a raiding party that is fought often breaks and runs.
     */
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof BandMember member) || !member.isWild()
                || !(member.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer player = event.getSource().getEntity() instanceof ServerPlayer p ? p
                : event.getSource().getEntity() instanceof BandMember own && own.leaderPlayer() instanceof ServerPlayer lead ? lead
                : null;
        if (player == null) {
            return;
        }
        Bands.Record band = bandOf(member);
        if (band == null) {
            return;
        }
        Demand demand = demands.get(player.getUUID());
        if (demand != null && demand.raid() && demand.band().equals(band.id)) {
            // Raiders who meet a band that holds together break sooner.
            float breaks = 0.35F + (Cohesion.get(player) - 25) / 100.0F + (Presence.get(player) - 20) / 100.0F;
            if (member.getRandom().nextFloat() < Mth.clamp(breaks, 0.2F, 0.8F)) {
                endRaid(player, level, band, true);
                return;
            }
        }
        RaidOn ours = raidsOn.get(player.getUUID());
        if (ours != null && ours.band().equals(band.id)) {
            raidBlow(player, level, band, member, event.getAmount() >= member.getHealth());
            return;
        }
        String key = player.getUUID() + "/struck/" + band.id;
        if (level.getGameTime() < cooldowns.getOrDefault(key, 0L)) {
            return;
        }
        cooldowns.put(key, level.getGameTime() + 600L);
        if (demand == null || !demand.band().equals(band.id)) {
            change(player, band, band.nomadic() ? -5 : -8, "you struck one of them");
        }
    }

    /** An ally's ground: when you go after big game there, they come too; when something hunts you, they come. */
    public static void alliesJoin(ServerPlayer player, LivingEntity target, boolean hunting) {
        ServerLevel level = player.serverLevel();
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || standing(player, band) < ALLIED || !band.holds(player.blockPosition())) {
                continue;
            }
            for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(48.0D),
                    m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby())) {
                member.setTarget(target);
            }
            String key = player.getUUID() + "/allies/" + band.id;
            if (level.getGameTime() >= cooldowns.getOrDefault(key, 0L)) {
                cooldowns.put(key, level.getGameTime() + 1200L);
                player.displayClientMessage(Component.literal(BandNames.capital(band.name)
                        + (hunting ? " join the hunt." : " come running.")).withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    // ------------------------------------------------------------ gifts

    private static void sendGiftStock(ServerPlayer player, Bands.Record band) {
        if (membersNear(player.serverLevel(), band, player, NEAR) == 0) {
            say(player, "None of " + band.name + " are near enough to hand anything to.");
            return;
        }
        List<Integer> sources = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        List<String> owners = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (!stack.isEmpty()) {
                sources.add(-1);
                slots.add(slot);
                stacks.add(stack.copy());
                owners.add("You");
            }
        }
        for (BandMember member : Band.ownNear(player, 16.0D)) {
            member.ensureName();
            SimpleContainer pack = member.getInventory();
            for (int slot = 0; slot < pack.getContainerSize(); slot++) {
                ItemStack stack = pack.getItem(slot);
                if (!stack.isEmpty()) {
                    sources.add(member.getId());
                    slots.add(slot);
                    stacks.add(stack.copy());
                    owners.add(member.getName().getString());
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new GiftStockPayload(band.id.toString(), band.name, sources, slots, stacks, owners));
    }

    /** The gift handed over: each thing checked again, taken from whoever held it, and weighed. */
    public static void giveGift(ServerPlayer player, String bandId, List<Integer> sources, List<Integer> slots,
            List<Integer> counts) {
        ServerLevel level = player.serverLevel();
        Bands.Record band = recordFor(level, bandId);
        if (band == null || sources.size() != slots.size() || sources.size() != counts.size() || sources.isEmpty()) {
            return;
        }
        BandMember receiver = nearestOf(level, band, player, NEAR);
        if (receiver == null) {
            say(player, "None of " + band.name + " are near enough to hand anything to.");
            return;
        }
        int worth = 0;
        int count = 0;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            ItemStack stack;
            int source = sources.get(i);
            int slot = slots.get(i);
            if (source < 0) {
                if (slot < 0 || slot >= player.getInventory().items.size()) {
                    continue;
                }
                stack = player.getInventory().items.get(slot);
            } else if (level.getEntity(source) instanceof BandMember own && own.isLedBy(player)
                    && own.distanceToSqr(player) < 24.0D * 24.0D && slot >= 0 && slot < own.getInventory().getContainerSize()) {
                stack = own.getInventory().getItem(slot);
            } else {
                continue;
            }
            if (stack.isEmpty() || counts.get(i) <= 0) {
                continue;
            }
            ItemStack given = stack.split(Math.min(counts.get(i), stack.getCount()));
            count += given.getCount();
            if (given.has(DataComponents.FOOD)) {
                // Food is what a band needs most, and every mouthful of it counts.
                var food = given.get(DataComponents.FOOD);
                worth += given.getCount() * (food != null && food.nutrition() >= 4 ? 3 : 2);
            } else {
                worth += Math.max(1, Trading.tierOf(given, receiver.getStage())) * Math.min(4, given.getCount());
            }
            names.add(given.getCount() + " " + given.getHoverName().getString().toLowerCase());
            receiver.addToInventory(given);
        }
        if (count == 0) {
            return;
        }
        receiver.ensureName();
        receiver.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        player.sendSystemMessage(Component.literal("You give " + band.name + " " + String.join(", ", names)
                + ". " + receiver.getName().getString() + " takes it.").withStyle(ChatFormatting.GREEN));
        worth = Psychopaths.talkUpGift(player, worth);
        earn(player, band, Math.max(1, worth / 3), "a gift");
        Demand demand = demands.get(player.getUUID());
        if (demand != null && demand.band().equals(band.id) && worth >= 4) {
            settle(player, level, band, "The gift will do. They let it go.");
        }
    }

    // ------------------------------------------------------------ demands: ransom, and raids

    /** A band wants something from the player, now: to be paid off their ground, or paid not to raid. */
    private record Demand(UUID band, long until, boolean raid, boolean spoken) {
    }

    private static final Map<UUID, Demand> demands = new HashMap<>();

    static boolean hasDemand(ServerPlayer player) {
        return demands.containsKey(player.getUUID());
    }

    /** A band wants something now; on a raid, it is theirs to take if nobody pays or stops them. */
    static void demand(ServerPlayer player, Bands.Record band, boolean raid) {
        demands.put(player.getUUID(), new Demand(band.id, player.level().getGameTime() + DEMAND_TICKS, raid, true));
    }

    /** Raiders set out now: they walk in, and say what they want when they reach you. */
    static void raidNow(ServerPlayer player, Bands.Record band) {
        demands.put(player.getUUID(), new Demand(band.id, player.level().getGameTime() + DEMAND_TICKS * 4, true, false));
    }

    static void clearDemand(ServerPlayer player) {
        demands.remove(player.getUUID());
    }

    static void settleDemand(ServerPlayer player, Bands.Record band, String how) {
        settle(player, player.serverLevel(), band, how);
        sendHome(player.serverLevel(), band, player);
    }

    static void sendHomeFrom(ServerPlayer player, Bands.Record band) {
        sendHome(player.serverLevel(), band, player);
    }

    /** You would not pay: they come at you, and your band stands with you. Break them and it is over. */
    static void fight(ServerPlayer player, Bands.Record band) {
        ServerLevel level = player.serverLevel();
        demands.put(player.getUUID(), new Demand(band.id, level.getGameTime() + DEMAND_TICKS * 2, true, true));
        List<BandMember> theirs = level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(48.0D),
                m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby());
        for (BandMember member : theirs) {
            member.defendAgainst(player);
        }
        Claims.gangFights(player, level);
        List<BandMember> ours = Band.ownNear(player, 24.0D);
        ours.removeIf(BandMember::isBaby);
        for (int i = 0; i < ours.size() && !theirs.isEmpty(); i++) {
            ours.get(i).defendAgainst(theirs.get(i % theirs.size()));
        }
    }

    public static boolean ransomDemanded(ServerPlayer player, UUID band) {
        Demand demand = demands.get(player.getUUID());
        return demand != null && demand.band().equals(band);
    }

    /** Pays whoever is demanding: food first, else good stone. */
    public static void payRansom(ServerPlayer player, Bands.Record band) {
        if (!ransomDemanded(player, band.id)) {
            say(player, BandNames.capital(band.name) + " are not asking you for anything.");
            return;
        }
        int food = 0;
        int stone = 0;
        var items = player.getInventory().items;
        for (ItemStack stack : items) {
            if (food >= 4) {
                break;
            }
            if (stack.has(DataComponents.FOOD)) {
                int take = Math.min(4 - food, stack.getCount());
                stack.shrink(take);
                food += take;
            }
        }
        if (food < 4) {
            for (ItemStack stack : items) {
                if (stone >= 2) {
                    break;
                }
                if (stack.is(ModItems.CHERT_ROCK.get()) || stack.is(ModItems.OBSIDIAN_ROCK.get())
                        || stack.is(ModItems.BASALT_ROCK.get()) || stack.is(ModItems.CHERT_HAMMERSTONE.get())) {
                    int take = Math.min(2 - stone, stack.getCount());
                    stack.shrink(take);
                    stone += take;
                }
            }
        }
        if (food == 0 && stone == 0) {
            say(player, "You have nothing they want - no food, no good stone.");
            return;
        }
        boolean enough = food >= 4 || stone >= 2 || food + stone * 2 >= 4;
        player.sendSystemMessage(Component.literal("You hand over " + (food > 0 ? food + " food" : "")
                + (food > 0 && stone > 0 ? " and " : "") + (stone > 0 ? stone + " good stone" : "") + ".")
                .withStyle(ChatFormatting.GOLD));
        if (!enough) {
            say(player, "It is not enough. They want more.");
            return;
        }
        settle(player, player.serverLevel(), band, "They take it and let you be.");
        change(player, band, 1, "you paid");
    }

    private static void settle(ServerPlayer player, ServerLevel level, Bands.Record band, String how) {
        Demand demand = demands.remove(player.getUUID());
        player.sendSystemMessage(Component.literal(how).withStyle(ChatFormatting.GRAY));
        if (demand != null && demand.raid()) {
            sendHome(level, band, player);
        }
    }

    private static void sendHome(ServerLevel level, Bands.Record band, ServerPlayer player) {
        Claims.releaseGang(player, level);
        for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(64.0D),
                m -> band.id.equals(m.getBandId()))) {
            member.setTarget(null);
            member.getNavigation().moveTo(band.home.getX() + 0.5D, band.home.getY(), band.home.getZ() + 0.5D, 1.1D);
        }
    }

    private static void endRaid(ServerPlayer player, ServerLevel level, Bands.Record band, boolean foughtOff) {
        demands.remove(player.getUUID());
        sendHome(level, band, player);
        if (foughtOff) {
            player.sendSystemMessage(Component.literal("The raiders break and run. They will not try that again soon.")
                    .withStyle(ChatFormatting.GREEN));
            Presence.add(player, 3, "you saw off raiders");
            change(player, band, -3, "you fought their raiders off");
            Claims.foughtOff(player, band);
        }
    }

    // ------------------------------------------------------------ pressing them: tribute, and raids

    /** A raid of yours on another band, while it lasts. */
    private record RaidOn(UUID band, long until) {
    }

    private static final Map<UUID, RaidOn> raidsOn = new HashMap<>();

    /** Your odds of making them pay: their weakness against your strength. */
    private static float tributeOdds(ServerPlayer player, Bands.Record band) {
        int adults = (int) Band.ownNear(player, 24.0D).stream().filter(m -> !m.isBaby()).count();
        float odds = 0.2F + (Presence.get(player) - band.presence) / 60.0F + (adults + 1 - band.size) * 0.05F
                + (25 - band.cohesion) / 50.0F;
        return Mth.clamp(odds, 0.05F, 0.9F);
    }

    /** "Pay us, or else." The weaker and less together they are, the likelier they pay. */
    private static void demandTribute(ServerPlayer player, Bands.Record band) {
        ServerLevel level = player.serverLevel();
        BandMember speaker = nearestOf(level, band, player, NEAR);
        if (speaker == null) {
            say(player, "None of " + band.name + " are near enough to lean on.");
            return;
        }
        String key = player.getUUID() + "/tribute/" + band.id + "/" + level.getDayTime() / 24000L;
        if (cooldowns.containsKey(key)) {
            say(player, "You already leaned on " + band.name + " today.");
            return;
        }
        cooldowns.put(key, level.getGameTime());
        speaker.ensureName();
        for (BandMember own : Band.ownNear(player, 16.0D)) {
            if (!own.isBaby()) {
                Band.memberDisplay(own, own.getRandom().nextInt(10));
            }
        }
        if (player.getRandom().nextFloat() < tributeOdds(player, band)) {
            int wanted = 2 + Math.max(0, (30 - band.presence) / 8);
            int given = 0;
            List<String> names = new ArrayList<>();
            for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(NEAR),
                    m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby())) {
                while (given < wanted && member.hasFood()) {
                    ItemStack food = member.takeFood();
                    names.add(food.getHoverName().getString().toLowerCase());
                    if (!player.getInventory().add(food)) {
                        player.drop(food, false);
                    }
                    given++;
                }
            }
            if (given < wanted) {
                // Whatever is lying about their camp makes up the rest.
                ItemStack rest = new ItemStack(player.getRandom().nextBoolean() ? ModItems.MEAT_CHUNK.get()
                        : net.minecraft.world.item.Items.SWEET_BERRIES, wanted - given);
                names.add(rest.getCount() + " " + rest.getHoverName().getString().toLowerCase());
                if (!player.getInventory().add(rest)) {
                    player.drop(rest, false);
                }
            }
            say(player, speaker, band.cohesion < 20 ? "Take it. Take it and go." : "Fine. This, and then leave us be.");
            player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " pay you off: " + String.join(", ", names)
                    + ".").withStyle(ChatFormatting.GOLD));
            Presence.add(player, 2, band.name + " paid you tribute");
            band.presence = Math.max(0, band.presence - 2);
            band.cohesion = Math.max(0, band.cohesion - 2);
            change(player, band, -6, "you made them pay");
            return;
        }
        say(player, speaker, band.presence >= 30 ? "Pay you? Look around you. Look at us." : "We have nothing for you. Go.");
        change(player, band, -4, "you tried to make them pay");
        if (band.cohesion >= 20 && player.getRandom().nextBoolean()) {
            for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(NEAR),
                    m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby())) {
                Band.memberDisplay(member, member.getRandom().nextInt(10));
            }
            player.sendSystemMessage(Component.literal("They close ranks and stare you down. Push this further and it "
                    + "is a fight.").withStyle(ChatFormatting.RED));
        }
    }

    /** Your band falls on theirs. A weak band that does not hold together breaks fast, and drops what it carries. */
    private static void raidThem(ServerPlayer player, Bands.Record band) {
        ServerLevel level = player.serverLevel();
        List<BandMember> theirs = level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(NEAR),
                m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby());
        if (theirs.isEmpty()) {
            say(player, "None of " + band.name + " are near enough to fall on.");
            return;
        }
        List<BandMember> ours = new ArrayList<>(Band.ownNear(player, 24.0D).stream().filter(m -> !m.isBaby()).toList());
        raidsOn.put(player.getUUID(), new RaidOn(band.id, level.getGameTime() + RAID_TICKS));
        for (int i = 0; i < ours.size(); i++) {
            ours.get(i).defendAgainst(theirs.get(i % theirs.size()));
        }
        for (int i = 0; i < theirs.size(); i++) {
            BandMember them = theirs.get(i);
            them.defendAgainst(ours.isEmpty() || i % 2 == 0 ? player : ours.get(i % ours.size()));
        }
        player.sendSystemMessage(Component.literal("You fall on " + band.name + "! " + (band.cohesion < 20
                ? "They are already arguing among themselves - they will not hold long."
                : band.presence >= 35 ? "They are strong, and they stand." : "Break them and they drop what they carry.")
                ).withStyle(ChatFormatting.RED));
        change(player, band, -15, "you raided them");
        for (UUID allyId : band.allies) {
            Bands.Record ally = Bands.get(level, allyId);
            if (ally != null && ally.knownTo(player.getUUID())) {
                change(player, ally, -8, "you raided " + band.name + ", their allies");
            }
        }
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.RAIDING);
    }

    /** One of theirs hurt in your raid: they may break. */
    private static void raidBlow(ServerPlayer player, ServerLevel level, Bands.Record band, BandMember hurt, boolean killing) {
        float breaks = 0.12F + (30 - band.cohesion) / 100.0F + (25 - band.presence) / 120.0F + (killing ? 0.3F : 0.0F);
        if (hurt.getRandom().nextFloat() >= Mth.clamp(breaks, 0.05F, 0.7F)) {
            return;
        }
        raidsOn.remove(player.getUUID());
        int dropped = 0;
        for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(48.0D),
                m -> m.isAlive() && band.id.equals(m.getBandId()))) {
            // Running, they drop what they were carrying.
            while (member.hasFood() && dropped < 8) {
                member.spawnAtLocation(member.takeFood());
                dropped++;
            }
            member.setTarget(null);
            member.getNavigation().moveTo(band.home.getX() + 0.5D, band.home.getY(), band.home.getZ() + 0.5D, 1.3D);
        }
        for (BandMember own : Band.ownNear(player, 32.0D)) {
            if (own.getTarget() instanceof BandMember target && band.id.equals(target.getBandId())) {
                own.setTarget(null);
            }
        }
        band.presence = Math.max(0, band.presence - 4);
        band.cohesion = Math.max(0, band.cohesion - 4);
        band.desperation = Math.min(5, band.desperation + 1);
        Bands.changed(level);
        if (Bands.horizontal(band.home, player.blockPosition()) < 64.0D * 64.0D) {
            List<ItemStack> looted = ToolPiles.plunder(level, band.id, 3);
            if (!looted.isEmpty()) {
                for (ItemStack tool : looted) {
                    if (!player.getInventory().add(tool)) {
                        player.drop(tool, false);
                    }
                }
                player.sendSystemMessage(Component.literal("Your band goes through their camp and comes away with "
                        + ToolPiles.describe(looted) + " from their pile.").withStyle(ChatFormatting.GOLD));
            }
        }
        Presence.add(player, 3, "you broke " + band.name);
        Claims.broke(player, band);
        player.sendSystemMessage(Component.literal("(For a day their ground is yours to take: set your territory on it "
                + "from the map.)").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " break and run" + (dropped > 0
                ? ", dropping " + dropped + " food as they go." : " - with nothing worth dropping.")).withStyle(ChatFormatting.GOLD));
    }

    private static void tickRaidOn(ServerPlayer player, ServerLevel level) {
        RaidOn raid = raidsOn.get(player.getUUID());
        if (raid == null || level.getGameTime() < raid.until()) {
            return;
        }
        raidsOn.remove(player.getUUID());
        Bands.Record band = Bands.get(level, raid.band());
        for (BandMember own : Band.ownNear(player, 32.0D)) {
            if (own.getTarget() instanceof BandMember target && raid.band().equals(target.getBandId())) {
                own.setTarget(null);
            }
        }
        if (band != null) {
            sendHome(level, band, player);
            player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " hold. Your band pulls back.")
                    .withStyle(ChatFormatting.GRAY));
            Presence.add(player, -2, band.name + " stood up to you");
        }
    }

    // ------------------------------------------------------------ once every two seconds

    public static void tick(ServerPlayer player) {
        if (player.isSpectator() || player.tickCount % 40 != 17) {
            return;
        }
        ServerLevel level = player.serverLevel();
        tickName(player);
        if (player.tickCount % 1200 == 17) {
            Bands.drift(level);
        }
        tickRaidOn(player, level);
        List<Bands.Record> bands = Bands.all(level);
        for (Bands.Record band : bands) {
            if (!band.knownTo(player.getUUID()) && membersNear(level, band, player, SIGHT) > 0) {
                meet(player, band, "You pick them out across the country.");
            }
        }
        tickDemand(player, level);
        Bands.Record ground = Bands.groundAt(level, player.blockPosition());
        if (ground != null && ground.knownTo(player.getUUID())) {
            onTheirGround(player, level, ground);
        }
        if (player.tickCount % 400 == 17) {
            calls(player, level, bands);
        }
        if (player.tickCount % 1200 == 17) {
            raids(player, level, bands);
            allyGifts(player, level, bands);
            neighbourly(player, bands);
        }
    }

    /** On a known band's ground: welcomed, ignored, or - if they dislike you - met and moved on. */
    private static void onTheirGround(ServerPlayer player, ServerLevel level, Bands.Record band) {
        String key = player.getUUID() + "/entered/" + band.id;
        long now = level.getGameTime();
        int standing = standing(player, band);
        if (now >= cooldowns.getOrDefault(key, 0L)) {
            cooldowns.put(key, now + 12000L);
            player.displayClientMessage(Component.literal(welcomes(player, band)
                    ? "You are on " + band.name + "'s ground. They know you, and you are welcome."
                    : standing <= UNFRIENDLY ? "You are on " + band.name + "'s ground - and they do not want you here."
                    : "You are on " + band.name + "'s ground. Whatever you take here, they will notice.")
                    .withStyle(colour(standing)), true);
        }
        boolean hard = Bands.desperateTimes(level);
        boolean patrolled = standing <= UNFRIENDLY || (hard && standing < FRIENDLY && band.desperation >= 3);
        if (!patrolled || demands.containsKey(player.getUUID())) {
            return;
        }
        BandMember spotter = nearestOf(level, band, player, hard ? 40.0D : 24.0D);
        if (spotter == null) {
            return;
        }
        // On sight: they come at you, and want you gone - or paid.
        demands.put(player.getUUID(), new Demand(band.id, now + DEMAND_TICKS, false, true));
        spotter.ensureName();
        for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(32.0D),
                m -> band.id.equals(m.getBandId()) && !m.isBaby())) {
            Band.memberDisplay(member, member.getRandom().nextInt(10));
            member.getNavigation().moveTo(player, 1.2D);
        }
        Claims.open(player, band, Claims.Kind.TRESPASS, spotter, "Off our ground. Now - or pay us to let you through.",
                "Give in: 4 food (or 2 good stone). Fight them. Or flee - you drop some of what you carry, and they chase "
                        + "you off.", List.of(new ItemStack(ModItems.MEAT_CHUNK.get(), 4)), null);
    }

    private static void tickDemand(ServerPlayer player, ServerLevel level) {
        Demand demand = demands.get(player.getUUID());
        if (demand == null) {
            return;
        }
        Bands.Record band = Bands.get(level, demand.band());
        if (band == null) {
            demands.remove(player.getUUID());
            return;
        }
        long now = level.getGameTime();
        if (!demand.raid()) {
            if (!band.holds(player.blockPosition())) {
                demands.remove(player.getUUID());
                player.displayClientMessage(Component.literal("You are off their ground. They let you go.")
                        .withStyle(ChatFormatting.GRAY), true);
                return;
            }
            if (now < demand.until()) {
                return;
            }
            // Still here, still unpaid: driven off.
            for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(24.0D),
                    m -> band.id.equals(m.getBandId()) && !m.isBaby())) {
                member.getNavigation().moveTo(player, 1.3D);
                if (member.distanceToSqr(player) < 9.0D) {
                    member.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    player.hurt(player.damageSources().mobAttack(member), 2.0F);
                    player.knockback(0.6D, member.getX() - player.getX(), member.getZ() - player.getZ());
                }
            }
            String key = player.getUUID() + "/driven/" + band.id;
            if (now >= cooldowns.getOrDefault(key, 0L)) {
                cooldowns.put(key, now + 400L);
                change(player, band, -1, "you would not leave their ground");
            }
            return;
        }
        // A raid: the raiders walk in, say what they want, and take it if nobody pays or stops them.
        List<BandMember> raiders = level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(96.0D),
                m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby());
        if (raiders.isEmpty()) {
            demands.remove(player.getUUID());
            return;
        }
        BandMember closest = raiders.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(player))).get();
        if (!demand.spoken() && now > demand.until()) {
            // They never found a way to you, or thought better of it.
            demands.remove(player.getUUID());
            sendHome(level, band, player);
            return;
        }
        if (!demand.spoken()) {
            raiders.forEach(m -> m.getNavigation().moveTo(player, 1.1D));
            if (closest.distanceToSqr(player) < 12.0D * 12.0D) {
                closest.ensureName();
                demands.put(player.getUUID(), new Demand(band.id, now + DEMAND_TICKS, true, true));
                raiders.forEach(m -> Band.memberDisplay(m, m.getRandom().nextInt(10)));
                Claims.open(player, band, Claims.Kind.FOOD_RAID, closest, band.desperation >= 4
                        ? "We are starving. You have food. Give it to us - or we take it."
                        : "We are hungry, and you have plenty. Food - now. Or we take it.",
                        "Give in: 4 food (or 2 good stone). Fight - a band that holds together breaks raiders fast. Or "
                                + "flee, dropping some of what you carry.",
                        List.of(new ItemStack(ModItems.MEAT_CHUNK.get(), 4)), null);
            }
            return;
        }
        if (now < demand.until()) {
            return;
        }
        // Nobody paid, nobody stopped them.
        int taken = 0;
        for (BandMember member : Band.ownNear(player, 32.0D)) {
            while (taken < 4 && member.hasFood()) {
                closest.addToInventory(member.takeFood());
                taken++;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (taken >= 4) {
                break;
            }
            if (stack.has(DataComponents.FOOD)) {
                int take = Math.min(4 - taken, stack.getCount());
                closest.addToInventory(stack.split(take));
                taken += take;
            }
        }
        demands.remove(player.getUUID());
        player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " take " + (taken == 0 ? "what little there is"
                : taken + " food") + " and go. The band watched you let them.").withStyle(ChatFormatting.RED));
        Cohesion.add(player, -2);
        sendHome(level, band, player);
        Claims.raidWon(player, band);
    }

    /** A band calling across the country: which way, how far - and a link to follow it. */
    private static void calls(ServerPlayer player, ServerLevel level, List<Bands.Record> bands) {
        ResourceLocation era = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        for (Bands.Record band : bands) {
            double distance = Math.sqrt(Bands.horizontal(band.home, player.blockPosition()));
            if (distance > 300.0D || distance < SIGHT || WildBands.isExtinctBy(band.species, era)
                    || membersNear(level, band, player, SIGHT) > 0) {
                continue;
            }
            boolean known = band.knownTo(player.getUUID());
            if (player.getRandom().nextFloat() >= (known ? 0.03F : 0.12F)) {
                continue;
            }
            call(player, level, band, (int) distance);
            return;
        }
    }

    public static void call(ServerPlayer player, ServerLevel level, Bands.Record band, int distance) {
        double dx = band.home.getX() - player.getX();
        double dz = band.home.getZ() - player.getZ();
        double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
        // Heard from the right direction: the sound is set down between you and them.
        level.playSound(null, player.getX() + dx / length * 20.0D, player.getY() + 2.0D, player.getZ() + dz / length * 20.0D,
                ModSounds.BAND_PANT_HOOT.get(), SoundSource.NEUTRAL, 2.5F, band.nomadic() ? 0.7F : 0.95F);
        boolean known = band.knownTo(player.getUUID());
        String who = known ? BandNames.capital(band.name) + (band.nomadic() ? " (Paranthropus) are" : " are")
                : band.nomadic() ? "A troop of Paranthropus is" : "Another band is";
        String after = known ? "" : " You will know them as " + band.name + " - "
                + (band.nomadic() ? "Paranthropus" : speciesName(band.species) + ", " + band.size + " of them") + ".";
        player.sendSystemMessage(Component.literal(who + " calling - " + distance + " blocks "
                + WildBands.bearingFrom(player, band.home) + "." + after + " ").withStyle(ChatFormatting.GOLD)
                .append(leadLink(band)));
        if (!known) {
            meet(player, band, "", true);
        }
    }

    /** Raids: a hostile band, or a desperate one that sees you weak, comes for your food. */
    private static void raids(ServerPlayer player, ServerLevel level, List<Bands.Record> bands) {
        if (demands.containsKey(player.getUUID()) || Band.all(player).isEmpty()) {
            return;
        }
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int minute = (int) (level.getGameTime() / 1200L);
        if (minute < counters.getOrDefault("raid_next_minute", 0)) {
            return;
        }
        boolean hard = Seasons.strained(level);
        boolean bad = level.isNight() || hard || Band.ownNear(player, 32.0D).size() <= 3;
        // How soft a target you look: no presence to speak of, a band that does not hold together.
        float soft = 0.5F * Mth.clamp((22 - Presence.get(player)) / 22.0F, 0.0F, 1.0F)
                + 0.5F * Mth.clamp((28 - Cohesion.get(player)) / 28.0F, 0.0F, 1.0F);
        for (Bands.Record band : bands) {
            if (band.nomadic() || Bands.horizontal(band.home, player.blockPosition()) > 220.0D * 220.0D) {
                continue;
            }
            int standing = standing(player, band);
            float chance = standing <= HOSTILE ? (bad ? 0.08F : 0.02F) + 0.08F * soft
                    : standing <= UNFRIENDLY ? 0.04F * soft
                    : hard && standing <= NEUTRAL ? 0.05F * soft : 0.0F;
            // A hungry band coming apart takes chances; a strong one is sure of itself; a desperate one, more so.
            chance *= (band.cohesion < 20 ? 1.5F : 1.0F) * (0.5F + band.presence / 50.0F) * (0.6F + band.desperation * 0.3F);
            chance *= Claims.fearFactor(player) * (Bands.desperateTimes(level) ? 2.0F : 1.0F);
            if (Claims.hasAccess(player, band)) {
                chance = 0.0F;
            }
            if (chance <= 0.0F || player.getRandom().nextFloat() >= chance) {
                continue;
            }
            counters.put("raid_next_minute", minute + (int) (RAID_GAP / 1200L));
            Bands.Record ally = allyNear(player, level, bands);
            if (ally != null) {
                player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " were coming for your food - but "
                        + ally.name + " stand with you, and they turn back.").withStyle(ChatFormatting.AQUA));
                return;
            }
            startRaid(player, level, band);
            return;
        }
    }

    @Nullable
    private static Bands.Record allyNear(ServerPlayer player, ServerLevel level, List<Bands.Record> bands) {
        for (Bands.Record band : bands) {
            if (!band.nomadic() && standing(player, band) >= ALLIED
                    && Bands.horizontal(band.home, player.blockPosition()) < 200.0D * 200.0D) {
                return band;
            }
        }
        return null;
    }

    /** Developer: this band raids you for food, now. */
    public static void devFoodRaid(ServerPlayer player, Bands.Record band) {
        startRaid(player, player.serverLevel(), band);
    }

    private static void startRaid(ServerPlayer player, ServerLevel level, Bands.Record band) {
        int close = level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(80.0D),
                m -> m.isAlive() && band.id.equals(m.getBandId())).size();
        if (close == 0) {
            // They come from over the horizon: a raiding party of two or three.
            double angle = Math.atan2(band.home.getZ() - player.getZ(), band.home.getX() - player.getX());
            int x = (int) (player.getX() + Math.cos(angle) * 40.0D);
            int z = (int) (player.getZ() + Math.sin(angle) * 40.0D);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                return;
            }
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            WildBands.placeBand(level, at, band.species, 2 + player.getRandom().nextInt(2), band.id, player.getRandom());
            Claims.gangUp(player, level, band, at);
        }
        demands.put(player.getUUID(), new Demand(band.id, level.getGameTime() + DEMAND_TICKS * 4, true, false));
        if (band.desperation >= 3) {
            Claims.warnDesperate(player, band);
        } else {
            player.sendSystemMessage(Component.literal("People are coming - " + band.name + ", and not to trade. "
                    + WildBands.bearingFrom(player, band.home) + ".").withStyle(ChatFormatting.RED));
        }
    }

    /** Friends use your ground as you use theirs: now and then some of them are seen foraging on it. */
    private static void neighbourly(ServerPlayer player, List<Bands.Record> bands) {
        BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
        for (Bands.Record band : bands) {
            int standing = standing(player, band);
            if (band.nomadic() || standing < FRIENDLY || player.getRandom().nextInt(40) != 0
                    || Bands.horizontal(band.home, camp) > 260.0D * 260.0D) {
                continue;
            }
            player.sendSystemMessage(Component.literal("A few of " + band.name + " are foraging on your ground. Fair is fair - "
                    + "you use theirs.").withStyle(ChatFormatting.GRAY));
            return;
        }
    }

    /** Allies remember you: now and then one of them turns up with something. */
    private static void allyGifts(ServerPlayer player, ServerLevel level, List<Bands.Record> bands) {
        for (Bands.Record band : bands) {
            if (standing(player, band) < ALLIED || band.desperation >= 4 || player.getRandom().nextInt(30) != 0) {
                continue;
            }
            BandMember giver = nearestOf(level, band, player, 48.0D);
            if (giver == null) {
                continue;
            }
            ItemStack gift = giver.hasFood() ? giver.takeFood() : new ItemStack(ModItems.FLAKE.get());
            giver.ensureName();
            giver.getNavigation().moveTo(player, 1.0D);
            say(player, giver, "For you. You are one of us, near enough.");
            player.displayClientMessage(Component.literal(giver.getName().getString() + " of " + band.name + " hands you "
                    + gift.getHoverName().getString() + ".").withStyle(ChatFormatting.AQUA), true);
            if (!player.getInventory().add(gift)) {
                player.drop(gift, false);
            }
            return;
        }
    }

    // ------------------------------------------------------------ "The others"

    @Nullable
    private static Bands.Record recordFor(ServerLevel level, String id) {
        try {
            return Bands.get(level, UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void act(ServerPlayer player, String bandId, int action) {
        ServerLevel level = player.serverLevel();
        if (action == OthersActionPayload.OPEN) {
            sendOthers(player);
            return;
        }
        Bands.Record band = recordFor(level, bandId);
        if (band == null || !band.knownTo(player.getUUID())) {
            return;
        }
        switch (action) {
            case OthersActionPayload.LEAD -> lead(player, band);
            case OthersActionPayload.RANSOM -> payRansom(player, band);
            case OthersActionPayload.GIFT -> sendGiftStock(player, band);
            case OthersActionPayload.DEMAND -> demandTribute(player, band);
            case OthersActionPayload.RAID -> raidThem(player, band);
            case OthersActionPayload.TELL_PLACES -> dev.hominin.evolution.world.Pois.tellBand(player, band);
            case OthersActionPayload.TRADE, OthersActionPayload.TRAVEL -> {
                BandMember member = nearestOf(level, band, player, NEAR);
                if (member == null) {
                    say(player, "None of " + band.name + " are near enough.");
                } else if (action == OthersActionPayload.TRADE) {
                    Trading.openTrade(player, member);
                } else {
                    Social.askToTravel(player, member);
                }
            }
            default -> {
            }
        }
    }

    /** Sets the pointer on a band: its people if they are about, its camp if not. */
    public static void lead(ServerPlayer player, Bands.Record band) {
        dev.hominin.evolution.mind.MentalMap.lead(player, whereIs(player.serverLevel(), band), band.name, band.id.toString());
    }

    public static BlockPos whereIs(ServerLevel level, Bands.Record band) {
        List<? extends BandMember> members = level.getEntities(ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && band.id.equals(m.getBandId()));
        if (members.isEmpty()) {
            return band.home;
        }
        double x = 0.0D;
        double z = 0.0D;
        for (BandMember member : members) {
            x += member.getX();
            z += member.getZ();
        }
        return BlockPos.containing(x / members.size(), band.home.getY(), z / members.size());
    }

    public static void sendOthers(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<OthersPayload.View> views = new ArrayList<>();
        List<Bands.Record> known = new ArrayList<>(Bands.all(level).stream().filter(b -> b.knownTo(player.getUUID())).toList());
        known.sort(Comparator.comparingDouble(b -> Bands.horizontal(whereIs(level, b), player.blockPosition())));
        boolean strained = Seasons.strained(level);
        for (Bands.Record band : known) {
            int standing = standing(player, band);
            BlockPos where = whereIs(level, band);
            int distance = (int) Math.sqrt(Bands.horizontal(where, player.blockPosition()));
            boolean near = membersNear(level, band, player, NEAR) > 0;
            List<String> lines = new ArrayList<>();
            lines.add(band.nomadic() ? "Paranthropus - a troop of " + band.size + ", never in one place for long"
                    : speciesName(band.species) + " - " + band.size + " of them");
            if (!band.nomadic() && !Species.abilities(band.species).isEmpty()) {
                lines.add(Species.abilities(band.species));
            }
            lines.add("Standing: " + standing + "/" + MAX + " - " + tier(standing));
            if (near) {
                lines.add("They are here, close enough to deal with.");
            } else {
                lines.add((band.nomadic() ? "Last heard of " : "Their camp: ") + distance + " blocks "
                        + WildBands.bearingFrom(player, where));
            }
            if (!band.nomadic()) {
                lines.add("Presence " + band.presence + "/50 - " + band.strength() + ". Cohesion " + band.cohesion
                        + "/50 - " + band.temper() + ".");
                lines.add("Desperation " + Claims.meter(band.desperation) + " " + band.desperation + "/5 - "
                        + Claims.desperationLabel(band.desperation) + ".");
                if (level.hasChunk(band.home.getX() >> 4, band.home.getZ() >> 4)) {
                    var land = dev.hominin.evolution.world.Land.of(level, band.home, band.radius());
                    lines.add("Their ground: pressure " + land.total() + "/10 - " + String.join(", ", land.describe(player)) + ".");
                }
                int stance = band.stance.getOrDefault(player.getUUID(), Claims.STANCE_NONE);
                if (Claims.hasAccess(player, band)) {
                    lines.add("They are using your ground, by your leave.");
                } else if (stance == Claims.STANCE_OFFER) {
                    lines.add("They have offered to pay for the use of your ground.");
                } else if (stance == Claims.STANCE_RANSOM) {
                    lines.add("They want paying to leave you on your ground.");
                } else if (stance == Claims.STANCE_CLAIM) {
                    lines.add("They want your ground for themselves.");
                }
                if (near) {
                    float odds = tributeOdds(player, band);
                    lines.add(odds >= 0.55F ? "Press them and they would likely pay."
                            : odds >= 0.3F ? "Press them and they might pay - or might not."
                            : "They would laugh at a demand from you.");
                }
                lines.add(band.holds(player.blockPosition()) ? "You are on their ground (" + band.radius() + " blocks round their camp)."
                        : "Their ground runs " + band.radius() + " blocks round their camp.");
                lines.add(welcomes(player, band) ? "You may use their water and stone."
                        : standing >= FRIENDLY && strained ? "In hard times only friends of 40 and up are welcome."
                        : "Taking from their ground costs you with them.");
            }
            if (ransomDemanded(player, band.id)) {
                lines.add("They are demanding to be paid - 4 food, or 2 good stone.");
            }
            if (Fates.inPlight(band.id)) {
                lines.add(0, "UNDER ATTACK - they need you, now.");
            }
            if (!band.nomadic()) {
                int places = dev.hominin.evolution.world.Pois.bandKnowsCount(level, band.id);
                lines.add(places == 0 ? "They know nowhere worth telling of."
                        : "They know " + places + (places == 1 ? " place" : " places") + " worth knowing - allies share.");
                if (dev.hominin.evolution.world.Pois.familiar(level, band.id, player.getUUID())) {
                    lines.add("They stood with your old band, and remember what it showed them.");
                }
            }
            views.add(new OthersPayload.View(band.id.toString(), band.name, standing, near, ransomDemanded(player, band.id),
                    !band.nomadic() && standing >= (erectusOn(band.species) ? FRIENDLY : NEUTRAL), band.nomadic(), lines,
                    band.presence, band.cohesion, band.desperation));
        }
        PacketDistributor.sendToPlayer(player, new OthersPayload(views));
    }

    // ------------------------------------------------------------ your own band's name

    /** Players asked for their band's name this session - asked once, when nothing else is going on. */
    private static final java.util.Set<UUID> prompted = new java.util.HashSet<>();

    /** A new band has formed round the player: they get to name it, once the dust settles. */
    public static void ownBandFormed(ServerPlayer player) {
        player.getData(Attachments.MIND).setNamePending(true);
        prompted.remove(player.getUUID());
    }

    /** Coming back: a band still waiting for its name is asked about again. */
    public static void promptName(ServerPlayer player) {
        prompted.remove(player.getUUID());
    }

    /**
     * Asks for the name when the moment is right: not during a cutscene, not the instant you arrive. A band
     * from before bands had names is asked about too.
     */
    private static void tickName(ServerPlayer player) {
        var mind = player.getData(Attachments.MIND);
        if (!mind.namePending() && mind.bandName().isEmpty() && player.tickCount > 200 && !Band.all(player).isEmpty()) {
            mind.setNamePending(true);
        }
        if (!mind.namePending() || prompted.contains(player.getUUID()) || player.tickCount < 100
                || dev.hominin.evolution.stage.CutsceneGuard.isProtected(player)) {
            return;
        }
        prompted.add(player.getUUID());
        String suggestion = BandNames.suggestion(player.serverLevel(), player.blockPosition(),
                new UUID(player.getUUID().getMostSignificantBits(), player.level().getGameTime()));
        PacketDistributor.sendToPlayer(player, new dev.hominin.evolution.network.BandNamePayload(suggestion));
    }

    public static void nameOwnBand(ServerPlayer player, String name) {
        var mind = player.getData(Attachments.MIND);
        String clean = net.minecraft.util.StringUtil.filterText(name).trim();
        if (clean.length() > 32) {
            clean = clean.substring(0, 32).trim();
        }
        if (clean.isEmpty()) {
            clean = BandNames.suggestion(player.serverLevel(), player.blockPosition(), player.getUUID());
        }
        mind.setBandName(clean);
        mind.setNamePending(false);
        player.sendSystemMessage(Component.literal("Your band: " + BandNames.capital(clean) + ".").withStyle(ChatFormatting.GREEN));
    }

    public static String ownName(Player player) {
        String name = player.getData(Attachments.MIND).bandName();
        return name.isEmpty() ? "your band" : name;
    }

    // ------------------------------------------------------------ helpers

    @Nullable
    static BandMember nearestMember(ServerLevel level, Bands.Record band, LivingEntity around, double radius) {
        return nearestOf(level, band, around, radius);
    }

    private static BandMember nearestOf(ServerLevel level, Bands.Record band, LivingEntity around, double radius) {
        BandMember best = null;
        for (BandMember member : level.getEntitiesOfClass(BandMember.class, around.getBoundingBox().inflate(radius),
                m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby())) {
            if (best == null || member.distanceToSqr(around) < best.distanceToSqr(around)) {
                best = member;
            }
        }
        return best;
    }

    private static void say(ServerPlayer player, BandMember speaker, String line) {
        player.sendSystemMessage(Component.literal("<" + speaker.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    private static void say(ServerPlayer player, String line) {
        player.displayClientMessage(Component.literal(line), true);
    }

    public static void forget(UUID player) {
        Claims.forget(player);
        demands.remove(player);
        prompted.remove(player);
        raidsOn.remove(player);
    }

    /** Whether you are raiding them right now. */
    public static boolean raiding(ServerPlayer player, Bands.Record band) {
        RaidOn raid = raidsOn.get(player.getUUID());
        return raid != null && raid.band().equals(band.id);
    }

    private Relations() {
    }
}
