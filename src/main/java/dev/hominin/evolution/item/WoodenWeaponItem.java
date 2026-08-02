package dev.hominin.evolution.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A wooden hand weapon that blunts with use.
 *
 * <p>Plain {@link Item#hurtEnemy} does nothing and returns false, so an item with a
 * max-damage component would otherwise never actually wear down from hitting things -
 * only from mining, which these are not used for.
 */
public class WoodenWeaponItem extends Item {
    public WoodenWeaponItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }
}
