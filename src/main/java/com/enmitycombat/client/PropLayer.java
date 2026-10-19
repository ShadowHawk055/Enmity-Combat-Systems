package com.enmitycombat.client;

import com.enmitycombat.EnmityCombat;
import com.enmitycombat.data.CharacterDef;
import com.enmitycombat.data.DataManager;
import com.enmitycombat.data.PropDef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Draws a character's props on any player we can see (third person; props are not drawn in first person).
 *
 * Frame for non-hand attach points ("back", "hip_left", "hip_right", "head", "chest"):
 *   x+ = character's LEFT, y+ = UP, z+ = FORWARD, origin = the body part's centre-ish; units are pixels.
 * Frame for "right_hand" / "left_hand": exactly vanilla's held-item frame, so rotation [0,0,0] looks like a normal held item.
 * Rotations are applied Z, then Y, then X.
 */
public class PropLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Map<PropDef, ItemStack> STACKS = new WeakHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static ItemStack dummy;

    public PropLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float partial, float age, float headYaw, float headPitch) {
        if (p.isInvisible() || p.isSpectator()) return;
        ClientVisuals.Visual v = ClientVisuals.get(p.getUUID());
        if (v == null) return;
        CharacterDef ch = DataManager.CHARACTERS.get(v.character());
        if (ch == null || (ch.props.isEmpty() && ch.rig == null)) return;

        boolean combat = (v.flags() & 1) != 0, transformed = (v.flags() & 2) != 0, drawn = (v.flags() & 4) != 0;
        for (PropDef pr : ch.props) {
            if (!pr.visible(combat, transformed, drawn)) continue;
            draw(ps, buf, light, p, pr, pr.xform(drawn));
        }
        com.enmitycombat.client.rig.RigRenderer.render(ps, buf, light, p, getParentModel(), ch, v.flags(), partial);
    }

    private void draw(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, PropDef pr, PropDef.Xform t) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        ItemStack stack;
        BakedModel baked;
        if (pr.model != null) {
            baked = mc.getModelManager().getModel(pr.model);
            if (baked == mc.getModelManager().getMissingModel()) {
                if (WARNED.add(pr.model.toString())) EnmityCombat.LOGGER.warn("[Enmity] Prop model '{}' not found (expected assets/{}/models/{}.json). Press F3+T after adding it.",
                        pr.model, pr.model.getNamespace(), pr.model.getPath());
                return;
            }
            if (dummy == null) dummy = new ItemStack(Items.STICK);
            stack = dummy;
        } else {
            stack = STACKS.computeIfAbsent(pr, d -> {
                var it = ForgeRegistries.ITEMS.getValue(d.item);
                ItemStack s = it == null ? ItemStack.EMPTY : new ItemStack(it);
                if (!s.isEmpty() && d.customModelData >= 0) s.getOrCreateTag().putInt("CustomModelData", d.customModelData);
                return s;
            });
            if (stack.isEmpty()) {
                if (WARNED.add(String.valueOf(pr.item))) EnmityCombat.LOGGER.warn("[Enmity] Prop item '{}' does not exist", pr.item);
                return;
            }
            baked = ir.getModel(stack, p.level(), p, p.getId());
        }

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        ItemDisplayContext ctx = ItemDisplayContext.NONE;
        boolean left = false;

        ps.pushPose();
        try {
            switch (t.attach()) {
                case "right_hand", "left_hand" -> {
                    left = t.attach().equals("left_hand");
                    model.translateToHand(left ? HumanoidArm.LEFT : HumanoidArm.RIGHT, ps);   // same steps as vanilla's ItemInHandLayer
                    ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
                    ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                    ps.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
                    ctx = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                }
                case "back" -> { upright(ps, model.body); ps.translate(0, -6 / 16f, -3 / 16f); }
                case "chest" -> { upright(ps, model.body); ps.translate(0, -4 / 16f, 3 / 16f); }
                case "hip_left" -> { upright(ps, model.body); ps.translate(5 / 16f, -10 / 16f, 0); }
                case "hip_right" -> { upright(ps, model.body); ps.translate(-5 / 16f, -10 / 16f, 0); }
                case "head" -> { upright(ps, model.head); ps.translate(0, 9 / 16f, 0); }
                default -> {
                    if (WARNED.add("attach:" + t.attach())) EnmityCombat.LOGGER.warn("[Enmity] Unknown prop attach point '{}'", t.attach());
                    return;
                }
            }
            ps.translate(t.offset().x / 16.0, t.offset().y / 16.0, t.offset().z / 16.0);
            ps.mulPose(Axis.ZP.rotationDegrees((float) t.rotation().z));
            ps.mulPose(Axis.YP.rotationDegrees((float) t.rotation().y));
            ps.mulPose(Axis.XP.rotationDegrees((float) t.rotation().x));
            ps.scale(t.scale(), t.scale(), t.scale());
            ir.render(stack, ctx, left, ps, buf, light, OverlayTexture.NO_OVERLAY, baked);
        } finally {
            ps.popPose();
        }
    }

    /** Move to the body part, then rotate 180 degrees about X so +y is up and +z is forward (x+ = character's left). */
    private static void upright(PoseStack ps, ModelPart part) {
        part.translateAndRotate(ps);
        ps.mulPose(Axis.XP.rotationDegrees(180.0F));
    }
}
