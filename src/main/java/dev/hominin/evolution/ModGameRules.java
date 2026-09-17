package dev.hominin.evolution;

import net.minecraft.world.level.GameRules;

/**
 * World settings, shown on the Game Rules page when creating a world.
 */
public final class ModGameRules {
    /**
     * Super hard mode: every player starts as Ardipithecus, with its unfair requirements,
     * instead of Australopithecus. A placeholder for a fuller hard mode later.
     */
    public static final GameRules.Key<GameRules.BooleanValue> SUPER_HARD_MODE = GameRules.register(
            "homininSuperHardMode", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false));

    /**
     * Extra effort: the fallback species stop being a safety net and become rungs of the
     * ladder. Habilis reaches erectus only by way of ergaster, and so on down the line.
     */
    public static final GameRules.Key<GameRules.BooleanValue> EXTRA_EFFORT = GameRules.register(
            "homininExtraEffort", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false));

    /** Touching this class is what registers the rules; call it during mod construction. */
    public static void bootstrap() {
    }

    private ModGameRules() {
    }
}
