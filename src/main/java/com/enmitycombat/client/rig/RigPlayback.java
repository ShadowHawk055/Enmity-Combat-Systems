package com.enmitycombat.client.rig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Two layers per player: a looping BASE animation chosen by state (rig.json idle / idle_drawn / idle_transformed),
 * and a one-shot OVERLAY started by the skill's "animation" action. The overlay only replaces the channels it
 * actually animates, so a skill animation doesn't have to repeat "hide the other sword" keyframes.
 */
public final class RigPlayback {
    public static final class Pose { public float[] pos, rot, scale; }
    private record Active(RigData.Anim anim, long start) {}

    private static final Map<UUID, Active> ONE_SHOT = new HashMap<>();

    /** Called whenever any player animation id is played (see AnimBridge). Unknown ids clear the overlay. */
    public static void trigger(AbstractClientPlayer p, @Nullable ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        RigData.Anim a = id == null ? null : RigData.ANIMS.get(id.toString());
        if (a == null || mc.level == null) ONE_SHOT.remove(p.getUUID());
        else ONE_SHOT.put(p.getUUID(), new Active(a, mc.level.getGameTime()));
    }

    public static void clear() { ONE_SHOT.clear(); }

    static Map<String, Pose> evaluate(UUID id, int flags, RigData.Settings s, float partial) {
        Map<String, Pose> out = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return out;
        float now = mc.level.getGameTime() + partial;

        boolean transformed = (flags & 2) != 0, drawn = (flags & 4) != 0;
        ResourceLocation baseId = (transformed && s.idleTransformed != null) ? s.idleTransformed
                : (drawn && s.idleDrawn != null) ? s.idleDrawn : s.idle;
        RigData.Anim base = baseId == null ? null : RigData.ANIMS.get(baseId.toString());
        if (base != null) put(out, base, now / 20f, true);

        Active a = ONE_SHOT.get(id);
        if (a != null) {
            float t = (now - a.start) / 20f;
            if (a.anim.loop == 0 && t > a.anim.length) ONE_SHOT.remove(id);
            else put(out, a.anim, t, false);
        }
        return out;
    }

    private static void put(Map<String, Pose> out, RigData.Anim anim, float t, boolean forceLoop) {
        if ((forceLoop || anim.loop == 1) && anim.length > 0) t = t % anim.length;
        else if (anim.loop == 2 && t > anim.length) t = anim.length;
        for (var e : anim.bones.entrySet()) {
            Pose p = out.computeIfAbsent(e.getKey(), k -> new Pose());
            RigData.BoneAnim b = e.getValue();
            if (b.pos != null) p.pos = b.pos.sample(t);
            if (b.rot != null) p.rot = b.rot.sample(t);
            if (b.scale != null) p.scale = b.scale.sample(t);
        }
    }
    private RigPlayback() {}
}
