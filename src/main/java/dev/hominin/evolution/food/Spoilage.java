package dev.hominin.evolution.food;

import java.util.List;

import dev.hominin.evolution.ModDataComponents;
import dev.hominin.evolution.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

/**
 * Raw meat left lying on the ground turns. Hung on a rack, carried, cooked - it keeps; dropped in the dirt in the
 * sun, a few minutes is all it takes. It still looks like food, and it is still food, after a fashion: eat it and
 * you will be ill (see {@link dev.hominin.evolution.survival.FoodIllness}). Cooking spoiled meat does not make it
 * fresh, only less likely to lay you out.
 */
public final class Spoilage {
    /** On the ground this long, and it has turned. */
    private static final int SPOIL_TICKS = 3 * 60 * 20;
    /** Sooner in the heat. */
    private static final int HOT_SPOIL_TICKS = 2 * 60 * 20;
    private static final int CHECK_TICKS = 100;

    /** Meat, raw - the kind that goes bad. */
    public static boolean isRawMeat(ItemStack stack) {
        return stack.is(ModItems.MEAT_CHUNK.get()) || stack.is(ModItems.RIB.get())
                || stack.is(ModItems.HOMININ_MEAT.get()) || stack.is(ModItems.HOMININ_BRAIN.get())
                || stack.is(ModItems.BONE_MARROW.get()) || stack.is(Tags.Items.FOODS_RAW_MEAT)
                || stack.is(Tags.Items.FOODS_RAW_FISH) || stack.is(Items.BEEF) || stack.is(Items.PORKCHOP)
                || stack.is(Items.MUTTON) || stack.is(Items.CHICKEN) || stack.is(Items.RABBIT);
    }

    public static boolean isSpoiled(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.SPOILED.get()));
    }

    /** Marks it turned: it says so in its name, and no longer stacks with fresh. */
    public static void spoil(ItemStack stack) {
        if (stack.isEmpty() || isSpoiled(stack)) {
            return;
        }
        Component name = stack.getItem().getDescription();
        stack.set(ModDataComponents.SPOILED.get(), true);
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.hominin_evolution.spoiled", name));
    }

    /** Whatever the raw piece was, the cooked one is too. */
    public static void carry(ItemStack from, ItemStack to) {
        if (isSpoiled(from)) {
            spoil(to);
        }
    }

    /** Every five seconds, per level: meat that has lain on the ground too long turns. */
    public static void tickLevel(ServerLevel level) {
        if (level.getGameTime() % CHECK_TICKS != 41L) {
            return;
        }
        List<? extends ItemEntity> lying = level.getEntities(EntityType.ITEM,
                item -> item.isAlive() && isRawMeat(item.getItem()) && !isSpoiled(item.getItem()));
        for (ItemEntity item : lying) {
            float heat = level.getBiome(item.blockPosition()).value().getBaseTemperature();
            int limit = heat >= 1.0F ? HOT_SPOIL_TICKS : SPOIL_TICKS;
            if (item.getAge() < limit) {
                continue;
            }
            ItemStack turned = item.getItem().copy();
            spoil(turned);
            item.setItem(turned);
            level.sendParticles(ParticleTypes.MYCELIUM, item.getX(), item.getY() + 0.2D, item.getZ(), 6, 0.15D, 0.1D,
                    0.15D, 0.0D);
        }
    }

    private Spoilage() {
    }
}
