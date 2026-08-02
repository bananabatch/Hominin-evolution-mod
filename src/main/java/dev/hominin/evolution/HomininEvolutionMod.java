package dev.hominin.evolution;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.hominin.evolution.command.HomininCommand;
import dev.hominin.evolution.event.BlockBreakHandler;
import dev.hominin.evolution.event.EvolutionEventHandler;
import dev.hominin.evolution.recipe.ModRecipeSerializers;
import dev.hominin.evolution.stage.BuiltinMilestones;
import dev.hominin.evolution.stage.StageDefinitionReloadListener;
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
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);

        BuiltinMilestones.bootstrap();

        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new StageDefinitionReloadListener()));
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onFinishUsingItem);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(EvolutionEventHandler::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(BlockBreakHandler::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(BlockBreakHandler::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(HomininCommand::onRegisterCommands);
    }
}
