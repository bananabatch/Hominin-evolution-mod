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
- **Thinking (hold K):** full health and most of a full stomach, on a 3-minute cooldown. It is how every recipe is first worked out — and, once something has run from you, how you keep hold of the chase instead.

---

## 3. Tools and materials

**Stone**
- No mining. Loose surface scatters and outcrops of chert, quartzite and limestone.
- Two loose cobbles make a hammerstone; a hammerstone opens deposits.
- A worked face gives 2–3 rocks: 22% chance of a hammerstone on quartzite, 18% on chert, 6% of a chert nodule. Loose quartzite gives one 25% of the time. Plain country rock is empty 40% of the time.
- Limestone will not hold an edge. It is good for grinding stones and nothing else.

**Knapping** (P, holding a rock with a hammerstone in the off hand)
- A menu of what you could try: flake, chopper, multi tool, split the core.
- An Oldowan multi tool costs 8 chert, 1 chert hammerstone or 2 obsidian, and splits back down into 12 chert.

**Hand recipes** (P, one thing in each hand)
- Nest; pointy stick (plain or sharpened stick + flake); sharpened spear; chopper; digging stick; wooden club; fire-hardened spear.
- Most must be thought of first. Trying one you have not worked out fumbles, and sometimes breaks the material.

**What the tools do**
- Flakes cut, crack bones and wear out.
- Sharpened and pointy sticks hunt; pointy sticks cut.
- Digging stick: foraging succeeds 75% of the time and brings up 1–3 insects at once.
- Long branch and club: concussions, fractures, brain bleeds.
- Spears cause the worst bleeding.
- Grinding rock puts an edge back on a worn tool.

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
- They swim rather than walk around water, and bathe in rivers on hot days.

**Talking (H)** — grouped by topic, and sneak-use one first to speak to just them.
- **Food:** Let's forage · I'm hungry
- **Tools and things:** I need an item · Get me…
- **Danger:** I'm hurt · Let's hunt together · Don't hunt with me · Let's climb a tree / All clear
- **Each other:** Let's stick together today · Groom them · Info

**Bonding**
- **Grooming:** stand beside one for 5 seconds — bond, a little healing, band cohesion, once per member per 10 minutes. Hair is picked through up to habilis; erectus and later clean skin and grit instead. They groom each other unprompted.
- Favourite foods raise bond. At bond 3 they look after you unasked: food when you are hungry, a better weapon than yours, a hammerstone if you have none, a flake if you have nothing to cut with.

**Habilis and later**
- Each member prefers chert or quartzite, or neither; one in five is obsessed with obsidian and will not part with it.
- They break rocks on their own account, avoid limestone, and complain about it when it is all there is.
- They make their own tools, want things and ask you for them, offer trades, and say what they are thinking.

**Fear and fighting**
- Whatever attacks you, they attack. Whatever you attack, they join — unless told otherwise, and never against another hominin.
- Your blows never hurt them while you hold anything; sneak-attacking is wrestling, which teaches both of you to fight.
- **Adrenaline**, once every 5 minutes: fight (Strength II, Resistance I, worse wounds inflicted), flight (Speed II for 2 seconds then Speed I for 3, up a tree or back to the band), or freeze — and after 3 seconds the band notices and comes for them.

**Fission-fusion**
- From habilis, a band over 6 adults splits into parties by day and merges at night. Only your party, and anyone within 10 blocks, comes when you are attacked.

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

---

## 7. Animals

- **Papio angusticeps:** troops of 14–20. They trade, mob whatever attacks one of them, and their bites bleed.
- **Pachycrocuta:** the giant hyena. Stalks you while you are not looking; a display drives it off.
- **Sabertooth:** attacks anything within 7 blocks, hunts baboons from 16, and is afraid of nothing.
- **Homotherium:** the scimitar cat. Daylight, open country, usually in pairs, faster than you, and it calls its partner in.
- **Crowned eagle:** circles by day and stoops on any child more than 7 blocks from an adult. Losing one earns The Taung Child.
- **Predator pressure:** camping in one place builds it — faster at night, with carcasses about, or while hurt; slower with a big band. First tracks you did not make, then visitors. Moving camp 64 blocks resets it.
- **Standing by stage:** australopithecines are prey; armed habilis with 3 band members nearby gives predators pause; armed erectus is not attacked at all.
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
- Keys: **P** work held items · **K** (hold) think · **H** talk to band · **G** threat display.
- Commands: `/hominin status`, `checklist`, `guide`, `start`, `band`, `wildband`, `become`, `unlockadvancements`, `bypass`, `dev`.
- Gamerules: `homininSuperHardMode`, `homininExtraEffort`.

---

## Planned

**Stages**
- A real gate and milestone for erectus, which is a placeholder today — anyone reaching it currently stops there.
- Heidelbergensis, sapiens and Neanderthals as playable stages, with their pre- and post-behavioural-modernity sub-stages.
- *H. antecessor* as heidelbergensis's fallback, once that stage exists.

**Social**
- **Small talk as grooming** — the hypothesis that language replaced grooming once groups grew too large to touch. Rare at heidelbergensis, the main way a sapiens band bonds.
- Consequences from erectus for ignoring what your band wants. Today, ignoring a want costs nothing.
- Group stability doing something: children dying already counts against it, but nothing reads it yet.
- Language learning between bands (tracked, unused).

**Predators and animals**
- Crocodiles at the water, to make drinking and droughts dangerous.
- Hyena clans contesting carcasses in numbers.
- Dinofelis as a night ambusher; a rock python in long grass.
- Baboon alarm calls warning of big cats.
- Custom recorded cries for the new predators, which use vanilla sounds as placeholders.

**Elsewhere**
- Fire as a lasting, carried thing rather than a one-off milestone.
- More of the erectus toolkit, now that the club and fire-hardened spear exist.
