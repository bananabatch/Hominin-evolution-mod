package dev.hominin.evolution.guide;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.data.PlayerEvolutionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * "A Guide to Your Inner Mind and Future" - the Patchouli book every player starts with.
 *
 * <p>Patchouli is an optional companion, not a dependency, so nothing here touches its
 * classes. The book is its ordinary guide-book item with a component naming our book,
 * both looked up by id and decoded through the component's own codec. If Patchouli is
 * missing there is simply no book, and the player is not marked as having had one - so
 * adding Patchouli to an existing world still hands it over on the next login.
 */
public final class GuideBook {
    public static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "inner_mind");

    /**
     * The book's full title. Patchouli draws the book's own name on a fixed-width
     * nameplate with no wrapping or scaling, so book.json carries a short form that
     * fits; the copy in the player's hands wears the real one.
     */
    private static final String FULL_TITLE = "A Guide to Your Inner Mind and Future";

    private static final ResourceLocation GUIDE_ITEM = ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book");
    private static final ResourceLocation BOOK_COMPONENT = ResourceLocation.fromNamespaceAndPath("patchouli", "book");

    /** Marker in the player's unlocked set, so the book is only ever handed over once. */
    private static final ResourceLocation RECEIVED =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "received_guide");

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.getUnlockedRecipes().contains(RECEIVED)) {
            return;
        }
        if (give(player)) {
            data.getUnlockedRecipes().add(RECEIVED);
        }
    }

    /** Hands a fresh copy to the player. Returns false if Patchouli is not installed. */
    public static boolean give(ServerPlayer player) {
        ItemStack book = create();
        if (book.isEmpty()) {
            return false;
        }
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        return true;
    }

    private static ItemStack create() {
        Item item = BuiltInRegistries.ITEM.get(GUIDE_ITEM);
        DataComponentType<?> component = BuiltInRegistries.DATA_COMPONENT_TYPE.get(BOOK_COMPONENT);
        if (item == Items.AIR || component == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (!setFromString(stack, component, BOOK_ID.toString())) {
            return ItemStack.EMPTY;
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(FULL_TITLE)
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
        return stack;
    }

    private static <T> boolean setFromString(ItemStack stack, DataComponentType<T> type, String value) {
        return type.codecOrThrow().parse(JsonOps.INSTANCE, new JsonPrimitive(value)).result()
                .map(decoded -> {
                    stack.set(type, decoded);
                    return true;
                })
                .orElse(false);
    }

    private GuideBook() {
    }
}
