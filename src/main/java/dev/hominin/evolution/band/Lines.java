package dev.hominin.evolution.band;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * What band members say, and how often.
 *
 * <p>Every kind of moment has a pool of lines. Three rules keep the chat sounding like a band
 * rather than a loop: nobody says a line that was said for that moment in the last few times;
 * a leader does not hear the same kind of moment twice in a row, back to back; and something
 * everybody notices at once - a carcass full of bones - is announced by whoever saw it first,
 * not by all of them.
 */
public final class Lines {
    /** A spoken line of one kind is heard at most this often by one leader. */
    private static final long SAY_GAP_TICKS = 30 * 20;
    /** Narration of one kind is not repeated back to back within this long. */
    private static final long TELL_GAP_TICKS = 2 * 60 * 20;
    /** How many recent lines of one kind are ruled out of the next pick. */
    private static final int MEMORY = 4;

    private static final Map<String, List<String>> POOLS = new HashMap<>();
    private static final Map<String, Deque<String>> recent = new HashMap<>();
    /** Per leader and kind: when it was last heard. */
    private static final Map<String, Long> heardAt = new HashMap<>();
    /** Per leader: the last kind of moment narrated, and when. */
    private static final Map<UUID, String> lastTold = new HashMap<>();
    private static final Map<UUID, Long> lastToldAt = new HashMap<>();

    private static void pool(String kind, String... lines) {
        POOLS.put(kind, List.of(lines));
    }

    static {
        // ---------------------------------------------------------------- said aloud
        pool("built", "Look at it. Out of the rain at last.", "We made that. All of us.",
                "A roof! I've never slept under one.", "Now that is a place to come back to.");
        pool("bone", "Sweet, a bone!", "There is still marrow in this one!", "Bones! Nobody has cracked these yet.",
                "Look what the hyenas left us.", "This one still has meat on it.", "Plenty of bone here. Bring a stone.",
                "Somebody else did the killing. We get the best part.", "Good heavy bones. Full ones.",
                "The birds missed the good part.", "Marrow! Who has a stone?");
        pool("obsidian_keep", "Look at it. Nothing else breaks like this.", "I am keeping this one. You understand.",
                "Black glass. Mine.", "Don't even ask. It's mine.", "It's sharper than anything. I'm not letting go of it.",
                "See how it shines? No. Look. Don't touch.");
        pool("obsidian_give", "Here. You should have this one.", "You'll use it better than I would. Take it.",
                "Go on. Before I change my mind.", "For you. Don't waste it.");
        pool("new_favourite", "Hmm. I like this a lot.", "Oh, that's the good stuff.", "More of this. Please.",
                "Now that is food.", "Where has this been all my life?", "I could eat this every day.");
        pool("share_thanks", "That's the one I like!", "You remembered.", "Mm. Mine, then.", "Thank you. Really.");
        pool("theft_victim", "Where did it go? It was right here!", "Somebody has been in my things.",
                "I had food. I know I had food.", "Who took it? Who?");
        pool("season_dry", "Everything is dry. We have to be careful now.", "The water holes are shrinking.",
                "It's going to be a hard few days.", "The herds are moving off. The rain has gone with them.");
        pool("turn_in", "I'm going to sleep. I won't be wandering further.",
                "I'm going to sleep now. I won't be wandering any further tonight.",
                "That's me for the night. I'm going to sleep - I won't wander.");
        pool("season_green", "The rain is back! Look at it all.", "Everything is green again.",
                "There will be plenty now. Eat while you can.", "Good days. Finally.");

        // ---------------------------------------------------------------- narrated: " <does something>"
        pool("pile_take", " takes a %s from the band's tool pile.", " goes to the pile and picks out a %s.",
                " has nothing to work with, and fetches a %s from the pile.");
        pool("pile_put", " puts a spare tool back on the pile.", " lays what they do not need on the tool pile.",
                " adds a tool to the pile for whoever needs it next.");
        pool("store_take", " goes to the store and takes a few mouthfuls.", " helps themselves from the store.",
                " is hungry, and eats from what the band put by.");
        pool("store_put", " puts what they cannot eat by in the store.", " lays spare food in the store for later.",
                " adds to the store - more than they can carry, let alone eat.");
        pool("store_marrow", " takes a bone from the store and cracks it for the marrow.",
                " finds nothing to eat in the store but a bone, and breaks it open on a stone.");
        pool("scavenge_go", " has smelled a kill, and goes to find it.",
                " catches the smell of something dead on the wind and slips off after it.",
                " has seen vultures circling, and goes to look.",
                " thinks there is a carcass nearby and goes to find it before the hyenas do.",
                " lifts their nose, and is off towards something dead.",
                " goes to see what the birds keep dropping down on.");
        pool("marrow", " cracks a long bone open with a stone and scoops out the marrow.",
                " splits a bone on a rock and licks the marrow out of it.",
                " works a bone open with a stone. The marrow is worth it.",
                " smashes a shin bone between two stones and eats what's inside.",
                " taps a bone until it splits, then scrapes out the marrow with a finger.");
        pool("quarry_go", " says they're going to find some good stone.",
                " heads off to look for stone that breaks clean.",
                " goes looking for a better cobble than the one they have.",
                " wanders off towards the gravel, looking for chert.",
                " is off to the stream bed to turn over stones.");
        pool("tinker_go", " says they're going to look for some good stones.",
                " goes to find two stones worth striking together.",
                " wants a stone that fits the hand, and goes looking.",
                " picks through the pebbles, looking for one worth working.");
        pool("termite_go", " says they're going to fish for termites.",
                " strips a twig and heads for the termite mound.",
                " has a twig in their mouth and a mound in mind.",
                " goes to see if the termites are biting.");
        pool("nest_go", " says they're making a nest for the night.", " starts bending branches into a nest.",
                " is pulling leaves together for somewhere to sleep.", " goes to make a bed before the light goes.");
        pool("groom", " sits with %s, picking through their hair.", " and %s take turns picking each other clean.",
                " grooms %s, flicking ticks away.", " settles in behind %s and works through their fur.",
                " finds a tick on %s and eats it, pleased.");
        pool("bathe", " wades into the water to cool off.", " splashes water over their head.",
                " sits in the shallows until the heat passes.",
                " cools off in the water, keeping one eye out for crocodiles.");
        pool("forage_go", " says they're going to forage.", " goes to root around for something to eat.",
                " is off to see what's growing.", " goes looking for grubs under the bark.",
                " heads off to dig for roots.");
        pool("arm_urgent", " is tearing a branch off a tree to fight with!", " snaps a branch off - they need something now!",
                " grabs the nearest branch and wrenches it free!");
        pool("arm", " says they're going to find a good branch.", " goes looking for a branch with some weight to it.",
                " wants something to hold on to, and heads for the trees.");
        pool("chase_home", " gives up the chase and heads back to the band.", " lets it go and jogs back to the others.",
                " turns back. It is not worth being alone out here.");
        pool("chase_pant", " gives up the chase, panting.", " stops, hands on knees. It got away.",
                " lets it run. There will be another.");

        // ---------------------------------------------------------------- narrated, and worth hearing
        pool("freeze", " freezes in terror!", " goes rigid, eyes wide.", " can't move - too scared to run.",
                " stops dead, frozen with fear.");
        pool("fight_back", "'s blood is up - they turn and fight!", " stops running and turns on it, screaming!",
                " has had enough - they charge!", " rounds on it, teeth bared!");
        pool("bolt", " bolts in a panic!", " breaks and runs!", " flees, screaming!", " scatters for the trees!");
        pool("sharpen_stick", " gnaws a stick to a point, in case it comes back.",
                " chews the end of a stick sharp, watching the grass.",
                " sharpens a stick with their teeth. Just in case.");
        pool("spot_obsidian", " has spotted obsidian, and can't think about anything else.",
                " saw black glass up there. Nothing else matters now.",
                " has seen the shine of obsidian and is already walking towards it.");
        pool("obsidian_turn", " can't stop turning the obsidian over in their hands.",
                " holds the obsidian up to the light, again.",
                " keeps running a thumb along the obsidian's edge.");
        pool("craft_flake", " strikes a sharp flake off a stone.", " knocks a clean flake off a cobble.",
                " sends a flake spinning off the stone, and grins.");
        pool("craft_chopper", " batters a stone down into a chopper.", " works one edge of a cobble into a chopper.",
                " hammers a cobble until it has a biting edge.");
        pool("craft_grinding", " shapes a flat, rough stone for grinding edges.",
                " rubs two stones flat - something to sharpen on.");
        pool("craft_spear", " whittles a branch into a sharpened spear with a flake.",
                " scrapes a branch to a point with a flake. A spear.");
        pool("craft_pointy", " works a flake along a stick and makes a pointy stick.",
                " shaves a stick to a fine point.");
        pool("craft_multitool", " works a stone on both faces into an Oldowan multitool!",
                " turns a stone over and over, working both faces - a multitool!");
        pool("craft_regrind", " grinds a fresh edge back onto their %s.", " puts a new edge on their %s.",
                " sits and sharpens their %s against a stone.");
        pool("bonobo_like", "I like these ones. They feed us and pick us clean, and want nothing back.",
                "I like how these ones provide and clean for us.", "Nobody fights here. Nobody. I could stay.",
                "They gave the little ones fruit, for nothing.", "The bonobos went through my hair for me. Every tick.");
        pool("craft_bedding", " lays hide over thatch at the work station and binds it with twine - bedding for a bed.",
                " stretches hide over a layer of thatch and ties it down. A bed, nearly.",
                " finishes a length of thatch bedding, and tries it with one hand. Soft.");
        pool("craft_twine", " twists grass stems against each other into twine.", " sits rolling thatch into cord.",
                " twists a few lengths of twine out of the thatch.");
        pool("craft_thatch_block", " binds thatch into blocks for the build.",
                " packs thatch tight and ties it - four blocks for the roof.");
        pool("craft_building_branch", " sets a branch upright in a base of stones. Something to build with.",
                " props branches up in stones - posts for the build.");
        pool("craft_workable_branch", " splits a branch with a hand axe.", " trims a branch clean with a hand axe.");
        pool("craft_club", " batters two branches together into a club.", " makes a club, and swings it once to try it.");
        pool("craft_cleaver", " works a stone into a %s cleaver (tier %s).",
                " sits at the knapping station and takes a straight edge off a stone: a %s cleaver (tier %s).");
        pool("stone_found", "Found one. Rings right, this.", "Here - two good stones. I'll make something.",
                "Told you I'd find one.");
        pool("gravel_found", "Look what was in the gravel!", "Good stone, just lying in the gravel.",
                "The river brings the best stone down.");
        pool("haul_gone", "It was gone. Somebody got there first.", "Nothing there. Just tracks round where it was.",
                "Someone took it - all of it.");
        pool("opinion_against_friend", "They were good to us. Why do this?", "I liked them. I don't like this.",
                "We'll need friends one day. You just lost one.");
        pool("opinion_against_foe", "Good. They had it coming.", "About time somebody did that.",
                "Now they know who we are.");
        pool("opinion_for_foe", "Why are we being nice to them?", "They'll take it and laugh at us.",
                "I wouldn't give them the bones.");
        pool("opinion_for_friend", "Good. They're decent people.", "That's how it should be with them.",
                "They'll remember that.");
        pool("practise_start", "Let me try that!", "Like this? Watch me.", "My turn!", "Wait - let me do it.");
        pool("practise_done", "Ha! It works!", "I've got it now.", "Again - I want to do it again.",
                "Did you see? I did it!");
        pool("roots_again", "Roots? Again? My teeth hate them.", "Roots. Always roots.",
                "My jaw aches just looking at these.", "Fine. Roots. Again.", "If I eat one more root my teeth fall out.");
        pool("abscess", "My tooth... it's swelling.", "Something is wrong with my tooth. It burns.",
                "I can't chew. My face is hot.");
        pool("feast_begin", "The feast! Everyone to the fire!", "Come on - it's the feast!", "Eat! Eat until you can't!",
                "Nobody goes hungry tonight.");
        pool("feast_eat", "Mm. More.", "Pass me that.", "I can't stop.", "This is the best thing I've ever eaten.",
                "Another! Just one more.", "Is there any left on the rack?");
        pool("feast_bring", " brings food to the feast fire.", " lays what they gathered on the food pile by the fire.",
                " puts food by for the feast.");
        pool("feast_hang", " hangs meat on the rack over the fire, for the feast.", " hooks meat onto the spit for the feast.");
        pool("feast_rack", " starts putting up a cooking rack by the feast fire.",
                " goes to raise a rack over a fire pit for the feast.");
        pool("feast_rack_done", " sets the last post: a cooking rack stands by the feast fire.",
                " lays the branch across: a new cooking rack for the feast.");
        pool("fire_feed", " feeds the fire.", " throws more wood on the fire.", " keeps the fire going.");
        pool("fire_light", " works a drill until the fire pit catches.", " gets the fire going.");
        pool("craft_schoningen", " turns a whole shaft through the fire, end to end, until it is hard and black and "
                + "balanced: a Schoningen spear.", " hardens a long shaft in the flames and sights along it. It flies "
                + "true - a Schoningen spear.");
        pool("craft_stone_tipped", " binds a Levallois blade onto a shaft, tight as sinew: a stone-tipped spear.",
                " sets a blade in the end of a shaft and winds the twine on: a stone-tipped spear.");
        pool("craft_hardened", " turns a spear's point in the embers until the wood darkens.",
                " holds a spear in the fire, turning it, until the point is hard.");
        pool("throw_spear", " draws back and throws their spear at the %s!", " hurls a spear - it flies true at the %s!",
                " sets their feet and throws. The spear goes after the %s.");
        pool("throw_rock", " flings a rock at the %s.", " picks a rock from their pack and throws it at the %s.",
                " hurls a stone at the %s, hard.");
        pool("cut_thatch", " goes to cut grass for thatch.", " is off cutting thatch - the band needs it.");
        pool("hunt_hide", " goes after a %s - the band needs hides.", " picks out a %s. There's a hide on that.");
        pool("hunt_hide_done", " brings down the %s. There will be a hide off it.", " has killed the %s. A good skin.");
        pool("build_place", " sets a %s into the %s.", " puts a %s in place on the %s.");
        pool("build_cure", " stretches a hide over raw thatch.", " pegs a hide down over the thatch. It will hold now.");
        pool("bed_laid", " lays a thatch bed down - their own.", " has a bed of their own now, and lies on it to try it.");
        pool("craft_hand_axe", " sits at the knapping station and shapes a %s hand axe (tier %s).",
                " works a stone into a %s hand axe (tier %s), both faces.",
                " takes their time at the knapping station: a %s hand axe (tier %s).");
        pool("tinker_made", " strikes two stones together - and makes a %s!",
                " hits one stone with another, and there's a %s in their hand!",
                " has made a %s, almost by accident.");
        pool("theft", " takes %s's %s while they aren't looking.", " quietly helps themselves to %s's %s.",
                " sneaks %s's %s into their own pack.");
        pool("theft_caught", " is caught with %s's %s. The band makes them hand it back.",
                " tries to take %s's %s - and the whole band sees it. It goes back.",
                " reaches for %s's %s, and is shoved away. Not now. Not here.");
        pool("theft_deterred", " eyes %s's %s, and thinks better of it.",
                " looks at %s's %s for a long moment, then looks away.");

        // ---------------------------------------------------------------- thoughts anyone might have
        pool("thought_any", "The sky is big out here. Bigger than it used to be.",
                "When the rains come, the whole plain turns green. I remember.",
                "Somebody walked here before us. Look at the ground.",
                "The hyenas laugh at night. I don't like it.",
                "I dreamed I was up a tree again.",
                "The little ones are growing fast.",
                "My feet hurt. Good. It means we've come a long way.",
                "Follow the water and you find the herds.",
                "I wonder what's on the other side of those hills.",
                "The vultures always know first.",
                "A good stone is worth carrying all day.",
                "Smoke on the horizon. Something is burning out there.",
                "I can hear the baboons arguing again.",
                "My hands know what to do before I do.",
                "The grass is taller than me here. Anything could be in it.",
                "I like it when we all sleep close together.",
                "There were more of us, once. I think.",
                "The water tastes of mud today.",
                "The flakes are sharper when you strike at the edge, not the middle.",
                "The ground here has good roots under it.",
                "I saw where the termites are. I'll remember.");
        pool("thought_dry", "The ground is cracked like old bone.", "Every water hole is smaller than yesterday.",
                "Nobody is sharing anything this season.", "The herds have gone after the water. We should too.",
                "Five days of this. Maybe more.");
        pool("thought_green", "Everything is green. Eat while you can.", "The herds are fat this season.",
                "So much food even the hyenas look happy.", "Good times. They never last, so enjoy them.");
        pool("thought_norm_dead", "Our dead stay with us. That is right.", "When I go, eat of me together. Promise.");
        pool("thought_share", "Sharing is good. It's always good.", "Nobody should eat alone.");
        pool("thought_tight", "When there isn't enough, you look after your own mouth first.",
                "Hard times. Keep your food close.");
        pool("thought_no_theft", "Nobody takes what isn't theirs. Not now.", "Whoever steals now steals from all of us.");
    }

    public static List<String> pool(String kind) {
        return POOLS.getOrDefault(kind, List.of());
    }

    /** A line of this kind, never one said for it in the last few times. */
    public static String pick(String kind, RandomSource random) {
        List<String> pool = pool(kind);
        if (pool.isEmpty()) {
            return "";
        }
        return pickFrom(kind, pool, random);
    }

    /** The same no-repeat rule over a list made up on the spot - thoughts, say. */
    public static String pickFrom(String memoryKey, List<String> options, RandomSource random) {
        Deque<String> memory = recent.computeIfAbsent(memoryKey, k -> new ArrayDeque<>());
        List<String> fresh = new ArrayList<>();
        for (String option : options) {
            if (!memory.contains(option)) {
                fresh.add(option);
            }
        }
        List<String> from = fresh.isEmpty() ? options : fresh;
        String line = from.get(random.nextInt(from.size()));
        memory.addLast(line);
        while (memory.size() > Math.min(MEMORY, Math.max(0, options.size() - 1))) {
            memory.removeFirst();
        }
        return line;
    }

    /** Whether this leader can hear this kind of thing again yet, and if so, marks it heard. */
    private static boolean fresh(Player leader, String kind, long now, long gap) {
        String key = leader.getUUID() + "|" + kind;
        Long last = heardAt.get(key);
        if (last != null && now - last < gap) {
            return false;
        }
        if (heardAt.size() > 4096) {
            heardAt.clear();
        }
        heardAt.put(key, now);
        return true;
    }

    /** Said aloud to the leader: {@code Name: "line"}. Whoever notices first speaks for everyone. */
    public static void say(BandMember member, String kind) {
        say(member, kind, "");
    }

    public static void say(BandMember member, String kind, String after) {
        Player leader = member.leaderPlayer();
        if (leader == null || member.distanceToSqr(leader) > 64.0D * 64.0D
                || !fresh(leader, "say:" + kind, member.level().getGameTime(), SAY_GAP_TICKS)) {
            return;
        }
        Band.announceDiscovery(member, ": \"" + pick(kind, member.getRandom()) + "\"" + after);
        Chatter.echo(member, kind);
    }

    /**
     * Narrated to the leader, gray: {@code Name <does something>}. Rate-limited as all band
     * news is, and never the same kind of news twice running.
     */
    public static void tell(BandMember member, String kind, Object... args) {
        Player leader = member.leaderPlayer();
        if (leader == null) {
            return;
        }
        long now = member.level().getGameTime();
        if (kind.equals(lastTold.get(leader.getUUID()))
                && now - lastToldAt.getOrDefault(leader.getUUID(), -99999L) < TELL_GAP_TICKS) {
            return;
        }
        String line = String.format(pick(kind, member.getRandom()), args);
        if (Band.announce(member, line)) {
            lastTold.put(leader.getUUID(), kind);
            lastToldAt.put(leader.getUUID(), now);
        }
    }

    /**
     * Narrated in gold - worth hearing whatever else was said lately - but still only once a
     * while for the same kind of thing, and never the same words twice running.
     */
    public static void announce(BandMember member, String kind, Object... args) {
        Player leader = member.leaderPlayer();
        if (leader == null || member.distanceToSqr(leader) > 64.0D * 64.0D
                || !fresh(leader, "announce:" + kind, member.level().getGameTime(), SAY_GAP_TICKS)) {
            return;
        }
        Band.announceDiscovery(member, String.format(pick(kind, member.getRandom()), args));
    }

    /** One member's thought, in the leader's chat. The caller has already chosen it. */
    public static void thought(BandMember member, Player leader, String thought) {
        member.ensureName();
        leader.sendSystemMessage(Component.literal(member.getName().getString() + " thinks: ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(thought).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
    }

    public static void forget(UUID leader) {
        lastTold.remove(leader);
        lastToldAt.remove(leader);
    }

    private Lines() {
    }
}
