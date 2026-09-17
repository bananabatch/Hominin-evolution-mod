package dev.hominin.evolution.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/** A shell of water. Drunk, not eaten - the thirst it answers is on its own bar. */
public class WaterEggshellItem extends Item {
    public WaterEggshellItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }
}
