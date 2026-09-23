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
    STICK("A stick", () -> Items.STICK, Source.LEAVES, 0.5F, 0),
    LONG_BRANCH("A long branch", ModItems.LONG_BRANCH, Source.LEAVES, 0.3F, 0),
    NESTING_MATERIAL("Nesting material", ModItems.NESTING_MATERIAL, Source.LEAVES, 0.7F, 0),
    FOOD("Some food", null, Source.FORAGE, 0.0F, 0),
    ANY_ROCK("Any rock", null, Source.ROCKS, 0.0F, 0),
    CHERT("Chert", ModItems.CHERT_ROCK, Source.ROCKS, 0.0F, 0),
    BASALT("Basalt", ModItems.BASALT_ROCK, Source.ROCKS, 0.0F, 0),
    QUARTZITE("Quartzite", ModItems.GRANITE_ROCK, Source.ROCKS, 0.0F, 0),
    LIMESTONE("Limestone", ModItems.LIMESTONE_ROCK, Source.ROCKS, 0.0F, 0),
    OBSIDIAN("Obsidian", ModItems.OBSIDIAN_ROCK, Source.ROCKS, 0.0F, 0),

    // Tools are not lying about to be picked up - somebody made this one, and handing
    // it over costs them it. So what you can ask for is a question of how well they
    // think of you, and the better the tool the more that has to be true.
    FLAKE("A flake", ModItems.FLAKE, Source.MADE, 0.0F, 1),
    HAMMERSTONE("A hammerstone", ModItems.HAMMERSTONE, Source.MADE, 0.0F, 2),
    POINTY_STICK("A pointy stick", ModItems.POINTY_STICK, Source.MADE, 0.0F, 2),
    DIGGING_STICK("A digging stick", ModItems.DIGGING_STICK, Source.MADE, 0.0F, 3),
    CHOPPER("A chopper", ModItems.CHOPPER, Source.MADE, 0.0F, 4),
    SHARPENED_SPEAR("A spear", ModItems.SHARPENED_SPEAR, Source.MADE, 0.0F, 5),
    WOODEN_CLUB("A club", ModItems.WOODEN_CLUB, Source.MADE, 0.0F, 6),
    OLDOWAN_MULTITOOL("A multi tool", ModItems.OLDOWAN_MULTITOOL, Source.MADE, 0.0F, 8);

    public enum Source {
        /** Torn out of a tree's leaves - some tries turn up nothing. */
        LEAVES,
        /** Loose rocks lying on the ground. */
        ROCKS,
        /** Rooting about for something edible. */
        FORAGE,
        /**
         * Not found at all: something the member already has, or has to make. Nobody
         * can go and pick a chopper off the ground.
         */
        MADE
    }

    private final String label;
    @Nullable
    private final Supplier<? extends Item> item;
    private final Source source;
    private final float chancePerTry;
    private final int minBond;

    FetchKind(String label, @Nullable Supplier<? extends Item> item, Source source, float chancePerTry,
            int minBond) {
        this.label = label;
        this.item = item;
        this.source = source;
        this.chancePerTry = chancePerTry;
        this.minBond = minBond;
    }

    /** How well a member has to think of you before they will part with one. */
    public int minBond() {
        return minBond;
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
