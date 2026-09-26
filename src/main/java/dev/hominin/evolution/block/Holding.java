package dev.hominin.evolution.block;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

/**
 * Something that holds the band's things the way a pile does - a tool rack, a cooking rack's spit: each thing on it
 * remembers who put it there and who it is for (everyone, them alone, or those close to them), and the whole
 * remembers who put the first thing on it. Looked over with the work key, like a pile.
 */
public interface Holding {
    int slots();

    ItemStack at(int slot);

    @Nullable
    UUID layerOf(int slot);

    String layerNameOf(int slot);

    int markOf(int slot);

    void setMark(int slot, int mark);

    /** Whether whoever this access is may take what is in this slot. */
    default boolean mayTake(int slot, ToolPileBlockEntity.Access access) {
        return slot >= 0 && slot < slots() && !at(slot).isEmpty() && access.allows(layerOf(slot), markOf(slot));
    }

    /** Takes what is in the slot, if allowed; empty otherwise. */
    ItemStack takeSlot(int slot, ToolPileBlockEntity.Access access);

    @Nullable
    UUID firstBy();

    String firstName();

    /** "A tool rack", "A cooking rack". */
    String holdingName();

    /** Anything more about what is in a slot: how near done it is over the fire, say. */
    default String extra(int slot) {
        return "";
    }
}
