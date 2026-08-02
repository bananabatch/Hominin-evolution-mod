package dev.hominin.evolution;

import dev.hominin.evolution.item.CarcassItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HomininEvolutionMod.MODID);

    public static final DeferredItem<Item> FLAKE = ITEMS.registerSimpleItem("flake", new Item.Properties());

    public static final DeferredItem<Item> CHOPPER = ITEMS.registerSimpleItem("chopper", new Item.Properties());

    public static final DeferredItem<Item> HAMMERSTONE = ITEMS.registerSimpleItem("hammerstone", new Item.Properties());

    public static final DeferredItem<Item> GRUB = ITEMS.registerSimpleItem("grub",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build()));

    public static final DeferredItem<Item> BEETLE = ITEMS.registerSimpleItem("beetle",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build()));

    public static final DeferredItem<Item> EARTHWORM = ITEMS.registerSimpleItem("earthworm",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build()));

    public static final DeferredItem<CarcassItem> CARCASS = ITEMS.register("carcass", CarcassItem::new);

    private ModItems() {
    }
}
