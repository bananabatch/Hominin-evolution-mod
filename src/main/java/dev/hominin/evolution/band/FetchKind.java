package dev.hominin.evolution.band;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Things a band member can be sent to fetch - only things there is a real way to get.
 * Twigs and branches come out of trees, stone comes from loose rocks lying about, and
 * food has to be foraged. If none of that is anywhere near, they come back empty-handed.
 */
public enum FetchKind {
    STICK("A stick", () -> Items.STICK, Source.LEAVES, 0.5F),
    LONG_BRANCH("A long branch", ModItems.LONG_BRANCH, Source.LEAVES, 0.3F),
    NESTING_MATERIAL("Nesting material", ModItems.NESTING_MATERIAL, Source.LEAVES, 0.7F),
    FOOD("Some food", null, Source.FORAGE, 0.0F),
    ANY_ROCK("Any rock", null, Source.ROCKS, 0.0F),
    CHERT("Chert", ModItems.CHERT_ROCK, Source.ROCKS, 0.0F),
    QUARTZITE("Quartzite", ModItems.GRANITE_ROCK, Source.ROCKS, 0.0F),
    LIMESTONE("Limestone", ModItems.LIMESTONE_ROCK, Source.ROCKS, 0.0F),
    OBSIDIAN("Obsidian", ModItems.OBSIDIAN_ROCK, Source.ROCKS, 0.0F);

    public enum Source {
        /** Torn out of a tree's leaves - some tries turn up nothing. */
        LEAVES,
        /** Loose rocks lying on the ground. */
        ROCKS,
        /** Rooting about for something edible. */
        FORAGE
    }

    private final String label;
    @Nullable
    private final Supplier<? extends Item> item;
    private final Source source;
    private final float chancePerTry;

    FetchKind(String label, @Nullable Supplier<? extends Item> item, Source source, float chancePerTry) {
        this.label = label;
        this.item = item;
        this.source = source;
        this.chancePerTry = chancePerTry;
    }

    public String label() {
        return label;
    }

    public Source source() {
        return source;
    }

    /** For leaves: how likely one handful is to give this. */
    public float chancePerTry() {
        return chancePerTry;
    }

    /** The exact item, or null when any food or any rock will do. */
    @Nullable
    public Item item() {
        return item == null ? null : item.get();
    }

    /** An item to show on the button. */
    public ItemStack icon() {
        return switch (this) {
            case FOOD -> new ItemStack(ModItems.GRUB.get());
            case ANY_ROCK -> new ItemStack(ModItems.ROCK.get());
            default -> new ItemStack(item());
        };
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (this) {
            case FOOD -> stack.has(DataComponents.FOOD);
            case ANY_ROCK -> stack.is(ModItems.ROCK.get()) || stack.is(ModTags.Items.KNAPPABLE_STONE);
            default -> stack.is(item());
        };
    }

    @Nullable
    public static FetchKind byId(int id) {
        FetchKind[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
