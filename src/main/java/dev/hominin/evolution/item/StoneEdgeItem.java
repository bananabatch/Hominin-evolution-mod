package dev.hominin.evolution.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A struck stone edge. Nothing is sharper when it comes off the core, and nothing
 * dulls faster: every cut takes a little off it, until it goes back on the grinding
 * stone or gets thrown away.
 */
public class StoneEdgeItem extends Item {
    public StoneEdgeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }
}
