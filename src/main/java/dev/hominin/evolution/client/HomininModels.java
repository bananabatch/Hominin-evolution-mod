package dev.hominin.evolution.client;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Drawing players as the hominin they are. On by default, and switched off per client
 * - in the mod's config screen or with {@code /hominin_model} - without touching
 * anyone else's view.
 *
 * <p>Three things change: the skin, a heavier brow and jaw built onto the head, and a
 * shorter body. Everything else about the player model stays vanilla, which is what
 * keeps held items, armour and every animation working on top of it.
 */
public final class HomininModels {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLED = builder
                .comment("Draw players as the hominin stage they are at. Turn off to see ordinary skins.")
                .define("hominin_player_models", true);
        SPEC = builder.build();
    }

    /** How a stage is drawn. Stages with no entry use the player's own skin. */
    public record Look(ResourceLocation skin, ResourceLocation features, ModelLayerLocation layer, float scale) {
    }

    public static final ModelLayerLocation AUSTRALOPITHECUS_LAYER = layer("australopithecus_features");
    public static final ModelLayerLocation HABILIS_LAYER = layer("homo_habilis_features");

    /**
     * Lucy stood about 1.1 m; habilis perhaps 1.3. Scaled well short of that - a
     * player a third smaller than the blocks around them reads as a child.
     */
    private static final Map<ResourceLocation, Look> LOOKS = Map.of(
            stage("australopithecus"), look("australopithecus", AUSTRALOPITHECUS_LAYER, 0.86F),
            // No model of its own: Ardipithecus is drawn as Australopithecus.
            stage("ardipithecus"), look("australopithecus", AUSTRALOPITHECUS_LAYER, 0.86F),
            stage("homo_habilis"), look("homo_habilis", HABILIS_LAYER, 0.92F));

    private static Look look(String name, ModelLayerLocation layer, float scale) {
        return new Look(texture("textures/entity/hominin/" + name + ".png"),
                texture("textures/entity/hominin/" + name + "_features.png"), layer, scale);
    }

    public static boolean enabled() {
        return SPEC.isLoaded() && ENABLED.get();
    }

    /** The look for this player right now, or null to draw them normally. */
    @Nullable
    public static Look lookFor(Player player) {
        if (!enabled()) {
            return null;
        }
        ResourceLocation stage = ClientSync.stageOf(player.getUUID());
        return stage == null ? null : LOOKS.get(stage);
    }

    /** Swaps the skin, keeping the player's own cape and elytra. */
    public static PlayerSkin skinFor(AbstractClientPlayer player, PlayerSkin original) {
        Look look = lookFor(player);
        if (look == null) {
            return original;
        }
        return new PlayerSkin(look.skin(), null, original.capeTexture(), original.elytraTexture(),
                PlayerSkin.Model.WIDE, original.secure());
    }

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AUSTRALOPITHECUS_LAYER, HomininFeaturesLayer::australopithecus);
        event.registerLayerDefinition(HABILIS_LAYER, HomininFeaturesLayer::habilis);
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new HomininFeaturesLayer<>(renderer, event.getEntityModels(), LOOKS.values(),
                        HomininModels::lookFor));
            }
        }
    }

    public static Collection<Look> allLooks() {
        return LOOKS.values();
    }

    /**
     * A stage's look regardless of the player-model setting - band members are always
     * drawn as hominins. Stages with no look of their own borrow the latest one there is.
     */
    public static Look lookForStage(ResourceLocation stage) {
        Look look = LOOKS.get(stage);
        return look != null ? look : LOOKS.get(stage("homo_habilis"));
    }

    /**
     * Shrinks the whole body about the feet. Skipped for your own body in first person,
     * where mods that draw it would otherwise sink it below the camera.
     */
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        float scale = scaleFor(event.getEntity());
        if (scale != 1.0F) {
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.scale(scale, scale, scale);
        }
    }

    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (scaleFor(event.getEntity()) != 1.0F) {
            event.getPoseStack().popPose();
        }
    }

    private static float scaleFor(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return 1.0F;
        }
        Look look = lookFor(player);
        return look == null ? 1.0F : look.scale();
    }

    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hominin_model").executes(ctx -> {
            boolean now = !ENABLED.get();
            ENABLED.set(now);
            SPEC.save();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(now
                        ? "Hominin player models on."
                        : "Hominin player models off - everyone looks like their own skin."), false);
            }
            return 1;
        }));
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(texture("player_features"), name);
    }

    private static ResourceLocation stage(String path) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, path);
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, path);
    }

    private HomininModels() {
    }
}
