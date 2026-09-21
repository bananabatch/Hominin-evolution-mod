package dev.hominin.evolution;

import java.util.List;

import dev.hominin.evolution.item.StoneEdgeItem;
import dev.hominin.evolution.item.WoodenWeaponItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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

    /**
     * Rocks are fist-sized and heavy - you can carry an armful, not a quarry.
     * The held model always shows a single rock regardless of how many are in the
     * stack; only the inventory count reflects the rest.
     */
    private static final int ROCKS_PER_STACK = 4;

    /** A few dozen blows. Enough to be worth making, few enough to replace. */
    private static final int LOMEKWIAN_DURABILITY = 24;
    private static final int OLDOWAN_MULTITOOL_DURABILITY = 96;

    /**
     * Grinding wears the stone flat as well as the edge sharp. A tool now, so it has
     * durability - which in vanilla also means it no longer stacks.
     */
    private static final int GRINDING_ROCK_DURABILITY = 48;

    /**
     * A fresh edge is the sharpest thing in the world and dulls almost at once. Worn
     * flakes go back on the grinding stone. Durability means flakes no longer stack.
     */
    private static final int FLAKE_DURABILITY = 32;

    /** A band of your own species - a wild one, the way you would meet it out in the country. */
    public static final DeferredItem<Item> BAND_MEMBER_SPAWN_EGG = ITEMS.register("band_member_spawn_egg",
            () -> new dev.hominin.evolution.item.TroopSpawnEggItem(null, 0,
                    0x6F4A2D, 0x9A6947, new Item.Properties()));

    /** Paranthropus only exists as a wild troop, so its egg puts down a troop of four. */
    public static final DeferredItem<Item> PARANTHROPUS_SPAWN_EGG = ITEMS.register("paranthropus_spawn_egg",
            () -> new dev.hominin.evolution.item.TroopSpawnEggItem(dev.hominin.evolution.band.Paranthropus.STAGE, 4,
                    0x3A3330, 0x6E625C, new Item.Properties()));
    public static final DeferredItem<Item> BABOON_SPAWN_EGG = ITEMS.register("baboon_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BABOON,
                    0x8A7A52, 0x4D4038, new Item.Properties()));
    public static final DeferredItem<Item> CHIMPANZEE_SPAWN_EGG = ITEMS.register("chimpanzee_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.CHIMPANZEE,
                    0x2E2622, 0xB8977A, new Item.Properties()));
    public static final DeferredItem<Item> BONOBO_SPAWN_EGG = ITEMS.register("bonobo_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BONOBO,
                    0x1E1A19, 0xC98A86, new Item.Properties()));
    public static final DeferredItem<Item> CROCODILE_SPAWN_EGG = ITEMS.register("crocodile_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.CROCODILE,
                    0x4A5236, 0xB8A878, new Item.Properties()));
    public static final DeferredItem<Item> DINOPITHECUS_SPAWN_EGG = ITEMS.register("dinopithecus_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.DINOPITHECUS,
                    0x55504A, 0x8C6A3F, new Item.Properties()));
    public static final DeferredItem<Item> PACHYCROCUTA_SPAWN_EGG = ITEMS.register("pachycrocuta_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.PACHYCROCUTA,
                    0x9C8058, 0x4E3A28, new Item.Properties()));
    public static final DeferredItem<Item> HOMOTHERIUM_SPAWN_EGG = ITEMS.register("homotherium_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.HOMOTHERIUM, 0xC8A05A, 0x3E3226, new Item.Properties()));

    public static final DeferredItem<Item> CROWNED_EAGLE_SPAWN_EGG = ITEMS.register("crowned_eagle_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.CROWNED_EAGLE, 0x4A3A2E, 0xD8CBB0, new Item.Properties()));

    public static final DeferredItem<Item> SABERTOOTH_SPAWN_EGG = ITEMS.register("sabertooth_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.SABERTOOTH,
                    0xB08A52, 0xEEE6CC, new Item.Properties()));

    public static final DeferredItem<Item> FLAKE = ITEMS.register("flake",
            () -> new StoneEdgeItem(new Item.Properties()
                    .durability(FLAKE_DURABILITY)
                    .attributes(weapon(3.0D, 3.0D, 0.0D))));

    /**
     * The workhorse Oldowan tool. Carries a TOOL component rather than being a
     * TieredItem, which is what makes it mine soft stone at a useful speed - and
     * Item#mineBlock then wears it down for free.
     */
    public static final DeferredItem<Item> CHOPPER = ITEMS.registerSimpleItem("chopper",
            new Item.Properties()
                    .durability(96)
                    .component(DataComponents.TOOL, chopping()));

    public static final DeferredItem<Item> HAMMERSTONE = ITEMS.registerSimpleItem("hammerstone", new Item.Properties());

    /**
     * A hammerstone struck out of a chert seam. It works like any hammerstone, but
     * it is also a nodule of the best stone there is - so it can be broken down into
     * chert, or worked straight into a multi tool.
     */
    /**
     * Cut grass, dried and bundled. Useless on its own and the beginning of almost
     * everything: twine, bedding, roofing. The first material that is made rather than
     * found - which is why it waits for erectus.
     */
    public static final DeferredItem<Item> THATCH =
            ITEMS.registerSimpleItem("thatch", new Item.Properties());

    /**
     * Thatch twisted against itself until the fibres bind. Two things held together is
     * the whole idea, and everything hafted comes out of it.
     */
    public static final DeferredItem<Item> TWINE =
            ITEMS.registerSimpleItem("twine", new Item.Properties());

    /**
     * A spindle and a hearth board: the trick of making fire instead of finding it.
     * Carrying one is proof you worked it out, which is what erectus is waiting for.
     */
    public static final DeferredItem<Item> FIRE_DRILL =
            ITEMS.registerSimpleItem("fire_drill", new Item.Properties());

    public static final DeferredItem<Item> CHERT_HAMMERSTONE =
            ITEMS.registerSimpleItem("chert_hammerstone", new Item.Properties());

    public static final DeferredItem<Item> DIGGING_STICK = ITEMS.registerSimpleItem("digging_stick", new Item.Properties());

    /**
     * The raw shaft: grind it against a rock for a spear, craft it into a digging
     * stick, or just swing it. Long enough to reach past a predator's own bite
     * range, which is the whole point of it as a weapon.
     */
    public static final DeferredItem<Item> LONG_BRANCH = ITEMS.registerSimpleItem("long_branch",
            new Item.Properties().attributes(weapon(0.5D, 1.0D, 2.0D)));

    /**
     * A branch with the weight left at one end, worked with a rock until it
     * carries its mass into the blow. Slow, but it breaks what it lands on:
     * where a bare branch has to be swung three times to concuss anything, a
     * club stands a fair chance of doing it on the first.
     */
    public static final DeferredItem<Item> WOODEN_CLUB = ITEMS.registerItem("wooden_club",
            WoodenWeaponItem::new,
            new Item.Properties().durability(140).attributes(weapon(2.5D, 0.8D, 1.0D)));

    /**
     * Savannah chimps sharpen sticks to jab small prey out of tree hollows - the
     * earliest weapon we know of, and about as much use against anything large.
     */
    public static final DeferredItem<Item> SHARPENED_STICK = ITEMS.registerSimpleItem("sharpened_stick",
            new Item.Properties().attributes(weapon(1.0D, 2.5D, 0.0D)));

    /**
     * The same stick finished with a flake instead of teeth: a long, even taper and a
     * point fine enough to open a wound. The first thing a stone edge makes better.
     */
    public static final DeferredItem<Item> POINTY_STICK = ITEMS.registerSimpleItem("pointy_stick",
            new Item.Properties().attributes(weapon(2.5D, 2.5D, 0.0D)));

    /**
     * A wooden spear ground to a point against a rock - the Schoeningen kit.
     * The durability set here is only a floor: each recipe patches its own
     * max-damage onto the result, so a spear ground against a worn stone lasts
     * longer than one ground against a fresh cobble.
     */
    public static final DeferredItem<Item> SHARPENED_SPEAR = ITEMS.registerItem("sharpened_spear",
            WoodenWeaponItem::new,
            new Item.Properties().durability(40).attributes(weapon(3.5D, 1.6D, 2.0D)));

    /**
     * The same spear with its point turned in a fire until the wood case-hardens.
     * Erectus work: it needs a fire to stand beside, which is exactly the thing
     * that stage is defined by.
     */
    public static final DeferredItem<Item> FIRE_HARDENED_SPEAR = ITEMS.registerItem("fire_hardened_spear",
            WoodenWeaponItem::new,
            new Item.Properties().durability(140).attributes(weapon(4.5D, 1.6D, 2.0D)));

    /**
     * A Lomekwi-style core: a big cobble with a flake knocked off it and nothing
     * done to the edge afterwards. It is what you get when a knapping attempt does
     * not come out as the thing you were aiming at - which is exactly how the real
     * ones read, 700,000 years before anyone was shaping stone on purpose.
     *
     * <p>It is not a dead end. Producing one teaches the hand something, and that
     * is banked as knapping skill.
     */
    public static final DeferredItem<Item> LOMEKWIAN_TOOL = ITEMS.registerSimpleItem("lomekwian_tool",
            new Item.Properties()
                    .durability(LOMEKWIAN_DURABILITY)
                    .attributes(weapon(2.0D, 1.0D, 1.0D))
                    .component(DataComponents.TOOL, chopping()));

    /**
     * The same idea with the edges actually worked: chert or obsidian, flaked on
     * purpose, heavy enough to strike with and sharp enough to cut. It does the job
     * of a hammerstone, a chopper and a flake, and outlasts a Lomekwian core several
     * times over - the Oldowan kit in one hand.
     */
    public static final DeferredItem<Item> OLDOWAN_MULTITOOL = ITEMS.registerSimpleItem("oldowan_multitool",
            new Item.Properties()
                    .durability(OLDOWAN_MULTITOOL_DURABILITY)
                    .attributes(weapon(2.5D, 1.4D, 1.0D))
                    .component(DataComponents.TOOL, chopping()));

    /** A fist-sized cobble. Knapping stock, and coarse enough to grind a shaft to a point. */
    public static final DeferredItem<Item> ROCK = ITEMS.registerSimpleItem("rock",
            new Item.Properties().stacksTo(ROCKS_PER_STACK));

    /**
     * A rough, flat-faced stone for putting an edge back on worn tools. Knapped
     * deliberately rather than worn in by accident, and good in soft stone too.
     */
    public static final DeferredItem<Item> GRINDING_ROCK = ITEMS.registerSimpleItem("grinding_rock",
            new Item.Properties().durability(GRINDING_ROCK_DURABILITY));

    // Block items for the loose surface rocks. Placing one puts the scatter
    // back on the ground; breaking it drops a plain ROCK again.
    // They carry like rocks because they are rocks, so they get the same
    // armful-sized stack limit rather than the block-item default of 64.
    public static final DeferredItem<BlockItem> CHERT_ROCK = ITEMS.registerSimpleBlockItem(
            ModBlocks.CHERT_ROCK, new Item.Properties().stacksTo(ROCKS_PER_STACK));
    public static final DeferredItem<BlockItem> GRANITE_ROCK = ITEMS.registerSimpleBlockItem(
            ModBlocks.GRANITE_ROCK, new Item.Properties().stacksTo(ROCKS_PER_STACK));
    public static final DeferredItem<BlockItem> LIMESTONE_ROCK = ITEMS.registerSimpleBlockItem(
            ModBlocks.LIMESTONE_ROCK, new Item.Properties().stacksTo(ROCKS_PER_STACK));
    public static final DeferredItem<BlockItem> OBSIDIAN_ROCK = ITEMS.registerSimpleBlockItem(
            ModBlocks.OBSIDIAN_ROCK, new Item.Properties().stacksTo(ROCKS_PER_STACK));

    // The bedrock outcrops. Full blocks, so ordinary stack sizes.
    public static final DeferredItem<BlockItem> CHERT_DEPOSIT = ITEMS.registerSimpleBlockItem(
            ModBlocks.CHERT_DEPOSIT, new Item.Properties());
    public static final DeferredItem<BlockItem> QUARTZITE_DEPOSIT = ITEMS.registerSimpleBlockItem(
            ModBlocks.QUARTZITE_DEPOSIT, new Item.Properties());
    public static final DeferredItem<BlockItem> LIMESTONE_DEPOSIT = ITEMS.registerSimpleBlockItem(
            ModBlocks.LIMESTONE_DEPOSIT, new Item.Properties());

    public static final DeferredItem<Item> NEST = ITEMS.register("nest",
            () -> new BlockItem(ModBlocks.NEST.get(), new Item.Properties()));

    /** A handful of leafy twigs stripped from a tree. Two of them make a nest. */
    public static final DeferredItem<Item> NESTING_MATERIAL = ITEMS.registerSimpleItem("nesting_material",
            new Item.Properties());

    public static final DeferredItem<BlockItem> TERMITE_MOUND = ITEMS.registerSimpleBlockItem(
            ModBlocks.TERMITE_MOUND, new Item.Properties());
    public static final DeferredItem<BlockItem> DECAYING_LOG = ITEMS.registerSimpleBlockItem(
            ModBlocks.DECAYING_LOG, new Item.Properties());
    public static final DeferredItem<BlockItem> DECAYED_LOG = ITEMS.registerSimpleBlockItem(
            ModBlocks.DECAYED_LOG, new Item.Properties());

    /**
     * A twig pulled back out of a mound with the soldiers still clamped to it.
     *
     * <p>Worth far more than the insects a hand can pick: this is a real meal, and
     * it renews itself, because eating the termites leaves you holding the stick.
     * That loop is the point - a tool you keep is the first thing that makes a
     * technique worth learning rather than repeating.
     */
    public static final DeferredItem<Item> TERMITE_STICK = ITEMS.registerSimpleItem("termite_stick",
            new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(6)
                    .saturationModifier(0.7F)
                    .usingConvertsTo(Items.STICK)
                    .build()));

    public static final DeferredItem<Item> HOMININ_CARCASS = ITEMS.register("hominin_carcass",
            () -> new BlockItem(ModBlocks.HOMININ_CARCASS.get(), new Item.Properties()));

    /** Meat from one of our own kind. Food like any other - and not like any other. */
    public static final DeferredItem<Item> HOMININ_MEAT = ITEMS.registerSimpleItem("hominin_meat",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.5F).build()));

    /** Rich, and a gamble: about one brain in three carries kuru. */
    public static final DeferredItem<Item> HOMININ_BRAIN = ITEMS.registerSimpleItem("hominin_brain",
            new Item.Properties().stacksTo(4).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.8F)
                    .build()));

    /** Somebody's skull, to hold up and make a promise to. */
    public static final DeferredItem<Item> HOMININ_SKULL = ITEMS.register("hominin_skull",
            () -> new dev.hominin.evolution.item.HomininSkullItem(new Item.Properties().stacksTo(1)));

    /** The carcass itself, for anyone who wants to put one down. */
    public static final DeferredItem<Item> CARCASS = ITEMS.register("carcass",
            () -> new BlockItem(ModBlocks.CARCASS.get(), new Item.Properties()));

    /**
     * An egg pierced and drunk out, the shell left whole. Ostrich shells were still being
     * carried as water flasks a hundred thousand years ago; this is the same idea, earlier.
     */
    public static final DeferredItem<Item> EMPTY_EGGSHELL = ITEMS.register("empty_eggshell",
            () -> new dev.hominin.evolution.item.EggshellItem(new Item.Properties().stacksTo(8)));

    /** A shell of water: a mouthful, carried. */
    public static final DeferredItem<Item> WATER_EGGSHELL = ITEMS.register("water_eggshell",
            () -> new dev.hominin.evolution.item.WaterEggshellItem(new Item.Properties().stacksTo(4)
                    .food(new FoodProperties.Builder().nutrition(0).saturationModifier(0.0F).alwaysEdible()
                            .usingConvertsTo(EMPTY_EGGSHELL.get()).build())));

    /**
     * A portion cut from a larger piece. The values here are only a fallback: a
     * chunk made by cutting carries its own food values, a quarter of its source.
     */
    public static final DeferredItem<Item> MEAT_CHUNK = ITEMS.registerSimpleItem("meat_chunk",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2F).build()));

    public static final DeferredItem<Item> GRUB = ITEMS.registerSimpleItem("grub",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build()));

    /**
     * Picked off somebody else and eaten on the spot. Barely food, and that is the
     * point: it is the immediate, concrete payment for grooming somebody, which is why
     * primates do so much of it and why it never had to be altruism.
     */
    public static final DeferredItem<Item> TICK = ITEMS.registerSimpleItem("tick",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build()));

    public static final DeferredItem<Item> BEETLE = ITEMS.registerSimpleItem("beetle",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build()));

    public static final DeferredItem<Item> EARTHWORM = ITEMS.registerSimpleItem("earthworm",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build()));

    public static final DeferredItem<Item> LONG_BONE = ITEMS.registerSimpleItem("long_bone", new Item.Properties());

    /**
     * A rib with the meat still on it: the best thing a carcass gives up. Eat the meat and
     * the bone is still in your hand, and a bone can be cracked for what is inside it.
     */
    public static final DeferredItem<Item> RIB = ITEMS.register("rib",
            // The properties are built inside the supplier: the long bone it leaves behind is
            // another deferred item, and asking for it any earlier reads an unbound registry.
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(5)
                    .saturationModifier(0.7F)
                    .usingConvertsTo(LONG_BONE.get())
                    .build())));


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
    /** A stone edge heavy enough to break what the stone-tool gate protects. */
    private static Tool chopping() {
        return new Tool(List.of(Tool.Rule.minesAndDrops(ModTags.Blocks.REQUIRES_STONE_TOOL, 2.0F)), 1.0F, 1);
    }

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
