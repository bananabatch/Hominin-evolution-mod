package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * The band talking among itself. Every so often two of your band near you say something to each other - about what
 * one of them is doing, what the other is doing, what the band is building, the season, the dark coming on, the
 * leader, the strangers over the ridge - and the other answers. Something that has just happened (a birth, a death,
 * a new roof, a kill, a newcomer) is talked over soon after. When one of them says something aloud, to you or to
 * everyone, somebody else near may have something to say about it.
 *
 * <p>Overheard, not addressed to you: {@code <Name> to Other: line}.
 */
public final class Chatter {
    /** Between exchanges, when nothing has happened. */
    private static final int GAP_MIN = 40 * 20;
    private static final int GAP_RANDOM = 45 * 20;
    /** Two people talking are this close to each other... */
    private static final double PAIR = 10.0D;
    /** ...and this close to you, to be overheard. */
    private static final double HEARD = 22.0D;
    /** News is talked over this long after it happened, or not at all. */
    private static final long NEWS_KEEPS = 3 * 60 * 20L;

    private record Talk(List<String> openers, List<String> replies) {
    }

    /** What one of the band is doing, as the doer tells it and as somebody else asks about it. */
    private record Doing(Talk says, Talk asked) {
    }

    private record Pending(UUID player, UUID speaker, UUID to, String line, long at) {
    }

    private record News(String topic, String subject, long at) {
    }

    private static final Map<String, Talk> TALK = new HashMap<>();
    private static final Map<String, Doing> DOING = new HashMap<>();
    private static final Map<String, List<String>> ECHO = new HashMap<>();
    private static final Map<String, String> GOALS = new HashMap<>();

    private static final List<Pending> pending = new ArrayList<>();
    private static final Map<UUID, Long> nextAt = new HashMap<>();
    private static final Map<UUID, List<News>> news = new HashMap<>();

    private static List<String> l(String... lines) {
        return List.of(lines);
    }

    private static void talk(String topic, List<String> openers, List<String> replies) {
        TALK.put(topic, new Talk(openers, replies));
    }

    private static void doing(String what, List<String> says, List<String> sayReplies, List<String> asks,
            List<String> answers, String... goals) {
        DOING.put(what, new Doing(new Talk(says, sayReplies), new Talk(asks, answers)));
        for (String goal : goals) {
            GOALS.put(goal, what);
        }
    }

    static {
        // {n} the one spoken to (in an answer, whoever started it), {m} the one answering - or, in an echo, whoever
        // said the thing being answered; {s} what it is about, {c} where the band sleeps, {l} the leader.
        // ------------------------------------------------------------ plans
        talk("project", l("Excited to see what we do with the {s}.", "The {s} is coming along, isn't it?",
                "When the {s} is done, I want the spot by the wall.", "{n}, how much more does the {s} need?",
                "I keep walking past the {s} just to look at it.",
                "I think the {s} will keep the rain off better than any tree.",
                "The {s} needs more thatch. Everything always needs more thatch.",
                "{n}, do you think the {s} will stand through the rains?"),
                l("Me too. Somewhere to come back to.", "Slowly. Somebody keeps sitting down.",
                        "You'll have to fight me for that spot.", "More branches. Always more branches.",
                        "Ask {l}. They'll know.", "It'll be worth it when the rains come.", "Then go and cut some.",
                        "We never had anything like it. Not ever.", "If we build it right, it will."));
        talk("party", l("Our party should be back soon.", "{n}, do you think they got there yet?",
                "I hope the others are all right out there.", "It's quiet with some of us gone."),
                l("They'll be back.", "They're strong. They'll manage.", "I'd have gone. Nobody asked me.",
                        "Stop worrying. You're making me worry."));
        talk("pile", l("Something should go on the Pile, {n}.", "What do you think {l} will lay on the Pile?",
                "The Pile is waiting. It should have something good."),
                l("Something good, I hope.", "It's not my place to say.", "The best thing. That's the point of it.",
                        "It will. {l} won't forget."));
        talk("feast", l("Not long to the feast, {n}. I'm hungry already.", "Bring everything you find to the fire - it's "
                + "for the feast.", "{n}, have you put anything by for the feast yet?",
                "When did we last eat so much we couldn't move?", "Hang the meat on the rack, {n}. Not in your mouth.",
                "Is there enough on the pile yet, do you think?"),
                l("I've got my hands full of berries.", "Some. Don't tell anyone how much I ate.",
                        "Never. That's what the feast is for.", "I'm going out again after this.",
                        "Ask {l} if we have enough.", "Never enough. Go and get more."));
        // ------------------------------------------------------------ what just happened
        // ------------------------------------------------------------ two to lead them ({s}: the co-leader)
        talk("co_leader", l("{n}, who do we listen to now - {l} or {s}?", "Two to lead us. {l} and {s}. I don't mind it.",
                "{s} doesn't know all our ways yet, {n}.", "I asked {s} where we sleep tonight. They said ask {l}.",
                "{s} went off on their own again. Somebody should go with them.",
                "When {l} and {s} don't agree, who wins, {n}?", "{s} brought me something today. I didn't even ask.",
                "{s} and {l} were talking half the night. Plans, I think.", "I like {s}. They listen.",
                "{s} carries more than {l} does. Don't tell {l}.", "If {l} is off somewhere, I go to {s}.",
                "{s} keeps watching the grass. Like they've lost somebody out there."),
                l("Both. Whoever is nearer.", "{s} is one of us now. Leave it.", "They'll learn, {m}.",
                        "Better two than one.", "Ask {s}. They're right there.", "As long as they both come back.",
                        "{s}? Good hands. I've seen them knap.", "It's more mouths to keep happy, that's all.",
                        "Two heads, {m}. Twice the ideas, twice the arguing."));
        // ------------------------------------------------------------ kuru ({s}: the one who has it)
        talk("kuru", l("{s} keeps shaking, {n}. And it isn't the cold.", "It's not just the shaking with {s}. "
                + "Something is going on behind it.", "{s} laughed at nothing again. Nothing at all.",
                "Whatever {s} has, it started after the feast. I'd swear to it.", "Don't eat from {s}'s hand, {n}.",
                "{s} dropped the stone twice today. Their hands won't do what they're told.",
                "It's something they ate, {n}. It has to be. Something nobody else ate."),
                l("Something's wrong. Something none of us can see.", "It came from eating, I think. From what we ate.",
                        "Keep the little ones away from them.", "There's nothing to be done, {m}. Only watch.",
                        "Don't say it out loud.", "What did they eat that we didn't?"));
        talk("news_coleader", l("{s} leads with {l} now. Did you see?", "{n}, we have two to lead us - {s}, and {l}.",
                "{s} is one of us now. Make them welcome.", "Another one to answer to. {s}, is it?"),
                l("I saw. They seem all right.", "Good. {l} needed the help.", "We'll see what {s} is made of.",
                        "Two to lead us. Why not?"));
        talk("news_feast", l("A feast! For {s}!", "{n}, did you hear? We feast in two days.",
                "A feast, for {s}. We'd better start gathering."),
                l("I'm going hunting right now.", "Two days. I can wait two days.", "Everyone will be there.",
                        "Then stop talking and go gather."));
        talk("news_feasted", l("I can't move, {n}.", "That was the best feast I've ever had.",
                "I'll never be hungry again.", "{n}, I think I love everyone."),
                l("Me neither. Don't make me laugh.", "Every single one of us, full.", "Give it a day.",
                        "Go to sleep. You're full."));
        talk("news_super_weapon", l("Did you see the {s}, {n}? Nothing will outrun that.", "A {s}! Can I hold it?",
                "With a {s} we could take anything."),
                l("Not a chance. Get your own.", "Show me how it's made.", "Anything. Even the big ones.",
                        "Careful with it."));
        talk("news_built", l("The {s} is done! I'm sleeping in it tonight.", "Did you see the {s}, {n}? We made that.",
                "I keep touching the walls of the {s}. It's real.", "A {s}. Our own. Who'd have thought."),
                l("I carried half of it.", "Took long enough.", "I want the corner out of the wind.",
                        "Our own place. I like the sound of that.", "Don't get used to it. Things fall down."));
        talk("news_birth", l("A new little one! Did you hear them?", "{n}, did you see the baby? So small.",
                "Another mouth. And a good one.", "The band's growing again."),
                l("Hard to miss. They're loud.", "They'll be carrying things before we know it.",
                        "I'll watch them when the others are busy.", "Good. We need more of us.",
                        "Small now. Not for long."));
        talk("news_death", l("I keep looking for {s}.", "{n}... it's quiet without {s}.",
                "{s} would have known what to do today.", "I dreamed about {s} last night."),
                l("Me too.", "Don't. Not yet.", "{s} would want us to keep going.", "I know. I know.",
                        "We'll remember. That's what we do."));
        talk("news_kill", l("Did you see the {s} go down, {n}?", "That {s}! I thought it would never fall.",
                "We'll eat well off that {s}."),
                l("I hit it first. Tell everyone.", "My arms are still shaking.", "We eat well tonight.",
                        "Next time you chase and I throw.", "Save me the good part."));
        talk("news_lost", l("It got away. All that running.", "{n}, we should have cut the {s} off at the water."),
                l("There'll be another.", "My legs are done.", "Next time we start earlier.",
                        "You say that now."));
        talk("news_join", l("So {s} is one of us now.", "{n}, what do you make of {s}?", "Another pair of hands. Good."),
                l("Watch how they share first.", "They seem all right.", "They'll learn our ways.",
                        "More of us is better than fewer.", "They eat a lot, I noticed."));
        talk("news_ally", l("{s} - friends now. Who'd have thought.", "{n}, with {s} on our side, the cats won't dare."),
                l("Friends until the dry season, maybe.", "Good. Fewer people to fight.", "I like their young ones.",
                        "We'll see."));
        // ------------------------------------------------------------ the day, the season, the world
        talk("hunt", l("Stay low, {n}. Keep downwind.", "Don't let it see you.", "Ready? Wait for it.",
                "Cut it off before the water, {n}."),
                l("I'm ready.", "Quiet! You'll scare it off.", "Go - I'm right behind you.", "I've got it. Go."));
        talk("dusk", l("Getting dark. Back to {c} soon.", "{n}, the light's going.",
                "I don't like being out when the sun goes.", "The cats will be moving soon."),
                l("Right behind you.", "One more thing, then we go.", "Stay close to me tonight.",
                        "Let them move. We'll be ready."));
        talk("night", l("Can't sleep. Can you, {n}?", "Did you hear that?", "The stars are out. All of them.",
                "Something's moving out there."),
                l("Shh. Go to sleep.", "It's nothing. Probably.", "I'll keep an eye out.", "It's just the wind. Sleep."));
        talk("dawn", l("Morning. Did you sleep, {n}?", "Up already?", "Cold this morning.", "What do we do today?"),
                l("Barely.", "Somebody snores.", "Ask {l}.", "Eat first. Then we'll see."));
        talk("rain", l("Rain again.", "{n}, get out of the wet.", "At least the water holes will fill."),
                l("I like the rain.", "My hair will never dry.", "Good for the grass. Bad for me."));
        talk("dry", l("The water holes are shrinking, {n}.", "Everything's so dry.", "I'm always thirsty now.",
                "Hard days. We have to stick together."),
                l("We'll find more water. We always do.", "Don't drink it all at once.",
                        "It'll rain again. It always does.", "Together. Yes."));
        talk("green", l("Everything's green again!", "So much to eat, {n}.", "The herds are fat this season."),
                l("Eat while you can.", "Don't get used to it.", "Best time of the year."));
        talk("hungry", l("I'm so hungry, {n}.", "Do you have anything to eat?", "My stomach won't be quiet."),
                l("We all are.", "Here - no. I ate it. Sorry.", "Go and dig some roots, then.",
                        "Ask {l}. They always have something."));
        talk("hungry_pile", l("I'm so hungry, {n}.", "Do you have anything to eat?"),
                l("There's food on the pile. Go on.", "The pile, {n}. That's what it's for."));
        talk("hurt", l("How's the wound, {n}?", "{n}, sit down. You're bleeding.", "Does it still hurt?"),
                l("It'll heal.", "Worse than it looks. I think.", "Don't fuss.", "Only when I move."));
        talk("young", l("The little ones are growing fast.", "{n}, look at that one run!", "The small ones never stop."),
                l("Remember when we were that small?", "They'll be taller than you soon.", "I wish I had their legs."));
        talk("leader_warm", l("{l} knows what they're doing.", "I'd follow {l} anywhere, {n}.", "{l} looks after us."),
                l("Mostly.", "So would I.", "Don't let them hear you. It'll go to their head."));
        talk("leader_cold", l("Why do we follow {l} again?", "{l} doesn't know where we're going.",
                "I'd lead better than {l}."),
                l("Quiet. They'll hear you.", "Then you lead.", "Give them time.", "Maybe. Maybe not."));
        talk("bonobos", l("The bonobos are close. I like it here.", "{n}, the bonobos brought fruit again.",
                "Nobody fights around the bonobos. Have you noticed?"),
                l("Nobody fights around them.", "They groomed me earlier. Every tick gone.", "I wish we could stay.",
                        "Don't get soft, {n}."));
        talk("strangers", l("Strangers, {n}. Over there.", "Another band. Keep an eye on them.",
                "Do you think they'll come closer?"),
                l("Let them look.", "Stay close to me.", "There are more of us. I think.", "If they come, they come."));
        talk("idle", l("{n}, remember that cat by the river?", "I found a good spot for roots.", "My feet ache.",
                "Do you ever wonder what's past the hills?", "You're in my spot, {n}.", "I had the strangest dream.",
                "Nice day, for once.", "{n}, you've got something in your hair.", "Did you hear the baboons this morning?",
                "I'm going to find the biggest termite mound there is."),
                l("Don't remind me.", "Show me later.", "Everybody's feet ache.", "Sometimes. Then I eat and forget.",
                        "It's not your spot. It's anyone's spot.", "Tell me about it.", "Don't say that. It'll change.",
                        "Get it out, then.", "Who didn't?", "You say that every day."));
        // ------------------------------------------------------------ what they are doing
        doing("build",
                l("This {s} won't build itself.", "Hold that branch, {n} - no, higher.", "One more for the {s}.",
                        "Stand back, {n}. This one's heavy."),
                l("I'm helping! Look.", "Higher? It's taller than me already.", "You say that every time.",
                        "It'll be fine."),
                l("How's the {s} coming, {n}?", "Need a hand with that?"),
                l("Slowly.", "Hand me that branch, then.", "Nearly there. I think.", "Ask me when it's standing."),
                "BuildHelpGoal", "LabourGoal");
        doing("craft",
                l("Watch this edge, {n}.", "Nearly got it. Don't bump me.", "One more strike should do it.",
                        "This stone is fighting me."),
                l("Careful of your fingers.", "Show me how when you're done.", "Nice. Very nice.",
                        "Hit it at the edge, not the middle."),
                l("What are you making, {n}?", "Can I have the next one?"),
                l("Something sharp. Stand back.", "You'll see.", "Make your own.", "Watch and learn."),
                "ErectusCraftGoal", "CraftGoal", "TinkerGoal");
        doing("thatch",
                l("Nobody cuts grass as fast as me.", "Thatch, thatch, always more thatch."),
                l("Then cut some more.", "My hands are all cuts from it."),
                l("More grass, {n}?"),
                l("The roof won't thatch itself.", "The beds need it."),
                "GatherThatchGoal");
        doing("hide",
                l("There's a good hide on that one, {n}.", "We need hides. I'll get one."),
                l("Take someone with you.", "Bring the meat back too."),
                l("Going after something, {n}?"),
                l("Something with a good skin.", "Back before dark."),
                "HideHuntGoal");
        doing("forage",
                l("Roots here, {n}. Good ones.", "I can smell fruit."),
                l("Save me some.", "Dig deeper, they're bigger down there."),
                l("Find anything, {n}?"),
                l("Grubs. Want one?", "Not yet.", "Enough for me."),
                "ForageGoal");
        doing("quarry",
                l("This stone rings right. Hear it?", "Good stone here, {n}."),
                l("Take two.", "Carry it yourself."),
                l("Found good stone, {n}?"),
                l("Maybe. Listen to it.", "Nothing that breaks clean."),
                "QuarryGoal");
        doing("termite",
                l("The termites are biting today.", "Hold the stick still - there!"),
                l("Save me some.", "You're better at that than me."),
                l("Any termites, {n}?"),
                l("Loads. Get a stick.", "Not yet. Patience."),
                "TermiteFishGoal");
        doing("nest",
                l("A good bed tonight, {n}. Watch.", "Bend it like this and it holds."),
                l("Make me one too.", "Mine's better."),
                l("Making your bed already, {n}?"),
                l("Before the light goes.", "Want me to make yours too?"),
                "NestBuildGoal");
        doing("groom",
                l("Hold still, {n}. There's a tick.", "Your turn next."),
                l("Ow.", "Mm. That's the spot."),
                l("Who are you picking at, {n}?"),
                l("Mind your own fur.", "Wait your turn."),
                "GroomGoal");
        doing("pile",
                l("Putting this by for later.", "The pile's getting big, {n}."),
                l("Good. For the hard days.", "Don't take the best ones."),
                l("What are you taking, {n}?"),
                l("Only what I need.", "Just putting it back."),
                "StoreGoal", "StockGoal", "ToolPileGoal");
        doing("scavenge",
                l("Vultures, {n}. Something's dead.", "I can smell a kill."),
                l("Watch for hyenas.", "I'll bring a stone for the bones."),
                l("Where are you off to, {n}?"),
                l("Something's dead over there.", "Bones. Marrow."),
                "ScavengeGoal");
        doing("watch",
                l("I'll keep watch, {n}.", "Something's out there in the grass."),
                l("Shout if you see anything.", "I'll stay close."),
                l("Anything out there, {n}?"),
                l("Not yet.", "Just the wind. For now."),
                "SentryGoal");
        doing("bathe",
                l("The water's cool, {n}! Come in.", "Ahh. That's better."),
                l("Watch for crocodiles.", "Too cold for me."),
                l("Nice in there, {n}?"),
                l("Come and see.", "Better than the heat."),
                "BatheGoal");
        doing("play",
                l("Catch me, {n}!", "You can't catch me!"),
                l("Too old for that.", "Watch me."),
                l("Who's winning, {n}?"),
                l("Me. Obviously.", "Not you."),
                "PlayGoal", "WrestleGoal");
        doing("sharpen",
                l("A sharp stick, just in case.", "Something's been watching us, {n}."),
                l("Make me one too.", "Good idea."),
                l("Expecting something, {n}?"),
                l("Always.", "Better sharp than sorry."),
                "SharpenStickGoal");
        // ------------------------------------------------------------ somebody else's words, answered
        ECHO.put("built", l("Took long enough.", "I carried most of it, you know.", "Now we'll never want to leave."));
        ECHO.put("bone", l("Save me the marrow!", "I'll find a stone.", "Don't eat it all, {m}."));
        ECHO.put("season_dry", l("Don't say it. I know.", "We'll manage. We always do."));
        ECHO.put("season_green", l("Finally!", "Eat while you can."));
        ECHO.put("new_favourite", l("Save some for me.", "You say that about everything, {m}."));
        ECHO.put("theft_victim", l("Don't look at me.", "Did you look under your bed?"));
        ECHO.put("turn_in", l("Night, {m}.", "Me too. Soon."));
        ECHO.put("bonobo_like", l("Me too.", "I could stay here forever."));
        ECHO.put("share_thanks", l("Where's mine?", "You spoil {m}."));
        ECHO.put("obsidian_keep", l("Nobody wants your glass, {m}.", "Let me see it. Just see it."));
        ECHO.put("to_leader", l("They say that to everyone, {l}.", "Don't listen to {m}.", "{m}'s right, you know.",
                "Ha. And {m} means it.", "Every day with this one.", "Same here."));
    }

    // ------------------------------------------------------------ hooks

    /** Something happened worth talking over: a birth, a death, a new roof, a kill, a newcomer, an ally. */
    public static void news(@Nullable ServerPlayer player, String topic, String subject) {
        if (player == null || !TALK.containsKey(topic)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        List<News> list = news.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        if (list.stream().anyMatch(n -> n.topic().equals(topic) && now - n.at() < 600L)) {
            // Heard about already: one kill is one piece of news, however many ways it was counted.
            return;
        }
        list.add(new News(topic, subject, now));
        while (list.size() > 3) {
            list.remove(0);
        }
        // Talked over soon - not in the same breath.
        nextAt.put(player.getUUID(), Math.min(nextAt.getOrDefault(player.getUUID(), Long.MAX_VALUE),
                now + 80 + player.getRandom().nextInt(160)));
    }

    /** One of the band has said something aloud; somebody near may answer it. */
    public static void echo(BandMember speaker, String kind) {
        List<String> answers = ECHO.get(kind);
        if (answers == null || !(speaker.leaderPlayer() instanceof ServerPlayer player)
                || speaker.getRandom().nextInt(100) >= 45) {
            return;
        }
        BandMember other = partnerFor(speaker, player);
        if (other == null) {
            return;
        }
        String line = fill(Lines.pickFrom("echo|" + kind, fitting(answers, other), player.getRandom()), other, speaker,
                player, "");
        if (!line.isEmpty()) {
            queue(player, other, kind.equals("to_leader") ? null : speaker, line,
                    player.serverLevel().getGameTime() + 40 + player.getRandom().nextInt(30));
        }
    }

    /** Every tick per player: answers due, and now and then a new exchange. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 10 != 3 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        deliver(player, level, now);
        long next = nextAt.computeIfAbsent(player.getUUID(), k -> now + GAP_MIN + player.getRandom().nextInt(GAP_RANDOM));
        if (now < next) {
            return;
        }
        nextAt.put(player.getUUID(), now + GAP_MIN + player.getRandom().nextInt(GAP_RANDOM));
        start(player, level, now);
    }

    public static void forget(UUID player) {
        nextAt.remove(player);
        news.remove(player);
        pending.removeIf(p -> p.player().equals(player));
    }

    // ------------------------------------------------------------ an exchange

    private static void start(ServerPlayer player, ServerLevel level, long now) {
        List<BandMember> near = Band.ownNear(player, HEARD);
        near.removeIf(m -> m.isBaby() || m.isSleeping() || m.inDanger() || Parties.away(m)
                || (m.getTarget() != null && !m.isHunting()));
        if (near.size() < 2) {
            return;
        }
        RandomSource random = player.getRandom();
        BandMember speaker = near.get(random.nextInt(near.size()));
        BandMember listener = null;
        double best = PAIR * PAIR;
        for (BandMember other : near) {
            double d = other.distanceToSqr(speaker);
            if (other != speaker && d <= best && (listener == null || random.nextBoolean())) {
                listener = other;
            }
        }
        if (listener == null) {
            return;
        }
        Choice choice = choose(player, level, speaker, listener, now, random);
        if (choice == null) {
            return;
        }
        String opener = fill(Lines.pickFrom("chat|" + choice.key, fitting(choice.talk.openers(), choice.opens),
                random), choice.answers, choice.opens, player, choice.subject);
        if (opener.isEmpty()) {
            return;
        }
        say(player, choice.opens, choice.answers, opener);
        List<String> replies = fitting(choice.talk.replies(), choice.answers);
        if (!replies.isEmpty()) {
            String reply = fill(Lines.pickFrom("reply|" + choice.key, replies, random), choice.opens, choice.answers,
                    player, choice.subject);
            queue(player, choice.answers, choice.opens, reply, now + 50 + random.nextInt(40));
            // Now and then somebody else has a word to add.
            if (near.size() > 2 && random.nextInt(5) == 0) {
                BandMember third = near.get(random.nextInt(near.size()));
                if (third != choice.opens && third != choice.answers) {
                    // Either a word on what was said ({m} is who said it), or an answer of their own.
                    boolean aside = random.nextBoolean();
                    List<String> more = aside ? fitting(ECHO.get("to_leader"), third)
                            : fitting(choice.talk.replies(), third);
                    String picked = Lines.pickFrom("third|" + choice.key, more, random);
                    String line = aside ? fill(picked, third, choice.opens, player, choice.subject)
                            : fill(picked, choice.opens, third, player, choice.subject);
                    if (!line.isEmpty() && !line.equals(reply)) {
                        queue(player, third, null, line, now + 110 + random.nextInt(50));
                    }
                }
            }
        }
    }

    private record Choice(String key, Talk talk, BandMember opens, BandMember answers, String subject) {
    }

    /** What the two of them talk about, weighted by what is going on. */
    @Nullable
    private static Choice choose(ServerPlayer player, ServerLevel level, BandMember speaker, BandMember listener, long now,
            RandomSource random) {
        List<Choice> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        // News first, if there is any fresh.
        List<News> fresh = news.get(player.getUUID());
        if (fresh != null) {
            fresh.removeIf(n -> now - n.at() > NEWS_KEEPS);
        }
        if (fresh != null && !fresh.isEmpty()) {
            News item = fresh.remove(0);
            return new Choice(item.topic(), TALK.get(item.topic()), speaker, listener, item.subject());
        }
        String speakerDoes = doing(speaker);
        if (speakerDoes != null) {
            add(options, weights, 5, new Choice("do_" + speakerDoes, DOING.get(speakerDoes).says(), speaker, listener,
                    projectName(level, player)));
        }
        String listenerDoes = doing(listener);
        if (listenerDoes != null) {
            add(options, weights, 4, new Choice("ask_" + listenerDoes, DOING.get(listenerDoes).asked(), speaker, listener,
                    projectName(level, player)));
        }
        String project = projectName(level, player);
        if (!project.isEmpty()) {
            add(options, weights, 4, topic("project", speaker, listener, project));
        }
        if (speaker.isHunting() && speaker.getTarget() != null) {
            add(options, weights, 5, topic("hunt", speaker, listener, ""));
        }
        long time = level.getDayTime() % 24000L;
        if (time >= 11500L && time < 13000L) {
            add(options, weights, 4, topic("dusk", speaker, listener, ""));
        } else if (time >= 13000L && time < 22500L) {
            add(options, weights, 3, topic("night", speaker, listener, ""));
        } else if (time >= 22500L || time < 1500L) {
            add(options, weights, 3, topic("dawn", speaker, listener, ""));
        }
        if (level.isRainingAt(speaker.blockPosition())) {
            add(options, weights, 3, topic("rain", speaker, listener, ""));
        }
        if (dev.hominin.evolution.survival.Seasons.strained(level)) {
            add(options, weights, 3, topic("dry", speaker, listener, ""));
        } else {
            add(options, weights, 1, topic("green", speaker, listener, ""));
        }
        if (speaker.isHungry()) {
            boolean pile = Mood.foodPiledNear(player) >= 4;
            add(options, weights, 4, topic(pile ? "hungry_pile" : "hungry", speaker, listener, ""));
        }
        if (listener.isInjured()) {
            add(options, weights, 4, topic("hurt", speaker, listener, ""));
        }
        if (!level.getEntitiesOfClass(BandMember.class, speaker.getBoundingBox().inflate(16.0D),
                m -> m.isBaby() && m.isLedBy(player)).isEmpty()) {
            add(options, weights, 1, topic("young", speaker, listener, ""));
        }
        int bond = speaker.getBond();
        if (bond >= 4 && !speaker.isAntisocial()) {
            add(options, weights, 2, topic("leader_warm", speaker, listener, ""));
        } else if (bond <= 0 || speaker.isAntisocial() || Cohesion.get(player) < 15) {
            add(options, weights, 2, topic("leader_cold", speaker, listener, ""));
        }
        if (!level.getEntitiesOfClass(dev.hominin.evolution.entity.Bonobo.class, speaker.getBoundingBox().inflate(24.0D))
                .isEmpty()) {
            add(options, weights, 3, topic("bonobos", speaker, listener, ""));
        }
        if (!level.getEntitiesOfClass(BandMember.class, speaker.getBoundingBox().inflate(40.0D),
                m -> m.isAlive() && m.isWild() && m.getBandId() != null && !m.isGuestOf(player)).isEmpty()) {
            add(options, weights, 3, topic("strangers", speaker, listener, ""));
        }
        if (Parties.awayFrom(player) > 0) {
            add(options, weights, 3, topic("party", speaker, listener, ""));
        }
        if (Feast.preparing(player)) {
            add(options, weights, 4, topic("feast", speaker, listener, ""));
        }
        if (SacredPile.open(player)) {
            add(options, weights, 3, topic("pile", speaker, listener, ""));
        }
        for (BandMember sick : Band.all(player)) {
            if (sick.hasKuru() && sick != speaker) {
                sick.ensureName();
                add(options, weights, 5, topic("kuru", speaker, listener, sick.getName().getString()));
                break;
            }
        }
        List<ServerPlayer> coLeaders = Newcomers.coLeaders(player);
        if (!coLeaders.isEmpty()) {
            // The one who leads with the leader: talked about, as anyone new at the top would be.
            add(options, weights, 3, topic("co_leader", speaker, listener,
                    coLeaders.get(random.nextInt(coLeaders.size())).getGameProfile().getName()));
        }
        add(options, weights, 3, topic("idle", speaker, listener, ""));
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(total);
        for (int i = 0; i < options.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return options.get(i);
            }
        }
        return options.get(options.size() - 1);
    }

    private static Choice topic(String key, BandMember speaker, BandMember listener, String subject) {
        return new Choice(key, TALK.get(key), speaker, listener, subject);
    }

    private static void add(List<Choice> options, List<Integer> weights, int weight, Choice choice) {
        options.add(choice);
        weights.add(weight);
    }

    /** What they are busy with, from the goal they are running - or nothing much. */
    @Nullable
    private static String doing(BandMember member) {
        for (String goal : member.runningGoals()) {
            String what = GOALS.get(goal);
            if (what != null) {
                return what;
            }
        }
        return null;
    }

    /** The first build the band is working on, or "". */
    private static String projectName(ServerLevel level, ServerPlayer player) {
        List<dev.hominin.evolution.build.Sites.Site> projects = ErectusWork.projects(level, player.getUUID());
        return projects.isEmpty() ? "" : projects.get(0).name();
    }

    @Nullable
    private static BandMember partnerFor(BandMember speaker, ServerPlayer player) {
        List<BandMember> near = speaker.level().getEntitiesOfClass(BandMember.class,
                speaker.getBoundingBox().inflate(PAIR), m -> m != speaker && m.isAlive() && m.isLedBy(player)
                        && !m.isBaby() && !m.isSleeping() && !m.inDanger());
        return near.isEmpty() ? null : near.get(speaker.getRandom().nextInt(near.size()));
    }

    private static List<String> fitting(@Nullable List<String> lines, BandMember member) {
        return lines == null ? List.of() : Speech.fitting(lines, member.getStage());
    }

    private static String fill(String line, BandMember to, BandMember by, ServerPlayer player, String subject) {
        if (line.isEmpty() || (line.contains("{s}") && subject.isEmpty())) {
            return "";
        }
        to.ensureName();
        by.ensureName();
        return line.replace("{n}", to.getName().getString()).replace("{m}", by.getName().getString())
                .replace("{s}", subject).replace("{c}", Speech.camp(by.getStage()))
                .replace("{l}", player.getName().getString());
    }

    // ------------------------------------------------------------ saying it

    private static void queue(ServerPlayer player, BandMember speaker, @Nullable BandMember to, String line, long at) {
        pending.add(new Pending(player.getUUID(), speaker.getUUID(), to == null ? null : to.getUUID(), line, at));
    }

    private static void deliver(ServerPlayer player, ServerLevel level, long now) {
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (!p.player().equals(player.getUUID())) {
                continue;
            }
            if (now < p.at()) {
                continue;
            }
            it.remove();
            if (now - p.at() > 200) {
                continue;
            }
            Entity speaker = level.getEntity(p.speaker());
            Entity to = p.to() == null ? null : level.getEntity(p.to());
            if (speaker instanceof BandMember member && member.isAlive() && !member.isSleeping()
                    && member.distanceToSqr(player) <= HEARD * HEARD * 1.5D) {
                say(player, member, to instanceof BandMember other && other.isAlive() ? other : null, p.line());
            }
        }
    }

    private static void say(ServerPlayer player, BandMember speaker, @Nullable BandMember to, String line) {
        speaker.ensureName();
        Component head = Component.literal("<" + speaker.getName().getString() + ">").withStyle(ChatFormatting.GOLD);
        Component whom = Component.literal(to == null ? " " : " to " + to.getName().getString() + ": ")
                .withStyle(ChatFormatting.DARK_GRAY);
        Component said = Component.empty().append(head).append(whom)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(said);
        // Whoever leads the band with them hears it too, if they are near enough.
        for (ServerPlayer co : Newcomers.coLeaders(player)) {
            if (co.distanceToSqr(speaker) <= HEARD * HEARD * 1.5D) {
                co.sendSystemMessage(said);
            }
        }
        if (to != null) {
            speaker.getLookControl().setLookAt(to, 30.0F, 30.0F);
        }
        speaker.playAmbientSound();
        // And what they said, they go and do.
        Intentions.said(speaker, to, line);
    }

    private Chatter() {
    }
}
