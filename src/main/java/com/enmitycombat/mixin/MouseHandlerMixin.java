package com.enmitycombat.mixin;

import com.enmitycombat.client.cutscene.CutsceneManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No mouse-look during a cutscene. Stops vanilla AND camera mods (they all turn through this path). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void enmitycombat$freezeLook(CallbackInfo ci) {
        if (CutsceneManager.blocksLook()) {
            accumulatedDX = 0;
            accumulatedDY = 0;
            ci.cancel();
        }
    }
}
