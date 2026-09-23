package dev.hominin.evolution.knapping;

import java.util.function.Supplier;

import dev.hominin.evolution.ModItems;
import net.minecraft.world.item.Item;

/**
 * What the knapper is aiming at when the hammerstone comes down.
 *
 * <p>Aiming is the whole mechanic. The stone does not care what you intended; a
 * core that comes out wrong is still a core, and the first two million years of
 * the record are full of them. Which of these are offered depends on what is in
 * the hand - see {@link Knapping#choicesFor}.
 */
public enum KnappingChoice {
    /**
     * A thin sharp sliver struck off the edge. The simplest thing stone does, and
     * the one that opens Oldowan technology.
     */
    FLAKE("knapping.hominin_evolution.flake",
            "knapping.hominin_evolution.flake.hint", ModItems.FLAKE),

    /** A core battered down one side until it has a working edge. */
    CHOPPER("knapping.hominin_evolution.chopper",
            "knapping.hominin_evolution.chopper.hint", ModItems.CHOPPER),

    /**
     * Everything at once: worked on both faces until it will strike, chop and cut.
     * It takes more stone than anything else here, and good stone at that.
     */
    MULTITOOL("knapping.hominin_evolution.multitool",
            "knapping.hominin_evolution.multitool.hint", ModItems.OLDOWAN_MULTITOOL),

    /**
     * A flat, rough face ground in rather than struck off - a stone for putting edges
     * back on worn tools. Soft stone does this job perfectly well.
     */
    GRINDING_STONE("knapping.hominin_evolution.grinding_stone",
            "knapping.hominin_evolution.grinding_stone.hint", ModItems.GRINDING_ROCK),

    /** Acheulean, at a knapping station: the hand axe. */
    HAND_AXE("knapping.hominin_evolution.hand_axe",
            "knapping.hominin_evolution.hand_axe.hint", ModItems.HAND_AXE),

    /** Acheulean: a broad straight cutting edge. */
    CLEAVER("knapping.hominin_evolution.cleaver",
            "knapping.hominin_evolution.cleaver.hint", ModItems.CLEAVER),

    /** Only for a chert hammerstone: give up the hammer and keep the stone. */
    SPLIT_CORE("knapping.hominin_evolution.split_core",
            "knapping.hominin_evolution.split_core.hint", ModItems.CHERT_ROCK);

    private final String titleKey;
    private final String hintKey;
    private final Supplier<? extends Item> result;

    KnappingChoice(String titleKey, String hintKey, Supplier<? extends Item> result) {
        this.titleKey = titleKey;
        this.hintKey = hintKey;
        this.result = result;
    }

    public String titleKey() {
        return titleKey;
    }

    public String hintKey() {
        return hintKey;
    }

    public Item result() {
        return result.get();
    }

    /** Network-safe lookup: an out-of-range ordinal from a bad packet becomes null. */
    public static KnappingChoice byId(int id) {
        KnappingChoice[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
