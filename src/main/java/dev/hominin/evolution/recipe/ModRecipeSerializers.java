package dev.hominin.evolution.recipe;

import java.util.function.Supplier;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, HomininEvolutionMod.MODID);

    // Deliberately reuses RecipeType.CRAFTING (via CraftingRecipe) so these show up
    // in the ordinary crafting grid rather than needing a station of their own.
    public static final Supplier<RecipeSerializer<TransformingShapelessRecipe>> SHAPELESS_TRANSFORMING =
            RECIPE_SERIALIZERS.register("shapeless_transforming", TransformingShapelessRecipe.Serializer::new);

    private ModRecipeSerializers() {
    }
}
