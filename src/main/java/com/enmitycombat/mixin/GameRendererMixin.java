package com.enmitycombat.mixin;

import com.enmitycombat.client.cutscene.CutsceneManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs right AFTER Camera.setup(...) returns inside renderLevel, whatever other mods did (or cancelled) inside it. */
@Mixin(value = GameRenderer.class, priority = 2000)
public abstract class GameRendererMixin {
    @Shadow private Camera mainCamera;

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.AFTER),
            require = 0)
    private void enmitycombat$afterCameraSetup(float partial, long nanoTime, PoseStack poseStack, CallbackInfo ci) {
        CutsceneManager.apply(this.mainCamera, partial);
    }
}
