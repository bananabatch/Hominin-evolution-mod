package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.entity.Baboon;
import dev.hominin.evolution.entity.TroopRelations;
import dev.hominin.evolution.item.AcheuleanToolItem;
import dev.hominin.evolution.item.StoneMaterial;
import dev.hominin.evolution.mind.Skills;
import dev.hominin.evolution.survival.Afflictions;
import dev.hominin.evolution.survival.Infestation;
import dev.hominin.evolution.survival.Seasons;
import dev.hominin.evolution.survival.Thirst;
import dev.hominin.evolution.world.Pois;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Developer tab under H: every number the mod keeps about you, set by hand, and every event it can throw at
 * you, thrown now. Split into sections - your band, you, the world, other bands, places and tools.
 *
 * <p>For testing. Nothing here is reachable outside developer mode - the tab is hidden, and every command checks
 * again on the server in case something asks anyway.
 */
public final class Developer {
    private static final double RANGE = 16.0D;

    public static void run(ServerPlayer player, int entityId, Social.Command command) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (!data.isDeveloperMode()) {
            player.displayClientMessage(Component.literal("That is a developer tool. /hominin dev turns it on."),
                    true);
            return;
        }
        ServerLevel level = player.serverLevel();
        var counters = data.getCriterionCounters();
        String done = switch (command) {
            // ------------------------------------------------ your band
            case DEV_BOND_UP -> bond(player, entityId, 5);
            case DEV_BOND_DOWN -> bond(player, entityId, -5);
            case DEV_COHESION -> {
                Cohesion.add(player, 10);
                yield "Band cohesion is now " + Cohesion.get(player) + "/" + Cohesion.MAX + ".";
            }
            case DEV_COHESION_DOWN -> {
                Cohesion.add(player, -10);
                yield "Band cohesion is now " + Cohesion.get(player) + "/" + Cohesion.MAX + ".";
            }
            case DEV_SPAWN_MEMBER -> Band.spawnMember(player) == null ? "Could not place a member here."
                    : "A new member joins the band.";
            case DEV_KILL_MEMBER -> {
                BandMember victim = pick(player, entityId);
                if (victim == null) {
                    yield "Nobody of yours is near enough.";
                }
                victim.ensureName();
                String name = victim.getName().getString();
                victim.kill();
                yield name + " is dead. (Lose enough in a fight with something still attacking, and allies come.)";
            }
            case DEV_MAKE_MATE -> {
                BandMember member = pick(player, entityId);
                if (member == null) {
                    yield "Nobody of yours is near enough.";
                }
                member.setMate(player.getUUID());
                member.ensureName();
                yield member.getName().getString() + " is your mate now.";
            }
            case DEV_MEMBER_FOOD -> {
                BandMember member = pick(player, entityId);
                if (member == null) {
                    yield "Nobody of yours is near enough.";
                }
                member.addToInventory(new ItemStack(Items.SWEET_BERRIES, 24));
                member.ensureName();
                yield member.getName().getString() + " is carrying 24 berries. Within the minute they share them out.";
            }
            case DEV_TRAINING -> {
                counters.put("play_tag", BandMember.MAX_TRAINING);
                counters.put("play_wrestle", BandMember.MAX_TRAINING);
                yield "Play training at its maximum.";
            }
            case DEV_TEACH_BAND -> {
                List<BandMember> band = Band.ownNear(player, RANGE);
                for (BandMember member : band) {
                    for (Skills.Skill skill : Skills.Skill.values()) {
                        member.learnSkill(skill);
                    }
                }
                yield band.size() + " band members now know every skill.";
            }
            // ------------------------------------------------ you
            case DEV_TICKS_UP -> {
                Infestation.set(player, Infestation.of(player) + 3);
                yield "Ticks: " + Infestation.of(player) + ".";
            }
            case DEV_HEAL_CLEAR -> {
                Infestation.set(player, 0);
                Afflictions.clear(player);
                dev.hominin.evolution.survival.FoodIllness.cure(player);
                player.setHealth(player.getMaxHealth());
                yield "Ticks and afflictions cleared, and healed.";
            }
            case DEV_SPOIL_HELD -> {
                net.minecraft.world.item.ItemStack held = player.getMainHandItem();
                if (!dev.hominin.evolution.food.Spoilage.isRawMeat(held)) {
                    yield "Hold raw meat - a chunk, a rib, marrow, hominin meat.";
                }
                dev.hominin.evolution.food.Spoilage.spoil(held);
                yield "It has turned. Eat it to be ill, or hang it on a rack over a fire to see it cook still spoiled.";
            }
            case DEV_FOOD_ILL -> {
                net.minecraft.world.item.ItemStack bad = new net.minecraft.world.item.ItemStack(
                        dev.hominin.evolution.ModItems.MEAT_CHUNK.get());
                dev.hominin.evolution.food.Spoilage.spoil(bad);
                dev.hominin.evolution.survival.FoodIllness.ate(player, bad);
                yield "You are sick. Drink (you can drink past full) and eat small; a big meal comes back up.";
            }
            case DEV_WATER -> {
                Thirst.set(player, Thirst.MAX);
                yield "Water filled.";
            }
            case DEV_FEED -> {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(10.0F);
                yield "Food filled.";
            }
            case DEV_FOOD_PILE -> {
                give(player, new ItemStack(Items.SWEET_BERRIES, 24));
                yield "24 berries. Within the minute, the band asks you to split them.";
            }
            case DEV_PRESENCE_UP -> {
                Presence.add(player, 4, "developer");
                yield "Presence " + Presence.get(player) + "/" + Presence.MAX + ".";
            }
            case DEV_PRESENCE_DOWN -> {
                Presence.add(player, -4, "developer");
                yield "Presence " + Presence.get(player) + "/" + Presence.MAX + ".";
            }
            case DEV_NAME_UP -> {
                Claims.addFeared(player, 2);
                yield "Your name: " + Claims.feared(player) + "/10.";
            }
            case DEV_SKILLS_ALL -> {
                for (Skills.Skill skill : Skills.Skill.values()) {
                    Skills.set(player, skill, true);
                }
                yield "You know every skill.";
            }
            case DEV_SKILLS_NONE -> {
                for (Skills.Skill skill : Skills.Skill.values()) {
                    Skills.set(player, skill, false);
                }
                yield "You have forgotten every skill.";
            }
            case DEV_RARE -> {
                give(player, new ItemStack(ModItems.WOODEN_CLUB.get()));
                give(player, new ItemStack(ModItems.FACE_PEBBLE.get()));
                give(player, new ItemStack(ModItems.QUARTZ_CRYSTAL.get()));
                give(player, new ItemStack(ModItems.CHERT_HAMMERSTONE.get()));
                yield "A club, a pebble with a face, a quartz crystal and a chert hammerstone.";
            }
            case DEV_TOOLS -> {
                for (ItemStack tool : toolSet()) {
                    give(player, tool);
                }
                yield "A set of stone tools. Sneak-use the ground on your own ground to lay one down.";
            }
            // ------------------------------------------------ the world
            case DEV_DUSK -> {
                long day = level.getDayTime() / 24000L;
                level.setDayTime(day * 24000L + 11500L);
                yield "Dusk. Nests go up, and the band turns in.";
            }
            case DEV_MORNING -> {
                long day = level.getDayTime() / 24000L;
                level.setDayTime((day + 1) * 24000L + 1000L);
                yield "Morning.";
            }
            case DEV_SEASON_NEXT -> {
                Seasons.force(Seasons.of(level) == Seasons.Season.DRY ? Seasons.Season.PROSPEROUS : Seasons.Season.DRY);
                yield Seasons.label(level) + " (forced).";
            }
            case DEV_SUPER_DRY -> {
                Seasons.forceExtreme(Seasons.Season.DRY);
                yield Seasons.label(level) + " (forced).";
            }
            case DEV_VERY_PROSPEROUS -> {
                Seasons.forceExtreme(Seasons.Season.PROSPEROUS);
                yield Seasons.label(level) + " (forced).";
            }
            case DEV_DESPERATE -> {
                boolean on = !Bands.desperateTimes(level);
                Bands.setDesperateTimes(level, on);
                yield on ? "Desperate times." : "Ordinary times.";
            }
            case DEV_TROOP_TRUST -> troop(player, TroopRelations.TRUSTED);
            case DEV_TROOP_GRUDGE -> troop(player, -1);
            case DEV_SPAWN_TROOP -> dev.hominin.evolution.entity.WildAnimals.spawnTroop(player) > 0
                    ? "A baboon troop nearby." : "Nowhere nearby for a troop.";
            case DEV_SPAWN_HERD -> {
                int count = player.getRandom().nextBoolean()
                        ? dev.hominin.evolution.entity.WildAnimals.spawnGroup(player, ModEntities.MAMMUTHUS.get(), 2, 20, 40)
                        : dev.hominin.evolution.entity.WildAnimals.spawnGroup(player, ModEntities.MEGALOTRAGUS.get(), 3,
                                20, 40);
                yield count > 0 ? "A herd, 20 to 40 blocks off." : "Nowhere nearby for a herd.";
            }
            // ------------------------------------------------ other bands
            case DEV_SPAWN_BAND -> {
                int size = WildBands.spawnNear(player, 24, 40);
                yield size > 0 ? "A band of " + size + " nearby." : "Nowhere nearby for a band.";
            }
            case DEV_ALLY -> {
                Bands.Record band = nearestKnown(player);
                if (band == null) {
                    yield "You know no band. Spawn one first.";
                }
                band.standing.put(player.getUUID(), Relations.ALLIED + 1);
                Bands.changed(level);
                yield BandNames.capital(band.name) + " are your allies (standing " + (Relations.ALLIED + 1) + ").";
            }
            case DEV_HOSTILE -> {
                Bands.Record band = nearestKnown(player);
                if (band == null) {
                    yield "You know no band. Spawn one first.";
                }
                band.standing.put(player.getUUID(), 5);
                Bands.changed(level);
                yield BandNames.capital(band.name) + " are hostile (standing 5).";
            }
            case DEV_DESPERATION -> {
                Bands.Record band = nearestKnown(player);
                if (band == null) {
                    yield "You know no band. Spawn one first.";
                }
                band.desperation = Math.min(5, band.desperation + 1);
                Bands.changed(level);
                yield BandNames.capital(band.name) + ": desperation " + band.desperation + "/5.";
            }
            case DEV_NIGHT_RAID -> {
                Bands.Record band = nearestKnown(player);
                if (band == null) {
                    yield "You know no band. Spawn one first.";
                }
                Claims.devNightRaid(player, band);
                yield BandNames.capital(band.name) + " raid you in the night. (Asleep with nobody on watch, they get away "
                        + "with it.)";
            }
            case DEV_FOOD_RAID -> {
                Bands.Record band = nearestKnown(player);
                if (band == null) {
                    yield "You know no band. Spawn one first.";
                }
                Relations.devFoodRaid(player, band);
                yield BandNames.capital(band.name) + " are coming for your food.";
            }
            case DEV_TRADE_VISIT -> {
                Bands.Record band = Fates.nearestKnown(player, true);
                if (band == null) {
                    yield "No allied band. Make one with 'Nearest band: allies'.";
                }
                Claims.devBegin(player, band, Claims.Kind.TRADE);
                yield BandNames.capital(band.name) + " are sending someone to trade.";
            }
            case DEV_KILL_BAND -> {
                Bands.Record band = Fates.nearestKnown(player, false);
                if (band == null) {
                    yield "No band you are not allied with.";
                }
                Fates.die(level, band, "(developer)");
                yield BandNames.capital(band.name) + " are gone.";
            }
            case DEV_PLIGHT -> {
                Bands.Record band = Fates.nearestKnown(player, true);
                if (band == null) {
                    yield "No allied band. Make one with 'Nearest band: allies'.";
                }
                Fates.startPlight(player, band, player.getRandom());
                yield "150 seconds.";
            }
            case DEV_RESCUE -> Fates.callAllies(player, true) ? "Your allies will be here in 10 seconds."
                    : "No allies within 500 blocks.";
            // ------------------------------------------------ places and tools
            case DEV_REVEAL_PLACES -> "The band knows " + Pois.revealAll(player, 400) + " more places.";
            case DEV_FORGET_PLACES -> {
                Pois.forgetAll(player);
                yield "The band knows nowhere now.";
            }
            case DEV_LOSE_PLACES -> {
                Pois.bandLost(player);
                yield "As if the band had died: its places gone, its allies merely familiar.";
            }
            case DEV_NEXT_PLACE -> nextPlace(player);
            case DEV_PLACE_SPRING -> made(player, Pois.makeHere(player, Pois.Kind.SPRING));
            case DEV_PLACE_LICK -> made(player, Pois.makeHere(player, Pois.Kind.LICK));
            case DEV_PLACE_CAMP -> made(player, Pois.makeHere(player, Pois.Kind.CAMP));
            case DEV_PLACE_CHERT -> made(player, Pois.makeHere(player, Pois.Kind.CHERT));
            case DEV_PLACE_DEPOSIT -> made(player, Pois.makeHere(player, Pois.Kind.TOOLS));
            case DEV_PLACE_OASIS -> made(player, Pois.makeHere(player, Pois.Kind.OASIS));
            case DEV_PLACE_TIDE -> made(player, Pois.makeHere(player, Pois.Kind.TIDE_POOL));
            case DEV_PLACE_GRAVEL -> made(player, Pois.makeHere(player, Pois.Kind.SUPER_GRAVEL));
            case DEV_DAWN -> {
                level.setDayTime(level.getDayTime() - level.getDayTime() % 24000L + 23200L);
                yield "First light. Animals come to an oasis or a haven within 128 blocks.";
            }
            case DEV_KNACKS -> {
                Skills.set(player, Skills.Skill.CLEAN_EYE, true);
                Skills.set(player, Skills.Skill.NOMAD, true);
                Skills.set(player, Skills.Skill.JACK, true);
                yield "You have a clean eye, a nomad's eye for country, and a hand for everything.";
            }
            case DEV_LACERATE -> {
                Afflictions.afflict(player, Afflictions.Affliction.LACERATED, 24000);
                yield "Lacerated for a day: you cannot heal past a third of your hearts. Drink - or sit in a spring 30s.";
            }
            case DEV_BLEED_EXTERNAL -> {
                dev.hominin.evolution.combat.Bleeding.inflict(player, dev.hominin.evolution.combat.Bleeding.Tier.EXTERNAL);
                yield "External bleeding (tier 1): it bleeds, and then it stops.";
            }
            case DEV_BLEED_INTERNAL -> {
                dev.hominin.evolution.combat.Bleeding.inflict(player, dev.hominin.evolution.combat.Bleeding.Tier.INTERNAL);
                yield "Internal bleeding (tier 2): nothing closes while it runs.";
            }
            case DEV_BLEED_CATASTROPHIC -> {
                dev.hominin.evolution.combat.Bleeding.inflict(player,
                        dev.hominin.evolution.combat.Bleeding.Tier.CATASTROPHIC);
                yield "Catastrophic bleeding (tier 3): drink, and keep drinking - or it kills you.";
            }
            case DEV_BLEED_STOP -> {
                player.removeEffect(dev.hominin.evolution.ModEffects.BLEEDING);
                dev.hominin.evolution.combat.Bleeding.forget(player.getUUID());
                Afflictions.relieve(player, Afflictions.Affliction.LACERATED);
                Afflictions.relieve(player, Afflictions.Affliction.BLED_OUT);
                yield "Every wound closed: no bleeding, no lacerations.";
            }
            case DEV_BRAIN -> {
                give(player, new ItemStack(ModItems.HOMININ_BRAIN.get(), 2));
                yield "Two brains. Drop one near an erectus band and watch - or eat one, and gamble.";
            }
            case DEV_TOOL_PILE -> ToolPiles.devPile(player, toolSet()) ? "A full pile of your band's tools in front of you."
                    : "No room in front of you.";
            case DEV_TROUBLED, DEV_SOUR -> {
                BandMember member = pick(player, entityId);
                if (member == null) {
                    yield "Nobody of yours is near enough.";
                }
                member.ensureName();
                if (member.getTrouble() == Troubles.NONE && Troubles.troubledIn(Band.all(player)) >= Troubles.MOST_AT_ONCE) {
                    yield "Two of the band are troubled already - never more than two at once.";
                }
                member.setTrouble(Troubles.TROUBLED, "Ama", "mate");
                if (command == Social.Command.DEV_SOUR) {
                    member.setTrouble(Troubles.SOUR, "Ama", "mate");
                    member.setTemper(true);
                }
                yield member.getName().getString() + (command == Social.Command.DEV_SOUR ? " has gone sour" : " is grieving")
                        + " (for Ama, their mate). A need will come for them within a minute or so.";
            }
            case DEV_PSYCHOPATH -> {
                BandMember member = pick(player, entityId);
                if (member == null) {
                    yield "Nobody of yours is near enough.";
                }
                member.ensureName();
                // Only ever one to a band: whoever it was before is not any more.
                for (BandMember other : Band.all(player)) {
                    if (other != member && other.isPsychopath()) {
                        other.setPsychopath(false);
                    }
                }
                member.setPsychopath(true);
                yield member.getName().getString() + " is the band's psychopath now - there is only ever one. (Suspect "
                        + "them in tribe stats with P.)";
            }
            case DEV_WHO_PSYCHOPATH -> {
                List<String> names = new ArrayList<>();
                for (BandMember member : Band.all(player)) {
                    if (member.isPsychopath()) {
                        member.ensureName();
                        names.add(member.getName().getString());
                    }
                }
                yield names.isEmpty() ? "Nobody in your band is one." : "Psychopath: " + String.join(", ", names);
            }
            case DEV_PSYCHOPATH_LEAVES -> {
                BandMember psycho = null;
                for (BandMember member : Band.all(player)) {
                    if (member.isPsychopath()) {
                        psycho = member;
                    }
                }
                if (psycho == null) {
                    yield "Nobody in your band is one.";
                }
                Psychopaths.leave(player, psycho);
                yield "Gone.";
            }
            case DEV_BUILD_SUGGEST -> dev.hominin.evolution.build.Building.devSuggest(player);
            case DEV_BUILD_FINISH -> dev.hominin.evolution.build.Building.devFinish(player);
            case DEV_BUILD_MATERIALS -> dev.hominin.evolution.build.Building.devMaterials(player);
            case DEV_BUILD_UNLOCK -> dev.hominin.evolution.build.Building.devUnlock(player);
            case DEV_BUILD_ASK -> dev.hominin.evolution.build.Building.devAskAgain(player);
            case DEV_BUILD_CLEAR -> dev.hominin.evolution.build.Building.devClear(player);
            // ------------------------------------------------ newcomers and evolving
            case DEV_SURVIVORS -> Refugees.devArrive(player, null);
            case DEV_SURVIVORS_PREDATOR -> Refugees.devArrive(player, Refugees.Cause.PREDATOR);
            case DEV_SURVIVORS_RAID -> Refugees.devArrive(player, Refugees.Cause.RAID);
            case DEV_SURVIVORS_FIGHT -> Refugees.devArrive(player, Refugees.Cause.INFIGHTING);
            case DEV_SURVIVORS_PSYCHO -> Refugees.devArrive(player, Refugees.Cause.PSYCHOPATH);
            case DEV_MERGE -> {
                Bands.Record band = nearestKnown(player);
                if (band == null) {
                    yield "You know no band. Spawn one first.";
                }
                yield Refugees.devMerge(player, band);
            }
            case DEV_GRUDGE_DAY -> Refugees.devGrudgeDay(player);
            case DEV_WHO_GRUDGE -> {
                List<String> held = new ArrayList<>();
                for (BandMember member : Band.all(player)) {
                    if (member.getGrudge() != null) {
                        Bands.Record band = Bands.get(level, member.getGrudge());
                        member.ensureName();
                        held.add(member.getName().getString() + " (against " + (band == null ? "a band now gone"
                                : band.name) + ")");
                    }
                }
                yield held.isEmpty() ? "Nobody in your band holds a grudge." : "Grudges: " + String.join(", ", held);
            }
            case DEV_PREGNANT -> {
                BandMember mother = null;
                if (entityId >= 0 && level.getEntity(entityId) instanceof BandMember picked && picked.isLedBy(player)) {
                    mother = picked;
                } else {
                    for (BandMember member : Band.ownNear(player, RANGE)) {
                        if (member.isFemale() && !member.isBaby() && !member.isPregnant()) {
                            mother = member;
                            break;
                        }
                    }
                }
                if (mother == null || !mother.isFemale() || mother.isBaby()) {
                    yield "No grown woman of yours near enough (pick one out, or stand near one).";
                }
                mother.arriveCarrying(0.5F);
                mother.ensureName();
                yield mother.getName().getString() + " is carrying - due in half a day. (Sleep the night through and it "
                        + "comes by morning.)";
            }
            case DEV_BIRTH -> {
                int born = 0;
                for (BandMember member : Band.all(player)) {
                    if (member.isPregnant()) {
                        member.deliverNow();
                        born++;
                    }
                }
                yield born == 0 ? "Nobody in your band is carrying." : born + " births. (Twins one time in seven, "
                        + "triplets one in thirty-three.)";
            }
            case DEV_LULL -> {
                dev.hominin.evolution.hunt.PredatorLull.endLull(player.getUUID());
                yield "The lull is over: the next hunter may come at once.";
            }
            case DEV_EVOLVE -> {
                var stage = dev.hominin.evolution.stage.StageRegistry.current(data);
                if (stage == null || stage.nextStage().isEmpty()) {
                    yield "There is nothing further to evolve into from here.";
                }
                dev.hominin.evolution.EvolutionManager.evolve(player, stage.nextStage().get());
                yield "Evolving into " + stage.nextStage().get().getPath() + ".";
            }
            case DEV_PLAYER_BANDS -> {
                Newcomers.openMenu(player);
                yield "";
            }
            case DEV_STONES -> {
                give(player, new ItemStack(ModItems.FINE_CHERT_ROCK.get(), 8));
                give(player, new ItemStack(ModItems.OBSIDIAN_CHUNK.get(), 2));
                give(player, new ItemStack(ModItems.DEAD_BRANCH.get(), 3));
                give(player, new ItemStack(ModItems.FINE_CHERT_DEPOSIT.get(), 2));
                yield "8 fine chert, 2 obsidian chunks, 3 dead branches and 2 fine chert deposit blocks.";
            }
            case DEV_RIBCAGE -> {
                BlockPos at = player.blockPosition().relative(player.getDirection(), 3);
                level.setBlock(at, dev.hominin.evolution.ModBlocks.GIANT_CARCASS.get().defaultBlockState(), 3);
                yield "A giant carcass, three blocks ahead. (A clan eats it down in four sittings.)";
            }
            case DEV_FINE_SEAM -> {
                BlockPos centre = player.blockPosition().relative(player.getDirection(), 3);
                int placed = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > 1 && player.getRandom().nextBoolean()) {
                            continue;
                        }
                        int x = centre.getX() + dx;
                        int z = centre.getZ() + dz;
                        BlockPos top = new BlockPos(x, level.getHeight(
                                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                        level.setBlock(top, dev.hominin.evolution.ModBlocks.FINE_CHERT_DEPOSIT.get().defaultBlockState(), 3);
                        placed++;
                    }
                }
                level.setBlock(new BlockPos(centre.getX(), level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(),
                        centre.getZ()), centre.getZ()), dev.hominin.evolution.ModBlocks.FINE_CHERT_DEPOSIT.get()
                        .defaultBlockState(), 3);
                yield "A small fine chert seam ahead (" + (placed + 1) + " blocks). Strike it with a hammerstone.";
            }
            default -> "";
        };
        if (!done.isEmpty()) {
            player.sendSystemMessage(Component.literal("[dev] " + done).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** One of everything a pile can hold, in good stone. */
    private static List<ItemStack> toolSet() {
        List<ItemStack> tools = new ArrayList<>();
        tools.add(StoneMaterial.stamp(new ItemStack(ModItems.HAMMERSTONE.get()), StoneMaterial.QUARTZITE));
        tools.add(StoneMaterial.stamp(new ItemStack(ModItems.FLAKE.get()), StoneMaterial.OBSIDIAN));
        tools.add(StoneMaterial.stamp(new ItemStack(ModItems.CHOPPER.get()), StoneMaterial.BASALT));
        tools.add(StoneMaterial.stamp(new ItemStack(ModItems.OLDOWAN_MULTITOOL.get()), StoneMaterial.CHERT));
        if (ModItems.HAND_AXE.get() instanceof AcheuleanToolItem axe) {
            tools.add(StoneMaterial.stamp(axe.make(2), StoneMaterial.CHERT));
            tools.add(StoneMaterial.stamp(axe.make(1), StoneMaterial.OBSIDIAN));
        }
        if (ModItems.CLEAVER.get() instanceof AcheuleanToolItem cleaver) {
            tools.add(StoneMaterial.stamp(cleaver.make(2), StoneMaterial.BASALT));
        }
        tools.add(StoneMaterial.stamp(new ItemStack(ModItems.FLAKE.get()), StoneMaterial.CHERT));
        return tools;
    }

    private static String made(ServerPlayer player, Pois.Poi poi) {
        Pois.learn(player, poi);
        return poi.label() + " at " + poi.pos().getX() + ", " + poi.pos().getZ() + " - the band knows it.";
    }

    private static String nextPlace(ServerPlayer player) {
        Pois.Poi best = null;
        double bestDistance = Double.MAX_VALUE;
        boolean erectus = Bands.erectusOn(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        for (Pois.Poi poi : Pois.near(player.serverLevel(), player.blockPosition(), 600, erectus)) {
            double distance = Bands.horizontal(poi.pos(), player.blockPosition());
            if (!Pois.knows(player, poi.id()) && distance < bestDistance) {
                bestDistance = distance;
                best = poi;
            }
        }
        if (best == null) {
            return "No place you do not know within 600 blocks.";
        }
        dev.hominin.evolution.mind.MentalMap.lead(player, best.pos(), best.label(), "");
        return "Leading you to " + best.label() + ", " + (int) Math.sqrt(bestDistance) + " blocks.";
    }

    /** The one you picked out, or whoever of yours is nearest. */
    @Nullable
    private static BandMember pick(ServerPlayer player, int entityId) {
        if (entityId >= 0 && player.level().getEntity(entityId) instanceof BandMember member && member.isLedBy(player)) {
            return member;
        }
        BandMember best = null;
        for (BandMember member : Band.ownNear(player, RANGE)) {
            if (!member.isBaby() && (best == null || member.distanceToSqr(player) < best.distanceToSqr(player))) {
                best = member;
            }
        }
        return best;
    }

    @Nullable
    private static Bands.Record nearestKnown(ServerPlayer player) {
        Bands.Record best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Bands.Record band : Bands.all(player.serverLevel())) {
            if (band.nomadic() || !band.knownTo(player.getUUID())) {
                continue;
            }
            double distance = Bands.horizontal(Relations.whereIs(player.serverLevel(), band), player.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = band;
            }
        }
        return best;
    }

    /** Bond with whoever you picked out, or everybody in earshot. */
    private static String bond(ServerPlayer player, int entityId, int change) {
        List<BandMember> members = new ArrayList<>();
        if (entityId >= 0 && player.level().getEntity(entityId) instanceof BandMember member) {
            members.add(member);
        } else {
            members.addAll(Band.ownNear(player, RANGE));
        }
        if (members.isEmpty()) {
            return "Nobody of yours is near enough.";
        }
        for (BandMember member : members) {
            member.addBond(change);
        }
        BandMember first = members.get(0);
        first.ensureName();
        return members.size() == 1 ? first.getName().getString() + "'s bond is now " + first.getBond() + "."
                : "Bond " + (change > 0 ? "+" : "") + change + " for " + members.size() + " members.";
    }

    /** Whatever troop is nearest: set how it feels about you. */
    private static String troop(ServerPlayer player, int trust) {
        Baboon nearest = null;
        for (Baboon baboon : player.level().getEntitiesOfClass(Baboon.class,
                player.getBoundingBox().inflate(48.0D), b -> b.getTroop() != null)) {
            if (nearest == null || baboon.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = baboon;
            }
        }
        if (nearest == null) {
            return "No baboon troop within 48 blocks.";
        }
        TroopRelations.setTrustFor(player, nearest.getTroop(), trust);
        return trust < 0 ? "The nearest troop now holds a grudge." : "The nearest troop now trusts you.";
    }

    private Developer() {
    }
}
