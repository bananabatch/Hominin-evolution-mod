package dev.hominin.evolution.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.climb.Climbing;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Whole-body animations: the climb, and one-shot displays. This sits on a layer above
 * the held-item animations, so a climber's arms go to the trunk whatever they carry.
 *
 * <p>Like {@link HeldAnimationHandler}, every Player Animator type is confined to this
 * class and nothing reaches it unless the library is loaded.
 */
public final class BodyAnimationHandler {
    private static final ResourceLocation LAYER =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "body");
    /** Above the held-item layer at 1000. */
    private static final int PRIORITY = 2000;

    private static final ResourceLocation CLIMB = id("climb");
    private static final int FADE = 4;

    /** What each player's body layer is playing, if anything. */
    private static final Map<UUID, ResourceLocation> PLAYING = new HashMap<>();

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER, PRIORITY, player -> new ModifierLayer<>());
    }

    /** A display or gesture, played through once. It takes precedence over the climb. */
    public static void playOnce(AbstractClientPlayer player, String name) {
        ModifierLayer<IAnimation> layer = layerOf(player);
        if (layer != null) {
            play(layer, player.getUUID(), id(name));
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            PLAYING.clear();
            return;
        }
        PLAYING.keySet().retainAll(level.players().stream().map(AbstractClientPlayer::getUUID).toList());
        for (AbstractClientPlayer player : level.players()) {
            update(player);
        }
    }

    private static void update(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = layerOf(player);
        if (layer == null) {
            return;
        }
        UUID id = player.getUUID();
        ResourceLocation playing = PLAYING.get(id);
        IAnimation current = layer.getAnimation();
        boolean active = current != null && current.isActive();

        if (playing != null && !playing.equals(CLIMB) && active) {
            // Let a one-shot finish before anything else takes the body.
            return;
        }
        boolean climbing = Climbing.isClimbing(player);
        if (climbing && !(CLIMB.equals(playing) && active)) {
            play(layer, id, CLIMB);
        } else if (!climbing && playing != null) {
            PLAYING.remove(id);
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE, Ease.INOUTSINE), null);
        }
    }

    private static void play(ModifierLayer<IAnimation> layer, UUID player, ResourceLocation id) {
        IPlayable animation = PlayerAnimationRegistry.getAnimation(id);
        if (animation == null) {
            return;
        }
        IAnimation played = animation.playAnimation();
        if (played instanceof KeyframeAnimationPlayer keyframes) {
            // Whole-body poses make no sense from behind your own eyes.
            keyframes.setFirstPersonMode(FirstPersonMode.NONE);
        }
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE, Ease.INOUTSINE), played);
        PLAYING.put(player, id);
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, name);
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layerOf(AbstractClientPlayer player) {
        IAnimation raw = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        return raw instanceof ModifierLayer<?> ? (ModifierLayer<IAnimation>) raw : null;
    }

    private BodyAnimationHandler() {
    }
}
