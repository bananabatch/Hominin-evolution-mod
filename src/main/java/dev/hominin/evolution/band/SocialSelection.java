package dev.hominin.evolution.band;

/**
 * The band member most recently picked out with a sneak-use, on the client. Kept in
 * common code so the entity can record it without referring to any client class.
 */
public final class SocialSelection {
    public static int entityId = -1;
    public static long selectedAtMillis;

    private SocialSelection() {
    }
}
