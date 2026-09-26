package dev.hominin.evolution.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A chunk of salt struck off a lick with a hammerstone, to carry. Lick it where you are, when you need it: the same
 * as the lick itself - once a day, and something the body did not know it was missing.
 */
public class SaltChunkItem extends Item {
    public SaltChunkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer server
                && dev.hominin.evolution.world.Pois.tasteSalt(server, player.blockPosition(),
                        "You lick the salt. Something the body did not know it was missing.")) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Use: lick it (once a day, as at a lick)").withStyle(ChatFormatting.GRAY));
    }
}
