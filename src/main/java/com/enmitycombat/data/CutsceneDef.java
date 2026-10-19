package com.enmitycombat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Camera path. Positions are LOCAL to the caster when the cutscene starts:
 * x = right, y = up from feet, z = forward.
 */
public class CutsceneDef {
    public enum Ease {
        LINEAR(t -> t), EASE_IN(t -> t * t), EASE_OUT(t -> 1 - (1 - t) * (1 - t)),
        EASE_IN_OUT(t -> t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2);
        private final UnaryOperator<Float> fn;
        Ease(UnaryOperator<Float> fn) { this.fn = fn; }
        public float apply(float t) { return fn.apply(t); }
    }

    public record Keyframe(int tick, Vec3 pos, @Nullable Vec3 lookAt, float yaw, float pitch, float fov, Ease ease) {}

    public final int duration;
    public final boolean hideHud;
    public final boolean letterbox;
    public final int blendIn;     // ticks to ease from the live camera into the first keyframe (0 = hard cut)
    public final int blendOut;    // ticks to ease back to the live camera (default 8; hides camera-mod smoothing / snaps)
    public final List<Keyframe> keyframes = new ArrayList<>();

    private CutsceneDef(JsonObject o) {
        this.duration = GsonHelper.getAsInt(o, "duration", 60);
        this.hideHud = GsonHelper.getAsBoolean(o, "hide_hud", true);
        this.letterbox = GsonHelper.getAsBoolean(o, "letterbox", true);
        this.blendIn = GsonHelper.getAsInt(o, "blend_in", 0);
        this.blendOut = GsonHelper.getAsInt(o, "blend_out", 8);
        JsonArray arr = GsonHelper.getAsJsonArray(o, "keyframes");
        for (JsonElement el : arr) {
            JsonObject k = el.getAsJsonObject();
            float yaw = 0, pitch = 0;
            if (k.has("rot")) { JsonArray r = k.getAsJsonArray("rot"); yaw = r.get(0).getAsFloat(); pitch = r.get(1).getAsFloat(); }
            keyframes.add(new Keyframe(
                    GsonHelper.getAsInt(k, "tick"), vec(k.getAsJsonArray("pos")),
                    k.has("look_at") ? vec(k.getAsJsonArray("look_at")) : null,
                    yaw, pitch, GsonHelper.getAsFloat(k, "fov", Float.NaN),
                    Ease.valueOf(GsonHelper.getAsString(k, "ease", "ease_in_out").toUpperCase(Locale.ROOT))));
        }
        if (keyframes.isEmpty()) throw new IllegalArgumentException("cutscene needs at least one keyframe");
        keyframes.sort(Comparator.comparingInt(Keyframe::tick));
    }

    private static Vec3 vec(JsonArray a) { return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()); }

    public static CutsceneDef parse(ResourceLocation id, JsonObject o) { return new CutsceneDef(o); }
}
