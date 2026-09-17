package dev.hominin.evolution.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A mob's accumulated head injuries, attached to the mob itself.
 *
 * <p>Repeated blows to the skull escalate: the first couple only daze, enough of
 * them concuss, and past that every further pair risks a bleed that kills on its
 * own. Tracking it per-mob is what makes a branch worth swinging more than once -
 * without it every hit would be an isolated stun.
 */
public final class HeadTrauma {
    public static final Codec<HeadTrauma> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("blows", 0).forGetter(HeadTrauma::getBlows),
            Codec.BOOL.optionalFieldOf("concussed", false).forGetter(HeadTrauma::isConcussed),
            Codec.INT.optionalFieldOf("blows_since_concussion", 0).forGetter(HeadTrauma::getBlowsSinceConcussion),
            Codec.BOOL.optionalFieldOf("pacified", false).forGetter(HeadTrauma::isPacified),
            Codec.BOOL.optionalFieldOf("bleeding", false).forGetter(HeadTrauma::isBleeding))
            .apply(instance, HeadTrauma::new));

    private int blows;
    private boolean concussed;
    private int blowsSinceConcussion;
    private boolean pacified;
    private boolean bleeding;

    public HeadTrauma() {
        this(0, false, 0, false, false);
    }

    public HeadTrauma(int blows, boolean concussed, int blowsSinceConcussion, boolean pacified, boolean bleeding) {
        this.blows = blows;
        this.concussed = concussed;
        this.blowsSinceConcussion = blowsSinceConcussion;
        this.pacified = pacified;
        this.bleeding = bleeding;
    }

    public int getBlows() {
        return blows;
    }

    public int addBlow() {
        blows++;
        if (concussed) {
            blowsSinceConcussion++;
        }
        return blows;
    }

    public boolean isConcussed() {
        return concussed;
    }

    public void setConcussed(boolean concussed) {
        this.concussed = concussed;
    }

    public int getBlowsSinceConcussion() {
        return blowsSinceConcussion;
    }

    /**
     * Spends a pair of post-concussion blows if a pair has accumulated. Returns
     * true exactly once per pair, so the bleed roll happens every second hit
     * rather than every hit.
     */
    public boolean consumeBlowPair() {
        if (blowsSinceConcussion < 2) {
            return false;
        }
        blowsSinceConcussion -= 2;
        return true;
    }

    public boolean isPacified() {
        return pacified;
    }

    public void setPacified(boolean pacified) {
        this.pacified = pacified;
    }

    public boolean isBleeding() {
        return bleeding;
    }

    public void setBleeding(boolean bleeding) {
        this.bleeding = bleeding;
    }
}
