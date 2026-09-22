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

    private ModBlockEntities() {
    }
}
