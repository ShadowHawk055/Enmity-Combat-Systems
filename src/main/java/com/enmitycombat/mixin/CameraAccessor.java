package com.enmitycombat.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPosition") void enmitycombat$setPosition(Vec3 pos);
    @Invoker("setRotation") void enmitycombat$setRotation(float yaw, float pitch);
    @Accessor("detached") void enmitycombat$setDetached(boolean detached);
}
