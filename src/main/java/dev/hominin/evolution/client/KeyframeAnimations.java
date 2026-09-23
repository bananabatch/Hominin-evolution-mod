package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Plays the players' animation files on band members.
 *
 * <p>Player Animator only animates players, but the files it reads are plain keyframes
 * of joint rotations - so this reads the very same files and poses a mob's model with
 * them. Band members climb, display and swing their weapons exactly as the player does,
 * from one set of animation data.
 *
 * <p>The files' {@code body} is not the torso. Player Animator moves the WHOLE player with it -
 * leans, twists and shifts everything about a point at the hips - so it is kept apart here as a
 * {@link BodyPose} for the renderer to apply the same way, rather than bent into the torso alone.
 */
public final class KeyframeAnimations extends SimpleJsonResourceReloadListener {
    public enum Part {
        HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG
    }

    /** A keyframe's value: degrees for a rotation, blocks for the body's shift. */
    private record Key(float tick, float value, String easing) {
    }

    /**
     * The whole body's pose at a moment: rotations in radians, the shift in blocks - the same
     * numbers, meant the same way, as Player Animator's {@code body}.
     */
    public record BodyPose(float pitch, float yaw, float roll, float x, float y, float z) {
    }

    /**
     * One animation: for each part, the keyframes of each of its axes - pitch, yaw and roll, and
     * for the body also its x, y and z.
     */
    public record Animation(int length, boolean loop, Map<Part, List<Key>[]> tracks) {
        private float time(float tick) {
            return loop && length > 0 ? tick % length : Math.min(tick, length);
        }

        /** Poses the model's limbs and head. The body is the renderer's - see {@link #bodyPose}. */
        public void apply(HumanoidModel<?> model, float tick) {
            float t = time(tick);
            for (var entry : tracks.entrySet()) {
                if (entry.getKey() == Part.BODY) {
                    continue;
                }
                ModelPart part = partOf(model, entry.getKey());
                List<Key>[] axes = entry.getValue();
                if (!axes[0].isEmpty()) {
                    part.xRot = sample(axes[0], t) * Mth.DEG_TO_RAD;
                }
                if (!axes[1].isEmpty()) {
                    part.yRot = sample(axes[1], t) * Mth.DEG_TO_RAD;
                }
                if (!axes[2].isEmpty()) {
                    part.zRot = sample(axes[2], t) * Mth.DEG_TO_RAD;
                }
            }
        }

        /** How the whole body leans, turns and shifts at this tick, or null if it never does. */
        @Nullable
        public BodyPose bodyPose(float tick) {
            List<Key>[] axes = tracks.get(Part.BODY);
            if (axes == null) {
                return null;
            }
            float t = time(tick);
            return new BodyPose(valueAt(axes[0], t) * Mth.DEG_TO_RAD, valueAt(axes[1], t) * Mth.DEG_TO_RAD,
                    valueAt(axes[2], t) * Mth.DEG_TO_RAD, valueAt(axes[3], t), valueAt(axes[4], t),
                    valueAt(axes[5], t));
        }
    }

    /** An axis nothing keys stays at rest. */
    private static float valueAt(List<Key> keys, float t) {
        return keys.isEmpty() ? 0.0F : sample(keys, t);
    }

    private static final Map<ResourceLocation, Animation> ANIMATIONS = new HashMap<>();

    public KeyframeAnimations() {
        super(new Gson(), "player_animations");
    }

    @Nullable
    public static Animation get(ResourceLocation id) {
        return ANIMATIONS.get(id);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        ANIMATIONS.clear();
        for (var file : files.entrySet()) {
            try {
                ANIMATIONS.put(file.getKey(), parse(file.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                dev.hominin.evolution.HomininEvolutionMod.LOGGER.warn("Could not read animation {}: {}",
                        file.getKey(), e.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Animation parse(JsonObject root) {
        JsonObject emote = root.getAsJsonObject("emote");
        boolean degrees = emote.has("degrees") && emote.get("degrees").getAsBoolean();
        int length = emote.get("endTick").getAsInt();
        boolean loop = emote.has("isLoop") && emote.get("isLoop").getAsBoolean();
        Map<Part, List<Key>[]> tracks = new EnumMap<>(Part.class);
        for (JsonElement moveElement : emote.getAsJsonArray("moves")) {
            JsonObject move = moveElement.getAsJsonObject();
            float tick = move.get("tick").getAsFloat();
            String easing = move.has("easing") ? move.get("easing").getAsString() : "linear";
            for (Part part : Part.values()) {
                JsonObject values = partJson(move, part);
                if (values == null) {
                    continue;
                }
                List<Key>[] axes = tracks.computeIfAbsent(part, p -> {
                    List<Key>[] made = new List[p == Part.BODY ? 6 : 3];
                    for (int i = 0; i < made.length; i++) {
                        made[i] = new ArrayList<>();
                    }
                    return made;
                });
                String[] names = {"pitch", "yaw", "roll", "x", "y", "z"};
                for (int axis = 0; axis < axes.length; axis++) {
                    if (values.has(names[axis])) {
                        float value = values.get(names[axis]).getAsFloat();
                        boolean rotation = axis < 3;
                        axes[axis].add(new Key(tick, rotation && !degrees ? value * Mth.RAD_TO_DEG : value, easing));
                    }
                }
            }
        }
        for (List<Key>[] axes : tracks.values()) {
            for (List<Key> keys : axes) {
                keys.sort((a, b) -> Float.compare(a.tick(), b.tick()));
            }
        }
        return new Animation(length, loop, tracks);
    }

    @Nullable
    private static JsonObject partJson(JsonObject move, Part part) {
        String name = switch (part) {
            case HEAD -> "head";
            case BODY -> move.has("torso") ? "torso" : "body";
            case RIGHT_ARM -> "rightArm";
            case LEFT_ARM -> "leftArm";
            case RIGHT_LEG -> "rightLeg";
            case LEFT_LEG -> "leftLeg";
        };
        return move.has(name) && move.get(name).isJsonObject() ? move.getAsJsonObject(name) : null;
    }

    private static float sample(List<Key> keys, float t) {
        Key first = keys.get(0);
        if (t <= first.tick()) {
            return first.value();
        }
        for (int i = 1; i < keys.size(); i++) {
            Key to = keys.get(i);
            if (t <= to.tick()) {
                Key from = keys.get(i - 1);
                float span = to.tick() - from.tick();
                float progress = span <= 0.0F ? 1.0F : (t - from.tick()) / span;
                return Mth.lerp(ease(to.easing(), progress), from.value(), to.value());
            }
        }
        return keys.get(keys.size() - 1).value();
    }

    /** The easings the mod's animation files use, by the names Emotecraft gives them. */
    private static float ease(String name, float x) {
        String key = name.toLowerCase().replace("ease", "");
        return switch (key) {
            case "inoutsine" -> -(Mth.cos(Mth.PI * x) - 1.0F) / 2.0F;
            case "insine" -> 1.0F - Mth.cos(x * Mth.PI / 2.0F);
            case "outsine" -> Mth.sin(x * Mth.PI / 2.0F);
            case "inquad" -> x * x;
            case "outquad" -> 1.0F - (1.0F - x) * (1.0F - x);
            case "incubic" -> x * x * x;
            case "outcubic" -> 1.0F - (float) Math.pow(1.0F - x, 3);
            case "inquart" -> x * x * x * x;
            case "outquart" -> 1.0F - (float) Math.pow(1.0F - x, 4);
            default -> x;
        };
    }

    private static ModelPart partOf(HumanoidModel<?> model, Part part) {
        return switch (part) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
        };
    }
}
