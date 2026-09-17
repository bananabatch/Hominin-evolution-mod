package dev.hominin.evolution.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Key bindings the mod adds. Rebindable from the vanilla controls screen. */
public final class ModKeyMappings {
    private static final String CATEGORY = "key.categories.hominin_evolution";

    /**
     * Work the item in the main hand with the one in the off hand. This is the
     * mod's whole crafting interface, so it gets a key of its own rather than
     * overloading right-click, which items already use for their own purposes.
     */
    public static final KeyMapping ITEM_INTERACT = new KeyMapping(
            "key.hominin_evolution.item_interact",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_P),
            CATEGORY);

    /**
     * Held, not tapped. Thinking costs a full stomach and locks out for minutes, so
     * it should never be something you do by brushing a key.
     */
    public static final KeyMapping THINK = new KeyMapping(
            "key.hominin_evolution.think",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K),
            CATEGORY);

    /** Talk to the band nearby, or to the member just picked out with a sneak-use. */
    public static final KeyMapping SOCIAL = new KeyMapping(
            "key.hominin_evolution.social",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H),
            CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SOCIAL);
        event.register(ITEM_INTERACT);
        event.register(THINK);
    }

    private ModKeyMappings() {
    }
}
