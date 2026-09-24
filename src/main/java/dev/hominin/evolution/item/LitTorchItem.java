package dev.hominin.evolution.item;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.entity.ThrownTorch;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A burning torch. It burns down whether or not you use it - ten minutes or so, faster in the rain - and
 * water puts it out. Held, it lights the ground round you. Used on the ground or a wall it is set down there
 * and lights the camp, burning twice as long again; used at anything else it is thrown - the one thing every
 * predator out here is afraid of: whatever it hits catches, and runs.
 */
public class LitTorchItem extends Item {
    /** Burns one point every two seconds. */
    public static final int BURN_POINTS = 300;
    private static final int BURN_EVERY = 40;

    public LitTorchItem(Properties properties) {
        super(properties);
    }

    /** Set down on the ground, or wedged into a wall: it burns there for twice what it had left. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.DOWN) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos at = context.getClickedPos().relative(face);
        if (!level.getBlockState(at).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        BlockState state = face == Direction.UP ? dev.hominin.evolution.ModBlocks.PLACED_TORCH.get().defaultBlockState()
                : dev.hominin.evolution.ModBlocks.WALL_PLACED_TORCH.get().defaultBlockState().setValue(WallTorchBlock.FACING, face);
        if (!state.canSurvive(level, at)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack stack = context.getItemInHand();
            level.setBlock(at, state, 11);
            int left = stack.getMaxDamage() - stack.getDamageValue();
            level.scheduleTick(at, state.getBlock(), Math.max(400, left * BURN_EVERY * 2));
            level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
                0.6F, 0.5F);
        if (!level.isClientSide()) {
            ThrownTorch torch = new ThrownTorch(level, player);
            torch.setItem(stack.copyWithCount(1));
            torch.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.1F, 1.5F);
            level.addFreshEntity(torch);
        }
        player.getCooldowns().addCooldown(this, 15);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel) || !(entity instanceof Player player) || entity.tickCount % BURN_EVERY != 0
                || player.getAbilities().instabuild) {
            return;
        }
        if (entity.isInWater()) {
            stack.shrink(1);
            ItemStack doused = new ItemStack(ModItems.TORCH.get());
            if (!player.getInventory().add(doused)) {
                player.drop(doused, false);
            }
            level.playSound(null, entity.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.2F);
            player.displayClientMessage(Component.literal("The water puts your torch out."), true);
            return;
        }
        int burn = entity.isInWaterOrRain() ? 3 : 1;
        if (stack.getDamageValue() + burn >= stack.getMaxDamage()) {
            stack.shrink(1);
            level.playSound(null, entity.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.0F);
            player.displayClientMessage(Component.literal("Your torch burns out."), true);
            return;
        }
        stack.setDamageValue(stack.getDamageValue() + burn);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // Burning down is not a new item in the hand.
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }
}
