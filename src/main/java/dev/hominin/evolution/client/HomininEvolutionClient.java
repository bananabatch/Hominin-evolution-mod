package dev.hominin.evolution.client;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entrypoint, for input the server never hears about on its own. */
@Mod(value = HomininEvolutionMod.MODID, dist = Dist.CLIENT)
public class HomininEvolutionClient {
    public HomininEvolutionClient(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onRightClickEmpty);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClimbController::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ClientSync::onLoggingOut);

        modContainer.registerConfig(ModConfig.Type.CLIENT, HomininModels.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(HomininModels::registerLayerDefinitions);
        modEventBus.addListener(HomininModels::addLayers);
        // Lowest, so nothing cancels the render after the pose has been pushed.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HomininModels::onRenderPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HomininModels::onRenderPost);
        NeoForge.EVENT_BUS.addListener(HomininModels::registerCommands);
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
        {
            event.registerEntityRenderer(ModEntities.THROWN_OBJECT.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(ModEntities.BAND_MEMBER.get(), BandMemberRenderer::new);
        });
        modEventBus.addListener(ModKeyMappings::register);
        modEventBus.addListener(ChecklistOverlay::register);
        modEventBus.addListener(EvolutionCutscene::register);
        modEventBus.addListener(RebirthCutscene::register);
        modEventBus.addListener(TiredApesFlash::register);
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener(new KeyframeAnimations()));
        // Guarded by name so the class - and every Player Animator type it
        // touches - is only ever loaded when the library is actually there.
        if (ModList.get().isLoaded("playeranimator")) {
            HeldAnimationHandler.register();
            NeoForge.EVENT_BUS.addListener(HeldAnimationHandler::onClientTick);
            BodyAnimationHandler.register();
            NeoForge.EVENT_BUS.addListener(BodyAnimationHandler::onClientTick);
        }
    }
}
