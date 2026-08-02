package dev.hominin.evolution.recipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * A shapeless recipe where some ingredients survive the craft as a different item
 * rather than being consumed - grinding a shaft against a rock wears the rock's
 * face flat instead of using the rock up.
 *
 * <p>This cannot be done with {@code Item.Properties#craftRemainder}, which is a
 * single global property of the item: a rock would then turn into a grinding rock
 * in every recipe that used one. The replacement has to be per-recipe, which means
 * overriding {@link #getRemainingItems}.
 */
public class TransformingShapelessRecipe implements CraftingRecipe {
    private final ShapelessRecipe delegate;
    private final ItemStack result;
    private final Map<Item, Item> transforms;

    public TransformingShapelessRecipe(String group, CraftingBookCategory category, ItemStack result,
            NonNullList<Ingredient> ingredients, Map<Item, Item> transforms) {
        this.delegate = new ShapelessRecipe(group, category, result, ingredients);
        this.result = result;
        this.transforms = Map.copyOf(transforms);
    }

    /**
     * Vanilla's {@code ResultSlot#onTake} puts each remaining item back into the grid
     * slot its ingredient came from, which is exactly the behaviour wanted here.
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack ingredient = input.getItem(slot);
            Item replacement = transforms.get(ingredient.getItem());
            remaining.set(slot, replacement != null
                    ? new ItemStack(replacement)
                    : ingredient.getCraftingRemainingItem());
        }
        return remaining;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return delegate.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return delegate.assemble(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return delegate.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return delegate.getResultItem(registries);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return delegate.getIngredients();
    }

    @Override
    public String getGroup() {
        return delegate.getGroup();
    }

    @Override
    public CraftingBookCategory category() {
        return delegate.category();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.SHAPELESS_TRANSFORMING.get();
    }

    public static class Serializer implements RecipeSerializer<TransformingShapelessRecipe> {
        private static final Codec<Map<Item, Item>> TRANSFORM_CODEC = Codec.unboundedMap(
                BuiltInRegistries.ITEM.byNameCodec(), BuiltInRegistries.ITEM.byNameCodec());

        private static final MapCodec<TransformingShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(TransformingShapelessRecipe::getGroup),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                                .forGetter(TransformingShapelessRecipe::category),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
                        Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients")
                                .<NonNullList<Ingredient>>xmap(
                                        list -> NonNullList.of(Ingredient.EMPTY, list.toArray(Ingredient[]::new)),
                                        List::copyOf)
                                .forGetter(TransformingShapelessRecipe::getIngredients),
                        TRANSFORM_CODEC.optionalFieldOf("transform", Map.of()).forGetter(r -> r.transforms)
                ).apply(instance, TransformingShapelessRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM_STREAM_CODEC =
                ByteBufCodecs.registry(Registries.ITEM);

        public static final StreamCodec<RegistryFriendlyByteBuf, TransformingShapelessRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<TransformingShapelessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TransformingShapelessRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, TransformingShapelessRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            buffer.writeVarInt(ingredients.size());
            for (Ingredient ingredient : ingredients) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
            }
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
            ByteBufCodecs.<RegistryFriendlyByteBuf, Item, Item, Map<Item, Item>>map(
                    HashMap::new, ITEM_STREAM_CODEC, ITEM_STREAM_CODEC).encode(buffer, recipe.transforms);
        }

        private static TransformingShapelessRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            int ingredientCount = buffer.readVarInt();
            NonNullList<Ingredient> ingredients = NonNullList.withSize(ingredientCount, Ingredient.EMPTY);
            ingredients.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            Map<Item, Item> transforms = ByteBufCodecs.<RegistryFriendlyByteBuf, Item, Item, Map<Item, Item>>map(
                    HashMap::new, ITEM_STREAM_CODEC, ITEM_STREAM_CODEC).decode(buffer);
            return new TransformingShapelessRecipe(group, category, result, ingredients, transforms);
        }
    }
}
