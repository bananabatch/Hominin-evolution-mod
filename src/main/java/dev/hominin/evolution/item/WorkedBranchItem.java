package dev.hominin.evolution.item;

import dev.hominin.evolution.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Wood worked at the station - a workable branch, or the shaft trued from one. It handles like
 * the long branch it came from, but the working opened the grain: every blow wears it, and once
 * it has split far enough all that is left in the hand is an ordinary long branch.
 */
public class WorkedBranchItem extends Item {
    public WorkedBranchItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            stack.hurtAndBreak(1, level, attacker instanceof ServerPlayer player ? player : null,
                    broken -> backToABranch(attacker));
        } else {
            stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    /** The worked face splits off; the branch underneath is still a branch. */
    private static void backToABranch(LivingEntity holder) {
        ItemStack branch = new ItemStack(ModItems.LONG_BRANCH.get());
        if (holder.getMainHandItem().isEmpty()) {
            holder.setItemSlot(EquipmentSlot.MAINHAND, branch);
        } else if (holder instanceof Player player) {
            if (!player.getInventory().add(branch)) {
                player.drop(branch, false);
            }
        } else {
            holder.spawnAtLocation(branch);
        }
    }
}
