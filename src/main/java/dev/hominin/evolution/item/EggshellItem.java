package dev.hominin.evolution.item;

import dev.hominin.evolution.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A drained eggshell. It holds about a mouthful of water - the oldest water container
 * there is, and ostrich shells were still being used for it a hundred thousand years
 * ago. Click a water source to fill it.
 */
public class EggshellItem extends Item {
    public EggshellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(held);
        }
        BlockPos pos = hit.getBlockPos();
        if (!level.mayInteract(player, pos) || !level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
            return InteractionResultHolder.pass(held);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8F, 1.4F);
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.literal("You dip the shell and it fills."), true);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        ItemStack filled = new ItemStack(ModItems.WATER_EGGSHELL.get());
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            if (!player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }
}
