package com.enmitycombat.client.cutscene;

import com.enmitycombat.client.ModCompat;
import com.enmitycombat.data.CutsceneDef;
import com.enmitycombat.data.CutsceneDef.Keyframe;
import com.enmitycombat.data.DataManager;
import com.enmitycombat.mixin.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Plays camera paths, and plays nicely with Shoulder Surfing / Focus / Leawind's Third Person:
 *  - the camera is overridden AFTER those mods have positioned it (two hook points, idempotent per frame);
 *  - final yaw/pitch/FOV are re-asserted by LOWEST-priority Forge events in ClientEvents;
 *  - mouse-look is frozen by MouseHandlerMixin, movement input is zeroed by ClientEvents;
 *  - if the player was in first person, the camera type is switched to third person for the cutscene and restored;
 *  - blend_in / blend_out ease between the mod's live camera and the cutscene (no hard snap when it ends).
 */
public final class CutsceneManager {
    public record Pose(Vec3 pos, float yaw, float pitch, float fov) {}

    private static CutsceneDef def;
    private static int tick;
    private static Vec3 origin = Vec3.ZERO;
    private static float originYaw;
    private static CameraType prevCamera;

    private static Pose lastPose;
    private static int blendLeft, blendTotal;

    private static long frameKey = Long.MIN_VALUE;
    private static Pose applied;
    private static float appliedAlpha = 1f;

    public static boolean isActive() { return def != null; }
    public static boolean blocksLook() { return def != null; }
    public static boolean hidesHud() { return def != null && def.hideHud; }
    public static boolean isOverriding() { return def != null || blendLeft > 0; }

    public static void start(ResourceLocation id) {
        CutsceneDef c = DataManager.CUTSCENES.get(id);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (c == null || p == null) return;
        def = c; tick = 0; blendLeft = 0; lastPose = null; frameKey = Long.MIN_VALUE;
        origin = p.position(); originYaw = p.getYRot();
        if (mc.options.getCameraType().isFirstPerson()) {
            prevCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    public static void stop() {
        if (def != null && def.blendOut > 0 && lastPose != null) { blendLeft = blendTotal = def.blendOut; }
        def = null;
        Minecraft mc = Minecraft.getInstance();
        if (prevCamera != null) { mc.options.setCameraType(prevCamera); prevCamera = null; }
    }

    public static void tick() {
        if (blendLeft > 0) blendLeft--;
        if (def == null) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.isAlive() || ++tick >= def.duration) stop();
    }

    /** Only used when no camera mod is installed (vanilla camera); camera mods + MouseHandlerMixin handle it otherwise. */
    public static void lockPlayerRotation(LocalPlayer p) {
        if (ModCompat.cameraModLoaded()) return;
        p.setYRot(originYaw); p.yRotO = originYaw;
    }

    // ------------------------------------------------------------------------------------------ applying to the camera
    /** Called from two mixin points. Safe to call repeatedly in one frame. */
    public static void apply(Camera cam, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!isOverriding() || mc.level == null) { applied = null; return; }
        long key = mc.level.getGameTime() * 1000L + (long) (partial * 1000f);
        if (key != frameKey) {          // first call this frame: remember what the camera mod produced, compute our pose
            frameKey = key;
            applied = compute(partial, new Pose(cam.getPosition(), cam.getYRot(), cam.getXRot(), Float.NaN));
        }
        if (applied == null) return;
        CameraAccessor acc = (CameraAccessor) cam;
        acc.enmitycombat$setPosition(applied.pos());
        acc.enmitycombat$setRotation(applied.yaw(), applied.pitch());
        acc.enmitycombat$setDetached(true);      // draws the player model even if the base camera was first person
    }

    @Nullable public static Pose appliedPose() { return isOverriding() ? applied : null; }

    /** Final FOV for this frame: cutscene FOV (blended), or the incoming one. */
    public static float fov(float incoming) {
        if (!isOverriding() || applied == null || Float.isNaN(applied.fov())) return incoming;
        return Mth.lerp(appliedAlpha, incoming, applied.fov());
    }

    private static Pose compute(float partial, Pose under) {
        if (def != null) {
            Pose p = pose(partial);
            float t = tick + partial;
            appliedAlpha = 1f;
            if (def.blendIn > 0 && t < def.blendIn) {
                appliedAlpha = smooth(t / def.blendIn);
                p = lerp(under, p, appliedAlpha);
                p = new Pose(p.pos(), p.yaw(), p.pitch(), pose(partial).fov());
            }
            lastPose = pose(partial);
            return p;
        }
        if (blendLeft > 0 && lastPose != null) {
            float progress = Mth.clamp((blendTotal - blendLeft + partial) / blendTotal, 0f, 1f);
            appliedAlpha = smooth(1f - progress);       // 1 = still cutscene, 0 = back to the camera mod
            Pose p = lerp(under, lastPose, appliedAlpha);
            return new Pose(p.pos(), p.yaw(), p.pitch(), lastPose.fov());
        }
        return null;
    }

    private static float smooth(float t) { t = Mth.clamp(t, 0f, 1f); return t * t * (3 - 2 * t); }

    private static Pose lerp(Pose a, Pose b, float f) {
        return new Pose(a.pos().lerp(b.pos(), f), Mth.rotLerp(f, a.yaw(), b.yaw()), Mth.lerp(f, a.pitch(), b.pitch()), b.fov());
    }

    // ------------------------------------------------------------------------------------------ path evaluation
    private static Vec3 toWorld(Vec3 local) {
        float r = originYaw * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(r), 0, Mth.cos(r)), right = new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
        return origin.add(right.scale(local.x)).add(0, local.y, 0).add(fwd.scale(local.z));
    }

    private static Pose poseOf(Keyframe k) {
        Vec3 pos = toWorld(k.pos());
        float yaw, pitch;
        if (k.lookAt() != null) {
            Vec3 d = toWorld(k.lookAt()).subtract(pos);
            yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        } else { yaw = originYaw + k.yaw(); pitch = k.pitch(); }
        return new Pose(pos, yaw, pitch, k.fov());
    }

    public static Pose pose(float partial) {
        List<Keyframe> k = def.keyframes;
        float t = tick + partial;
        if (t <= k.get(0).tick()) return poseOf(k.get(0));
        for (int i = 0; i < k.size() - 1; i++) {
            Keyframe a = k.get(i), b = k.get(i + 1);
            if (t >= a.tick() && t <= b.tick()) {
                float f = b.ease().apply((t - a.tick()) / (float) Math.max(1, b.tick() - a.tick()));
                Pose pa = poseOf(a), pb = poseOf(b);
                float fov = Float.isNaN(pa.fov()) || Float.isNaN(pb.fov()) ? pb.fov() : Mth.lerp(f, pa.fov(), pb.fov());
                return new Pose(pa.pos().lerp(pb.pos(), f), Mth.rotLerp(f, pa.yaw(), pb.yaw()), Mth.lerp(f, pa.pitch(), pb.pitch()), fov);
            }
        }
        return poseOf(k.get(k.size() - 1));
    }

    public static void drawLetterbox(GuiGraphics g, int w, int h) {
        if (def == null || !def.letterbox) return;
        float in = Math.min(1f, tick / 8f), out = Math.min(1f, (def.duration - tick) / 8f);
        int bar = (int) (h * 0.11f * Math.min(in, out));
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }
    private CutsceneManager() {}
}
