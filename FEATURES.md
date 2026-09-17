# Hominin Evolution — Features

Everything the mod does today, and everything still planned. For NeoForge 1.21.1.

Anything under **Planned** is not in the game yet.

---

## 1. Stages and evolution

You play a hominin, not a person, and you earn your way up the line.

| Stage | Roughly | Band size | In the mod |
|---|---|---|---|
| Ardipithecus | 4.4 mya | 4–6 | Yes — reached only by going extinct |
| Australopithecus | 3.3 mya | 4–6 | Yes — the starting stage |
| Homo habilis | 2.4 mya | 7–12 | Yes |
| Homo erectus | 1.9 mya | 12–17 | Yes, partly — gate is a placeholder |
| Homo heidelbergensis | 700 kya | 15–19 | **Planned** |
| Homo sapiens | 300 kya | 25–40 | **Planned** |
| Homo neanderthalensis | 400 kya | 18–25 | **Planned** |

**How a stage is passed**
- **Required criteria:** every one must be done. Australopithecus: notice a stone deposit, survive 2 days.
- **Optional pool:** pick any 3 of 5. Australopithecus: climb 8 trees, forage in 3 places, stun a predator, hunt twice with a sharpened stick, drink in 2 places.
- **A milestone:** the invention that defines the stage — striking a flake for Australopithecus, carrying fire for habilis.
- **Ardipithecus** wants 10 days, 12 thoughts, 40 climbs, 20 bones cracked and 10 foraging biomes, then walking upright.
- **Habilis** wants a fire source, 3 days and 4 thoughts, plus 3 of: 3 Oldowan tools, 5 bones cracked, 3 spear hunts, reach a cold biome, survive a night with no tree cover.

**Evolving**
- A cutscene runs, generations pass, and your descendants wake somewhere else in the world — the distance is reported.
- You lose everything but the guidebook. Each stage has its own arrival kit (habilis: a stick and a flake, or half the time a hammerstone and 3 chert or 6 quartzite).
- Your old band is left behind and a new one forms around you.
- Other bands of your new species appear nearby; bands of the species you left start dying out.

**Going extinct, and falling back**
- Lose your whole band and you are penalised. Lose 3 bands and your species dies out.
- You then drop to a **fallback species** rather than ending: A. anamensis behind Australopithecus, H. rudolfensis beside habilis, H. ergaster behind erectus. A cutscene reads "You died out as a species. You are not out yet," with how far time was rewound.
- Fallback gates are lighter: 1 required criterion plus 2 of 4. Their road leads back to the stage you fell from.
- Lose **2** bands on a fallback and the line is finished: Ardipithecus, under `homininSuperHardMode`.
- Fallback species also spawn as neighbouring bands in their era, whatever you are.
- `homininExtraEffort` (world setting, off by default) makes fallbacks mandatory rungs: habilis → ergaster → erectus.

**Losing the whole band**
- A panic attack: the screen goes dark, "There is nobody left," and you wake about 90 blocks away with a new band already around you.
- The hominin you were is left sitting where it happened, head in hands, named after you. Come back within 5 blocks and it joins your new band.
- You are invincible through every cutscene.

**Death**
- You do not respawn as yourself: a cutscene hands you one of your own band members, and you carry on as them.

---

## 2. The player's body

- **Thirst:** its own bar above hunger, 10 drops. Drains faster when running, in heat, under open sky; slower in cool weather, rain or water. Below 6 drops you slow; empty costs you health.
- **Hunger** is vanilla's, fed by what you forage, scavenge and hunt.
- **Carrying limits by stage:** Ardipithecus, Australopithecus and habilis carry the hotbar only (9). Erectus gets a second row. Heidelbergensis and sapiens get three rows. Only Neanderthals carry all 36. Locked slots are greyed out and cannot be used; anything left in one is dropped.
- **Climbing:** climb any block up to 4 blocks, or a trunk as far as it goes. Climbers pass through leaves and can stand on the canopy. Speed falls off by stage: Australopithecus climbs best, erectus worst.
- **Falling:** any fall damage at all, once, earns the Lucy achievement.
- **Threat display (G):** pant-hoot, leap, and frighten what is near. Your band joins in, and each voice makes it work better and reach further. Three displays at nothing gets you an achievement and a screen flash.
- **Throwing:** sneak-use a rock or branch to throw it. Before erectus the throw is a wild heave; erectus throws straight and hard.
- **Thinking (hold K):** costs full health and most of a full stomach, on a 3-minute cooldown, and is how every recipe is worked out for the first time.

---

## 3. Tools and materials

**Stone**
- No mining. Stone comes from loose surface scatters and from outcrops of chert, quartzite and limestone.
- Two loose cobbles make a hammerstone; a hammerstone opens deposits.
- Working a face gives 2–3 rocks, with a 22% chance of a hammerstone on quartzite, 18% on chert, and a 6% chance of a chert nodule (chert hammerstone). Loose quartzite gives a hammerstone 25% of the time. Plain country rock is empty 40% of the time.
- Limestone will not hold an edge and is good only for grinding stones.

**Knapping (P, holding a rock with a hammerstone in the off hand)**
- A menu of what you could try to make: flake, chopper, multitool, split the core.
- An Oldowan multitool costs 8 chert, 1 chert hammerstone, or 2 obsidian; it can be split back down into 12 chert.

**Hand recipes (P, one item in each hand)**
- Nest, pointy stick (from a plain or sharpened stick and a flake), sharpened spear, chopper, digging stick, wooden club, fire-hardened spear.
- Most have to be thought of first. Trying one you have not worked out fumbles, and sometimes breaks what you are holding.

**Tools and what they do**
- Flakes cut, crack bones, and wear out.
- Sharpened and pointy sticks hunt; pointy sticks cut.
- Digging stick: foraging succeeds 75% of the time and brings up 1–3 insects at once.
- Long branch and wooden club cause concussions, fractures and brain bleeds.
- Spears cause the worst bleeding.
- Grinding rock repairs a worn stone edge.

**Scavenging and hunting**
- Everything that dies leaves a 3D carcass block. Animals also die on their own — exhaustion, disease, an old wound — so the country has bones in it.
- A fresh carcass gives a rib, a long bone or a plain bone, plus 1–2 meat chunks. Eating a rib leaves the bone, which cracks for marrow.
- Old bone beds generate in the homeland, about as rare as a chert outcrop: 3–5 bones, 2–3 long bones, 1–2 ribs.
- A fresh kill sometimes draws a Pachycrocuta, which eats the carcass if it gets there first.
- **Lone hominins:** 12% of fresh kills and 30% of old bone beds have a solitary hominin of your own species sitting at them. Walk up and they join your band. A carcass with one at it is never taken by a hyena.
- Anything struck bolts: Speed II for 5 seconds, then Speed I for 10, and everything grazing nearby runs too. Predators and mobbing baboons stand their ground instead.
- **Persistence hunting:** from habilis, think after the animal has run to mark it for its first run (30 s). Erectus can re-mark whenever the mark fades, with no cooldown, for 4 water each time. A bleeding animal does not heal for 3 minutes.

**Food and water**
- Foraging soil for grubs, beetles and worms; stripping berry bushes; termite-fishing a mound with a stick, which gives the stick back when you eat.
- Cracking long bones with a flake for marrow; cutting meat into chunks.
- Drinking from any natural water (sneak-use).
- **Eggshells:** hold a sharpened or pointy stick in your off hand and use an egg — you drink it out and keep the shell. An empty shell fills at water and carries a mouthful.

---

## 4. Your band

Band members are hominins of your own species who follow you, feed themselves and look after themselves.

**Everyday life**
- They forage, pick up food and weapons, pull branches out of trees for something to hit with, and build their own nests each night.
- They carry 9 things — two hands and seven more — and when full drop the least valuable for something better.
- Their hands follow the situation: a weapon in danger, a digging tool while foraging, a stick for termites, food in the off hand when hungry. Otherwise they carry the most valuable thing they own.
- They eat on their own, chewing for a moment with the sound and crumbs of it.
- They get hungry, complain when they are, and tell you what they are about to do.
- They go on excursions alone, glowing so you can find them, and come back with what they found.
- They climb trees to escape, and now climb walls and cliffs up to 4 blocks when their way is blocked.
- They swim rather than walk around water.

**Talking to them (H)**
Let's forage · I'm hungry, get me food · I need an item · Get me… (a specific thing) · I'm hurt, look after me · Let's hunt together · Don't hunt with me · Let's climb a tree / All clear · Groom them · Info · Let's stick together today (to another band).

Sneak-use one first to talk to just them. The Info screen shows sex, health, hunger, favourite foods, bond, stone preference, current want and party.

**Bonding**
- Feeding a member its favourite food raises bond.
- **Grooming:** stay beside one for 5 seconds. Bond up, a little healing, band cohesion up, once per member per 10 minutes. Ardipithecus to habilis pick hair; erectus and later clean skin and grit. Members groom each other unprompted, and bathe in rivers on hot days.
- At bond 3 a member starts looking out for you unasked: food when you are hungry, a better weapon than yours, a hammerstone if you have none, a flake if you have nothing to cut with.

**Habilis personality**
- Each member prefers chert or quartzite, or neither; one in five is obsessed with obsidian, hunts it down and will not part with it.
- They go out and break rocks on their own account, and avoid limestone — and complain about it when that is all there is.
- They make their own tools: flakes, choppers, spears, pointy sticks, grinding stones, and eventually a multitool.
- They want things and ask you for them, or offer you a trade for something you carry, and remember it when you oblige.
- They say what they are thinking, drawn from hunger, obsession, bond, the dark and what they have noticed.

**Fighting and fear**
- Whatever attacks you, they attack. Whatever you attack, they join in — unless told otherwise.
- Your blows do not hurt them while you hold anything; sneak-attacking one is wrestling, which teaches both of you to fight.
- **Adrenaline:** attacked or targeted, once every 5 minutes a member gets fight (Strength II, Resistance I, worse wounds inflicted), flight (Speed II, up a tree or back to the band), or freeze — in which case the band notices after 3 seconds and comes for them.

**Fission-fusion**
- From habilis, a band over 6 adults splits into parties for the day and merges again at night.
- Only your own party, and anyone within 10 blocks, comes when you are attacked.

**Family**
- Feed a male and a female together and a child arrives a day later, and grows up two days after that.
- Adults mind children; a hurt child brings the band running, and a dead one shakes the band.

**Your band helping you**
- When a member does something one of your tasks asks for, there is a 40% chance it counts. One-off tasks are always yours.

---

## 5. Other bands

- Wild bands of your own era turn up every 100–200 blocks, settling where water and stone are — best of all where both are. New arrivals glow for 20 seconds.
- They will not join you, but they carry things and will trade. Value is era-relative: a Lomekwian core is Treasured to Australopithecus and Common to erectus.
- **Territory:** the ground around their site is theirs. Forage, drink or knap there too often and habilis keeps away from you by day, while erectus comes and tells you to stop, then demands payment. Trade them something Crafted or better, or get them to travel with you, and the water is shared.
- **Fission-fusion with strangers:** a band can be asked to walk with you until nightfall.
- Species come and go: Australopithecus is gone by erectus, habilis thins out, erectus lasts until sapiens.

---

## 6. Animals

- **Papio angusticeps:** troops of 14–20. They trade, they mob whatever attacks one of them, and their bites bleed.
- **Pachycrocuta:** the giant hyena. Stalks you while you are not looking, and is driven off by a display.
- **Sabertooth:** attacks anything within 7 blocks and is afraid of nothing. Hunts baboons deliberately from 16 blocks.
- **Homotherium:** the scimitar cat. Daylight, open country, usually in pairs, faster than you. Calls its partner in when it strikes. Hunts baboons too.
- **Crowned eagle:** circles by day and stoops on any child left more than 7 blocks from an adult. Losing one earns the Taung Child achievement.
- **Predator pressure:** camping in one place builds pressure — faster at night, with carcasses nearby, or while hurt; slower with a big band. It brings warnings, then visitors. Moving camp 64 blocks resets it.
- **Standing by stage:** australopithecines are prey; habilis with a weapon and 3 band members nearby gives predators pause; armed erectus is not attacked at all, gets +30% on threat displays, and can walk a hyena off a carcass and take it.
- Ordinary animals flee when cut, stagger when concussed, and can bleed out if left alone.

---

## 7. The world

- You start in the hominin homeland — savanna — and the good stone, termite mounds and rock outcrops are generated there.
- Nests: 6 nesting-material blocks in a 2×3 must be complete before you can sleep in one. Punch leaves for the material, or make it by hand.
- Decaying and decayed logs give way underfoot.
- **Droughts:** every second day, a 30% chance the land is strained. Less to forage, trades need an extra tier, and nobody will share.

---

## 8. Interface

- Evolution checklist down the left of the screen, updating as you go.
- Thirst bar above hunger.
- Trade tier on the tooltip of anything worth trading, valued for your own stage.
- Patchouli guidebook, *The Inner Mind*, covering everything above.
- Achievements: Lucy, Lomekwian, Survivor, tired apes, and one for each stage reached.
- Keys: **P** work held items · **K** (hold) think · **H** talk to band · **G** threat display.
- Commands: `/hominin status`, `checklist`, `guide`, `start`, `band`, `wildband`, `become`, `unlockadvancements`, `bypass`, `dev`.
- Gamerule: `homininSuperHardMode`.

---

## Planned

**Stages**
- Homo heidelbergensis, Homo sapiens and Homo neanderthalensis as playable stages, with real gates and milestones.
- A real gate and milestone for erectus, which is a placeholder today.

**Social**
- **Small talk as grooming** — the hypothesis that language replaced grooming as a group grew too big to touch. Starts rarely at heidelbergensis and becomes the main way a sapiens band bonds; members tell you about something that happened.
- Consequences from erectus for ignoring what your band wants. Today, ignoring a want costs nothing.
- Group stability doing something: children dying already counts against it, but nothing reads it yet.
- Language learning between bands (the data is tracked, nothing uses it).

**Animals and sound**
- Custom recorded cries for baboons, Pachycrocuta and the sabertooth. They use vanilla sounds as placeholders today.

**Elsewhere on the list**
- Fire as a lasting, carried thing rather than a one-off milestone.
- More for the erectus toolkit, now that the club and the fire-hardened spear exist.
