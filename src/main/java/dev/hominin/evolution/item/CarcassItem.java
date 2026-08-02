package dev.hominin.evolution.item;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.data.PlayerEvolutionData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CarcassItem extends Item {
    public CarcassItem() {
        super(new Item.Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        boolean hasFlake = player.getInventory().countItem(ModItems.FLAKE.get()) > 0;
        if (!hasFlake) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.literal("You need a flake to process this carcass."));
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide()) {
            stack.shrink(1);
            if (player instanceof ServerPlayer serverPlayer) {
                EvolutionManager.incrementCriterion(serverPlayer, "scavenge_carcasses", 1);
                PlayerEvolutionData data = serverPlayer.getData(Attachments.PLAYER_EVOLUTION_DATA);
                data.addMeatScavenged(1);
            }
            player.sendSystemMessage(Component.literal("You crack the bone and extract the marrow."));
        }
        return InteractionResultHolder.success(stack);
    }
}
