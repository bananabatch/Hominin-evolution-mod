package dev.hominin.evolution;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
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

        /** Soil you can root through for insects. */
        public static final TagKey<Block> FORAGING_GROUND = block("foraging_ground");

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

        private Items() {
        }
    }

    public static final class EntityTypes {
        /**
         * Creatures that never spawn naturally. A named list rather than an
         * {@code instanceof Monster} check, so predators from Alex's Mobs or
         * Naturalist are unaffected unless deliberately added.
         */
        public static final TagKey<EntityType<?>> BLOCKED_SPAWNS = entityType("blocked_spawns");

        private EntityTypes() {
        }
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, id(path));
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, id(path));
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
