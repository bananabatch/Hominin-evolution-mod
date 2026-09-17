package dev.hominin.evolution.band;

import java.util.Map;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Exchange with another band. There is no shared language and no money: you hold
 * something out, and they weigh it against what they carry.
 *
 * <p>Everything worth having falls into a tier, by what it cost to get. Anything can
 * be traded for something of its own tier or any tier below - a long branch buys food,
 * a flake buys a long branch - but never for something ranked above it. At the top
 * are the things a band may never have made or found at all: obsidian, a chert
 * hammerstone, an Oldowan multi tool.
 */
public final class Trading {
    public static final int MAX_TIER = 5;

    /** Tier names, lowest first, as the guidebook describes them. */
    public static final String[] TIER_NAMES = {"", "Common", "Useful", "Crafted", "Prized", "Treasured"};

    private static final Map<String, Integer> TIERS = Map.ofEntries(
            // 1 - picked up anywhere
            Map.entry("rock", 1), Map.entry("grub", 1), Map.entry("beetle", 1), Map.entry("earthworm", 1),
            Map.entry("meat_chunk", 1), Map.entry("nesting_material", 1),
            // 2 - worth a walk or a little work
            Map.entry("long_branch", 2), Map.entry("sharpened_stick", 2), Map.entry("bone_marrow", 2),
            Map.entry("long_bone", 2), Map.entry("termite_stick", 2), Map.entry("hammerstone", 2),
            Map.entry("limestone_rock", 2), Map.entry("granite_rock", 2),
            // 3 - made, or good stone
            Map.entry("flake", 3), Map.entry("pointy_stick", 3), Map.entry("lomekwian_tool", 3),
            Map.entry("digging_stick", 3), Map.entry("grinding_rock", 3), Map.entry("chert_rock", 3),
            Map.entry("nest", 3),
            // 4 - real tools
            Map.entry("chopper", 4), Map.entry("sharpened_spear", 4), Map.entry("wooden_club", 4),
            // 5 - rare, or skill a band may not have
            Map.entry("obsidian_rock", 5), Map.entry("chert_hammerstone", 5), Map.entry("oldowan_multitool", 5),
            Map.entry("fire_hardened_spear", 5));

    /** Finer ordering inside a tier, so "the best thing they have" is well defined. */
    private static final Map<String, Integer> WITHIN_TIER = Map.of(
            "oldowan_multitool", 3, "chert_hammerstone", 2, "chopper", 2, "flake", 2, "termite_stick", 1);

    /** The trade tier of one of this item, 0 if a band has no use for it. */
    public static int tierOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key.getNamespace().equals(HomininEvolutionMod.MODID)) {
            Integer tier = TIERS.get(key.getPath());
            if (tier != null) {
                return tier;
            }
        }
        if (stack.is(Items.STICK)) {
            return 1;
        }
        if (stack.is(ModTags.Items.KNAPPABLE_STONE)) {
            return 2;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            // A proper meal is worth more than a mouthful.
            return food.nutrition() >= 6 ? 2 : 1;
        }
        return 0;
    }

    /** A sortable worth: tier first, then the finer ordering inside it. Zero means worthless. */
    public static int valueOf(ItemStack stack) {
        int tier = tierOf(stack);
        if (tier == 0) {
            return 0;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return tier * 10 + WITHIN_TIER.getOrDefault(path, 0);
    }

    /**
     * Offers what the player holds. The other band hands back the best thing it carries
     * from the offer's tier or below - never something ranked higher, and never the same
     * kind of thing straight back.
     */
    public static void offer(BandMember member, Player player, ItemStack offered) {
        int offerTier = tierOf(offered);
        if (offerTier <= 0) {
            player.displayClientMessage(Component.literal(
                    member.getName().getString() + " turns it over and hands it back. No use to them."), true);
            return;
        }
        SimpleContainer pack = member.getInventory();
        int bestSlot = -2;
        int bestValue = 0;
        ItemStack held = member.getMainHandItem();
        if (isFairReturn(held, offered, offerTier) && valueOf(held) > bestValue) {
            bestSlot = -1;
            bestValue = valueOf(held);
        }
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (isFairReturn(stack, offered, offerTier) && valueOf(stack) > bestValue) {
                bestSlot = slot;
                bestValue = valueOf(stack);
            }
        }
        if (bestSlot == -2) {
            player.displayClientMessage(Component.literal(member.getName().getString()
                    + " looks at it, and at you, and keeps what they have. (Offer something of a higher tier.)"),
                    true);
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

    private static boolean isFairReturn(ItemStack candidate, ItemStack offered, int offerTier) {
        int tier = tierOf(candidate);
        return tier > 0 && tier <= offerTier && !candidate.is(offered.getItem());
    }

    private Trading() {
    }
}
