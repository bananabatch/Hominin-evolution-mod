package dev.hominin.evolution.item;

import dev.hominin.evolution.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A spear whittled from a workable branch rather than gnawed to a point. It hits harder than a
 * sharpened spear and lasts longer - and when it finally breaks, the branch underneath it does
 * not go with it: you get a workable branch back, ready to point again.
 */
public class WorkableSpearItem extends Item {
    public WorkableSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            stack.hurtAndBreak(1, level, attacker instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
                    broken -> giveBranchBack(attacker));
        } else {
            stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    private void giveBranchBack(LivingEntity attacker) {
        ItemStack branch = new ItemStack(ModItems.WORKABLE_BRANCH.get());
        if (attacker instanceof Player player) {
            if (!player.getInventory().add(branch)) {
                player.drop(branch, false);
            }
        } else {
            attacker.spawnAtLocation(branch);
        }
    }
}
