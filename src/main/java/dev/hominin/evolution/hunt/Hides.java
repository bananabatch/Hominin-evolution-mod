package dev.hominin.evolution.hunt;

import dev.hominin.evolution.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Big grazers give up their skins: a hide from a cow, a horse, or anything horse-like. */
public final class Hides {
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead.isBaby() || !(dead instanceof Cow || dead instanceof AbstractHorse)) {
            return;
        }
        event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(),
                new ItemStack(ModItems.HIDE.get())));
    }

    private Hides() {
    }
}
