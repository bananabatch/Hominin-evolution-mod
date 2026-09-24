package dev.hominin.evolution.item;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A torch: a stick with thatch bound round the head. It does nothing until it is lit - and it is lit the
 * way everything was, from a fire somebody else kept: a burning fire pit or campfire, open flame, lava, or
 * another torch already burning in your other hand.
 */
public class TorchItem extends Item {
    public TorchItem(Properties properties) {
        super(properties);
    }

    /** Whether there is fire here to take a flame from. */
    public static boolean isFireSource(BlockState state) {
        if (state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.getFluidState().is(FluidTags.LAVA)) {
            return true;
        }
        if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)) {
            return true;
        }
        return state.is(ModBlocks.FIRE_PIT.get()) && state.getValue(dev.hominin.evolution.block.FirePitBlock.LIT);
    }

    private static boolean fireAt(Level level, BlockPos pos) {
        if (isFireSource(level.getBlockState(pos))) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (isFireSource(level.getBlockState(pos.relative(direction)))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !fireAt(context.getLevel(), context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            lightOne(server, context.getHand(), context.getItemInHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    /** Held beside a torch already burning: light this one off it. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!player.getItemInHand(other).is(ModItems.LIT_TORCH.get())) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.literal("Light it at a fire - a fire pit, a campfire, flame or lava."),
                        true);
            }
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer server) {
            lightOne(server, hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** One torch out of the stack in this hand, burning. */
    public static void lightOne(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        ItemStack lit = new ItemStack(ModItems.LIT_TORCH.get());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (stack.isEmpty()) {
            player.setItemInHand(hand, lit);
        } else if (!player.getInventory().add(lit)) {
            player.drop(lit, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(Component.literal("The thatch catches. Throw it at anything that hunts - fire "
                + "sends it running."), true);
    }
}
