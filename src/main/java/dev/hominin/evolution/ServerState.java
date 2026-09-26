package dev.hominin.evolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Everything the server keeps in memory about the world it is running - cooldowns, pending asks, cached sites - goes
 * when that world is closed. In single player the game does not restart between worlds: without this, the next world
 * opened would start with the last one's cooldowns (counted in a game time it has not reached yet), its havens and
 * water sites, its hunts and requests.
 */
public final class ServerState {
    private static final List<Object> HELD = new ArrayList<>();
    private static final List<Runnable> RESETS = new ArrayList<>();

    /** A map or collection that belongs to the running world, and is emptied when it closes. */
    public static synchronized <T> T track(T holder) {
        HELD.add(holder);
        return holder;
    }

    /** Anything else to put back when the world closes. */
    public static synchronized void onReset(Runnable reset) {
        RESETS.add(reset);
    }

    public static synchronized void clear() {
        for (Object holder : HELD) {
            if (holder instanceof Map<?, ?> map) {
                map.clear();
            } else if (holder instanceof Collection<?> collection) {
                collection.clear();
            }
        }
        RESETS.forEach(Runnable::run);
    }

    private ServerState() {
    }
}
