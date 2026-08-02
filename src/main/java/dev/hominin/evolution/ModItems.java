package dev.hominin.evolution;

import java.util.List;

import dev.hominin.evolution.item.WoodenWeaponItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HomininEvolutionMod.MODID);

    /** Bare hands already deal 1 damage, so a weapon's modifier is (total damage - 1). */
    private static final double UNARMED_DAMAGE = 1.0D;
    /** Likewise the bare-handed swing rate is 4 attacks per second. */
    private static final double UNARMED_ATTACK_SPEED = 4.0D;

    private static final ResourceLocation REACH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "haft_reach");

    public static final DeferredItem<Item> FLAKE = ITEMS.registerSimpleItem("flake",
            new Item.Properties().attributes(weapon(3.0D, 3.0D, 0.0D)));

    /**
     * The workhorse Oldowan tool. Carries a TOOL component rather than being a
     * TieredItem, which is what makes it mine soft stone at a useful speed - and
     * Item#mineBlock then wears it down for free.
     */
    public static final DeferredItem<Item> CHOPPER = ITEMS.registerSimpleItem("chopper",
            new Item.Properties()
                    .durability(96)
                    .component(DataComponents.TOOL, new Tool(
                            List.of(Tool.Rule.minesAndDrops(ModTags.Blocks.REQUIRES_STONE_TOOL, 2.0F)),
                            1.0F, 1)));

    public static final DeferredItem<Item> HAMMERSTONE = ITEMS.registerSimpleItem("hammerstone", new Item.Properties());

    public static final DeferredItem<Item> DIGGING_STICK = ITEMS.registerSimpleItem("digging_stick", new Item.Properties());

    /**
     * The raw shaft: grind it against a rock for a spear, craft it into a digging
     * stick, or just swing it. Long enough to reach past a predator's own bite
     * range, which is the whole point of it as a weapon.
     */
    public static final DeferredItem<Item> LONG_BRANCH = ITEMS.registerSimpleItem("long_branch",
            new Item.Properties().attributes(weapon(0.5D, 1.6D, 2.0D)));

    /**
     * Savannah chimps sharpen sticks to jab small prey out of tree hollows - the
     * earliest weapon we know of, and about as much use against anything large.
     */
    public static final DeferredItem<Item> SHARPENED_STICK = ITEMS.registerSimpleItem("sharpened_stick",
            new Item.Properties().attributes(weapon(1.0D, 2.5D, 0.0D)));

    /**
     * A wooden spear ground to a point against a rock - the Schoeningen kit.
     * The durability set here is only a floor: each recipe patches its own
     * max-damage onto the result, so a spear ground against a worn stone lasts
     * longer than one ground against a fresh cobble.
     */
    public static final DeferredItem<Item> SHARPENED_SPEAR = ITEMS.registerItem("sharpened_spear",
            WoodenWeaponItem::new,
            new Item.Properties().durability(80).attributes(weapon(2.6D, 1.6D, 2.0D)));

    /** A fist-sized cobble. Knapping stock, and coarse enough to grind a shaft to a point. */
    public static final DeferredItem<Item> ROCK = ITEMS.registerSimpleItem("rock", new Item.Properties());

    /** The same rock once its working face has been worn flat against wood. */
    public static final DeferredItem<Item> GRINDING_ROCK = ITEMS.registerSimpleItem("grinding_rock",
            new Item.Properties());

    public static final DeferredItem<Item> GRUB = ITEMS.registerSimpleItem("grub",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build()));

    public static final DeferredItem<Item> BEETLE = ITEMS.registerSimpleItem("beetle",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build()));

    public static final DeferredItem<Item> EARTHWORM = ITEMS.registerSimpleItem("earthworm",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build()));

    public static final DeferredItem<Item> LONG_BONE = ITEMS.registerSimpleItem("long_bone", new Item.Properties());

    // Marrow is fatty and calorie-dense - the whole reason cracking bones open
    // was worth the effort, so it feeds better than the insect forage items.
    public static final DeferredItem<Item> BONE_MARROW = ITEMS.registerSimpleItem("bone_marrow",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.6F).build()));

    /**
     * Builds the attribute set for a hand weapon.
     *
     * @param attackDamage total damage shown in the tooltip
     * @param attackSpeed  swings per second shown in the tooltip
     * @param bonusReach   extra blocks of entity reach, or 0 for none
     */
    private static ItemAttributeModifiers weapon(double attackDamage, double attackSpeed, double bonusReach) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage - UNARMED_DAMAGE,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed - UNARMED_ATTACK_SPEED,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        if (bonusReach > 0.0D) {
            builder.add(Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(REACH_MODIFIER_ID, bonusReach, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        return builder.build();
    }

    private ModItems() {
    }
}
