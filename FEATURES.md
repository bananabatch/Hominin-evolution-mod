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
| Homo erectus | 1.9 mya | 12–17 | Yes |
| Homo heidelbergensis | 700 kya | 15–19 | Reachable; its own content is **planned** |
| Homo sapiens | 300 kya | 25–40 | **Planned** |
| Homo neanderthalensis | 400 kya | 18–25 | **Planned** |

**Passing a stage**
- **Required criteria**, all of them. Australopithecus: notice a stone deposit, survive 3 days.
- **Days to survive:** Australopithecus 3, habilis 4, erectus 6 (anamensis and rudolfensis 4, ergaster 6).
- **An optional pool**, any 3 of 5. Australopithecus: climb 8 trees, forage in 3 places, stun a predator, hunt twice with a sharpened stick, drink in 2 places.
- **A milestone** — the invention that defines the stage: striking a flake for Australopithecus, carrying fire for habilis.
- Fallback species ask less: 1 required criterion and 2 of 4.

**Evolving**
- A cutscene, deep time passing, and your descendants wake somewhere else in the world.
- You lose everything but the guidebook. Each stage has an arrival kit — habilis gets a stick and a flake, or half the time a hammerstone with 3 chert or 6 quartzite.
- Your old band stays in the past; a new one forms around you, and bands of your new species appear nearby while the species you left begins to die out.
- `/hominin become <stage>` runs exactly this sequence — cutscene, move, fresh inventory, new band — for testing.

**What is remembered**
- Evolving takes everything except a name. At the moment you evolve, the member you were closest to — your mate, else whoever thought most of you — is remembered by name and species.
- One grown member of the band that forms around your descendants is called after them, and starts at +2 bond. Nobody in that band knows who the name belonged to.
- The journal keeps the line: one name from every band you have left behind, ten deep. It survives evolution, death and falling back, because it is stored apart from everything else a player carries.

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
- **Creative mode:** while you are in creative, your band cannot die of falls - follow you however you fly.
- **Climbing:** any block up to 4 blocks, or a trunk as far as it goes. Climbers pass through leaves and can stand on the canopy. Australopithecus climbs best, erectus worst.
- **Falling:** any fall damage at all, once, earns the Lucy achievement.
- **Looks by stage.** Each stage is drawn as its own kind: skin, height, and a face built out from the head. Australopithecus has a heavy brow and a muzzle; habilis a smaller jaw; erectus keeps the brow ridge over a face gone nearly flat - a nose that stands out from it (the first hominin thought to have had one) over a mouth that projects only a little.
- **Threat display (G):** pant-hoot, leap and frighten what is near. Your band joins in; every voice widens the radius and raises the odds. Erectus adds 30% on its own, habilis 10%. Three displays at nothing earns an achievement and a screen flash.
- **Throwing:** sneak-use a rock or branch. Before erectus it is a wild heave; erectus throws straight and hard.
- **Thinking (hold K):** needs 7 shanks of food and 60% health, on a 3-minute cooldown. It is how every recipe is first worked out — and, once something has run from you, how you keep hold of the chase instead. With empty hands it wanders instead: the day, the night, hunger, thirst, the size of the band, the bones you are carrying.
- **Bleeding, in three tiers.** *External* (teeth, flakes) bleeds and stops. *Internal* (spears, hyenas, the big cats) bleeds harder and stops you healing while it runs. *Catastrophic* is a clock: 60 seconds, and the only way out is 24 water — four drinks, or three full eggshells. Survive and you are lacerated until dawn; get hurt again or eat raw meat before then and it goes bad — an infection that blocks healing for two days, drains hunger and makes you sick. Death by any of it says so: *bled to death*, *bled out*, or *a bleed inside the skull* (never "magic").
- **Ticks.** At most two a day. Past six, nothing heals until somebody grooms them off you.
- **Healing:** exactly one thing can ever stop you mending — blood loss, infection, lacerations or ticks. A worse affliction replaces a lesser one and wipes it; a lesser one arriving while something worse runs changes nothing. One cause, one timer, and it always tells you which.

---

## 3. Tools and materials

**Stone**
- **No mining at all.** Bare hands take only what is soft enough to pull up: leaves, grass, fruit, mushrooms, nests, carcasses, loose rocks. Everything else needs the right tool, and stone stays in the ground until far later. You cannot break a deposit and you cannot put one down.
- Loose surface scatters and outcrops of chert, quartzite and limestone.
- **Basalt** - lava that cooled: dark, heavy, fine-grained cobbles with a rusty weathered top and gas-bubble pits. It lies scattered around every lava pool, thick near the lava and thinning out to 40 blocks away (as far as the chunks around the lava reach - 24 to 40 blocks), on the cave floors round underground lava lakes, and in patches on the high peaks where obsidian weathers out, twice as often and spread wider. Every surface lava pool that throws basalt also leaves a piece or two of obsidian right at its edge - so a trail of dark stones means obsidian ahead. Basalt is a tier behind chert: good knapping stone, never flawless in the Acheulean, 10 of it for a multi tool, and +0.25 damage on its tools.
- Outcrops come in three sizes: a low knuckle you could walk past (40%), a proper outcrop up to three blocks high (45%), and now and then a big one - nine blocks across, up to five high, often with a second crest - that you can see from a long way off (15%). Every one is solid rock at the heart, breaking up into scattered stone at the edges, with roots running down under it.
- Two loose cobbles make a hammerstone; a hammerstone opens deposits.
- A worked face gives 2–3 rocks: 22% chance of a hammerstone on quartzite, 18% on chert, 22% of a chert nodule. Loose quartzite gives one 25% of the time. Plain country rock is empty 40% of the time.
- **A seam holds two round cobbles and no more.** It keeps giving stone forever, but once its hammerstones are out it is flat rubble — so hammerstones mean walking to the next outcrop rather than standing at one.
- Limestone will not hold an edge. It is good for grinding stones and nothing else.

**Knapping** (P, holding a rock with a hammerstone in the off hand)
- A menu of what you could try: flake, chopper, multi tool, split the core.
- An Oldowan multi tool costs 8 chert, 10 basalt, 1 chert hammerstone or 2 obsidian, and splits back down into 12 chert. Chert stacks to four, so the cost is counted and paid across your whole pack, not one stack.

**Hand recipes** (P, one thing in each hand)
- Nest; pointy stick (plain or sharpened stick + flake); sharpened spear; chopper; digging stick; wooden club; fire-hardened spear; **twine** (thatch + thatch); **fire drill** (stick + stick).
- Most must be thought of first. Trying one you have not worked out fumbles, and sometimes breaks the material.

**What the tools do**
- Flakes cut, crack bones and wear out.
- Sharpened and pointy sticks hunt; pointy sticks cut.
- Digging stick: foraging succeeds 75% of the time and brings up 1–3 insects at once.
- **The digging stick in the hand.** Carried upright like a staff, the blade planted by the right foot. At anything alive, both hands raise it and stab it down and forward, the body driving it. Digging (dirt, sand, gravel and the rest) and foraging: both hands lift it, drive the blade into the ground a block ahead with the weight over it, then lean back on it to lever the soil up - stroke after stroke while you dig, one stroke per go at the ground when foraging. Band members dig the same way when they forage with one.
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

### Homo erectus

- **Taking things down.** Everything an erectus band builds can be picked back up by hand: the knapping station, the work station, thatch, bedding and building posts. A hominin carcass is butchered with any stone tool.
- **Checklist.** Required: survive 6 days, make an Acheulean tool, spend a night by a lit hearth, **hunt a megafauna animal** (a Pelorovis, or a sabertooth, homotherium, giant hyena or crocodile — the killing blow, your band's, or one you drew first blood on and ran down), and **reach knapping level 2**. Then any 3 of: eat 3 pieces of cooked meat, run down big game you wounded (it dies 30+ seconds after your first hit), walk a scavenger off a kill, range 1,000 blocks from where you began as erectus, trade with another erectus band, grow the band to 14, adopt a moral.
- **Milestone → heidelbergensis:** make a tier 2 or better Acheulean tool from anything but obsidian.
- **Hide** drops from cows and horses. **Knapping station:** 4 sticks in the main hand, a hide in the off hand, press P.
- **The knapping station** is a proper workbench now. Open it and lay out a **hammerstone**, a **bopper** (a bone or long bone) and up to **four rows of stone**; it all stays there when you walk away, and breaking the station drops it. The block shows what is on it: an empty hide mat and anvil, then the hammerstone, the bone and a pile of cobbles that grows as you stock it.
  - **One button** along the top cycles the **industry**: *Oldowan* (flake, chopper, multi tool, grinding stone — hammerstone only, no bone needed) and *Acheulean* (erectus on; needs the bone too). Click what you want to make.
  - The first stone laid out is the one worked. For the Acheulean the side panel shows your **odds of each tier** with that stone at your current skill level, and warns if the hammer or bone is missing.
- **The Acheulean.** 2 of a stone per tool. A **hand axe** (chops wood — logs finally break — and is a heavy weapon) or a **cleaver** (a lasting knife: meat, marrow, thatch — and it chops like a chopper). Both are solid 3D knapped stone in the hand; every tier of a tool looks the same. The **hand axe** is remade: a teardrop biface - a heavy rounded butt with a patch of the cobble's skin left on it, widest a third of the way up, tapering to a point; lenticular in section with a thin, sharp rim all round; scars knapped from both faces, offset face to face, and a median ridge up each face. The cleaver is modelled properly: a rounded butt, sides flaring to one straight, thin, glassy bit across the end, and flake scars and a ridge on both faces.
- **Swinging them.** The hand axe is carried low in front in both hands, lying level. Attacking: a heavy, sluggish sideways slash — dragged out to the right at chest height, a beat while the weight gets moving, then swept across and carried through to the left, the axe level and its edge leading the whole way. Breaking a block, it works like the cleaver's push: the body sits back and turns a little into the stroke, and both hands drive it straight in, level, pointing ahead. Hold the button and it goes again and again from the grip, both hands staying on it; let go and it goes back to the carry. The cleaver (and the chopper) is carried low in front in both hands, flat face down and pointing ahead; to use it, the body sits back a moment, then both hands drive it straight forward with the whole body leaning in behind - it never rises or dips, the blade stays level and face down the whole way - and it comes back to the carry. It lands quickly, well inside the cleaver's own attack time. The carries show in third person; first person holds both tools pointing forward and in toward the middle of the screen, flat. Band members swing them the same way, and lean with their whole body just as a player does. Every other tool or weapon without its own swing slashes like a flake.
- **Stone tools have materials.** Every stone tool - flake, chopper, hammerstone, Lomekwian core, multi tool, grinding stone, hand axe, cleaver - is made of the stone it was knapped from, and looks it: **basalt** (dark grey), **quartzite** (pinkish, grainy), **chert** (honey-brown and waxy, with a chalky white rind on a chopper), **limestone** (pale cream) and **obsidian** (black glass with a violet sheen). Basalt adds 0.25 damage, chert 0.5 (a quarter of a heart), obsidian 1 (half a heart) - and a quarter of the time an obsidian cut opens one bleeding tier deeper than the blow would on its own (a clean cut starts one; external becomes internal; internal becomes catastrophic). The tooltip says what it is. Tools knapped from plain country rock are nothing in particular and keep the plain look, as do tools made before materials existed. Hammerstones struck from a face are that face's stone. The creative tab has a hand axe, cleaver, chopper and flake of each stone.
- **The flake** is refined: a striking platform at the struck end, the bulb of percussion under it, two ridges on the back meeting in a Y, thin feathered sides and a curve toward the tip.
- **Lomekwian cores** can be made once every two minutes - the hands need a rest between mistakes.
- **Wood.** The hand axe fells trees outright and works anything made of wood — planks, slabs, stairs, fences, gates, doors, trapdoors, signs, ladders, chests, barrels, crafting and work tables. Choppers (the chopper, the cleaver, a Lomekwian core, the Oldowan multi tool) still only hack a long branch off a trunk and leave it standing.
- **The chopper** is modelled properly now: a fist-sized, water-rounded river cobble, tan cortex with a few low swellings, one end struck from both faces into a heavy, sinuous, zig-zag edge with grey flake scars offset face to face. It is carried and swung exactly like the cleaver - carried flat in one hand, caught by the other and driven straight forward.
- **Trade value.** Hand axes and cleavers trade at Treasured (5) when excellent or flawless, Prized (4) at tier 2–3 and Crafted (3) when crude, and better-made ones rank higher within a tier; to bands before erectus they are all Treasured. Hides, twine, thatch, cooked meat and worked wood are tradeable too.
- **A flawless hand axe is the multitool.** There is no separate Acheulean multitool: a tier 0 hand axe carries the `multitools` tag and also counts as a flake and a hammerstone everywhere the game asks, and its tooltip says *Multitool*. The odds panel reminds you when a flawless one is possible.
- **Quality tiers** 4 (crude) to 0 (flawless). Durability ×0.4 / ×0.7 / ×1 / ×1.6 / ×2.5. Damage rises 0.375 per tier: a hand axe hits for 3.5 at tier 4 and 5 when flawless (cleaver 3–4.5). Tiers 4–3 sometimes cause external bleeding, tier 2 internal, tier 1 always internal with a 15% chance of catastrophic, tier 0 30%.
- **Knapping skill** levels 4 to 1, shown in the journal. Leave 4 after 1 tool, 3 after 2 more, 2 after 3 more; thinking (K) while holding chert, obsidian or a good Acheulean tool sometimes counts. A Lomekwian knapper starts at 3. Carries over when you evolve.
  - Level 4 makes tier 4; level 3 tier 3 (20% tier 2); level 2 tier 2 (25% tier 1, 40% with obsidian); level 1 tier 1 (15% tier 0 with chert, 45% with obsidian).
  - The stone caps it: limestone never beats tier 4; quartzite tier 3, or 2 at level 1; basalt tier 1 (excellent, never flawless); chert and obsidian can reach 0; obsidian is never worse than tier 2.
  - Tier 0 earns *The Last Tool You Will Ever Need*.
- **Band members** each have a knapping level (mostly 3–2, rarely 1 or 4; shown in Info). Children start at 4, or learn their minder's level or one below it. Erectus members with a knapping station within 24 blocks make their own hand axes and get better at it.
- **Hearths.** From erectus the fire drill lights a hearth (a campfire) instead of a flash of fire. It burns 2 minutes on its own; feed it sticks (+1 min), long branches (+2.5), logs (+5), grass or nesting material (+0.5), up to 20 minutes banked. It goes out when it runs dry; relight it with the drill. It cooks: meat chunks become cooked meat chunks. At night no predator comes within 24 blocks of a lit hearth you are sitting by.
- **The primitive work station.** 2 hide in hand, 4 rocks of any kind in the off hand, press P. It opens a proper 3×3 grid with its own tool slot — nothing is made without the right tool sitting in it.
  - **Workable branch:** a hand axe worn against a log (or a long branch) — 2 from a log, 1 from a branch. A 3D squared branch with the knots cut flush.
  - **Workable shaft:** one workable branch with a **cleaver** in the tool slot — trued straight and even. The stock for stone spears, proper digging sticks and species super-weapons to come.
  - **Worked wood handles like a long branch:** both are carried and swung with the long branch's animations and do what it does — reach, concussions, knocking on trunks, throwing, fuel — but they have durability (48 and 64), and when it runs out they become an ordinary long branch.
  - **Club:** two workable branches down the centre column, a hammerstone in the tool slot.
  - **Building branch:** a workable branch on a base of 3 rocks, makes 2. It places as an upright post in a ring of rocks; stack them and the ones above stand on the post below with no rocks of their own.
  - **Thatch block:** a full grid of thatch, 4 twine in the tool slot. Left uncured it rots away on its own over time; right-click it with hide to cure it, and it lasts for good. The binding is only on top and around the top of the sides — twine for raw thatch, a laced hide flap for cured — with straw below and cut stem ends underneath.
  - **Thatch bedding:** 3 hide over a row of thatch, 10 twine in the tool slot — makes 2. Lay two side by side to sleep in; a nest still works, but a nest is what leaves you with ticks. Beds laid together join into one: a bed placed beside another turns to face the same way, the hide runs across the join with no hem, the pillows meet, and a bed joined at its head end gives its pillow up to the longer bed.
  - **A hide roof shelters the wall under it.** An uncured thatch block does not rot if a cured one sits anywhere above it in an unbroken stack of thatch - so cure the top of a wall and everything beneath it holds too.
  - **A proper digging stick:** a branch and a hammerstone side by side, 10 twine in the tool slot.
  - Not yet built: building blueprints (preset thatch tents and huts). Posts and thatch place freely for now.

## 4. Scavenging and hunting

- Everything that dies leaves a **carcass** — a 3D ribcage block. Animals also die on their own of exhaustion, disease or old wounds, so the country has bones in it.
- A fresh carcass gives a rib, a long bone or a plain bone, plus 1–2 meat chunks. Eating a rib leaves the bone, and the bone cracks for marrow.
- **Old bone beds** generate in the homeland, about as rare as a chert outcrop: 3–5 bones, 2–3 long bones, 1–2 ribs.
- **Giant carcass:** megafauna leave a giant ribcage block instead of an ordinary carcass. It is far too big to pull apart by hand: **crack it open with a hammerstone** (or anything that counts as one) for 9–14 meat chunks, 3–5 ribs, 4–6 long bones and 4–7 bones, scaled by the season. A clan strips it in four sittings, and you can see the meat going; what is left drops less.
- **Who comes for a kill.** An ordinary carcass may draw a **Crocuta clan** (25%; 25–40% if the kill was yours — clans come for your kills too). A giant carcass draws the **giant hyena** 45% of the time (60% in hard times), otherwise sometimes a clan. Kills register themselves, so scavengers walk straight to them.
- A clan strips a carcass only when nobody is standing within 6 blocks of it; with anyone there, they fight for it instead (see Crocuta, under Animals).
- **Lone hominins:** 12% of fresh kills and 30% of old bone beds have a solitary hominin of your species sitting at them. Walk up and they join you. A clan will not strip a carcass with one sitting at it - though a giant hyena will go straight through them.
- **Prey bolts:** anything you strike runs — Speed II for 2 seconds, then Speed I for 3 — and every animal within 12 blocks runs with it. Predators, the fearless, and baboons with a troop behind them stand their ground instead. Hominins are never treated as prey.
- **Habilis scavenges on purpose:** the band hunts out carcasses within 24 blocks, strips them, and cracks long bones with a stone for marrow — *"Sweet, a bone!"* Foraging pays 40% less at habilis, so the carcass is the meal.
- **Persistence hunting:** from habilis, think after the animal has run and it is marked for its first run (30 s). Erectus can re-mark whenever the mark fades, with no cooldown, for 4 water a time. A bleeding animal does not heal for 3 minutes. Only for animals big enough to outrun you.
- **Dead trees for grubs.** Right-click a decaying log (empty-handed, or holding anything that is not a block) to pull it open: up to 3 grubs curled in the rot (one time in five, only sawdust), and the log is left hollow - a decayed log, fit for termite fishing. A dead tree has two logs worth cracking, and then it has given everything it had.
- **Persistence hunting is a skill, level 3 to 1.** When big game breaks and runs, you keep it in sight on your own 20% of the time at level 3, 40% at level 2, 60% at level 1; otherwise you are told to hold K and pick its tracks up. Experience hits harder too: +half a heart at level 2, +1 heart at level 1, on any game animal (band members get their own level's bonus). Two runs-down take level 3 to 2, three more take 2 to 1. The journal (J) shows your level.
- **When you lose sight of it**, the band stops chasing blind and waits for you to think. Every few seconds, a member who tracks at least as well as you may find it for you (*"Mira finds the tracks again and points the way"*), and the chase goes on.
- **Megafauna run for distance, not escape.** Broken, a Pelorovis makes one hard run (Speed II, 5 seconds), then has to stop and catch its breath: Slowness IV for 10 seconds, standing still - that is your window. Rested, it watches you, and runs again the moment you come within 10 blocks. Every run is a fresh roll to keep it in sight.

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

**Talking (H)** — grouped by topic (Food, Tools and things, Danger, Social); right-click one first to speak to just them. **Right-click one while holding food** and they take it straight from your hand — fed, bonded, a want settled, or a stray won over — no menu needed. Everything else goes through the menu: right-clicking with anything else only picks them out, and handing things over is *Here, take this* under Tools and things.
- **Food:** Let's forage · I'm hungry
- **Tools and things:** Here, take this (whatever you hold — food, what they asked for, anything for their pack; feeding a stray this way is how you win it over) · Trade · I need an item · Get me… (sticks, branches, nesting material, any of the four stones — and, as they come to think more of you, a flake, hammerstone, pointy stick, digging stick, chopper, spear, club or multi tool. They can only hand over one they actually have.)
- **Danger:** I'm hurt · Let's hunt together · Don't hunt with me · Let's climb a tree / All clear
- **Wants.** Every member wants something - their favourite food, a preferred stone, obsidian, a branch - with an urgency: eating for two or going hungry above all, then an obsession, then having nothing to hold, then a taste. Only the **two most urgent** in the band say so out loud; the rest keep it to themselves (Info still shows it) until one of the two is settled, and a want never asked of you costs nothing when it lapses.
- **Knap me a tool...** (H → Things, on one member you picked out). Erectus members make a hand axe or a cleaver, anyone from habilis a chopper. Their knapping level decides what comes out - a master's hand axe is excellent, and flawless off chert or obsidian - and what they ask: what they want right now, or else their favourite food, 1 to 4 of it by skill (a chopper is always 1). Bond 5 takes one off; **bond 8 and they do it for nothing**. Hand the price over with *Here, take this*; once paid and they have two good stones (one stone for a chopper - they go looking, or you can give them some), they sit and knap for 25 seconds and hand it to you. An unpaid ask lapses after 10 minutes.
- **Friends who are good at things look after you with them.** At bond 6, a good or great tracker sometimes slips away when you are going hungry and brings you meat, and comes along whenever you go after something; a skilled or master knapper who sees you with no real edge makes you a hand axe or cleaver unasked.
- **Social:** Let's stick together today · Groom them · Get these off me · Let's play · Let's share food · Info · Tribe stats · Let's have a child
- **Culture** (erectus on): your people's ways — see below.

**What they say**
- Every kind of moment has a pool of lines (a dozen kinds of news, a dozen more of things said aloud, the crafting announcements, fear, and thoughts), and the chat never repeats a line said for that moment in the last few times.
- A leader never hears the same kind of news twice in a row, back to back; and something everybody notices at once — a carcass full of bones — is announced by whoever saw it first, once every 30 seconds at most, not by all of them.
- Thoughts draw on the season and on the band's own morals, and never repeat the last few you heard.

**Bonding**
- **Picking somebody out stops them.** Right-click any hominin and they stop, turn to face you and wait; press H within five seconds to talk to just them.
- **Trading** is done face to face: pick someone out, press H, choose *Trade*. You see everything they carry and what each thing is worth to them; choose what to offer from your hotbar, then what you want. Worth is measured in their era, droughts cost a tier, and obsidian obsessives will not part with obsidian.
- **Teach** (under H): pick a skill you know and your band watches you — then do it, and everyone watching learns it. Skills that cannot be shown are told instead, and words only get through to some. Children learn what their minder knows as they grow up. Taught members fish termites far more often, crack extra marrow, and groom you better.
- **Grooming pays.** Two ticks come off per session and you keep them as food. Being groomed puts that member in debt, and they come back and groom you in return — nobody can reach their own back. Ask for it with *Get these off me*; anyone in your band will do it. A member with ticks on them can always be groomed again; bond only rises once per ten minutes.
- **Play:** tag or wrestling, only when nothing has attacked recently. Each round of tag gives an extra second of flight the next time adrenaline hits; each round of wrestling gives an extra five seconds of fight. Up to three rounds per species, and it counts for you too. Children play on their own.
- **Band cohesion, 0 to 50.** A new band starts at **30, neutral**. It rises with sharing food (+1 to 3, once in 10 minutes), grooming (+1, once in 2 minutes), play-wrestling (+1, once in 5 minutes), feasts for the dead and the skull (+3), meeting a need (+2) and keeping a promise (+2); above 40 it slips by 1 a day unless kept up. It falls with deaths (-3), theft in the band (-1), wants left unmet (-1 each, and they add up), needs left unmet (-6), breaking your own rules (-3, -8) and broken promises (-6). Tribe stats and the journal show it with its tier.
  - **10 and under - dire.** One more failing of yours (a need or want ignored, a rule broken) and the band has had enough: they display at you, sometimes the biggest of them beats you (60% of your health), and they drive you out - the band is gone and it counts as a band lost. Misfortunes (a death, a theft) never trigger it.
  - **20 and under - borderline.** No trades at all. Every few minutes someone tells you what they think of how you lead - and what you should have done (the last failing, named).
  - **21 to 29 - tipping.** They tell you what you could be doing to help the band, and turn down half your trades unless you promise to do better (H → Social → *I'll do better* - only while tipping; at 20 and below it has gone past promises): then they trade, and give you a day - cohesion up 3 and no new failings. Keep it: +2. Fail, or slip before the day is out: -6.
  - **30 to 39 - neutral.** Life as usual.
  - **40 to 49 - positive.** One more joins you when you go after something; bond gains come faster (half the time +1 extra); gifts come half as often again; members freeze less and stand to fight more when something comes for them.
  - **50 - perfect.** Everyone treats you as bond 2 at the least; every bond gain is +1; gifts twice as often; two more hunters join you; foraging members find more and more often; and when anything hurts you, every grown member within 32 blocks comes at once.
- **A band that does not trust you falls apart under pressure.** Below neutral, members freeze more and stand to fight less when something comes for them (worse at borderline, worse again at dire), and there is more theft: 1.4× when tipping, 2× borderline, 2.5× dire.
- **Needs, not wants.** At most one at a time for the whole band, said out loud and marked with a glow: a pregnant member needs a proper meal (a rib, a meat chunk, a grub or a termite stick); an injured member needs a good stone to work while they are laid up. Ten minutes to meet it, with a reminder at half time; hand it over with *Here, take this*. Met: +2 cohesion, +2 bond, and the band saw you put them first. Ignored: -6 cohesion and -3 bond - and it counts as a failing. Tribe stats shows the need at the top, and the name finds them.
- **Injured members.** Hurt below half health by something (not you), a member is laid up for a day: 35% slower, no exploring, roaming, quarrying or joining hunts - they stay with the band - and anything that attacks one has every grown member within 24 blocks come for it. They are not idle: every couple of minutes, with a good stone to hand they practise knapping (and get better), and once in a while they lie still and think, and work out a skill they did not have (once per injury) - which they can then pass on. *"… is back on their feet."*
- **Share:** everyone puts food in and it goes to whoever likes it best — bond for all, cohesion for the band, once every 8 minutes. Tastes change: eat something often enough and it may become a favourite, and they will say so.
- **Grooming:** stand beside one for 5 seconds — bond, a little healing, band cohesion, once per member per 10 minutes. Hair is picked through up to habilis; erectus and later clean skin and grit instead. They groom each other unprompted.
- Feeding raises bond: a favourite always does, sometimes by two; anything else about one time in four.
- **Deaths are announced** in chat, like a named pet's, and each one costs the band 3 cohesion (a misfortune, not your failing - it never gets you driven out).
- Favourite foods raise bond. At bond 3 they look after you unasked: food when you are hungry, a better weapon than yours, a hammerstone if you have none, a flake if you have nothing to cut with.

**Habilis and later**
- Each member prefers chert, quartzite or basalt, or none of them; one in five is obsessed with obsidian and will not part with it.
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

**The dead**
- Every hominin that dies (your band, other bands, Paranthropus) and every chimpanzee or bonobo leaves a **hominin carcass**. Butchered, it gives 2–3 hominin meat, sometimes a rib or long bone, the **brain**, and a 35% chance of the **skull**.
- **Eating hominin meat** without a norm costs 1 cohesion — the band saw — at most once every ten minutes.
- **The norm** (*Our dead stay with us*, in the Culture tab, erectus and later, kept across evolutions): eating your dead is right, and costs nothing. But it binds — every time one of your band dies, you owe a feast: eat hominin meat or a brain with at least one of the band within 16 blocks, within a day (+3 cohesion). Miss it and it is a betrayal: −8 cohesion and −1 bond with everyone.
- **Kuru.** A brain carries it one time in three (*"The brain tastes strange. Sweet, and wrong somehow."* tells you it did; your journal shows it), whoever eats it — you, or whoever at the feast takes the brain when you do not. Incurable, milk included. Day one, trembling and nausea; late on day two, blindness; the morning of day three, death (*"shook, laughed, and died of kuru"*). Meanwhile predators can tell: camp pressure builds far faster. A band member with kuru shakes visibly and dies after two and a half days. Your own death clears it.
- **Hominin Skull.** Use it to hold it out at arm's length and look it in the eye — the pose holds for two seconds — while the screen shows the picture and *"Whatever took you — a predator, another band, disease — you will be avenged."* +3 cohesion, +1 bond with everyone of yours within 8 blocks, once a day. Achievement: *Tuff Pose + Monologue*.

**Culture: your people's ways** (H → Culture, erectus on)
- The screen fits any window: as many morals as there is room for, the rest a scroll (or the ▲ ▼ buttons) away.
- A screen of morals: each with what it does, when it binds, whether it binds *right now*, and a button to adopt it or let it go. The season and days left sit at the top.
- Morals are situational. Most only bind in the times they are about — **hard times** (a dry season or a dry day) or **plenty** (a prosperous season) — and each pays off in one time and costs in another.
  - *Our dead stay with us* — always (see The dead).
  - *It's okay not to share when times are tight* — in hard times: half as many wants, no bond lost when a want runs out; but no food gifts, and only members at bond 3+ hand food over when you ask.
  - *Stealing is wrong when there isn't much to go around* — in hard times: 70% of would-be thieves think better of it, and the rest are caught and made to hand it back (+1 cohesion, the thief −1 bond). Take food from a hungry member's pack yourself and it costs 3 cohesion - a failing of yours.
  - *Stealing is wrong even when we have plenty* — the same, in a prosperous season.
  - *Sharing is always good* — shared meals give 2 bond instead of 1 and come twice as often; an unmet want costs 2 bond instead of 1. Cannot be held with the tight-times rule.
- **Letting go** takes two in-game days, and the moral still binds while it fades (*Keep it* takes it back up). Once gone, it cannot be adopted again for a day.
- **Theft.** Every couple of minutes there is a chance someone in the band steals — the hungriest, taking food, in hard times (35%); anyone, taking food or good stone, otherwise (15%) — more in a dry season (×1.5) and in a band coming apart (see cohesion). Antisocial members are the thief 70% of the time when there is one, and defy the rule against it half the time unless the band holds *Nobody lords it over the rest*. Unchecked, it costs a point of cohesion and the victim says so.
- **Seasons bite.** In a dry season everyone's hunger runs down faster - yours and the band's - and there is more stealing, so rules about food matter. In a good season, a band that has never decided *Sharing is always good* keeps what it finds: half the time a member will not hand you food or give you anything unasked. Members talk about the rules they do not have - sharing in good times, theft in hard ones, bullies nobody stands up to - and point you to *Our ways*.
- **Antisocial members** (from habilis): one in ten is born not caring what the band thinks, and more become it - with no ways of its own, the lower the band's cohesion below 30, the more members stop caring (the band is told). They are the thieves, they will not hand you food or things (unless bond 6), will not trade, never give unasked, are always on at you for food, teach the child they mind nothing, and now and then pick a fight - never to the death, but it draws blood, costs a point of cohesion, and 40% of the time lays the victim up. Tribe stats and Info mark them. A band with ways of its own and cohesion 40+ brings them round in time.
  - ***Nobody lords it over the rest*** (a new way, erectus on): reverse dominance. The band stops 70% of fights before they start, catches antisocial thieves like anyone else, makes shunning work far better and brings the antisocial round faster.
  - **Shun them** (H → Social, one member, erectus on): the whole band turns its back. An antisocial member either bends (40%, or 70% under reverse dominance: +2 cohesion) or walks off for good (+1). Shun someone who has done nothing and the band will not do it: -4 cohesion, -4 bond.
- **You are held to it too.** Carry 12+ food while members near you go hungry and the hungriest come and stand in front of you: *"Pass it around."* **Pass around what I'm holding** (H → Things) splits the food in your hand between everyone near who is hungry, hungriest first (+1 or 2 cohesion, bond for some). Ignore it for three minutes: -2 cohesion, and again every three minutes. And they keep count: food handed over, things taken from their packs and errands run are taking; anything you hand them, needs and wants met, sharing and passing around are giving back. Take five more than you give and someone tells you so; keep on to eight and it costs 3 cohesion. The debt fades slowly on its own.
- Adopting any moral counts for the erectus *adopt a moral* goal. Tribe stats lists your morals and the season.

**Mates and children**
- From habilis on, hominins **pair-bond**: one mate, kept. Before that, nobody is anybody's in particular.
- **Nobody arrives paired.** A new band starts single; each member takes one to three days before looking for a mate, and about a third never pair up on their own.
- Well-fed members (hunger above 15, mostly healthy, not bleeding) pair up on their own and announce it in chat, then conceive now and then: *"Asha is expecting. Ido is the father."*
- **You can be chosen too.** Groom or feed an adult of the other sex in your band. It takes three times, or once with a favourite food. Or, at bond 2, pick them out and ask outright: H → Social → *Be my mate*. Then *Let's have a child*: if you are female, you carry it; if male, they do.
- From habilis, **your mate keeps close** (within about 4 blocks, not the band's loose 10). When a predator hits you, your mate gets Speed I and Strength I for 5 seconds; when one hits your mate, you do. At most once every 3 seconds.
- **Pregnancy** takes a day. Hunger runs twice as fast (for you, faster exhaustion); pregnant members ask for food more often and forage more.
- **Labour**, the last quarter: a member goes off alone to a quiet spot, glowing, and predators come for her, with more arriving while it lasts. You glow in your own. While a birth is being guarded, anyone within 48 blocks gets Strength, and your threat display drives off **anything**, the fearless included.
- H → Social → **Tribe stats** (whole band): cohesion, your mate, your morals, the season, days on this ground, your own skill levels; then **Most valuable** (the skilled and master knappers and good trackers, best first), **Asking you for** (the wants said out loud, and tools being made for you), **Expecting** (who is pregnant or giving birth, and the father), **Children** (and who minds them), and **Everyone** (bond, knapping and hunting levels, mate, closest friend). **Click any name** and that member glows for 15 seconds and you are told how far and which way. Scrolls for big bands.

**Family**
- Feed a male and female together and a child comes a day later, grown two days after that.
- Adults mind children; a hurt child brings the band running, a dead one shakes it.
- When a member does something one of your tasks asks for, there is a 40% chance it counts. One-off tasks are always yours.

---

## 6. Other bands

- Wild bands settle where water and stone are — best of all where both are — and keep well apart: none spawns within 220 blocks of another. Their call tells you the direction and distance, and they light up when you get within 64 blocks.
- They will not join you, but they trade. Value is era-relative: a Lomekwian core is Treasured to Australopithecus and Common to erectus.
- **By season:** in hard times (a dry season or a dry day) trades need a tier more, territory warnings come twice as fast, and walking up to their camp gets you bared teeth and *"Keep walking."* In a prosperous season trades need a tier less, warnings take twice as long, they will travel with you whatever you have taken, and someone comes over and hands you food — once per band per season.
- **Territory:** foraging, drinking or knapping within 28 blocks of their site is noticed. Habilis keeps away from you by day; erectus tells you to stop and then demands payment. Trade them something Crafted or better, or get them to travel with you, and the ground is shared.
- Species come and go: Australopithecus is gone by erectus, habilis thins out, erectus lasts until sapiens.
- **Paranthropus boisei**, from Australopithecus until antecessor: troops of 3–6, dark and heavy-built, with a crest along the skull, a broad flat face and flared cheekbones. Not a stage you can play. They carry long branches and sharpened sticks, forage constantly, and **strip the ground for 60 blocks** — foraging near them works about a third as often, and in a drought barely at all. A threat display, or hitting one with anything (a branch, a thrown stone), sends the troop off 100 blocks, taking their foraging with them. They never fight you over it. Killing one leaves a hominin carcass. They are also good neighbours: when a predator comes into the country within 128 blocks of a troop, they shriek, and you are told which way it is coming from — once per minute at most, so a pair of cats is one alarm. Under H they only understand three things: *Trade*, where about 40% of lowball offers up to two tiers short get through (*"You got the better of that one"*); *Show me good stone*, where one walks you to the nearest chert or quartzite, waiting when you fall behind; and *Show me obsidian*. They guide once a day per troop. Make an Oldowan tool in front of them and the ones watching learn it, and knap now and then from then on; the rest only rarely pick it up once Homo habilis is about.
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
- **Bonobos** have their own model: small round head with the hair parted down the middle, flat black face, pale lips, bare dark chest, long thin limbs. From erectus on, in troops of 6–10 by rivers and at forest edges. **Nothing hunts within 64 blocks of a peaceful troop**: no predators spawn, no visitors come to camp, and camp pressure drains away. It's the only safe ground in the game. They give food to anyone nearby who is hungry, groom anyone with ticks without needing trust first, drift along beside you while you forage, and share out whatever food you hand them. Sneak-use tells you where you stand. **Hurt one** (you, or one of your band), and that troop is no refuge any more. Nor is any troop you meet afterwards, for the rest of your line: they keep away from you. There is no apology.
- **Crocodiles** lie in warm water at least two blocks deep: savanna pools, rivers, jungle, swamp. They're twice as likely during a drought. Come within about 7 blocks of the water and one lunges. If it connects, it grabs you and rolls: you're dragged toward deep water, slowed right down, and bitten every second, and the first bite bleeds. One hit of 3 damage or more makes it let go, and so does a band member hitting it. A miss sends it back under to wait. It never chases far up the bank, a threat display does nothing to it, and it drops 2–4 meat.
- **Dinopithecus**, the giant baboon: groups of 2–3, never hunting you, but deadly if you come within 5 blocks. It pauses between bites and never backs off. A threat display does not frighten it — it charges you and earns *Nice Try, Genius*. Its bites cause internal bleeding, sometimes catastrophic. Gone by erectus.
- **Pelorovis**, the giant buffalo — **megafauna**. Herds of 3–5 on open homeland grass, 80 health, horns two metres across. It never starts a fight; strike a healthy one and it turns and charges (8 damage, heavy knockback, sometimes a bleeding gore), and the whole herd comes with it. Worn below 45% it breaks and runs - a burst for distance, then ten seconds catching its breath - and the hunt becomes a persistence hunt. Drops 5–8 beef, 2–3 hides and 1–3 long bones, and leaves a large carcass. Megafauna (tag `hominin_evolution:megafauna`): Pelorovis, sabertooth, homotherium, Pachycrocuta, crocodile.
- **Pachycrocuta, the giant hyena** — a loner, and much bigger now (70 health, 9 damage, armour, heavy knockback resistance, 1.5× the old size). Rare in the country and a rare camp visitor.
  - **Kill thief.** It goes for the nearest giant carcass within 56 blocks first, then any fresh kill. Anybody standing within 7 blocks of its meal gets attacked outright — no warning — and it eats the carcass whole once it is clear (then leaves everyone alone for half a day).
  - **Stalker.** Between meals it follows the band at 16 blocks, preferring children and stragglers over you, and freezes whenever the leader looks at it. Unwatched for 5 seconds, it goes in; once it has killed one, it runs.
  - **Hard to move:** a display drives it off 70% of the time if you are looking right at it, otherwise 20% + 8% per band member (max 55%) — and when it fails, it growls and stays. It breaks off below 25% health. Driving it off a meal counts as walking a scavenger off a kill.
- **Crocuta, the clan hyena** — the spotted hyena's early line. Clans of 3–5 roam the grass (4–6 in hard times at a kill); 22 health, 4 damage, bites sometimes bleed. Spawn egg puts down a clan of five.
  - **Neutral until you contest their food.** Come within 9 blocks of a carcass a clan is on and the whole clan barks — the warning. After 4 seconds, if you (or one of your band) are still within 7 blocks, they all attack. Back off past 13 and they settle; they chase you up to 22 blocks from the kill and then go back to it.
  - **G is a bluff.** A display at a clan that is on a kill or after you scatters it (they leave the kill for 45 seconds) with 30% + 10% per band member, max 80%, 10% less in hard times. If it fails, they call it and attack at once.
  - **Morale.** Each one killed makes each of the rest 40% likely to break and run; any one below 35% health runs. Killing one on a kill, or scattering a clan off one, counts as walking a scavenger off a kill.
- **Sabertooth:** attacks anything within 7 blocks, hunts baboons from 16, and is afraid of nothing — no threat display will move it.
- **Homotherium:** the scimitar cat. Daylight, open country, usually in pairs, faster than you, and it calls its partner in.
- **Crowned eagle:** circles by day and stoops on any child more than 7 blocks from an adult. Losing one earns The Taung Child.
- **Predator pressure:** camping in one place builds it — faster at night, with carcasses about, or while hurt; slower with a big band. First tracks you did not make, then visitors. Moving camp 64 blocks resets it.
- **Nomadism: the home range.** Living within 200 blocks of the same ground for two days is too long. At a day and a half you are warned; at two days everything that hunts there knows your band - camp pressure builds twice as fast, and every few minutes the country reminds you: a predator comes calling, something steals food out of a band member's pack, or one waits for whoever strays furthest and goes for them. Move 200 blocks and it starts over (*"New country. Nothing here knows your band yet."*). Tribe stats shows how long you have been on this ground. Bonobo ground stays safe.
- **Standing by stage:** australopithecines are prey; armed habilis with 3 band members nearby gives predators pause; armed erectus is not attacked at all.
- **Predators are not monsters.** One takes two of the band and then leaves with what it came for — *"The sabertooth has what it came for, and goes."* It keeps that count until it dies, so one that finds a new target cannot start again and work through everybody. Satiation is not fear, so even the fearless walk away from a full belly.
- **Predator damage** (before difficulty): giant hyena 9, sabertooth 7, homotherium 6, clan hyena 4, Dinopithecus 5, crocodile 4 a bite, chimpanzee 4, crowned eagle 4 — they hurt, and they open wounds, but no single bite kills a healthy hominin.
- **Predators fight in passes.** A bite, then a real pause and a withdrawal, which is your chance to run, climb or get a spear up. A sabertooth bites every 2.5 seconds, not every second.
- **Only a club cracks a predator's skull.** A branch stings it; four blows and anything that can be frightened breaks off instead. Nothing fearless breaks off, which is what the club is for. A concussed predator runs, your band stands down rather than chasing it into cover, and sees it off with a collective display.
- Predators hunt **ordinary game** — cattle, horses, whatever grazes near the water. Hominins are the exception, not the diet.
- Wounded animals flee, stagger when concussed, and can bleed out.

---

## 8. The world

- You start in the hominin homeland — savanna — where the good stone, termite mounds and outcrops generate.
- **Nests:** six nesting-material blocks in a complete 2×3 before you can sleep. Punch leaves for the material, or make it by hand.
- Decaying and decayed logs give way underfoot.
- **Seasons:** five days of **prosperous season**, then five of **dry season**, and round again — announced as they turn, and the band remarks on it.
  - *Dry:* foraging pays 25% less, kills and carcasses lose about 45% of what they would drop (never quite nothing), dry days are far more likely, and other bands are hard.
  - *Prosperous:* foraging pays 35% more, kill and carcass drops often come half as much again, dry days are rare, and other bands are generous.
- **Dry days:** only ever every second day, so never two in a row — 70% likely in the dry season, 10% in the rains. Foraging pays 45% less on top, trades need an extra tier, and nobody will share. Worked out from the day, so it is the same for everyone.

---

## 9. Interface

- Evolution checklist down the left, updating as you go, and cleared properly between worlds.
- Thirst bar above the health bar.
- Trade tier on the tooltip of anything worth trading, valued for your own stage.
- Patchouli guidebook, *The Inner Mind*, including a section on every key, everything you can make, and everything you can ask the band. New entries: *Wounds* (the three bleeds, the catastrophic clock, lacerations, infection, ticks) and *Holding Together* (cohesion, needs, give and take, the antisocial); basalt under *Where Stone Is*; reverse dominance under *Your People's Ways*.
- **Tips, when you need them.** 44 of them, each tied to a moment rather than a timer and shown once, ever: the first time your water runs low or you go hungry with nothing to eat, the first dusk, ticks piling up, a bleed (and at once, for a catastrophic one), a laceration or infection, full arms, a block your hands cannot break, knapping with no hammerstone, your first hammerstone, a fumbled recipe, limestone crumbling, your first basalt, a hide at erectus, being ready to evolve (with how, for your kind), a hungry band member while you carry food, the first want, the first need, cohesion crossing 30, 20 and 10, hoarding, taking without giving, an antisocial member, the dry season, the home range, being hunted (and by something fearless), a crocodile, a hyena clan's warning, a chimpanzee alpha's status check, big game, and losing a trail. Each comes as a card in the top corner and a line in chat, in your own key bindings; **double-click the chat line and the guide opens at the page it is about**. No more than one every 45 seconds - one that comes up too soon waits its turn - except the urgent ones. `/hominin tips off` turns them off, `on` back on, `reset` lets them all come again, and `/hominin tips` says how many you have seen.
- Achievements: Lucy, Lomekwian, Survivor, tired apes, Ez arms race, The Taung Child, Tuff Pose + Monologue, and one per stage.
- Spawn eggs: the **Hominin Band** egg puts down a wild band of your own species; the **Paranthropus Troop** egg a troop of four.
- Keys: **P** work held items · **K** (hold) think · **H** talk to band · **G G** (double-tap) threat display · **J** journal.
- **Journal (J).** *Stats*: species, sex (drawn fresh for each descendant), health and water, ticks, what is stopping you healing, band size and cohesion, your standing with the band and each member's bond, how many bands you can still lose, and your play training. *Skills*: everything you know by name and everything you don't as `???`; pick one to see what it is, how to do it again, what it gives you, and whether it survives evolving.
- **Starting skills.** Every time you become erectus or a later species, the new body's hands are rolled and told to you once the new band is round you: knapping 4 (40%), 3 (38%), 2 (16%), 1 (6%) - a Lomekwian knapper is never a 4 - and persistence hunting 3 (67%), 2 (25%), 1 (8%). Band members are rolled the same way, so a master knapper or a great tracker is rare, and worth keeping.
- **Some skills belong to one moment of the line.** Lomekwian knapping can only be learned by the first hominins (ardipithecus and australopithecus); **early tracking** only by habilis - think (K) right after something runs from you. Miss them then and they are missed for good; learn them and they carry over. Early tracking makes whatever you evolve into start a better persistence hunter: the rolled level is one better.
- **Skills**, learned by doing: Lomekwian knapping (multi tools never shatter), termite fishing (bonus grubs), marrow (extra marrow), firemaking (drills often survive), tracking (trails last longer), grooming (an extra tick off everyone), primate de-escalation (3 more seconds in the baboon window), and the long view — earned by thinking empty-handed — which shortens the wait between thoughts. Knowledge carries over when you evolve; tracking and grooming belong to the body and must be relearned.
- **Developer tab** (H, in developer mode only): bond up or down, band cohesion up or down by 10, the nearest troop's trust or grudge, ticks, clear afflictions, fill water, learn or forget every skill, max play training, teach the band everything.
- Commands: `/hominin status`, `checklist`, `guide`, `start`, `band`, `wildband`, `become`, `season` (and `season dry` / `prosperous` / `natural` to force one for testing), `unlockadvancements`, `bypass`, `dev`, `tips` (`on`, `off`, `reset`).
- Gamerules: `homininSuperHardMode`, `homininExtraEffort`.

---

## Planned

Everything below is written down and not yet built. Roughly in the order it is being built.

### 1. Foundations

- **More skills**, and the Levallois technique at heidelbergensis.

### 2. Screens and society

- **More for the band to learn**, as more skills arrive.

### 3. Primate relations

- Bonobos reacting to your band's standing, not only yours.

### 4. Megafauna

- **Pelorovis is in** (see Animals). Still to come: *Mammuthus subplanifrons*, *Rusingtoryx*, *Megalotragus*. Groups of 1–3 near water, grazing. Erectus and up, and only with a fire-hardened spear or better. Heavy, slow attacks — usually with a wind-up you can read, and sometimes without one. Never concussable, though a club breaks bones. A kill leaves 5+ meat and a giant carcass needing a stone tool to open, and draws hyenas, other scavengers and desperate bands you will have to fight off. Wounded, they run, and the hunt becomes a persistence hunt.

### 5. Erectus and beyond

- **The knapping station**, and industries: Acheulean at erectus, Levallois at heidelbergensis, Mousterian for sapiens and Neanderthals, Aurignacian for behavioural modernity. Quality tiers 4 to 0 decided by material and skill — limestone tops out at 4, obsidian is superb but capped at 2 unless your hands are very good, chert alone reaches 0. Band members keep their own skill levels and teach their children.
- **Blueprint-only structures** (thatch tents and huts) for the work station's building branches and thatch.
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
- Dinofelis as a night ambusher; a rock python in long grass; bonobos answering baboon alarm calls.
- Custom recorded cries for the new predators, which use vanilla sounds as placeholders.
- Fire as a lasting, carried thing rather than a one-off milestone.
- Region-based world generation, so different parts of the world hold different species.
