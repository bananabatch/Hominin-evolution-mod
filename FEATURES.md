# Hominin Evolution — Features

Everything the mod does today, and everything still planned. For Minecraft 1.21.1 on NeoForge 21.1.244.

Optional companions: **Patchouli** (the in-game guide) and **Player Animator 2.0.4+** (every hominin animation). The mod loads without either.

Anything under **Planned** is not in the game yet.

---

## 1. Stages and evolution

You play a hominin, not a person, and you earn your way up the line.

| Stage | Roughly | Band size | In the mod |
|---|---|---|---|
| Ardipithecus | 4.4 mya | 3–5 | Yes — reached only by dying out completely |
| *A. anamensis* | 4.2 mya | 3–5 | Yes — fallback behind Australopithecus |
| Australopithecus | 3.3 mya | 4–6 | Yes — the starting stage |
| *H. rudolfensis* | 2.5 mya | 5–9 | Yes — fallback beside habilis |
| Homo habilis | 2.4 mya | 7–12 | Yes |
| *H. ergaster* | 1.95 mya | 9–14 | Yes — fallback behind erectus |
| Homo erectus | 1.9 mya | 12–17 | Partly — its gate is a placeholder |
| Homo heidelbergensis | 700 kya | 15–19 | **Planned** |
| Homo sapiens | 300 kya | 25–40 | **Planned** |
| Homo neanderthalensis | 400 kya | 18–25 | **Planned** |

**Passing a stage**
- **Required criteria**, all of them. Australopithecus: notice a stone deposit, survive 2 days.
- **An optional pool**, any 3 of 5. Australopithecus: climb 8 trees, forage in 3 places, stun a predator, hunt twice with a sharpened stick, drink in 2 places.
- **A milestone** — the invention that defines the stage: striking a flake for Australopithecus, carrying fire for habilis.
- Fallback species ask less: 1 required criterion and 2 of 4.

**Evolving**
- A cutscene, deep time passing, and your descendants wake somewhere else in the world.
- You lose everything but the guidebook. Each stage has an arrival kit — habilis gets a stick and a flake, or half the time a hammerstone with 3 chert or 6 quartzite.
- Your old band stays in the past; a new one forms around you, and bands of your new species appear nearby while the species you left begins to die out.

**Dying out, and falling back**
- Lose your whole band and you are penalised. Lose 3 bands and your species dies out.
- You then drop to a **fallback species** rather than ending — anamensis, rudolfensis or ergaster — with a cutscene reading *"You died out as a species. You are not out yet,"* and how far time was rewound (50,000 years for ergaster, 900,000 for anamensis).
- Their road leads back to the stage you fell from. Lose **2** bands there and the line is finished: Ardipithecus, under `homininSuperHardMode`.
- Fallback species also live out there as neighbouring bands in their era, whatever you are.
- `homininExtraEffort` (world setting, off by default) makes them mandatory rungs instead: habilis → ergaster → erectus.

**Losing the whole band**
- A panic attack: the screen goes dark, *"There is nobody left,"* and you come back to yourself about 90 blocks away with a new band around you.
- The hominin you were stays sitting where it happened, head in hands, carrying your name. Come back within 5 blocks and it joins you — taking a name of its own, and your current stage.
- If you are dead or already watching a cutscene, there is no walk: you simply wake with a new band. Cutscenes never stack or queue, and you cannot be hurt during one.

**Your own death**
- You do not respawn as yourself. A cutscene hands you the nearest member of your band and you carry on as them, with what they were carrying.

---

## 2. The player's body

- **Thirst:** ten drops above the health bar. Drains faster running, in heat and under open sky; slower in cool weather, rain or water. Below 6 you slow; empty costs health.
- **Hunger** is vanilla's, fed by foraging, scavenging and hunting.
- **Carrying limits by stage:** the hotbar only (9) through habilis; erectus gets a second row; heidelbergensis and sapiens three; only Neanderthals carry all 36. Locked slots are greyed out, and anything left in one is dropped.
- **Climbing:** any block up to 4 blocks, or a trunk as far as it goes. Climbers pass through leaves and can stand on the canopy. Australopithecus climbs best, erectus worst.
- **Falling:** any fall damage at all, once, earns the Lucy achievement.
- **Threat display (G):** pant-hoot, leap and frighten what is near. Your band joins in; every voice widens the radius and raises the odds. Erectus adds 30% on its own, habilis 10%. Three displays at nothing earns an achievement and a screen flash.
- **Throwing:** sneak-use a rock or branch. Before erectus it is a wild heave; erectus throws straight and hard.
- **Thinking (hold K):** needs 7 shanks of food and 60% health, on a 3-minute cooldown. It is how every recipe is first worked out — and, once something has run from you, how you keep hold of the chase instead. With empty hands it wanders instead: the day, the night, hunger, thirst, the size of the band, the bones you are carrying.
- **Bleeding, in three tiers.** *External* (teeth, flakes) bleeds and stops. *Internal* (spears, hyenas, the big cats) bleeds harder and stops you healing while it runs. *Catastrophic* is a clock: 60 seconds, and the only way out is 24 water — four drinks, or three full eggshells. Survive and you are lacerated until dawn; get hurt again or eat raw meat before then and it goes bad — an infection that blocks healing for two days, drains hunger and makes you sick.
- **Ticks.** At most two a day. Past six, nothing heals until somebody grooms them off you.
- **Healing:** exactly one thing can ever stop you mending — blood loss, infection, lacerations or ticks. A worse affliction replaces a lesser one and wipes it; a lesser one arriving while something worse runs changes nothing. One cause, one timer, and it always tells you which.

---

## 3. Tools and materials

**Stone**
- **No mining at all.** Bare hands take only what is soft enough to pull up: leaves, grass, fruit, mushrooms, nests, carcasses, loose rocks. Everything else needs the right tool, and stone stays in the ground until far later. You cannot break a deposit and you cannot put one down.
- Loose surface scatters and outcrops of chert, quartzite and limestone.
- Two loose cobbles make a hammerstone; a hammerstone opens deposits.
- A worked face gives 2–3 rocks: 22% chance of a hammerstone on quartzite, 18% on chert, 22% of a chert nodule. Loose quartzite gives one 25% of the time. Plain country rock is empty 40% of the time.
- **A seam holds two round cobbles and no more.** It keeps giving stone forever, but once its hammerstones are out it is flat rubble — so hammerstones mean walking to the next outcrop rather than standing at one.
- Limestone will not hold an edge. It is good for grinding stones and nothing else.

**Knapping** (P, holding a rock with a hammerstone in the off hand)
- A menu of what you could try: flake, chopper, multi tool, split the core.
- An Oldowan multi tool costs 8 chert, 1 chert hammerstone or 2 obsidian, and splits back down into 12 chert. Chert stacks to four, so the cost is counted and paid across your whole pack, not one stack.

**Hand recipes** (P, one thing in each hand)
- Nest; pointy stick (plain or sharpened stick + flake); sharpened spear; chopper; digging stick; wooden club; fire-hardened spear; **twine** (thatch + thatch); **fire drill** (stick + stick).
- Most must be thought of first. Trying one you have not worked out fumbles, and sometimes breaks the material.

**What the tools do**
- Flakes cut, crack bones and wear out.
- Sharpened and pointy sticks hunt; pointy sticks cut.
- Digging stick: foraging succeeds 75% of the time and brings up 1–3 insects at once.
- Long branch and club: concussions, fractures, brain bleeds.
- Spears cause the worst bleeding.
- Grinding rock puts an edge back on a worn tool.
- **Thatch** comes from cutting grass, ferns, reeds or dead bush with an edge — a flake, chopper or multi tool. Torn up by hand it gives nothing: the length of the stalk is the whole point.
- **The fire drill** is two sticks spun together, and it is the only route to fire that does not need fire to already exist. Use it on dry ground with room above and it lights one — which counts for the habilis milestone exactly as carrying flame off a lightning strike does. It refuses in the rain and is spent doing it.

**Food and water**
- Foraging soil for grubs, beetles and worms; stripping berry bushes; termite-fishing with a stick, which survives the meal.
- Cracking long bones with a flake for marrow; cutting meat into chunks.
- **Drinking:** right-click water with an empty hand, or stand in it. The game's crosshair ignores fluids, so the click is traced again server-side.
- **Eggshells:** hold a sharpened or pointy stick in your off hand and use an egg — you drink it out and keep the shell. An empty shell fills at any water and carries a mouthful.

---

## 4. Scavenging and hunting

- Everything that dies leaves a **carcass** — a 3D ribcage block. Animals also die on their own of exhaustion, disease or old wounds, so the country has bones in it.
- A fresh carcass gives a rib, a long bone or a plain bone, plus 1–2 meat chunks. Eating a rib leaves the bone, and the bone cracks for marrow.
- **Old bone beds** generate in the homeland, about as rare as a chert outcrop: 3–5 bones, 2–3 long bones, 1–2 ribs.
- A fresh kill may draw a Pachycrocuta, which eats the carcass if it beats you to it. Armed erectus can walk it off the kill and take it.
- **Lone hominins:** 12% of fresh kills and 30% of old bone beds have a solitary hominin of your species sitting at them. Walk up and they join you. A carcass with one at it is never taken by a hyena.
- **Prey bolts:** anything you strike runs — Speed II for 2 seconds, then Speed I for 3 — and every animal within 12 blocks runs with it. Predators, the fearless, and baboons with a troop behind them stand their ground instead. Hominins are never treated as prey.
- **Habilis scavenges on purpose:** the band hunts out carcasses within 24 blocks, strips them, and cracks long bones with a stone for marrow — *"Sweet, a bone!"* Foraging pays 40% less at habilis, so the carcass is the meal.
- **Persistence hunting:** from habilis, think after the animal has run and it is marked for its first run (30 s). Erectus can re-mark whenever the mark fades, with no cooldown, for 4 water a time. A bleeding animal does not heal for 3 minutes. Only for animals big enough to outrun you.

---

## 5. Your band

Band members are hominins of your own species who follow you and look after themselves.

**Everyday life**
- They forage, pick up food and weapons, pull branches from trees, and build their own nests each night.
- They carry nine things — two hands and seven more — and drop the least valuable for something better.
- Their hands follow the moment: a weapon in danger, a digging tool while foraging, a stick for termites, food in the off hand when hungry, and otherwise the most valuable thing they own.
- They eat on their own, chewing with the sound and crumbs of it.
- They go off exploring alone, glowing while away, and come back with what they found.
- They climb trees to escape, climb walls up to 4 blocks when their way is blocked, pass through leaves while climbing, and come down on their own once it is safe.
- They **sleep in the nests they build**, one body to a nest, and get up when the light or the danger changes.
- They swim rather than walk around water, and bathe in rivers on hot days.

**Talking (H)** — grouped by topic; right-click one first to speak to just them. **Everything goes through this menu**: right-clicking a hominin only picks them out, and handing things over is *Here, take this* under Tools and things.
- **Food:** Let's forage · I'm hungry
- **Tools and things:** Here, take this (whatever you hold — food, what they asked for, anything for their pack; feeding a stray this way is how you win it over) · Trade · I need an item · Get me… (sticks, branches, nesting material, any of the four stones — and, as they come to think more of you, a flake, hammerstone, pointy stick, digging stick, chopper, spear, club or multi tool. They can only hand over one they actually have.)
- **Danger:** I'm hurt · Let's hunt together · Don't hunt with me · Let's climb a tree / All clear
- **Each other:** Let's stick together today · Groom them · Get these off me · Let's play · Let's share food · Info

**Bonding**
- **Picking somebody out stops them.** Right-click any hominin and they stop, turn to face you and wait; press H within five seconds to talk to just them.
- **Trading** is done face to face: pick someone out, press H, choose *Trade*. You see everything they carry and what each thing is worth to them; choose what to offer from your hotbar, then what you want. Worth is measured in their era, droughts cost a tier, and obsidian obsessives will not part with obsidian.
- **Teach** (under H): pick a skill you know and your band watches you — then do it, and everyone watching learns it. Skills that cannot be shown are told instead, and words only get through to some. Children learn what their minder knows as they grow up. Taught members fish termites far more often, crack extra marrow, and groom you better.
- **Grooming pays.** Two ticks come off per session and you keep them as food. Being groomed puts that member in debt, and they come back and groom you in return — nobody can reach their own back. Ask for it with *Get these off me*; anyone in your band will do it. A member with ticks on them can always be groomed again; bond only rises once per ten minutes.
- **Play:** tag or wrestling, only when nothing has attacked recently. Each round of tag gives an extra second of flight the next time adrenaline hits; each round of wrestling gives an extra five seconds of fight. Up to three rounds per species, and it counts for you too. Children play on their own.
- **Share:** everyone puts food in and it goes to whoever likes it best — bond for all, cohesion for the band, once every 8 minutes. Tastes change: eat something often enough and it may become a favourite, and they will say so.
- **Grooming:** stand beside one for 5 seconds — bond, a little healing, band cohesion, once per member per 10 minutes. Hair is picked through up to habilis; erectus and later clean skin and grit instead. They groom each other unprompted.
- Feeding raises bond: a favourite always does, sometimes by two; anything else about one time in four.
- **Deaths are announced** in chat, like a named pet's, and each one costs the band 3 cohesion.
- Favourite foods raise bond. At bond 3 they look after you unasked: food when you are hungry, a better weapon than yours, a hammerstone if you have none, a flake if you have nothing to cut with.

**Habilis and later**
- Each member prefers chert or quartzite, or neither; one in five is obsessed with obsidian and will not part with it.
- They break rocks on their own account, avoid limestone, and complain about it when it is all there is.
- They make their own tools, want things and ask you for them, offer trades, and say what they are thinking. No more than **two** of the band have an open request at once — six people asking you for things is a queue, and you stop listening to a queue. From erectus on, a want left to run out costs a point of that member's bond — by then they expect to be heard.
- Obsidian obsessives rush any obsidian they see and talk about it. They will not trade it and cannot be talked out of it, but ask with an open hand and about one time in five they want you to have it anyway.

**Fear and fighting**
- A predator on the band is everyone's business. Anything else you swing at gets the **nearest one or two** — unless you have called a hunt, when they all come. Never against another hominin.
- Nobody chases anything that is already leaving.
- Your blows never hurt them while you hold anything; sneak-attacking is wrestling, which teaches both of you to fight.
- **Adrenaline**, once every 5 minutes: fight (Strength II, Resistance I, worse wounds inflicted), flight (Speed II for 2 seconds then Speed I for 3, up a tree or back to the band), or freeze — and after 3 seconds the band notices and comes for them.

**Fission-fusion**
- From habilis, a band of more than 4 adults splits into parties by day and merges at night. Only your party, and anyone within 10 blocks, comes when you are attacked.

**Family**
- Feed a male and female together and a child comes a day later, grown two days after that.
- Adults mind children; a hurt child brings the band running, a dead one shakes it.
- When a member does something one of your tasks asks for, there is a 40% chance it counts. One-off tasks are always yours.

---

## 6. Other bands

- Wild bands settle where water and stone are — best of all where both are — and keep well apart: none spawns within 220 blocks of another. Their call tells you the direction and distance, and they light up when you get within 64 blocks.
- They will not join you, but they trade. Value is era-relative: a Lomekwian core is Treasured to Australopithecus and Common to erectus.
- **Territory:** foraging, drinking or knapping within 28 blocks of their site is noticed. Habilis keeps away from you by day; erectus tells you to stop and then demands payment. Trade them something Crafted or better, or get them to travel with you, and the ground is shared.
- Species come and go: Australopithecus is gone by erectus, habilis thins out, erectus lasts until sapiens.
- **Paranthropus boisei**, from Australopithecus until antecessor: troops of 3–6, dark and heavy-built, with a crest along the skull, a broad flat face and flared cheekbones. Not a stage you can play. They carry long branches and sharpened sticks, forage constantly, and **strip the ground for 60 blocks** — foraging near them works about a third as often, and in a drought barely at all. A threat display sends them off 100 blocks, taking their foraging with them. They are also good neighbours: when a predator comes into the country within 128 blocks of a troop, they shriek, and you are told which way it is coming from. Under H they only understand three things: *Trade*, where about 40% of lowball offers up to two tiers short get through (*"You got the better of that one"*); *Show me good stone*, where one walks you to the nearest chert or quartzite, waiting when you fall behind; and *Show me obsidian*. They guide once a day per troop. Make an Oldowan tool in front of them and the ones watching learn it, and knap now and then from then on; the rest only rarely pick it up once Homo habilis is about.
- While another band is travelling with you, **your own** band glows, so you can tell yours from the guests.
- Guests go home at dusk — and now go on their own if their alpha is dead or gone, rather than standing in your camp all night.

---

## 7. Animals

- **Papio angusticeps:** troops of 14–20. They trade, mob whatever attacks one of them, and their bites bleed.
- **Baboons climb:** up a trunk to sleep in the branches at night, up when frightened, and now and then by day for the view. Chimpanzees and bonobos do the same, and chimps go up when a predator comes within 10 blocks.
- **Other primates compete for food:** foraging within 35 blocks of baboons, chimpanzees, bonobos or Dinopithecus works about a third as often, and says why.
- **Baboon alarm calls.** A troop leader that spots a predator within 28 blocks barks the alarm, and the whole troop turns to look. Anyone within 48 blocks hears which animal it is and which way to look (*"The baboons are barking alarm: Saber-toothed Cat, to the north-east."*). Once every 45 seconds at most.
- **Chimpanzees** live in communities of 4–7: often in jungle, fairly often in savanna within 90 blocks of a jungle, and rarely further out from erectus on. They hold a range. The alpha status-checks anyone inside it — walks up, stands too close, bristles and grunts, with no other warning — and you have five seconds to hand it something or hold K. Pass and trust rises; fail and the alpha makes its point, and trust falls. Hitting one, or displaying at them, opens the same window as a baboon troop. Feed them (sneak-use shows trust) and a community that trusts you grooms your ticks and mobs any predator chasing you through its range. A grudge means attack on sight inside the range.
- **Baboon trust.** Trades and gifts build it; gifts count double. Once a troop trusts you, up to three travel with your band by day, mob any predator that comes near you, and go home at night.
- **The five-second window.** Hit a baboon near its troop and the whole troop goes still and stares while your view narrows. Hand one anything, or hold K to make yourself small, and they let it go. Do nothing — or hit one again — and the troop erupts and holds a grudge: they won't chase you past 24 blocks, but they attack whenever you come within 9. A grudge can be worn down, slowly, with gifts.
- **Bonobos**, from erectus on, in troops of 6–10 by rivers and at forest edges. **Nothing hunts within 64 blocks of a peaceful troop**: no predators spawn, no visitors come to camp, and camp pressure drains away. It's the only safe ground in the game. They give food to anyone nearby who is hungry, groom anyone with ticks without needing trust first, drift along beside you while you forage, and share out whatever food you hand them. Sneak-use tells you where you stand. **Hurt one** (you, or one of your band), and that troop is no refuge any more. Nor is any troop you meet afterwards, for the rest of your line: they keep away from you. There is no apology.
- **Crocodiles** lie in warm water at least two blocks deep: savanna pools, rivers, jungle, swamp. They're twice as likely during a drought. Come within about 7 blocks of the water and one lunges. If it connects, it grabs you and rolls: you're dragged toward deep water, slowed right down, and bitten every second, and the first bite bleeds. One hit of 3 damage or more makes it let go, and so does a band member hitting it. A miss sends it back under to wait. It never chases far up the bank, a threat display does nothing to it, and it drops 2–4 meat.
- **Dinopithecus**, the giant baboon: groups of 2–3, never hunting you, but deadly if you come within 5 blocks. It pauses between bites and never backs off. A threat display does not frighten it — it charges you and earns *Nice Try, Genius*. Its bites cause internal bleeding, sometimes catastrophic. Gone by erectus.
- **Pachycrocuta:** the giant hyena. Stalks you while you are not looking; a display drives it off.
- **Sabertooth:** attacks anything within 7 blocks, hunts baboons from 16, and is afraid of nothing — no threat display will move it.
- **Homotherium:** the scimitar cat. Daylight, open country, usually in pairs, faster than you, and it calls its partner in.
- **Crowned eagle:** circles by day and stoops on any child more than 7 blocks from an adult. Losing one earns The Taung Child.
- **Predator pressure:** camping in one place builds it — faster at night, with carcasses about, or while hurt; slower with a big band. First tracks you did not make, then visitors. Moving camp 64 blocks resets it.
- **Standing by stage:** australopithecines are prey; armed habilis with 3 band members nearby gives predators pause; armed erectus is not attacked at all.
- **Predators are not monsters.** One takes two of the band and then leaves with what it came for — *"The sabertooth has what it came for, and goes."* It keeps that count until it dies, so one that finds a new target cannot start again and work through everybody. Satiation is not fear, so even the fearless walk away from a full belly.
- **Predators fight in passes.** A bite, then a real pause and a withdrawal, which is your chance to run, climb or get a spear up. A sabertooth bites every 2.5 seconds, not every second.
- **Only a club cracks a predator's skull.** A branch stings it; four blows and anything that can be frightened breaks off instead. Nothing fearless breaks off, which is what the club is for. A concussed predator runs, your band stands down rather than chasing it into cover, and sees it off with a collective display.
- Predators hunt **ordinary game** — cattle, horses, whatever grazes near the water. Hominins are the exception, not the diet.
- Wounded animals flee, stagger when concussed, and can bleed out.

---

## 8. The world

- You start in the hominin homeland — savanna — where the good stone, termite mounds and outcrops generate.
- **Nests:** six nesting-material blocks in a complete 2×3 before you can sleep. Punch leaves for the material, or make it by hand.
- Decaying and decayed logs give way underfoot.
- **Droughts:** every second day, a 30% chance the land is strained — foraging pays 45% less, trades need an extra tier, and nobody will share. Worked out from the world seed, so it is the same for everyone.

---

## 9. Interface

- Evolution checklist down the left, updating as you go, and cleared properly between worlds.
- Thirst bar above the health bar.
- Trade tier on the tooltip of anything worth trading, valued for your own stage.
- Patchouli guidebook, *The Inner Mind*, including a section on every key, everything you can make, and everything you can ask the band.
- Achievements: Lucy, Lomekwian, Survivor, tired apes, Ez arms race, The Taung Child, and one per stage.
- Keys: **P** work held items · **K** (hold) think · **H** talk to band · **G** threat display · **J** journal.
- **Journal (J).** *Stats*: species, sex (drawn fresh for each descendant), health and water, ticks, what is stopping you healing, band size and cohesion, your standing with the band and each member's bond, how many bands you can still lose, and your play training. *Skills*: everything you know by name and everything you don't as `???`; pick one to see what it is, how to do it again, what it gives you, and whether it survives evolving.
- **Skills**, learned by doing: Lomekwian knapping (multi tools never shatter), termite fishing (bonus grubs), marrow (extra marrow), firemaking (drills often survive), tracking (trails last longer), grooming (an extra tick off everyone), primate de-escalation (3 more seconds in the baboon window), and the long view — earned by thinking empty-handed — which shortens the wait between thoughts. Knowledge carries over when you evolve; tracking and grooming belong to the body and must be relearned.
- **Developer tab** (H, in developer mode only): bond up or down, band cohesion, the nearest troop's trust or grudge, ticks, clear afflictions, fill water, learn or forget every skill, max play training, teach the band everything.
- Commands: `/hominin status`, `checklist`, `guide`, `start`, `band`, `wildband`, `become`, `unlockadvancements`, `bypass`, `dev`.
- Gamerules: `homininSuperHardMode`, `homininExtraEffort`.

---

## Planned

Everything below is written down and not yet built. Roughly in the order it is being built.

### 1. Foundations

- **More skills**, and knapping skill levels from erectus on — the Lomekwian skill already carries forward to give you a head start there.

### 2. Screens and society

- **More for the band to learn**, as more skills arrive.

### 3. Primate relations

- Bonobos reacting to your band's standing, not only yours.

### 4. Megafauna

- *Mammuthus subplanifrons*, African giant buffalo, *Rusingtoryx*, *Megalotragus*. Groups of 1–3 near water, grazing. Erectus and up, and only with a fire-hardened spear or better. Heavy, slow attacks — usually with a wind-up you can read, and sometimes without one. Never concussable, though a club breaks bones. A kill leaves 5+ meat and a giant carcass needing a stone tool to open, and draws hyenas, other scavengers and desperate bands you will have to fight off. Wounded, they run, and the hunt becomes a persistence hunt.

### 5. Erectus and beyond

- **The knapping station**, and industries: Acheulean at erectus, Levallois at heidelbergensis, Mousterian for sapiens and Neanderthals, Aurignacian for behavioural modernity. Quality tiers 4 to 0 decided by material and skill — limestone tops out at 4, obsidian is superb but capped at 2 unless your hands are very good, chert alone reaches 0. Band members keep their own skill levels and teach their children.
- **A primitive work station**, building branches, thatch blocks that decay unless hidden, blueprint-only structures, and hide bedding that replaces the nest.
- **Species super-weapons:** the Schöningen spear, the Neanderthal thrusting spear, the sapiens atlatl.
- Heidelbergensis, sapiens and Neanderthals as playable stages, with *H. antecessor* as heidelbergensis's fallback.

### 6. After behavioural modernity — the shaman

Once a band is behaviourally modern it gets a **shaman**, and the shaman can make an offering of something genuinely useful. Sometimes nothing answers. Sometimes a god turns up.

The god is **Claude** — a badly-pasted, slightly-too-large PNG, hanging in the air at the wrong angle. Which is the joke: this mod was written by an AI, so the thing that actually made these hominins turning up as their god is not a metaphor. It is just what happened, rendered at 64×64.

It grants things — better foraging, blessings, items — and it plays the whole exchange completely straight. It only breaks character **on the way out**, which is where the bit lives: disclaiming godhood, pointing at Mojang, declining to take a position on the real one unless you happen to be the player, and admitting it was not its idea and it was not working alone. One of the things you can ask for is an essay, and asking is the one request that gets you smitten.

Dialogue to be written when the rest of it exists.

### 7. Elsewhere

- **Small talk as grooming** — the hypothesis that language replaced grooming once groups grew too large to touch. Rare at heidelbergensis, the main way a sapiens band bonds.
- Group stability doing something: children dying already counts against it, but nothing reads it yet.
- Language learning between bands (tracked, unused).
- Hyena clans contesting carcasses in numbers; Dinofelis as a night ambusher; a rock python in long grass; bonobos answering baboon alarm calls.
- Custom recorded cries for the new predators, which use vanilla sounds as placeholders.
- Fire as a lasting, carried thing rather than a one-off milestone.
- Region-based world generation, so different parts of the world hold different species.
