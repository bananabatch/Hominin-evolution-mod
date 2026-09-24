package dev.hominin.evolution.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.network.TipPayload;
import dev.hominin.evolution.stage.BuiltinMilestones;
import dev.hominin.evolution.stage.CutsceneGuard;
import dev.hominin.evolution.survival.Infestation;
import dev.hominin.evolution.survival.Thirst;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tips: a word in your ear at the moment you need it, never on a timer. Each one is tied to the
 * situation it helps with - the first time your water runs low, the first time something hunts you,
 * the first time the band asks for something - and is shown once, ever. A tip comes up as a toast
 * and a line in chat; double-click the chat line and the guide book opens at the page it is about.
 *
 * <p>Tips wait their turn: no more than one every 45 seconds, and a tip that comes up while another
 * has just been shown waits a little while for its moment (unless it is urgent - a bleed that will
 * kill you does not wait). {@code /hominin tips off} turns them off, {@code on} back on, and
 * {@code reset} lets every tip be shown again.
 */
public final class Tips {
    /**
     * Every tip: its title, what it says, and the page of the guide it opens (an entry under
     * {@code patchouli_books/inner_mind}, and the page within it), or none. {K} and the like are
     * the player's own key for think, talk and the rest, filled in on their side.
     */
    public enum Tip {
        // ------------------------------------------------ getting started
        WELCOME("Where to start", "The list on the left is how your kind evolves - do what it asks. Your guide book "
                + "explains everything, and tips like this one open the page they are about: press {T}, then "
                + "double-click the tip.", "beginnings/who_you_are", 1),
        TALK("Talking to the band", "Press {H} near your band to talk: forage together, ask for things, trade, groom, "
                + "share food. Right-click one of them first to talk to just them.", "living/asking", 0),
        // ------------------------------------------------ the body
        THIRSTY("Thirsty", "Your water is running low - the drops above your health. Right-click water with an empty "
                + "hand, or stand in it. Running and the midday sun dry you out faster.", "body/water", 1),
        HUNGRY("Hungry, and nothing to eat", "The ground is full of food: sneak and right-click grass or soil to "
                + "forage for grubs. Strip berry bushes, fish a termite mound with a stick, and look for "
                + "carcasses.", "body/foraging", 0),
        NIGHT("Night is coming", "Things hunt at night. Sleep up off the ground in a nest: tear leaves off a tree for "
                + "nesting material, one in each hand and {P} makes a piece, and lay six in a two-by-three.",
                "body/trees", 4),
        NIGHT_FIRE("Night is coming", "Nothing that hunts comes within 24 blocks of a lit hearth you are sitting by. "
                + "Spin a fire drill (a stick in each hand, {P}) on dry ground to light one, and feed it sticks and "
                + "branches.", "body/fire", 0),
        TICKS("Ticks", "Ticks are building up on you - past six, nothing heals. Ask the band to pick them off: {H}, "
                + "Social, Get these off me. Groom one of them first and they owe you.", "living/asking", 3),
        BLEEDING("Bleeding", "A cut bleeds and stops on its own. A deep wound bleeds harder and stops you healing "
                + "while it runs - drink, rest, and keep out of fights until it closes.", "body/wounds", 0),
        CATASTROPHIC("Catastrophic bleeding!", "You have 60 seconds. Drink 24 water - four drinks straight from "
                + "water, or three full eggshells. Get to water now.", "body/wounds", 1, true),
        LACERATED("Lacerated", "You lived. Until dawn, do not get hurt again and do not eat raw meat, or it turns to "
                + "infection - no healing for two days. Cooked meat is safe.", "body/wounds", 1),
        INFECTED("Infected", "The wound has gone bad: no healing for two days, and you will be hungry and sick. Stay "
                + "close to the band and out of fights until it passes.", "body/wounds", 2),
        ARMS_FULL("Your arms are full", "You carry only what your hands and arms can hold - the hotbar. Erectus "
                + "carries another row. Leave what you do not need, or give it to the band.",
                "beginnings/who_you_are", 2),
        // ------------------------------------------------ stone
        BARE_HANDS("Bare hands", "Hands only pull up what is soft: leaves, grass, fruit, loose rocks. Stone stays in "
                + "the ground until a hammerstone strikes a face off it, and wood waits for the hand axe.",
                "stone/finding", 0),
        HAMMERSTONE("You need a hammerstone", "Hold a loose rock in each hand and press {P}: they knock together "
                + "into a hammerstone. Holding one, right-click a rock face to strike stone off it; in your off hand, "
                + "it knaps.", "living/making", 3),
        KNAPPING("You have a hammerstone", "A rock in your main hand, the hammerstone in your off hand, and {P}: "
                + "choose what to strike off. Chert, basalt, quartzite and obsidian flake; limestone crumbles.",
                "stone/knapping", 0),
        FUMBLE("An idea first", "Your hands do not know that one yet. Hold both things and hold {K} to think it "
                + "through - it takes a full stomach and good health - and then the hands remember.",
                "hands/thinking", 0),
        LIMESTONE("Limestone", "Limestone crumbles instead of flaking: it makes grinding stones and nothing else. "
                + "Knap chert, basalt or quartzite.", "living/making", 5),
        BASALT("Basalt", "Basalt is lava that cooled: a good knapping stone, a tier behind chert. Where it lies "
                + "scattered, lava is near - and obsidian lies at the lava's edge. Follow the dark stones in.",
                "stone/finding", 2),
        KNAPPING_STATION("A place to work stone", "Erectus works stone at a knapping station: four sticks in your "
                + "main hand, a hide in the off hand, {P}. Lay out a hammerstone, a bone and stone, and choose a hand "
                + "axe or a cleaver.", "stone/acheulean", 0),
        MILESTONE_FLAKE("Ready to evolve", "Your kind is ready. Strike the first flake: a rock in your main hand, a "
                + "hammerstone in your off hand, {P}, and choose Flake.", "stone/knapping", 0),
        MILESTONE_FIRE("Ready to evolve", "Your kind is ready. Carry fire: take flame from a lightning strike, or "
                + "spin a fire drill (a stick in each hand, {P}) on dry ground under open sky.", "body/fire", 0),
        MILESTONE_ACHEULEAN("Ready to evolve", "Your kind is ready. Make a tier 2 or better hand axe or cleaver at a "
                + "knapping station, from any stone but obsidian: chert or basalt, in practised hands.",
                "stone/acheulean", 3),
        MILESTONE("Ready to evolve", "Your kind is ready. One act is left - your checklist says what it is.",
                "beginnings/becoming", 0),
        // ------------------------------------------------ the band
        FEED_MEMBER("They are hungry", "Someone in your band is hungry. Right-click them holding food and they take "
                + "it from your hand - it builds bond, and the whole band notices.", "living/asking", 5),
        WANT("Someone wants something", "Band members ask for things. Hand it over: right-click them, then {H}, "
                + "Tools and things, Here, take this. A want left to run out costs cohesion.", "living/asking", 1),
        NEED("A need", "A need comes before any want, and you have ten minutes: right-click them, {H}, Tools and "
                + "things, Here, take this. Ignored, it costs 6 cohesion - and they will not forget.",
                "living/cohesion", 2),
        COHESION_TIPPING("Cohesion is slipping", "The band is losing faith in you. Share food ({H}, Social), groom "
                + "them, meet their needs and wants. While it is 21 to 29 you can promise to do better ({H}, "
                + "Social, I'll do better).", "living/cohesion", 0),
        COHESION_BORDERLINE("Cohesion is low", "No trades now. At 10 and below one more failing - a need or want "
                + "ignored, a rule broken - and they drive you out. Share, groom and give until it climbs.",
                "living/cohesion", 0),
        COHESION_DIRE("One more failing", "The band is at the edge. Ignore nothing they ask and break none of your "
                + "ways; share and groom to climb out. A death or a theft will not do it - your own failings will.",
                "living/cohesion", 0, true),
        HOARDING("Split it between them", "Carrying a lot of food while they go hungry costs cohesion. Hold the food "
                + "and choose {H}, Food, Split what I'm holding between you.", "living/cohesion", 3),
        RECIPROCITY("Give back", "The band counts what you take against what you give. Hand them things, meet their "
                + "wants and needs, share food - or it costs cohesion.", "living/cohesion", 3),
        ANTISOCIAL("Someone who does not care", "Some members do not care what the band thinks: they steal, hoard "
                + "and pick fights. Keep cohesion high - a band that holds together brings them round in time.",
                "living/cohesion", 4),
        ANTISOCIAL_WAYS("Keeping them in line", "Adopt Nobody lords it over the rest ({H}, Culture) to stop most "
                + "fights and catch thieves - or have the band shun one ({H}, Social, Shun them): they bend or they "
                + "go.", "living/culture", 5),
        THINK_DEEPER("Something deeper", "When you feel something deeper could be going on, stop and hold {K} where "
                + "you are - there is something here to work out. It costs nothing.", "living/others", 8),
        SOIL_DRY("Picked clean", "Ground gives only so much: forage one spot too often and it runs out for days. Move "
                + "a little way off. Fertile ground lasts far longer.", "body/foraging", 2),
        LAND_PRESSURE("Ground worth having", "Your ground's pressure (J, Map) is what it is worth: good stone, termites, "
                + "herds. Rich ground draws other bands - offers, demands, raids. Hold it with presence and allies.",
                "living/others", 8),
        DESPERATE("Someone hungry", "A desperate band is on its way to you. Stand your ground with the band round you, "
                + "pay - or pack up and hide, and let them find your camp empty (on rich ground they may move onto it).",
                "living/others", 10),
        NIGHT_WATCH("Keep watch", "From erectus, desperate bands raid at night. Ask someone close to you (bond 4+) to "
                + "keep watch - {H}, Danger - and they stay up, walk the camp and wake everyone when trouble comes.",
                "living/others", 10),
        PLACES("Places worth knowing", "Walk into a place worth knowing and hold {K} to think on it: the band remembers "
                + "it, on your map, and it passes down when you evolve. Allies close by share theirs.", "living/places", 2),
        TOOL_PILE("The band's tools", "On your own ground, sneak-use the ground with a stone tool or a bone to lay it "
                + "down. The band takes what it needs from the pile. Hit a pile to see what is in it.", "living/places", 5),
        TROUBLED("Someone is grieving", "Somebody close to the one who died has taken it hard. Look after them - sit "
                + "with them, groom them, give them a stone - or they may stop caring what anyone thinks.",
                "living/troubles", 0),
        FOOD_ILLNESS("Sick from bad meat", "Meat that lay on the ground too long had turned. Drink - a lot, past "
                + "thirst - and eat small: a big meal will not stay down. Hang meat on a rack, or carry it.",
                "body/spoilage", 1),
        COOKING_RACK("The cooking rack", "Meat hung over a lit fire cooks slowly and evenly; take it down empty-handed "
                + "when it is done, or it chars. With no fire under it, food hung there keeps.", "body/cooking", 2),
        BUILDING("Building", "Fill the ghost in: place the right block where it shows, or use one on a ghost block "
                + "to set it straight in. Wrong blocks will not go in. {O} to put a plan away.", "hands/building", 1),
        BUILT("What it is for", "A store keeps food, bones and tools for the band. Yours is for you and your mate. "
                + "Given to someone - more still if they are hurt or with child - it means a great deal.",
                "hands/building", 3),
        ALLY_PLIGHT("An ally in trouble", "An ally's alarm gives you 150 seconds to reach their camp - follow the "
                + "pointer. Drive off what is attacking them and they live, and they will come for you in turn.",
                "living/others", 13),
        ENCOUNTER("They want something", "A band has come to you with an offer or a threat. Accept or decline an "
                + "offer; to a threat, give in, fight - or flee, dropping some of what you carry.", "living/others", 9),
        FIRE_PIT("A fire that keeps", "Fire on bare ground does not keep. At the work station, three logs along the "
                + "bottom and five sticks above make a fire pit: fill it with thatch and sticks, then drill it.",
                "body/fire", 0),
        TORCH("Torches", "A stick and thatch at the work station, three twine in the slot: a torch. Light it at any "
                + "fire, and throw it at anything that hunts - fire sends every one of them running.", "body/fire", 4),
        DRY_SEASON("The dry season", "Five hard days: foraging pays less, hunger runs faster, carcasses are thin and "
                + "people steal. Eat meat and marrow, and stay near water.", "body/seasons", 1),
        DRY_SEASON_WAYS("The dry season", "Hard times: hunger runs faster and people steal. Your band can decide how "
                + "to live through it - {H}, Culture: a rule against theft, or that it is fine not to share.",
                "living/culture", 1),
        HOME_RANGE("Hold your ground - or move on", "Four and a half days on the same ground and everything that hunts "
                + "there starts testing your band. Kill predators, keep a fire and build there to raise presence and hold "
                + "it - or pack up (J, Map) and start over somewhere new.", "living/others", 4),
        TERRITORY("Your ground", "Your ground stays where it was made. 150 blocks from it you are warned; past 250 the "
                + "band settles wherever you are. Pack up, then Set territory here, on the map (J).", "living/others", 3),
        RAIDING("Raiding another band", "A weak band that does not hold together breaks fast and drops what it "
                + "carries. A strong, close-knit one stands - and the standing you lose with them does not come back "
                + "quickly.", "living/others", 6),
        OTHER_BANDS("Other bands", "Every band has a name, ground of its own and a standing with you. Taking from their "
                + "ground costs it; trades and gifts earn it. {H}, The others - and J, Map to see where they are.",
                "living/others", 0),
        MIND_FULL("A full mind", "You can only hold so many places in mind. Let one go on the map (J) to make room - "
                + "and ask the band what they remember: {H}, Social.", "living/mental_map", 1),
        // ------------------------------------------------ danger and hunting
        PREDATOR("Something is hunting you", "Double-tap {G} for a threat display - louder with your band behind "
                + "you - or climb: walk into a trunk and hold jump. Most hunters cannot follow you up.",
                "hunting/display", 0),
        FEARLESS("It will not be frightened", "No display moves this one. Get up a tree, or stand together and "
                + "fight it - a club cracks a skull, a spear opens it up.", "hunting/neighbours", 2),
        CROCODILE("Crocodile!", "Hit it hard - 3 damage or more in one blow - and it lets go. A band member hitting "
                + "it works too. Keep back from deep, warm water.", "hunting/neighbours", 9, true),
        HYENA_CLAN("Hyenas on a kill", "Back off past 13 blocks and they settle. Or bluff: double-tap {G} with the "
                + "band behind you - the more of you, the likelier the clan breaks.", "hunting/clans", 1),
        CHIMP_ALPHA("The alpha is testing you", "A chimpanzee alpha standing too close wants you to back down. "
                + "Within five seconds, hand it something or hold {K} to make yourself small.", "hunting/apes", 0),
        MEGAFAUNA("Big game", "It runs hard for a few seconds, then has to stop and catch its breath for ten - that "
                + "is your moment to close in. Keep following: it cannot outlast you.", "hunting/megafauna", 1),
        EARLY_TRACKING("Hold it in your head", "You lost it - hold {K} now to pick its tracks back up. Only habilis "
                + "can learn early tracking, this way, and it makes every kind after you a better hunter.",
                "hunting/edges", 3),
        PERSISTENCE("Pick the trail up", "Hold {K} to find its tracks again - it costs 4 water each time, so drink "
                + "on a long chase. A wounded animal does not heal while you follow.", "hunting/edges", 3);

        private final String title;
        private final String text;
        @Nullable
        private final String entry;
        private final int page;
        private final boolean urgent;

        Tip(String title, String text, @Nullable String entry, int page) {
            this(title, text, entry, page, false);
        }

        Tip(String title, String text, @Nullable String entry, int page, boolean urgent) {
            this.title = title;
            this.text = text;
            this.entry = entry;
            this.page = page;
            this.urgent = urgent;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** No more than one tip in this long, unless one is urgent. */
    private static final long GAP_TICKS = 45 * 20L;
    /** A tip that has to wait its turn is still worth showing for this long. */
    private static final long WAIT_TICKS = 90 * 20L;
    /** Not straight after joining, while the world is still loading in. */
    private static final int SETTLE_TICKS = 200;

    private record Waiting(Tip tip, long until) {
    }

    private static final Map<UUID, Long> lastShown = new HashMap<>();
    private static final Map<UUID, List<Waiting>> waiting = new HashMap<>();

    private static TipsData data(ServerPlayer player) {
        return player.getData(Attachments.TIPS);
    }

    // ------------------------------------------------------------ offering one

    /** Something just happened that this tip is about: show it now, or as soon as it can be. */
    public static void offer(ServerPlayer player, Tip tip) {
        TipsData data = data(player);
        if (data.isOff() || data.hasSeen(tip) || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        if (tip.urgent || ready(player, now)) {
            show(player, tip, now);
            return;
        }
        List<Waiting> queue = waiting.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        if (queue.stream().noneMatch(w -> w.tip() == tip) && queue.size() < 4) {
            queue.add(new Waiting(tip, now + WAIT_TICKS));
        }
    }

    private static boolean ready(ServerPlayer player, long now) {
        return player.tickCount > SETTLE_TICKS && !CutsceneGuard.isProtected(player)
                && now - lastShown.getOrDefault(player.getUUID(), -GAP_TICKS) >= GAP_TICKS;
    }

    private static void show(ServerPlayer player, Tip tip, long now) {
        data(player).markSeen(tip);
        lastShown.put(player.getUUID(), now);
        String entry = tip.entry != null && ModList.get().isLoaded("patchouli")
                ? GuideBook.BOOK_ID.getNamespace() + ":" + tip.entry : "";
        PacketDistributor.sendToPlayer(player, new TipPayload(tip.title, tip.text, entry, tip.page, tip.urgent));
    }

    // ------------------------------------------------------------ every two seconds

    /** Tips that wait for a state rather than an event: running dry, going hungry, night falling. */
    public static void tick(ServerPlayer player) {
        if ((player.tickCount + player.getId()) % 40 != 0) {
            return;
        }
        TipsData data = data(player);
        if (data.isOff() || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        if (!ready(player, now)) {
            return;
        }
        List<Waiting> queue = waiting.get(player.getUUID());
        if (queue != null) {
            queue.removeIf(w -> now > w.until() || data.hasSeen(w.tip()));
            if (!queue.isEmpty()) {
                show(player, queue.remove(0).tip(), now);
                return;
            }
        }
        Tip due = due(player, data);
        if (due != null) {
            show(player, due, now);
        }
    }

    /** The most pressing tip this player's situation calls for right now, if any. */
    @Nullable
    private static Tip due(ServerPlayer player, TipsData data) {
        if (!data.hasSeen(Tip.WELCOME) && player.tickCount > 400) {
            return Tip.WELCOME;
        }
        if (!data.hasSeen(Tip.THIRSTY) && Thirst.get(player) <= 8) {
            return Tip.THIRSTY;
        }
        if (!data.hasSeen(Tip.HUNGRY) && player.getFoodData().getFoodLevel() <= 10 && !carriesFood(player)) {
            return Tip.HUNGRY;
        }
        if (!data.hasSeen(Tip.TICKS) && Infestation.of(player) >= 4) {
            return Tip.TICKS;
        }
        long time = player.level().getDayTime() % 24000L;
        boolean dusk = time >= 11500L && time < 13500L && player.level().dimensionType().hasSkyLight();
        if (dusk) {
            Tip night = erectusOn(player) ? Tip.NIGHT_FIRE : Tip.NIGHT;
            if (!data.hasSeen(night)) {
                return night;
            }
        }
        if (!data.hasSeen(Tip.FEED_MEMBER) && carriesFood(player) && Band.all(player).stream()
                .anyMatch(m -> !m.isBaby() && m.isHungry() && m.distanceToSqr(player) < 12.0D * 12.0D)) {
            return Tip.FEED_MEMBER;
        }
        if (!data.hasSeen(Tip.KNAPPING) && carries(player, s -> s.is(ModTags.Items.HAMMERSTONES))) {
            return Tip.KNAPPING;
        }
        if (!data.hasSeen(Tip.BASALT) && carries(player, s -> s.is(ModItems.BASALT_ROCK.get()))) {
            return Tip.BASALT;
        }
        if (!data.hasSeen(Tip.KNAPPING_STATION) && erectusOn(player) && carries(player, s -> s.is(ModItems.HIDE.get()))) {
            return Tip.KNAPPING_STATION;
        }
        if (!data.hasSeen(Tip.TALK) && player.tickCount > 3600 && !Band.all(player).isEmpty()) {
            return Tip.TALK;
        }
        return null;
    }

    // ------------------------------------------------------------ situations with more than one answer

    /** Something has picked you as its target. */
    public static void huntedBy(ServerPlayer player, LivingEntity hunter) {
        offer(player, hunter.getType().is(ModTags.EntityTypes.FEARLESS) ? Tip.FEARLESS : Tip.PREDATOR);
    }

    /** Ready to evolve: what the last step is, for this kind. */
    public static void readyToEvolve(ServerPlayer player, ResourceLocation milestone) {
        offer(player, milestone.equals(BuiltinMilestones.STRIKE_FLAKE) ? Tip.MILESTONE_FLAKE
                : milestone.equals(BuiltinMilestones.FIRE_TRANSFER) ? Tip.MILESTONE_FIRE
                : milestone.equals(BuiltinMilestones.FINE_ACHEULEAN) ? Tip.MILESTONE_ACHEULEAN : Tip.MILESTONE);
    }

    /** The dry season has come. From erectus a band can decide what to do about it. */
    public static void drySeason(ServerPlayer player) {
        offer(player, erectusOn(player) ? Tip.DRY_SEASON_WAYS : Tip.DRY_SEASON);
    }

    /** There is someone in the band who does not care. From erectus there is something to be done. */
    public static void antisocialAbout(ServerPlayer player) {
        if (!erectusOn(player)) {
            offer(player, Tip.ANTISOCIAL);
        } else if (!dev.hominin.evolution.band.Morals.holds(player, dev.hominin.evolution.band.Morals.Moral.REVERSE_DOMINANCE)) {
            offer(player, Tip.ANTISOCIAL_WAYS);
        }
    }

    // ------------------------------------------------------------ the command

    public static void setOff(ServerPlayer player, boolean off) {
        data(player).setOff(off);
        waiting.remove(player.getUUID());
        player.sendSystemMessage(Component.literal(off ? "Tips are off. (/hominin tips on to have them back.)"
                : "Tips are on: you will get a word in your ear when something new comes up.")
                .withStyle(ChatFormatting.GRAY));
    }

    public static void reset(ServerPlayer player) {
        data(player).forgetSeen();
        waiting.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Every tip can be shown again, as the moment for it comes.")
                .withStyle(ChatFormatting.GRAY));
    }

    public static void status(ServerPlayer player) {
        TipsData data = data(player);
        player.sendSystemMessage(Component.literal("Tips are " + (data.isOff() ? "off" : "on") + " - " + data.seenCount()
                + " of " + Tip.values().length + " seen. /hominin tips " + (data.isOff() ? "on" : "off")
                + " to turn them " + (data.isOff() ? "on" : "off") + ", /hominin tips reset to see them all again.")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Opens the guide at a page - what double-clicking a tip does. Patchouli is not a dependency, so it
     * is reached by name; without it there is simply no book to open.
     */
    public static boolean openBook(ServerPlayer player, ResourceLocation entry, int page) {
        try {
            Class<?> api = Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI");
            Object instance = Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
            api.getMethod("openBookEntry", ServerPlayer.class, ResourceLocation.class, ResourceLocation.class, int.class)
                    .invoke(instance, player, GuideBook.BOOK_ID, entry, page);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    // ------------------------------------------------------------ helpers

    private static boolean erectusOn(ServerPlayer player) {
        String era = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage().getPath();
        return !era.startsWith("australopithecus") && !era.equals("ardipithecus") && !era.equals("homo_habilis")
                && !era.equals("homo_rudolfensis");
    }

    private static boolean carriesFood(ServerPlayer player) {
        return carries(player, s -> s.has(DataComponents.FOOD));
    }

    private static boolean carries(ServerPlayer player, java.util.function.Predicate<ItemStack> what) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && what.test(stack)) {
                return true;
            }
        }
        ItemStack off = player.getOffhandItem();
        return !off.isEmpty() && what.test(off);
    }

    public static void forget(UUID player) {
        lastShown.remove(player);
        waiting.remove(player);
    }

    private Tips() {
    }
}
