package dev.hominin.evolution.stage;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class BuiltinMilestones {
    public static final ResourceLocation STRIKE_FLAKE = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "strike_flake");

    private BuiltinMilestones() {
    }

    public static void bootstrap() {
        MilestoneHandlers.register(STRIKE_FLAKE, (player, stage) -> {
            player.addItem(new ItemStack(ModItems.FLAKE.get()));
            player.sendSystemMessage(Component.literal("You strike the rock against another - a sharp flake breaks free."));
        });
    }
}
