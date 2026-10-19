package com.enmitycombat.mixin;

import com.enmitycombat.client.cutscene.CutsceneManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First of TWO places the cutscene camera is applied (the other is GameRendererMixin).
 * Camera mods (Shoulder Surfing, Focus, Leawind's Third Person) may cancel Camera.setup early, in which case this
 * RETURN hook never runs; GameRendererMixin then does the job. Both calls are idempotent within one frame.
 */
@Mixin(value = Camera.class, priority = 2000)
public abstract class CameraMixin {
    @Inject(method = "setup", at = @At("RETURN"), require = 0)
    private void enmitycombat$cutscene(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        CutsceneManager.apply((Camera) (Object) this, partial);
    }
}
