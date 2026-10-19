package com.enmitycombat.client.rig;

import com.enmitycombat.data.CharacterDef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class RigRenderer {
    public static void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p,
                              PlayerModel<AbstractClientPlayer> model, CharacterDef ch, int flags, float partial) {
        if (ch.rig == null) return;
        RigInstance inst = RigData.instance(ch.rig);
        if (inst == null) return;
        inst.apply(model, RigPlayback.evaluate(p.getUUID(), flags, inst.settings, partial));
        inst.root.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(inst.texture)), light, OverlayTexture.NO_OVERLAY);
    }
    private RigRenderer() {}
}
