# Handoff: the `claude/keen-lamport-dqsh4q` session

This covers everything done in one long chat session on **Hominin Evolution** (NeoForge 1.21.1, Mojang mappings).

- **Branch:** everything was pushed to `claude/keen-lamport-dqsh4q`.
- **Commits:** 39, from `aac743c` (salt textures) to `359114b` (knapping hands fix). The session started from `474942f`.
- **Merge:** `git pull origin claude/keen-lamport-dqsh4q`, then `git push origin main`.
- **FEATURES.md** was updated with every change. It is the player-facing record; this file is the developer summary.

> **Important: nothing in this session was compiled or run in game.** The environment blocks NeoForge's maven and Mojang downloads, so `gradlew build` cannot run here. Each change was checked in two ways instead:
> - a JDK parse-only syntax check;
> - a `javac` run with no classpath, filtered to errors in calls between the mod's own classes on the changed lines.
>
> Neither catches wrong Minecraft or NeoForge API calls. **The first job in the main chat is to build and fix any compile errors**, then test in game, especially the newest items (knapping animations, the knapping hands override mixin, and the intermission for multiple players).

---

## 1. Textures, models and looks

| What | Commit | Notes |
|---|---|---|
| Salt block, salt chunk and wet sand in vanilla style | `aac743c` | Wet sand uses sand's six-tone grain in darker tones. The salt block is an off-white crust with crystal facets. The salt chunk is drawn like sugar or quartz. |
| 3D roots | `e074f78` | A tuber with a snapped stem and hair roots. |
| Eating animation | `e074f78`, then `d84d266` | Third person only: the hand is held at the mouth and bobs with each bite, for any 3D food. **The user disliked the first-person version, so it was removed; keep it that way.** |
| Homo heidelbergensis model | `9f6b6b6` | A new skin and features layer (`HomininModels.HEIDELBERGENSIS_LAYER`, `HomininFeaturesLayer.heidelbergensis()`), drawn 1.04× tall. It applies to players and band members. Sapiens and Neanderthal still use erectus. |
| Leather station screens | `f3413c4`, then `8ca1b1c` | Covers the knapping and work station screens. `LeatherButton` is a new widget. The label colours are the constants in `KnappingStationScreen`. |
| Smooth leather everywhere | `8ca1b1c` | Covers the player inventory (`assets/minecraft/textures/gui/container/inventory.png`, rebuilt on the vanilla layout), the hotbar, the hotbar selection and the off-hand sprites. The leather is a soft low-frequency mottle with no grain. Generator: `scratchpad/inv/smooth_leather.py`, which is not in the repo. |
| Evolution cutscene | `baf7ca3`, then `73230bd` | The years count down, slowly at first and then faster and faster. **It must stay vanilla:** a black background, stars and the game font, with no bar, glow or underline (user's request). |
| Levallois hand axe and flake models | `8a5b620` | **Bug fix:** `levallois_hand_axe.json` parented `hand_axe.json`, so it looked exactly like the Acheulean one. It now has its own thin, symmetrical geometry with no cortex, one big preferential scar and centripetal scars. The `levallois_hand_axe_<stone>` files parent it. The Levallois flake is now a broad oval with a faceted platform and ridges meeting in the centre. |

## 2. Knapping

- **The knapping animations** (`1087aed`, `cb7d2b3`) are in the new `knapping/KnapShow.java`:
  - **In the hands:** the stone is in the right hand and the hammerstone in the left strikes it 3 times, with small smoke puffs and chips. The tool then appears in the hand and is looked at.
  - **At the station:** the screen closes and the camera crouches (`client/KnapCrouch.java` holds shift through `MovementInputUpdateEvent`; no body lean, so the legs never show). A bone taps the stone for Acheulean and Levallois work, both hands bring the hammerstone down twice, and the finished tool turns on the mat. Then it is held and looked at.
  - **Walking off or dying:** walking more than 5 blocks away, or dying, cancels the job. Nothing is spent until the stone breaks.
  - **Animation files:** `assets/hominin_evolution/player_animations/` (`knap_hand_strike`, `station_bop`, `station_set_down`, `station_smash`, `knap_inspect`).
  - **First person:** arms showing in first person during the animation is fine by the user.
- **The station block shows only what is on it** (`1087aed`). Empty, it is just the leather mat. The hammer, bone and stone pile appear as they are laid out. `client/KnappingStationRenderer.java` draws the stone being worked and the finished tool, using the block entity's `display` value, which is synced through the update tag.
- **Band members knap with the same animations** (`cb7d2b3`). This uses `band/MemberKnapping.java` and a gesture system in `BandMember`: a synced `gesture` name plus shown main and off-hand items, played with the player's JSON animations in `BandMemberModel`.
- **Bug fix: an extra tool after knapping** (`359114b`). To show items in your hands, `KnapShow` sent a fake `ClientboundSetEquipmentPacket` to everyone, the knapper included. The knapper's client wrote that fake into its selected hotbar slot, so you briefly had the tool twice (and in creative the copy could be kept). The fix:
  - Others still get the equipment packet (`broadcast`, which excludes the knapper).
  - The knapper gets a new `network/KnapHandsPayload`. It is drawn through `client/KnapHands` and a new client mixin, `mixin/client/PlayerHeldItemMixin`, which overrides `Player.getItemBySlot` on the client, for the local player only.
  - The inventory is never touched. **The mixin needs checking in the first build.**
- **Levallois flakes give 2 per stone by design** (`StationKnapping.LEVALLOIS_FLAKES`). The user thought "2 flakes" was a bug. It is probably the extra-copy bug above, but if they want 1 flake per stone, change that constant and the hint text.

## 3. Stone materials and hammerstones

- **Every stone tool has a stone** (`bc82701`). Unmarked means quartzite (`StoneMaterial.plain()`). Unmarked tools are stamped for good in inventories, member packs, dropped items and opened containers (`StoneMaterial.tidy`).
- **`StoneMaterial.stamp()` can return a new stack:** a plain hammerstone stamped chert becomes `CHERT_HAMMERSTONE`. **Always keep its return value.** Dropping it was the cause of hammerstones in tool piles turning into quartzite.
- **Hammerstones in every stone but limestone** (`8a5b620`):
  - **Rock faces:** quartzite and basalt give one 22% of the time, chert 18%, fine chert 12%, obsidian 10% (`EvolutionEventHandler.hammerstoneChance`).
  - **Gravel:** 12% of chert, fine chert or obsidian finds come up as a round river-cobble hammerstone (`survival/Gravel.java`).
  - **Tool piles:** hammerstones in every stone (`band/ToolPiles.java`).
  - **Habilis arrival kit:** the hammerstone matches its stone.
  - **Recipe:** it now uses the new tag `data/hominin_evolution/tags/item/hammerstone_rocks.json` (no limestone), and the result takes the first cobble's stone (`onItemCrafted`).

## 4. Band members get a player's capabilities

The user asked for **everything a player can do but members couldn't**, except felling trees, fire before erectus, and leader-only things.

- **A body like a player's** (`b604b40`, mostly in `band/MemberSurvival.java`):
  - **Thirst** on the player's clock. Springs count double, and members fill the shells they carry.
  - **Catastrophic bleeding:** the same one-minute clock. They run for water, and 24 mouthfuls turn it into lacerations.
  - **Illness:** turned meat makes them ill, raw meat on a laceration infects it, and a starving member will eat turned meat.
  - **Other habits:** they drink eggs with a sharpened stick, lick salt once a day, and get the hearth's protection from predators at night.
  - **Carry space** grows with their stage, like a player's. The trade list now scrolls.
- **Fending for themselves** (`637cbc1`, using the shared goal shape `WalkAndDoGoal`):
  - **Cooking:** they cook their own meat at a fire pit, campfire or rack (`CookGoal`).
  - **Food:** they crack dead logs for grubs, and crack giant carcasses with a hammerstone.
  - **Hunting:** armed and hungry, they hunt big game and call 1–2 others along; from erectus, three spears together will take on a giant. They stay on bleeding prey longer.
- **The camp's gear** (`c6a41d4`):
  - **Erectus members build:** a work station and a knapping station, a fire pit, two racks and a spit, torches and a proper digging stick.
  - **Habilis and later** hack out a digging stick with a chopper.
  - **Fire:** they light pits with a fire drill or a torch.
  - **Torches at night:** they carry lit torches (with dynamic light), plant them by where they sleep, and throw them at predators.
- **Thirsty members reach water** (`3d3f837`). `DrinkGoal` searches up to 96 blocks, walks in 16-block legs, overrides fighting and guiding, and is exempt from the leader's catch-up teleport while seeking.
- **Tree nests** (`79d8b9e`):
  - **Who:** Australopithecus, habilis and earlier kinds can place nests on leaves or branches, against a trunk, or one block out from one. Erectus and later are told to nest on the ground.
  - **Climbing members:** they build in the crown of a tree, climb up to sleep, and come down in the morning.
  - **Tick bites:** half as likely in a tree nest.
- **They stay out of fire and lava:**
  - **Lava** (`4645988`): pathfinding avoids lava and fire (`keepOutOfLava` is a step guard that runs every tick, and `escapeLava` gets them out).
  - **Campfires** (`6bac973`): `BandMember.burningAt()` covers fire, lit campfires, lit fire pits and magma, and counts them as lava. Items lying in fire aren't fetched (the cook rakes them out from the edge), and the catch-up teleport never lands on fire.

## 5. Multiplayer

- **Right-click another player** (`24e3ad3`, `band/PlayerMenu.java`): Groom, Trade (swap held items), Teach skill, Be my mate, Let's have a child (with an opposite-sex mate) and Info. The other player can decline anything except Info. Mates are stored in `band/PlayerTies.java` (SavedData).
- **"For players only" pile mark** (`f028620`, `band/PilePlayers.java`): an optional filter to exact players. It is `ToolPileBlockEntity.FOR_PLAYERS = 3`.
- **Roles, permissions and usurping** (`21e2b92`, `band/BandRoles.java`; roles live in `Newcomers`). This is in the H menu, under "Roles and leadership":
  - **Roles:** co-leader, influential or member. Commands are gated by role.
  - **Per-player bonds** with members (`BandMember.playerBonds`).
  - **Usurping:** split off with your followers, or start a civil challenge. Bond 3+ always joins; otherwise the chance is 20%, higher when cohesion is below 20 unless the "we don't betray our own" moral holds it down to 10. A split is easier than a challenge.
- **Your band's view of other players' bands** (`100f470`, `band/BandViews.java`): allied, friendly, neutral, unfriendly or hostile. With hostile, your members attack their people on your land. With allied, your members defend their players.
- **Fixes:**
  - Co-leaders now lose the band along with its leader, with the cutscene and the count (`4645988`).
  - Hitting a same-band leader or co-leader no longer turns the band on you (`1bbddc1`).
  - Choosing to walk with another band no longer quietly gives you your own band far away (`f31e32d`).
- **The evolution intermission** (`06ae5b5`, `1bd54b4`, `5c2ebf9`; `stage/Intermission.java`, `stage/Arrival.java`):
  - **Everyone sees it:** a title shows whose band is evolving, into what, and a countdown. Anyone who hasn't chosen is asked again with 7 seconds left.
  - **`/hominin become`** now goes through the intermission. `/hominin become <stage> alone` is the old instant, you-only change.
  - **Who comes along:** co-leaders and joiners always go with the band and wake beside the leader with their things replaced. Other players are carried along only if they were within 200 blocks. Everyone wakes 24–48 blocks from the evolved band, with their own band.

## 6. Band conflict and havens

- **"Set an example"** (`fcc4bac`, `6675831`):
  - It bars you from settling on a haven or within 200 blocks of one.
  - It lasts only for your current species; evolving or falling back forgets it.
  - Each band fended off at a haven lowers future raids (far fewer after 5), but desperate times and the dry season bring them as usual.
- **Bands break only after heavy losses** (`6675831`): 1 death for australopiths, 3 for habilis, 5 for erectus and later.
  - **Cowed bands:** half the time a broken band is cowed. It stops patrols, demands, claims and raids until it is back to strength, holds a haven, or 5 days pass.
  - **Infamy:** killing outside self-defence builds infamy. At 3, bands you meet start lower and some fear you. At 10, the nearest non-friendly bands raid you together.
- **Band end fix** (`fb5ebb7`): dying with only children left now ends the band.

## 7. Other changes

- **Termites** (`e074f78`): building beside a super colony no longer kills it. Instead, termites eat wood and grass builds within 30 blocks, and raw meat there spoils twice as fast.
- **Days to evolve** (`6d337a9`): habilis 4 → 3, erectus 6 → 5.
- **Bug-hunt fix** (`7d55829`): about 200 static maps lived for the whole game session and leaked from one single-player world into the next. They are now registered through `ServerState.track(...)` and cleared on `ServerStoppedEvent`. **Any new static map of world state should use `ServerState.track`.** Also, survived days now keep counting correctly when the time is set back.

## 8. Advice given (no code)

- **Lithostitched:** not needed yet. It is a compatibility library for worldgen and doesn't provide regions or continents; those are your own logic either way. It can be added later without redoing anything. Use it from the start only if the mod should run alongside other worldgen mods.
- **Tectonic:** a terrain overhaul that owns the continent layout, so it clashes if your regions shape terrain. Either mark it incompatible (if you own the geography, e.g. real land bridges), or make regions that only label existing land (compatible with Tectonic, Terralith and vanilla, but you can't guarantee shapes). New biome placement affects only new chunks, so test on fresh worlds.

## 9. Open items / next steps

1. **Build it** and fix compile errors. The riskiest pieces are listed below.
   - `KnapShow` and `KnapCrouch`.
   - `PlayerHeldItemMixin`, which is in the `client` list of `hominin_evolution.mixins.json` and targets `Player.getItemBySlot`.
   - `KnapHandsPayload` codecs.
   - The gesture sync in `BandMember`.
   - `KnappingStationRenderer`.
   - The member survival and camp-gear goals.
2. **In-game checks:**
   - Knapping (in the hands and at the station) should leave exactly one tool and no ghost in the hotbar.
   - The Levallois models should look distinct.
   - Evolving with a friend online, both near and more than 200 blocks away.
   - Thirsty members walking to far water.
   - Members avoiding campfires.
3. **Ask the user** whether Levallois flakes should stay at 2 per stone.
4. **Scratchpad generator scripts** (leather, heidelbergensis skin, model previews) were not committed. The textures they made are in the repo.
