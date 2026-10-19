package com.enmitycombat.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Camera-mod compatibility lives here: we never read the player's rotation for aiming. We ray-cast from the CAMERA
 * (whatever Shoulder Surfing / a lock-on mod made it do) and send "from player's eyes to that point" to the server.
 * Lock-on mods: register an AimProvider that returns direction-to-locked-target and it takes priority.
 */
public final class AimResolver {
    public interface AimProvider { @Nullable Vec3 aim(Minecraft mc); }
    private static final List<AimProvider> PROVIDERS = new ArrayList<>();
    public static void register(AimProvider p) { PROVIDERS.add(0, p); }

    public static Vec3 resolve(Minecraft mc) {
        for (AimProvider p : PROVIDERS) { Vec3 v = p.aim(mc); if (v != null && v.lengthSqr() > 1e-6) return v.normalize(); }
        return cameraAim(mc);
    }

    private static Vec3 cameraAim(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_FRONT) return p.getLookAngle();   // camera looks AT the player
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 look = new Vec3(cam.getLookVector());
        Vec3 eye = p.getEyePosition();
        // start the ray at the point on the camera ray closest to the player, so a wall behind the player
        // (3rd person) can't swallow the ray
        double along = Math.max(0, eye.subtract(cam.getPosition()).dot(look));
        Vec3 from = cam.getPosition().add(look.scale(along));
        Vec3 to = from.add(look.scale(64));

        BlockHitResult bhit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = bhit.getType() == HitResult.Type.MISS ? to : bhit.getLocation();
        EntityHitResult ehit = ProjectileUtil.getEntityHitResult(mc.level, p, from, end, new AABB(from, end).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable() && e != p);
        if (ehit != null) end = ehit.getLocation();

        Vec3 dir = end.subtract(eye);
        return dir.lengthSqr() < 1e-4 ? p.getLookAngle() : dir.normalize();
    }

    /** Dodge direction: movement keys relative to the camera yaw; no keys = away from where the camera looks. */
    public static Vec3 dodgeDir(Minecraft mc) {
        float yaw = mc.gameRenderer.getMainCamera().getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        float f = mc.player.input.forwardImpulse, l = mc.player.input.leftImpulse;
        Vec3 d = fwd.scale(f).add(left.scale(l));
        return d.lengthSqr() < 1e-4 ? fwd.scale(-1) : d.normalize();
    }
    private AimResolver() {}
}
