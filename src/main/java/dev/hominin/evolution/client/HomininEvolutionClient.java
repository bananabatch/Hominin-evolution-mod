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
        // Each bleeding tier draws its own icon.
        modEventBus.addListener(BleedingIcons::register);
        // Food with a 3D model is eaten from the hand bite by bite, not turned edge-on.
        modEventBus.addListener(EatingAnimation::register);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onRightClickEmpty);
        NeoForge.EVENT_BUS.addListener(ToolPlacing::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ToolPlacing::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(BuildPlanner::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(BuildPlanner::onInteract);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                BuildPlanner.onLoggingOut());
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClimbController::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(TradeTierTooltip::onTooltip);
        NeoForge.EVENT_BUS.addListener(FocusCamera::onComputeFov);
        NeoForge.EVENT_BUS.addListener(ClientSync::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ChecklistOverlay::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(LockedSlotOverlay::onRender);
        NeoForge.EVENT_BUS.addListener(TipDisplay::onMouseClicked);
        NeoForge.EVENT_BUS.addListener(ClientMapCache::tick);
        NeoForge.EVENT_BUS.addListener(ClientMapCache::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientMapCache::onLoggingOut);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> NameBandScreen.showWaiting());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                WaypointHud.clear());

        modContainer.registerConfig(ModConfig.Type.CLIENT, HomininModels.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(HomininModels::registerLayerDefinitions);
        // Every stone tool looks like the stone it was made from.
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            net.minecraft.resources.ResourceLocation material = net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath(dev.hominin.evolution.HomininEvolutionMod.MODID, "material");
            for (var item : java.util.List.of(dev.hominin.evolution.ModItems.FLAKE, dev.hominin.evolution.ModItems.CHOPPER,
                    dev.hominin.evolution.ModItems.HAMMERSTONE, dev.hominin.evolution.ModItems.LOMEKWIAN_TOOL,
                    dev.hominin.evolution.ModItems.OLDOWAN_MULTITOOL, dev.hominin.evolution.ModItems.GRINDING_ROCK,
                    dev.hominin.evolution.ModItems.HAND_AXE, dev.hominin.evolution.ModItems.CLEAVER,
                    dev.hominin.evolution.ModItems.LEVALLOIS_FLAKE, dev.hominin.evolution.ModItems.LEVALLOIS_BLADE,
                    dev.hominin.evolution.ModItems.KNIFE, dev.hominin.evolution.ModItems.LEVALLOIS_HAND_AXE,
                    dev.hominin.evolution.ModItems.STONE_TIPPED_SPEAR)) {
                net.minecraft.client.renderer.item.ItemProperties.register(item.get(), material,
                        (stack, level, entity, seed) -> {
                            Integer stone = stack.get(dev.hominin.evolution.ModDataComponents.MATERIAL.get());
                            return stone == null ? 0.0F : (stone + 1) / 10.0F;
                        });
            }
            // A spear drawn back to throw is held point forward, over the shoulder.
            net.minecraft.resources.ResourceLocation throwing = net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath(dev.hominin.evolution.HomininEvolutionMod.MODID, "throwing");
            for (var item : java.util.List.of(dev.hominin.evolution.ModItems.SHARPENED_SPEAR,
                    dev.hominin.evolution.ModItems.FIRE_HARDENED_SPEAR, dev.hominin.evolution.ModItems.SCHONINGEN_SPEAR,
                    dev.hominin.evolution.ModItems.STONE_TIPPED_SPEAR)) {
                net.minecraft.client.renderer.item.ItemProperties.register(item.get(), throwing,
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem()
                                && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
        }));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) ->
                dev.hominin.evolution.item.StoneMaterial.describe(event.getItemStack(), event.getToolTip()));
        // The question a work station shows for something not thought of yet.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) -> {
            if (event.getItemStack().is(dev.hominin.evolution.ModItems.UNWORKED_IDEA.get())) {
                event.getToolTip().add(net.minecraft.network.chat.Component.literal(
                        "Something could be made of this - but it has not come to you yet.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                event.getToolTip().add(net.minecraft.network.chat.Component.literal(
                        "Hold K with the blade or the shaft in hand to think it through.")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        });
        modEventBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> {
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("baboon"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::baboon);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("chimpanzee"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::chimpanzee);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("bonobo"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::bonobo);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("crocodile"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::crocodile);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("dinopithecus"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::dinopithecus);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("pachycrocuta"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::pachycrocuta);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("sabertooth"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::sabertooth);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("homotherium"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::homotherium);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("crowned_eagle"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::crownedEagle);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("pelorovis"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::pelorovis);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("mammuthus"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::mammuthus);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("megalotragus"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::megalotragus);
            event.registerLayerDefinition(dev.hominin.evolution.client.model.WildAnimalRenderer.layer("rusingoryx"),
                    dev.hominin.evolution.client.model.WildAnimalLayers::rusingoryx);
        });
        modEventBus.addListener(HomininModels::addLayers);
        // Lowest, so nothing cancels the render after the pose has been pushed.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HomininModels::onRenderPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HomininModels::onRenderPost);
        NeoForge.EVENT_BUS.addListener(HomininModels::registerCommands);
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
        {
            event.registerEntityRenderer(ModEntities.THROWN_OBJECT.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(ModEntities.THROWN_TORCH.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(ModEntities.THROWN_SPEAR.get(), ThrownSpearRenderer::new);
            event.registerBlockEntityRenderer(dev.hominin.evolution.ModBlockEntities.FIRE_PIT.get(), FirePitRenderer::new);
            event.registerBlockEntityRenderer(dev.hominin.evolution.ModBlockEntities.TOOL_PILE.get(), ToolPileRenderer::new);
            event.registerBlockEntityRenderer(dev.hominin.evolution.ModBlockEntities.TOOL_RACK.get(), ToolRackRenderer::new);
            event.registerBlockEntityRenderer(dev.hominin.evolution.ModBlockEntities.COOKING_SPIT.get(),
                    CookingSpitRenderer::new);
            event.registerEntityRenderer(ModEntities.BAND_MEMBER.get(), BandMemberRenderer::new);
            event.registerEntityRenderer(ModEntities.BABOON.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("baboon"),
                            "baboon", 1.0F, 0.4F));
            event.registerEntityRenderer(ModEntities.CHIMPANZEE.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("chimpanzee"),
                            "chimpanzee", 1.0F, 0.5F));
            event.registerEntityRenderer(ModEntities.BONOBO.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("bonobo"),
                            "bonobo", 1.0F, 0.45F));
            event.registerEntityRenderer(ModEntities.CROCODILE.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("crocodile"),
                            "crocodile", 1.0F, 0.7F));
            event.registerEntityRenderer(ModEntities.DINOPITHECUS.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("dinopithecus"),
                            "dinopithecus", 1.45F, 0.7F));
            event.registerEntityRenderer(ModEntities.PACHYCROCUTA.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("pachycrocuta"),
                            "pachycrocuta", 1.55F, 1.0F));
            // A hyena is a hyena: the same build, smaller, spotted and sandy.
            event.registerEntityRenderer(ModEntities.CROCUTA.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("pachycrocuta"),
                            "crocuta", 0.85F, 0.5F));
            event.registerEntityRenderer(ModEntities.SABERTOOTH.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("sabertooth"),
                            "sabertooth", 1.2F, 0.8F));
            event.registerEntityRenderer(ModEntities.HOMOTHERIUM.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("homotherium"),
                            "homotherium", 1.15F, 0.8F));
            event.registerEntityRenderer(ModEntities.PELOROVIS.get(), ctx -> new dev.hominin.evolution.client.model
                    .WildAnimalRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("pelorovis"),
                            "pelorovis", 1.4F, 1.1F));
            event.registerEntityRenderer(ModEntities.MAMMUTHUS.get(), ctx -> new dev.hominin.evolution.client.model
                    .MegafaunaRenderer<>(ctx, "mammuthus", 2.3F, 2.4F));
            event.registerEntityRenderer(ModEntities.MEGALOTRAGUS.get(), ctx -> new dev.hominin.evolution.client.model
                    .MegafaunaRenderer<>(ctx, "megalotragus", 1.4F, 1.2F));
            event.registerEntityRenderer(ModEntities.RUSINGORYX.get(), ctx -> new dev.hominin.evolution.client.model
                    .MegafaunaRenderer<>(ctx, "rusingoryx", 1.3F, 0.9F));
            event.registerEntityRenderer(ModEntities.CROWNED_EAGLE.get(), ctx -> new dev.hominin.evolution.client.model
                    .BirdRenderer<>(ctx, dev.hominin.evolution.client.model.WildAnimalRenderer.layer("crowned_eagle"),
                            "crowned_eagle", 1.0F, 0.4F));
        });
        modEventBus.addListener(ModKeyMappings::register);
        modEventBus.addListener(ChecklistOverlay::register);
        modEventBus.addListener(ThirstOverlay::register);
        modEventBus.addListener(WaypointHud::register);
        modEventBus.addListener(AlertHud::register);
        modEventBus.addListener(TrackHud::register);
        modEventBus.addListener(ThreatHud::register);
        modEventBus.addListener(ArmsRaceFlash::register);
        modEventBus.addListener(SkullPoseFlash::register);
        modEventBus.addListener(EvolutionCutscene::register);
        modEventBus.addListener(RebirthCutscene::register);
        modEventBus.addListener(TiredApesFlash::register);
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) -> {
            event.register(dev.hominin.evolution.ModMenus.WORK_STATION.get(),
                    dev.hominin.evolution.client.WorkStationScreen::new);
            event.register(dev.hominin.evolution.ModMenus.KNAPPING_STATION.get(),
                    dev.hominin.evolution.client.KnappingStationScreen::new);
        });
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
