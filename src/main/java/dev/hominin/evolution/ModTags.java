package dev.hominin.evolution;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/**
 * Every tag the mod reads. Tags are the mod's main extension point - a datapack
 * or another mod can slot its own blocks, items and creatures into these without
 * touching code.
 */
public final class ModTags {
    public static final class Blocks {
        /** Exposed stone you can knap against. */
        public static final TagKey<Block> WORKABLE_STONE_DEPOSIT = block("workable_stone_deposit");

        /**
         * Anything with termites in it. A tag rather than two block checks, so a
         * datapack can add its own rotten wood or mounds as fishing spots.
         */
        public static final TagKey<Block> TERMITE_SOURCE = block("termite_source");

        /** Soil you can root through for insects. */
        public static final TagKey<Block> FORAGING_GROUND = block("foraging_ground");

        /**
         * What bare hands may pull up: leaves, grass, fruit, mushrooms, a nest, a
         * carcass, a loose rock. Everything outside this - and outside the tool gates
         * below - simply cannot be broken, because a hominin cannot punch through stone
         * and never could. This is the allowance; the rest of the world is not.
         */
        public static final TagKey<Block> TAKEABLE_BY_HAND = block("takeable_by_hand");

        public static final TagKey<Block> REQUIRES_DIGGING_STICK = block("requires_digging_stick");
        public static final TagKey<Block> REQUIRES_STONE_TOOL = block("requires_stone_tool");
        public static final TagKey<Block> REQUIRES_HAND_AXE = block("requires_hand_axe");
        public static final TagKey<Block> REQUIRES_HAFTED_TOOL = block("requires_hafted_tool");

        private Blocks() {
        }
    }

    public static final class Items {
        public static final TagKey<Item> DIGGING_TOOLS = item("digging_tools");
        public static final TagKey<Item> STONE_TOOLS = item("stone_tools");

        /** Acheulean hand axes. Empty until Homo erectus content lands, which is what keeps wood unbreakable. */
        public static final TagKey<Item> HAND_AXE_TOOLS = item("hand_axe_tools");

        public static final TagKey<Item> HAFTED_TOOLS = item("hafted_tools");

        /** Every kind of loose cobble - any of them will batter a branch into a club. */
        public static final TagKey<Item> ROCKS = item("rocks");

        /**
         * Stone worth putting under a hammerstone. Limestone is in here because it
         * can be struck - it just shatters wrong, which is what makes it the
         * interesting mistake rather than a wasted click; the code, not the tag,
         * decides which of these hold an edge.
         */
        public static final TagKey<Item> KNAPPABLE_STONE = item("knappable_stone");

        // Tool roles. Several items can do the same job - a Lomekwian core is a crude
        // hammerstone and chopper at once - so the game asks "can this strike / chop /
        // cut" through these tags instead of naming one item each time.

        /** Anything that can be used to strike stone: working deposits, and knapping. */
        public static final TagKey<Item> HAMMERSTONES = item("hammerstones");

        /** Anything with a heavy chopping edge: hacking branches off trunks. */
        public static final TagKey<Item> CHOPPERS = item("choppers");

        /** Anything with a fine cutting edge: whittling, and cracking bones. */
        public static final TagKey<Item> FLAKES = item("flakes");

        /** Meat a flake can portion. Vanilla's meat, plus the common tags other mods use. */
        public static final TagKey<Item> SPLITTABLE_MEAT = item("splittable_meat");

        private Items() {
        }
    }

    public static final class Biomes {
        /**
         * The country this mod is about: open savanna and dry shrubland, with enough
         * tree cover to climb. A tag rather than a list, so Terralith - or any other
         * worldgen pack - can nominate its own biomes as valid hominin range.
         */
        public static final TagKey<Biome> HOMININ_HOMELAND = biome("hominin_homeland");

        private Biomes() {
        }
    }

    public static final class EntityTypes {
        /**
         * Creatures that never spawn naturally. A named list rather than an
         * {@code instanceof Monster} check, so predators from Alex's Mobs or
         * Naturalist are unaffected unless deliberately added.
         */
        public static final TagKey<EntityType<?>> BLOCKED_SPAWNS = entityType("blocked_spawns");

        /**
         * Animals a hominin has reason to fear. Only these are driven off by a
         * teeth-baring display; a knock startles anything nearby. Vanilla has
         * almost nothing that fits, so this stays thin until Naturalist's
         * predators can be added.
         */
        public static final TagKey<EntityType<?>> PREDATORS = entityType("predators");

        /** Predators no threat display will move. */
        public static final TagKey<EntityType<?>> FEARLESS = entityType("fearless");

        private EntityTypes() {
        }
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, id(path));
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, id(path));
    }

    private static TagKey<Biome> biome(String path) {
        return TagKey.create(Registries.BIOME, id(path));
    }

    private static TagKey<EntityType<?>> entityType(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, path);
    }

    private ModTags() {
    }
}
