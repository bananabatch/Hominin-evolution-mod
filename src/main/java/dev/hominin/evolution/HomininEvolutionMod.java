package dev.hominin.evolution;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.hominin.evolution.advancement.HomininAdvancements;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import dev.hominin.evolution.climb.ClimbingServer;
import dev.hominin.evolution.command.HomininCommand;
import dev.hominin.evolution.guide.GuideBook;
import dev.hominin.evolution.world.HomelandSpawn;
import dev.hominin.evolution.world.feature.ModFeatures;
import dev.hominin.evolution.event.BlockBreakHandler;
import dev.hominin.evolution.event.EvolutionEventHandler;
import dev.hominin.evolution.network.ModNetworking;
import dev.hominin.evolution.recipe.ModRecipeSerializers;
import dev.hominin.evolution.stage.BuiltinMilestones;
import dev.hominin.evolution.stage.StageDefinitionReloadListener;
import dev.hominin.evolution.stage.StageSync;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@Mod(HomininEvolutionMod.MODID)
public class HomininEvolutionMod {
    public static final String MODID = "hominin_evolution";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HomininEvolutionMod(IEventBus modEventBus, ModContainer modContainer) {
        Attachments.ATTACHMENT_TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);
        ModFeatures.FEATURES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener(ModNetworking::register);
        dev.hominin.evolution.hunt.PredatorLull.register();

        BuiltinMilestones.bootstrap();
        ModGameRules.bootstrap();

        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new StageDefinitionReloadListener()));
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(
                new dev.hominin.evolution.build.Blueprints()));
        // Early, so a block refused by a blueprint never reaches presence, termites and the rest.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, dev.hominin.evolution.build.Building::onPlace);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.build.Building::onBreak);
        // A pile is not broken by hitting it: that opens its menu, on the client.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) -> {
            if (!event.getLevel().isClientSide() && event.getLevel().getBlockState(event.getPos()).is(ModBlocks.TOOL_PILE.get())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.build.Building::onWakeUp);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.build.Building::onDatapackSync);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                dev.hominin.evolution.build.Building.syncSites(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,
                dev.hominin.evolution.item.FireHardening::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,
                dev.hominin.evolution.survival.Roots::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onFinishUsingItem);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                dev.hominin.evolution.survival.Hearths.tickLevel(level);
                dev.hominin.evolution.survival.Soils.tidy(level);
                dev.hominin.evolution.survival.Termites.tick(level);
                dev.hominin.evolution.survival.TreeFelling.tick(level);
                dev.hominin.evolution.survival.LeafRegrowth.tickLevel(level);
                dev.hominin.evolution.food.Spoilage.tickLevel(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.tool.ToolUse::onDamageDealt);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.survival.TreeFelling::onHurt);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.hunt.Hides::onDrops);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) -> {
            var dead = event.getEntity();
            if ((dead instanceof dev.hominin.evolution.entity.Megafauna || dev.hominin.evolution.hunt.Predation.giant(dead))
                    && dead.getRandom().nextFloat() < 0.35F) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(dead.level(), dead.getX(), dead.getY(),
                        dead.getZ(), new net.minecraft.world.item.ItemStack(ModItems.BONE_CLUB.get())));
            }
        });
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.survival.Hearths::onDrops);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                dev.hominin.evolution.band.Presence.built(player, event.getPos(), event.getPlacedBlock());
                // A nest or a bed you lay down is yours.
                if (event.getPlacedBlock().is(ModBlocks.NEST.get()) || event.getPlacedBlock().is(ModBlocks.THATCH_BEDDING.get())) {
                    dev.hominin.evolution.block.NestOwners.set(player.serverLevel(), event.getPos(), player.getUUID());
                }
            }
        });
        // After everything else has added its drops, so the season scales the lot.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW,
                dev.hominin.evolution.survival.Seasons::onDrops);
        // And a run-down animal gives more of whatever the season left.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                dev.hominin.evolution.hunt.Quarry::onDrops);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(BlockBreakHandler::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(BlockBreakHandler::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(BlockBreakHandler::onBlockPlace);
        // Building on a super colony's ground wears the colony down.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) -> {
            if (!event.isCanceled() && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                dev.hominin.evolution.survival.Termites.builtNear(level, event.getPos(),
                        event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player ? player : null);
            }
        });
        NeoForge.EVENT_BUS.addListener(HomelandSpawn::onCreateSpawnPosition);
        NeoForge.EVENT_BUS.addListener(GuideBook::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(Band::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(Band::onPlayerHurt);
        // Highest, so nothing lands on a player who cannot see or move during a cutscene.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,
                dev.hominin.evolution.stage.CutsceneGuard::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.combat.Scare::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.hunt.PredatorMood::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(Band::onMemberHurt);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.hunt.Quarry::onHurt);
        // Anything hurt by anything runs - properly, and far.
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.entity.WoundedFleeGoal::onHurt);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.hunt.Persistence::onHurt);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.item.StoneMaterial::onHurt);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.band.Relations::onHurt);
        // Coming back: the pointer, and a band still waiting for its name.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                dev.hominin.evolution.mind.MentalMap.sync(player);
                dev.hominin.evolution.band.Relations.promptName(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.hunt.Carcasses::onHurt);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.survival.Afflictions::onHeal);
        // High, so a wrestle is cancelled before anything treats it as a real blow.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, Band::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(Band::onPlayerDeath);
        NeoForge.EVENT_BUS.addListener(Band::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW, Band::onMemberThreatened);
        modEventBus.addListener((EntityAttributeCreationEvent event) ->
        {
            event.put(ModEntities.BAND_MEMBER.get(), BandMember.createAttributes().build());
            event.put(ModEntities.BABOON.get(), dev.hominin.evolution.entity.Baboon.createAttributes().build());
            event.put(ModEntities.DINOPITHECUS.get(), dev.hominin.evolution.entity.Dinopithecus.attributes().build());
            event.put(ModEntities.CHIMPANZEE.get(), dev.hominin.evolution.entity.Chimpanzee.createAttributes().build());
            event.put(ModEntities.BONOBO.get(), dev.hominin.evolution.entity.Bonobo.createAttributes().build());
            event.put(ModEntities.CROCODILE.get(), dev.hominin.evolution.entity.Crocodile.createAttributes().build());
            event.put(ModEntities.PACHYCROCUTA.get(), dev.hominin.evolution.entity.Pachycrocuta.createAttributes().build());
            event.put(ModEntities.CROCUTA.get(), dev.hominin.evolution.entity.Crocuta.createAttributes().build());
            event.put(ModEntities.SABERTOOTH.get(), dev.hominin.evolution.entity.Sabertooth.createAttributes().build());
            event.put(ModEntities.HOMOTHERIUM.get(), dev.hominin.evolution.entity.Homotherium.createAttributes().build());
            event.put(ModEntities.CROWNED_EAGLE.get(), dev.hominin.evolution.entity.CrownedEagle.createAttributes().build());
            event.put(ModEntities.PELOROVIS.get(), dev.hominin.evolution.entity.Pelorovis.createAttributes().build());
            event.put(ModEntities.MAMMUTHUS.get(), dev.hominin.evolution.entity.Mammuthus.createAttributes().build());
            event.put(ModEntities.MEGALOTRAGUS.get(), dev.hominin.evolution.entity.Megalotragus.createAttributes().build());
            event.put(ModEntities.RUSINGORYX.get(), dev.hominin.evolution.entity.Rusingoryx.createAttributes().build());
        });
        NeoForge.EVENT_BUS.addListener(HomininAdvancements::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(StageSync::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.stage.ChecklistTracker::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(StageSync::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(StageSync::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(StageSync::onStartTracking);
        NeoForge.EVENT_BUS.addListener(ClimbingServer::onStartTracking);
        NeoForge.EVENT_BUS.addListener(ClimbingServer::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(ClimbingServer::onDeath);
        NeoForge.EVENT_BUS.addListener(HomininCommand::onRegisterCommands);
    }
}
