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

        BuiltinMilestones.bootstrap();
        ModGameRules.bootstrap();

        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new StageDefinitionReloadListener()));
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onFinishUsingItem);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                dev.hominin.evolution.survival.Hearths.tickLevel(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.hunt.Hides::onDrops);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(BlockBreakHandler::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(BlockBreakHandler::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(BlockBreakHandler::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(HomelandSpawn::onCreateSpawnPosition);
        NeoForge.EVENT_BUS.addListener(GuideBook::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(Band::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(Band::onPlayerHurt);
        // Highest, so nothing lands on a player who cannot see or move during a cutscene.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,
                dev.hominin.evolution.stage.CutsceneGuard::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.combat.Scare::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(Band::onMemberHurt);
        NeoForge.EVENT_BUS.addListener(dev.hominin.evolution.hunt.Quarry::onHurt);
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
            event.put(ModEntities.SABERTOOTH.get(), dev.hominin.evolution.entity.Sabertooth.createAttributes().build());
            event.put(ModEntities.HOMOTHERIUM.get(), dev.hominin.evolution.entity.Homotherium.createAttributes().build());
            event.put(ModEntities.CROWNED_EAGLE.get(), dev.hominin.evolution.entity.CrownedEagle.createAttributes().build());
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
