package dev.hominin.evolution.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Fed past wanting, after a feast. It does nothing itself: hunger falls at a quarter of its pace while it lasts -
 * see {@link dev.hominin.evolution.band.Feast} for a player, and a band member's own hunger for theirs.
 */
public class FullEffect extends MobEffect {
    public FullEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xD9A05B);
    }
}
