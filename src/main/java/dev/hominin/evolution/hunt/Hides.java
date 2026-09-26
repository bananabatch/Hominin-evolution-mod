package dev.hominin.evolution.hunt;

import dev.hominin.evolution.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Anything with a skin worth scraping gives it up: grazers and big game always, the hunters most of the time, the
 * smaller things now and then. (The mod's own megafauna and the giant hyena drop theirs from their loot tables.)
 */
public final class Hides {
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead.isBaby()) {
            return;
        }
        float chance = chanceFor(dead);
        if (chance <= 0.0F || dead.getRandom().nextFloat() >= chance) {
            return;
        }
        event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(),
                new ItemStack(ModItems.HIDE.get())));
    }

    /** How likely this animal is to give up a hide: 1 for the grazers and big game. */
    public static float chanceFor(LivingEntity dead) {
        if (dead instanceof Cow || dead instanceof AbstractHorse || dead instanceof net.minecraft.world.entity.animal.Sheep
                || dead instanceof net.minecraft.world.entity.animal.goat.Goat
                || dead instanceof net.minecraft.world.entity.animal.Pig
                || dead instanceof net.minecraft.world.entity.animal.PolarBear
                || dead instanceof net.minecraft.world.entity.animal.Panda
                || dead instanceof net.minecraft.world.entity.animal.sniffer.Sniffer
                || dead instanceof net.minecraft.world.entity.monster.hoglin.Hoglin
                || dead instanceof dev.hominin.evolution.entity.Sabertooth
                || dead instanceof dev.hominin.evolution.entity.Homotherium
                || dead instanceof dev.hominin.evolution.entity.Crocuta) {
            return 1.0F;
        }
        if (dead instanceof dev.hominin.evolution.entity.Crocodile || dead instanceof dev.hominin.evolution.entity.Dinopithecus
                || dead instanceof net.minecraft.world.entity.animal.Wolf) {
            return 0.6F;
        }
        if (dead instanceof net.minecraft.world.entity.animal.Fox || dead instanceof dev.hominin.evolution.entity.Baboon
                || dead instanceof net.minecraft.world.entity.animal.Rabbit) {
            return 0.25F;
        }
        return 0.0F;
    }

    private Hides() {
    }
}
