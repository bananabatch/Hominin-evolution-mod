package dev.hominin.evolution.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * No villages, temples, mineshafts or ruins: nobody has built anything yet. Emptying the
 * vanilla structure sets was not enough, because every worldgen mod registers its own -
 * Terralith brought its villages straight back. So the gate is here, at the one place any
 * structure from any source has to pass through to be placed, and only what is on the
 * allowed list (the Nether and the End, which are not this mod's business) gets by.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    private static final TagKey<Structure> ALLOWED = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "allowed_structures"));

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void hominin$onlyAllowedStructures(StructureSet.StructureSelectionEntry entry, StructureManager manager,
            RegistryAccess registryAccess, RandomState random, StructureTemplateManager templates, long seed,
            ChunkAccess chunk, ChunkPos chunkPos, SectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        if (!entry.structure().is(ALLOWED)) {
            cir.setReturnValue(false);
        }
    }
}
