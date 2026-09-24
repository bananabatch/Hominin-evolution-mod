package dev.hominin.evolution;

import java.util.function.Supplier;

import dev.hominin.evolution.block.KnappingStationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HomininEvolutionMod.MODID);

    public static final Supplier<BlockEntityType<KnappingStationBlockEntity>> KNAPPING_STATION =
            BLOCK_ENTITIES.register("knapping_station", () -> BlockEntityType.Builder.of(
                    KnappingStationBlockEntity::new, ModBlocks.KNAPPING_STATION.get()).build(null));

    public static final Supplier<BlockEntityType<dev.hominin.evolution.block.FirePitBlockEntity>> FIRE_PIT =
            BLOCK_ENTITIES.register("fire_pit", () -> BlockEntityType.Builder.of(
                    dev.hominin.evolution.block.FirePitBlockEntity::new, ModBlocks.FIRE_PIT.get()).build(null));

    public static final Supplier<BlockEntityType<dev.hominin.evolution.block.ToolPileBlockEntity>> TOOL_PILE =
            BLOCK_ENTITIES.register("tool_pile", () -> BlockEntityType.Builder.of(
                    dev.hominin.evolution.block.ToolPileBlockEntity::new, ModBlocks.TOOL_PILE.get()).build(null));

    public static final Supplier<BlockEntityType<dev.hominin.evolution.block.CookingSpitBlockEntity>> COOKING_SPIT =
            BLOCK_ENTITIES.register("cooking_spit", () -> BlockEntityType.Builder.of(
                    dev.hominin.evolution.block.CookingSpitBlockEntity::new, ModBlocks.COOKING_SPIT.get()).build(null));

    private ModBlockEntities() {
    }
}
