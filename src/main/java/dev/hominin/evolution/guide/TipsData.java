package dev.hominin.evolution.guide;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Which tips a player has already been shown, and whether they want tips at all. Kept for good -
 * through death and evolving alike - because a tip is about knowing how the game works, and that
 * does not change with the body.
 */
public final class TipsData {
    public static final Codec<TipsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(data -> List.copyOf(data.seen)),
            Codec.BOOL.optionalFieldOf("off", false).forGetter(data -> data.off)
    ).apply(instance, TipsData::new));

    private final Set<String> seen = new HashSet<>();
    private boolean off;

    public TipsData() {
    }

    private TipsData(List<String> seen, boolean off) {
        this.seen.addAll(seen);
        this.off = off;
    }

    public boolean hasSeen(Tips.Tip tip) {
        return seen.contains(tip.id());
    }

    public void markSeen(Tips.Tip tip) {
        seen.add(tip.id());
    }

    public int seenCount() {
        return seen.size();
    }

    public void forgetSeen() {
        seen.clear();
    }

    public boolean isOff() {
        return off;
    }

    public void setOff(boolean off) {
        this.off = off;
    }
}
