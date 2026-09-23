package dev.hominin.evolution.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
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
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Drives the held-weapon animations: a looping carry while an item is in the
 * main hand, and a one-shot strike on each swing.
 *
 * <p>Every reference to Player Animator lives in this class, and nothing calls
 * into it unless the mod is present - so the library stays genuinely optional
 * and the JVM never tries to load its classes without it.
 *
 * <p>The strike needs no packet of its own. Vanilla already tells every client
 * when a player swings, via {@code ClientboundAnimatePacket}, and sets
 * {@link net.minecraft.world.entity.LivingEntity#swinging} from it - so
 * watching that flag animates other players for free.
 */
public final class HeldAnimationHandler {
    private static final ResourceLocation LAYER =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "held");

    /**
     * An item's carry loop and its strike, both keyed by the emote name in the JSON.
     * The item is a supplier, not a value: this table is built when the mod is
     * constructed, which is before the item registry is populated.
     */
    private record HeldAnims(Supplier<Item> item, ResourceLocation hold, ResourceLocation strike,
            ResourceLocation breakStrike, ResourceLocation breakAgain, boolean twoHanded) {
        static HeldAnims of(Supplier<Item> item, String hold, String strike, boolean twoHanded) {
            return new HeldAnims(item, hold == null ? null : id(hold), id(strike), null, null, twoHanded);
        }

        /**
         * A carry, a swing for hitting things, and strokes for taking a block apart: the first
         * takes the tool up from the carry, and each one after goes again from where the last left
         * the hands, for as long as the work goes on.
         */
        static HeldAnims attackAndBreak(Supplier<Item> item, String hold, String strike, String breakStrike,
                String breakAgain) {
            return new HeldAnims(item, id(hold), id(strike), id(breakStrike), id(breakAgain), false);
        }

        boolean isStrike(ResourceLocation playing) {
            return playing.equals(strike) || isBreakStroke(playing);
        }

        boolean isBreakStroke(ResourceLocation playing) {
            return playing.equals(breakStrike) || playing.equals(breakAgain);
        }

        private static ResourceLocation id(String name) {
            return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, name);
        }

        /** Small tools get a strike only - vanilla's own idle pose is fine for them. */
        static HeldAnims strikeOnly(Supplier<Item> item, String strike) {
            return of(item, null, strike, false);
        }
    }

    private static final List<HeldAnims> ANIMS = List.of(
            HeldAnims.of(ModItems.SHARPENED_SPEAR, "spear_hold", "spear_thrust", true),
            // Same weapon, harder point - same grip and thrust.
            HeldAnims.of(ModItems.FIRE_HARDENED_SPEAR, "spear_hold", "spear_thrust", true),
            HeldAnims.of(ModItems.LONG_BRANCH, "branch_hold", "branch_swing", true),
            // Worked wood is carried and swung exactly as the branch it came from.
            HeldAnims.of(ModItems.WORKABLE_BRANCH, "branch_hold", "branch_swing", true),
            HeldAnims.of(ModItems.WORKABLE_SHAFT, "branch_hold", "branch_swing", true),
            // One-handed: the club's weight does the work, so the other hand stays free.
            HeldAnims.of(ModItems.WOODEN_CLUB, "club_hold", "club_swing", false),
            HeldAnims.strikeOnly(ModItems.SHARPENED_STICK, "stick_stab"),
            HeldAnims.strikeOnly(ModItems.POINTY_STICK, "stick_stab"),
            HeldAnims.strikeOnly(ModItems.FLAKE, "flake_slash"),
            // The hand axe: carried low in front, lying level in the hand; a heavy sideways slash at
            // anything alive; and to take a block apart, the cleaver's push - caught in both hands and
            // driven in, stroke after stroke.
            HeldAnims.attackAndBreak(ModItems.HAND_AXE, "hand_axe_hold", "hand_axe_slash", "hand_axe_chop",
                    "hand_axe_chop_again"),
            // The digging stick: carried upright like a staff; levelled and jabbed at anything alive;
            // lifted, driven into the ground and levered, over and over, to dig - or to forage.
            HeldAnims.attackAndBreak(ModItems.DIGGING_STICK, "digging_stick_hold", "digging_stick_jab",
                    "digging_stick_dig", "digging_stick_dig_again"),
            // The cleaver is carried in one hand; to use it, the other hand catches it and both push it
            // straight forward, the body leaning in behind it.
            HeldAnims.of(ModItems.CLEAVER, "cleaver_hold", "cleaver_push", false),
            // The chopper is the cleaver's ancestor, and is carried and swung the same way.
            HeldAnims.of(ModItems.CHOPPER, "cleaver_hold", "cleaver_push", false));

    /**
     * Carries that hang the arm low. Drawn from the animation in first person, the item would sink
     * out of view while idle - so these show in third person only, and first person keeps the
     * ordinary held-item view until a strike takes over.
     */
    private static final Set<ResourceLocation> THIRD_PERSON_ONLY = Set.of(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "cleaver_hold"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "hand_axe_hold"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "digging_stick_hold"));

    /** Swings that need the other hand drawn in first person too. */
    private static final Set<ResourceLocation> TWO_HANDED = Set.of(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "cleaver_push"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "hand_axe_chop"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "hand_axe_chop_again"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "digging_stick_jab"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "digging_stick_dig"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "digging_stick_dig_again"));

    /**
     * Whether this swing is at a block rather than at something alive. For the local player that
     * is what the crosshair is on; for anyone else, what their look lands on first.
     */
    private static boolean isBreaking(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            return mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
        }
        return player.pick(player.blockInteractionRange(), 1.0F, false).getType()
                == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    /**
     * Anything else that is swung as a tool or a weapon - a cleaver, a chopper, a hammerstone -
     * slashes like a flake rather than doing vanilla's limp punch.
     */
    private static final HeldAnims DEFAULT_SLASH = HeldAnims.strikeOnly(() -> net.minecraft.world.item.Items.AIR,
            "flake_slash");

    /** Whether a held stack is a tool or weapon worth a proper swing. Empty hands keep the punch. */
    private static boolean isSwungTool(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && (stack.isDamageableItem()
                || stack.is(dev.hominin.evolution.ModTags.Items.STONE_TOOLS)
                || stack.is(dev.hominin.evolution.ModTags.Items.CUTTING_EDGE)
                || stack.is(dev.hominin.evolution.ModTags.Items.CHOPPERS)
                || stack.is(dev.hominin.evolution.ModTags.Items.HAMMERSTONES));
    }

    /**
     * Above the vanilla layers so the carry wins over the idle arm pose, but
     * low enough that anything added later can still sit on top.
     */
    private static final int PRIORITY = 1000;

    private static final int HOLD_FADE = 5;

    /** Players already mid-strike, so one swing does not retrigger every tick. */
    private static final Set<UUID> STRIKING = new HashSet<>();

    /** Players who swung again at a block mid-stroke: the next stroke follows this one. */
    private static final Set<UUID> AGAIN = new HashSet<>();

    /** What each player's layer is currently playing, so the strike can hand back to the hold. */
    private static final Map<UUID, ResourceLocation> PLAYING = new HashMap<>();

    private static final Logger LOG = LogUtils.getLogger();

    /** Ids already complained about, so a missing animation warns once, not every tick. */
    private static final Set<ResourceLocation> MISSING = new HashSet<>();

    /**
     * First-person Model already draws the whole third-person body in first person.
     * Asking Player Animator to do the same renders the player - and the held item -
     * twice. Resolved lazily because ModList is not populated at class-init.
     */
    private static Boolean firstPersonModLoaded;

    private static FirstPersonMode firstPersonMode() {
        if (firstPersonModLoaded == null) {
            firstPersonModLoaded = ModList.get().isLoaded("firstperson");
        }
        return firstPersonModLoaded ? FirstPersonMode.NONE : FirstPersonMode.THIRD_PERSON_MODEL;
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER, PRIORITY, player -> new ModifierLayer<>());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            STRIKING.clear();
            AGAIN.clear();
            PLAYING.clear();
            return;
        }
        var present = level.players().stream().map(AbstractClientPlayer::getUUID).toList();
        STRIKING.retainAll(present);
        AGAIN.retainAll(present);
        PLAYING.keySet().retainAll(present);
        for (AbstractClientPlayer player : level.players()) {
            update(player);
        }
    }

    private static HeldAnims animsFor(AbstractClientPlayer player) {
        for (HeldAnims anims : ANIMS) {
            if (player.getMainHandItem().is(anims.item().get())) {
                return anims;
            }
        }
        return isSwungTool(player.getMainHandItem()) ? DEFAULT_SLASH : null;
    }

    private static void update(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = layerOf(player);
        if (layer == null) {
            return;
        }
        UUID id = player.getUUID();
        HeldAnims anims = animsFor(player);
        if (anims == null) {
            STRIKING.remove(id);
            AGAIN.remove(id);
            PLAYING.remove(id);
            if (layer.getAnimation() != null) {
                layer.replaceAnimationWithFade(fade(4, Ease.INOUTSINE), null);
            }
            return;
        }
        IAnimation current = layer.getAnimation();
        ResourceLocation playing = PLAYING.get(id);
        boolean active = current != null && current.isActive();
        boolean striking = playing != null && anims.isStrike(playing) && active;
        boolean breakStroke = playing != null && anims.isBreakStroke(playing) && active;
        // swingTime lands on 0 for exactly one tick per swing, whether the swing
        // is the local player's own or relayed from the server. A fresh swing always strikes -
        // except at a block mid-stroke, where it queues the next stroke instead of cutting this
        // one off; a swing held down (digging, chopping) strokes on by itself, below.
        if (player.swinging && player.swingTime == 0 && (STRIKING.add(id) || !striking)) {
            boolean atBlock = anims.breakStrike() != null && isBreaking(player);
            if (atBlock && breakStroke) {
                AGAIN.add(id);
            } else {
                play(layer, id, anims, atBlock ? anims.breakStrike() : anims.strike(), 1, Ease.OUTQUAD);
                return;
            }
        }
        if (!player.swinging) {
            STRIKING.remove(id);
        }
        // Still at the work: go straight into the next stroke as this one ends, the hands never
        // leaving the tool. Only when the work stops does the carry take the tool back.
        if (breakStroke && current instanceof KeyframeAnimationPlayer strokes
                && (AGAIN.contains(id) || (player.swinging && isBreaking(player)))) {
            if (strokes.getCurrentTick() >= strokes.getStopTick() - 1) {
                AGAIN.remove(id);
                play(layer, id, anims, anims.breakAgain(), 1, Ease.INOUTSINE);
            }
            return;
        }
        if (anims.hold() == null) {
            // Strike-only item: once the strike is spent, hand the arms back to vanilla.
            if (current != null && !current.isActive()) {
                PLAYING.remove(id);
                layer.replaceAnimationWithFade(fade(HOLD_FADE, Ease.INOUTSINE), null);
            }
            return;
        }
        // Switching items mid-carry: the layer still holds the old item's loop. (A break stroke is
        // this item's own - counting it as a stranger's loop is what used to cut the hand axe's
        // chop off one tick after it began.)
        boolean wrongLoop = playing != null && !playing.equals(anims.hold()) && !anims.isStrike(playing);
        if (current == null || !current.isActive() || wrongLoop) {
            play(layer, id, anims, anims.hold(), HOLD_FADE, Ease.INOUTSINE);
            return;
        }
        // Hand back to the hold while the strike is still running. A fade only
        // captures the outgoing pose if that animation is still active, so
        // waiting for the strike to finish would blend out of the vanilla pose
        // instead of out of the strike's last frame - a visible pop.
        if (playing != null && anims.isStrike(playing)
                && current instanceof KeyframeAnimationPlayer keyframes
                && keyframes.getCurrentTick() >= keyframes.getStopTick() - HOLD_FADE) {
            play(layer, id, anims, anims.hold(), HOLD_FADE, Ease.INOUTSINE);
        }
    }

    private static void play(ModifierLayer<IAnimation> layer, UUID player, HeldAnims anims, ResourceLocation id,
            int fadeTicks, Ease ease) {
        IPlayable animation = PlayerAnimationRegistry.getAnimation(id);
        if (animation == null) {
            if (MISSING.add(id)) {
                LOG.warn("Animation {} did not load - check assets/{}/player_animations/ and that the file's"
                        + " root-level \"name\" matches the path", id, id.getNamespace());
            }
            return;
        }
        IAnimation played = animation.playAnimation();
        if (played instanceof KeyframeAnimationPlayer keyframes) {
            // Animations are third-person-only unless they say otherwise. THIRD_PERSON_MODEL
            // draws the real arms in the first-person pass so the same pose serves both
            // views - unless First-person Model is installed, which already does that.
            keyframes.setFirstPersonMode(THIRD_PERSON_ONLY.contains(id) ? FirstPersonMode.NONE : firstPersonMode())
                    .setFirstPersonConfiguration(new FirstPersonConfiguration()
                            .setShowRightArm(true)
                            .setShowLeftArm(anims.twoHanded() || TWO_HANDED.contains(id))
                            .setShowRightItem(true)
                            .setShowLeftItem(false));
        }
        layer.replaceAnimationWithFade(fade(fadeTicks, ease), played);
        PLAYING.put(player, id);
    }

    private static AbstractFadeModifier fade(int ticks, Ease ease) {
        return AbstractFadeModifier.standardFadeIn(ticks, ease);
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layerOf(AbstractClientPlayer player) {
        IAnimation raw = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        return raw instanceof ModifierLayer<?> ? (ModifierLayer<IAnimation>) raw : null;
    }

    private HeldAnimationHandler() {
    }
}
