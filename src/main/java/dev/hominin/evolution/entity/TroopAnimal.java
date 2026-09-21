package dev.hominin.evolution.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;

/**
 * A primate that lives in a group with a memory: a baboon troop, a chimpanzee community.
 * Anything that implements this shares one set of relations with the player - trust built
 * by gifts, the five seconds to put a mistake right, and a grudge if you do not.
 */
public interface TroopAnimal {
    @Nullable
    UUID getTroop();

    /** The group has decided about you, and it was not in your favour. */
    void turnOn(Player player);

    boolean isAngry();
}
