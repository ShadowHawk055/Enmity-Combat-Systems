package com.enmitycombat.client;

import com.enmitycombat.client.cutscene.CutsceneManager;
import com.enmitycombat.combat.CombatData;
import com.enmitycombat.data.CharacterDef;
import com.enmitycombat.data.Slot;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Minimal placeholder HUD: 6 skill squares with cooldown fill + energy bar. Replace with your own art later. */
public final class HudOverlay {
    private static final Slot[] SLOTS = {Slot.SKILL1, Slot.SKILL2, Slot.SKILL3, Slot.SPECIAL, Slot.HEAVY, Slot.ULTIMATE};
    private static final KeyMapping[] KEYS = {KeyBindings.SKILL1, KeyBindings.SKILL2, KeyBindings.SKILL3, KeyBindings.SPECIAL, KeyBindings.HEAVY, KeyBindings.ULTIMATE};

    public static final IGuiOverlay HUD = (gui, g, partial, w, h) -> {
        Minecraft mc = gui.getMinecraft();
        if (CutsceneManager.isActive()) { CutsceneManager.drawLetterbox(g, w, h); return; }
        CombatData d = ClientState.data;
        if (!d.isActive() || mc.options.hideGui) return;
        CharacterDef ch = d.character();
        if (ch == null) return;

        int x0 = w / 2 + 98, y = h - 24, size = 20, gap = 2;
        // energy bar
        float frac = ch.maxEnergy <= 0 ? 0 : d.energy / ch.maxEnergy;
        int barW = SLOTS.length * (size + gap) - gap;
        g.fill(x0 - 1, y - 9, x0 + barW + 1, y - 3, 0xFF000000);
        g.fill(x0, y - 8, x0 + (int) (barW * frac), y - 4, frac >= 1f ? 0xFFFFD34D : 0xFF4DA6FF);
        if (d.transformed()) g.drawString(mc.font, "TRANSFORMED " + (d.transformTicks / 20) + "s", x0, y - 19, 0xFFFFD34D);
        else g.drawString(mc.font, ch.name, x0, y - 19, 0xFFFFFFFF);

        for (int i = 0; i < SLOTS.length; i++) {
            int x = x0 + i * (size + gap);
            ResourceLocation id = ch.resolve(SLOTS[i], d.transformed());
            g.fill(x, y, x + size, y + size, 0xAA101018);
            CombatData.Cd cd = id == null ? null : d.cooldowns.get(id);
            if (cd != null) {
                int fill = (int) (size * Math.min(1f, cd.remaining / cd.total));
                g.fill(x, y, x + size, y + fill, 0xCC707070);
                g.drawCenteredString(mc.font, String.valueOf((int) Math.ceil(cd.remaining / 20f)), x + size / 2, y + 6, 0xFFFFFFFF);
            } else {
                g.drawCenteredString(mc.font, KEYS[i].getTranslatedKeyMessage().getString(), x + size / 2, y + 6, 0xFFE0E0E0);
            }
        }
        if (d.blocking) g.drawCenteredString(mc.font, "BLOCK", w / 2, h / 2 + 24, 0xFF8FD3FF);
    };
    private HudOverlay() {}
}
