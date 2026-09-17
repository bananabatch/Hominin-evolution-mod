package dev.hominin.evolution.band;

import java.util.Map;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Exchange with another band. There is no shared language and no money: you hold
 * something out, they weigh it against what they carry, and if they have something
 * worth about as much - but no more - they hand it over.
 *
 * <p>The values are what the thing cost to get. A termite stick is patience; a
 * Lomekwian core is a morning at a stone outcrop; a multi tool is skill that band may
 * not have. It is the first time anything in the mod has a price.
 */
public final class Trading {
    private static final Map<String, Integer> VALUES = Map.ofEntries(
            Map.entry("rock", 1),
            Map.entry("grub", 1), Map.entry("beetle", 1), Map.entry("earthworm", 1),
            Map.entry("meat_chunk", 2),
            Map.entry("long_branch", 3), Map.entry("sharpened_stick", 3), Map.entry("bone_marrow", 3),
            Map.entry("long_bone", 3),
            Map.entry("termite_stick", 4), Map.entry("hammerstone", 4), Map.entry("digging_stick", 4),
            Map.entry("flake", 5), Map.entry("pointy_stick", 5), Map.entry("grinding_rock", 5),
            Map.entry("lomekwian_tool", 6), Map.entry("wooden_club", 6), Map.entry("chert_hammerstone", 6),
            Map.entry("chopper", 7), Map.entry("sharpened_spear", 7),
            Map.entry("oldowan_multitool", 9), Map.entry("fire_hardened_spear", 10));

    /** What one of this is worth to a band. Zero means they have no use for it. */
    public static int valueOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key.getNamespace().equals(dev.hominin.evolution.HomininEvolutionMod.MODID)) {
            Integer value = VALUES.get(key.getPath());
            if (value != null) {
                return value;
            }
        }
        if (stack.is(ModTags.Items.KNAPPABLE_STONE)) {
            return 2;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null ? Math.max(1, food.nutrition() / 2) : 0;
    }

    /**
     * Offers what the player holds. The band member gives back the most valuable thing
     * it carries that is not worth more than the offer, and not the same kind of thing.
     */
    public static void offer(BandMember member, Player player, ItemStack offered) {
        int offerValue = valueOf(offered);
        if (offerValue <= 0) {
            player.displayClientMessage(Component.literal(
                    member.getName().getString() + " turns it over and hands it back. No use to them."), true);
            return;
        }
        SimpleContainer pack = member.getInventory();
        int bestSlot = -2;
        int bestValue = 0;
        ItemStack held = member.getMainHandItem();
        if (isFairReturn(held, offered, offerValue) && valueOf(held) > bestValue) {
            bestSlot = -1;
            bestValue = valueOf(held);
        }
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (isFairReturn(stack, offered, offerValue) && valueOf(stack) > bestValue) {
                bestSlot = slot;
                bestValue = valueOf(stack);
            }
        }
        if (bestSlot == -2) {
            player.displayClientMessage(Component.literal(member.getName().getString()
                    + " looks at it, and at you, and keeps what they have."), true);
            return;
        }
        ItemStack given = bestSlot == -1
                ? member.getMainHandItem().split(1)
                : pack.getItem(bestSlot).split(1);
        if (bestSlot == -1 && member.getMainHandItem().isEmpty()) {
            member.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        ItemStack taken = offered.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            offered.shrink(1);
        }
        member.addToInventory(taken);
        Component givenName = given.getHoverName();
        if (!player.getInventory().add(given)) {
            player.drop(given, false);
        }
        member.playSound(SoundEvents.ITEM_PICKUP, 0.7F, 0.9F);
        ((ServerLevel) member.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, member.getX(), member.getEyeY(),
                member.getZ(), 5, 0.3D, 0.3D, 0.3D, 0.0D);
        player.displayClientMessage(Component.literal(member.getName().getString() + " takes it, and hands you ")
                .append(givenName).append("."), true);
    }

    private static boolean isFairReturn(ItemStack candidate, ItemStack offered, int offerValue) {
        int value = valueOf(candidate);
        return value > 0 && value <= offerValue && !candidate.is(offered.getItem());
    }

    private Trading() {
    }
}
