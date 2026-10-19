package com.enmitycombat.client;

import com.enmitycombat.EnmityCombat;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * ALL PlayerAnimator calls live in this one class. If the library's API changes, only this file needs fixing.
 * Animations are Emotecraft-format JSON in assets/<ns>/player_animations/<name>.json, referenced as "<ns>:<name>".
 * Missing animations are skipped (logged at debug) so the mod works before you have made any.
 */
public final class AnimBridge {
    private static final ResourceLocation LAYER = EnmityCombat.id("combat");
    private static final java.util.Set<ResourceLocation> WARNED = new java.util.HashSet<>();

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 100, player -> new ModifierLayer<>());
    }

    @SuppressWarnings("unchecked")
    public static void play(AbstractClientPlayer player, @Nullable ResourceLocation anim) {
        com.enmitycombat.client.rig.RigPlayback.trigger(player, anim);   // rig props animate from the same id
        var layer = (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (layer == null) return;
        if (anim == null) { layer.setAnimation(null); return; }
        var kf = PlayerAnimationRegistry.getAnimation(anim);
        if (kf == null) {
            if (WARNED.add(anim)) EnmityCombat.LOGGER.warn("[Enmity] Player animation '{}' not found (expected assets/{}/player_animations/{}.json) - skipping",
                    anim, anim.getNamespace(), anim.getPath());
            return;
        }
        layer.setAnimation(new KeyframeAnimationPlayer(kf));
    }
    private AnimBridge() {}
}
