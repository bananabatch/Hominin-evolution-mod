package dev.hominin.evolution.stage;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BuiltinMilestones {
    public static final ResourceLocation STRIKE_FLAKE = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "strike_flake");
    public static final ResourceLocation WALK_UPRIGHT = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "walk_upright");
    public static final ResourceLocation FIRE_TRANSFER = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "fire_transfer");
    /** Erectus to heidelbergensis: a tier 2 or better Acheulean tool from anything but obsidian. */
    public static final ResourceLocation FINE_ACHEULEAN = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "fine_acheulean");

    private BuiltinMilestones() {
    }

    public static void bootstrap() {
        MilestoneHandlers.register(STRIKE_FLAKE, (player, stage) -> {
            player.addItem(new ItemStack(ModItems.FLAKE.get()));
            player.sendSystemMessage(Component.literal("The blow lands where you meant it to. A sharp flake breaks free."));
        });
        MilestoneHandlers.register(WALK_UPRIGHT, (player, stage) -> player.sendSystemMessage(Component.literal(
                "You think about the ground a long way below the trees - and decide to walk on it.")));
        MilestoneHandlers.register(FINE_ACHEULEAN, (player, stage) -> player.sendSystemMessage(Component.literal(
                "You look at what you made, and see the next one already, better, inside the stone.")));
        MilestoneHandlers.register(FIRE_TRANSFER, (player, stage) -> {
            player.addItem(new ItemStack(Items.TORCH));
            player.sendSystemMessage(Component.literal("You coax the flame onto a bundle of tinder and carry it away, alight."));
        });
    }
}
