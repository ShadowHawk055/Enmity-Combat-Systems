package com.enmitycombat.client;

import com.enmitycombat.data.*;
import com.enmitycombat.network.NetworkHandler;
import com.enmitycombat.network.SelectCharacterPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Codex: browse every loaded character, read the lore and all moves/passives, and equip one.
 * Everything shown comes from the JSON ("name", "title", "description" fields), nothing is hard-coded here.
 */
public class CodexScreen extends Screen {
    private record Line(FormattedCharSequence text, int color, int indent) {}

    private static final Slot[] ORDER = {Slot.SKILL1, Slot.SKILL2, Slot.SKILL3, Slot.SPECIAL, Slot.HEAVY, Slot.ULTIMATE, Slot.ULTIMATE_HOLD};
    private static final int GOLD = 0xFFFFD34D, WHITE = 0xFFFFFFFF, GRAY = 0xFFA8A8A8, BLUE = 0xFF8FD3FF, RED = 0xFFFF6B6B;
    private static final int LIST_W = 130, MARGIN = 12, ROW = 20, LINE_H = 10;

    private final List<CharacterDef> chars = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private int selected, cachedSel = -1, cachedW = -1;
    private double scroll, listScroll;
    private Button equip;

    public CodexScreen() { super(Component.literal("Codex")); }

    @Override
    protected void init() {
        chars.clear();
        chars.addAll(DataManager.CHARACTERS.values());
        chars.sort(Comparator.comparing(c -> c.name));
        for (int i = 0; i < chars.size(); i++) if (chars.get(i).id.equals(ClientState.data.character)) selected = i;
        cachedSel = -1;

        equip = Button.builder(Component.literal("Equip"), b -> {
            if (!chars.isEmpty()) NetworkHandler.CHANNEL.sendToServer(new SelectCharacterPacket(chars.get(selected).id));
        }).bounds(width - MARGIN - 90, height - 28, 90, 20).build();
        addRenderableWidget(equip);
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width - MARGIN - 96 - 90, height - 28, 90, 20).build());
    }

    @Override
    public void tick() {
        if (chars.isEmpty()) { equip.active = false; return; }
        boolean isEquipped = chars.get(selected).id.equals(ClientState.data.character);
        equip.active = !isEquipped;
        equip.setMessage(Component.literal(isEquipped ? "Equipped" : "Equip"));
    }

    // ------------------------------------------------------------------------------------------------ layout
    private int top() { return 30; }
    private int bottom() { return height - 36; }
    private int detailX() { return MARGIN + LIST_W + MARGIN; }
    private int detailW() { return width - MARGIN - detailX() - 8; }

    // ------------------------------------------------------------------------------------------------ content
    private void text(String s, int color, int indent) {
        for (FormattedCharSequence seq : font.split(Component.literal(s), Math.max(40, detailW() - indent))) lines.add(new Line(seq, color, indent));
    }
    private void gap() { lines.add(new Line(FormattedCharSequence.EMPTY, 0, 0)); }
    private void header(String s) { gap(); text("=== " + s + " ===", GOLD, 0); }

    private void skill(String label, ResourceLocation id) {
        SkillDef s = DataManager.SKILLS.get(id);
        if (s == null) { text(label + ": (missing skill " + id + ")", RED, 0); return; }
        text(label + ": " + s.name, WHITE, 0);
        List<String> stats = new ArrayList<>();
        if (s.cooldown > 0) stats.add(String.format("Cooldown %.1fs", s.cooldown / 20f));
        if (s.energyCost > 0) stats.add("Costs " + (int) s.energyCost + " energy");
        if (!stats.isEmpty()) text(String.join("  |  ", stats), BLUE, 8);
        if (!s.description.isEmpty()) text(s.description, GRAY, 8);
    }

    private static String label(Slot s) {
        return switch (s) {
            case SKILL1 -> "Skill 1"; case SKILL2 -> "Skill 2"; case SKILL3 -> "Skill 3";
            case SPECIAL -> "Special"; case HEAVY -> "Heavy Attack";
            case ULTIMATE -> "Ultimate (tap)"; case ULTIMATE_HOLD -> "Ultimate (hold)"; default -> s.key;
        };
    }

    private void moveset(CharacterDef.Moveset m) {
        if (!m.basic.isEmpty()) {
            text("Basic Attack - " + m.basic.size() + "-hit chain", GOLD, 0);
            for (int i = 0; i < m.basic.size(); i++) skill("  " + (i + 1), m.basic.get(i));
        }
        for (Slot s : ORDER) if (m.slots.containsKey(s)) skill(label(s), m.slots.get(s));
    }

    private void rebuild(CharacterDef c) {
        lines.clear();
        text(c.name, GOLD, 0);
        if (!c.title.isEmpty()) text(c.title, GRAY, 0);
        gap();
        if (!c.description.isEmpty()) { text(c.description, 0xFFE6E6E6, 0); gap(); }
        text("Max energy " + (int) c.maxEnergy + "   |   Base attack " + c.baseAttack, BLUE, 0);

        header("MOVESET");
        moveset(c.base);
        if (c.transformed != null) {
            header("TRANSFORMED  (hold Ultimate)");
            text("Slots not listed here keep their normal move.", GRAY, 0);
            moveset(c.transformed);
        }
        if (!c.passives.isEmpty()) {
            header("PASSIVES");
            for (PassiveDef p : c.passives) {
                text(p.name + "   [" + p.trigger.name().toLowerCase().replace('_', ' ') + "]", WHITE, 0);
                if (!p.description.isEmpty()) text(p.description, GRAY, 8);
            }
        }
        cachedSel = selected; cachedW = detailW();
        scroll = 0;
    }

    // ------------------------------------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "CODEX", width / 2, 12, GOLD);
        int top = top(), bottom = bottom();

        // left: character list
        g.fill(MARGIN, top, MARGIN + LIST_W, bottom, 0xAA000000);
        g.enableScissor(MARGIN, top, MARGIN + LIST_W, bottom);
        for (int i = 0; i < chars.size(); i++) {
            int y = top + 4 + i * ROW - (int) listScroll;
            boolean sel = i == selected, hover = mx >= MARGIN && mx < MARGIN + LIST_W && my >= y && my < y + ROW && my >= top && my < bottom;
            if (sel) g.fill(MARGIN + 2, y, MARGIN + LIST_W - 2, y + ROW - 2, 0x55FFD34D);
            else if (hover) g.fill(MARGIN + 2, y, MARGIN + LIST_W - 2, y + ROW - 2, 0x33FFFFFF);
            CharacterDef c = chars.get(i);
            boolean on = c.id.equals(ClientState.data.character);
            g.drawString(font, (on ? "* " : "") + c.name, MARGIN + 6, y + 5, sel ? GOLD : WHITE);
        }
        g.disableScissor();

        // right: details
        int dx = detailX();
        g.fill(dx - 4, top, width - MARGIN, bottom, 0xAA000000);
        if (chars.isEmpty()) {
            g.drawString(font, "No characters loaded. Check your datapack / data/<ns>/characters/", dx, top + 8, RED);
        } else {
            if (cachedSel != selected || cachedW != detailW()) rebuild(chars.get(selected));
            int viewH = bottom - top - 8, total = lines.size() * LINE_H;
            double max = Math.max(0, total - viewH);
            scroll = Mth.clamp(scroll, 0, max);
            g.enableScissor(dx - 4, top + 2, width - MARGIN, bottom - 2);
            for (int i = 0; i < lines.size(); i++) {
                int y = top + 6 + i * LINE_H - (int) scroll;
                if (y < top - LINE_H || y > bottom) continue;
                Line l = lines.get(i);
                g.drawString(font, l.text(), dx + l.indent(), y, l.color());
            }
            g.disableScissor();
            if (max > 0) {   // scrollbar
                int barH = Math.max(14, (int) (viewH * (viewH / (double) total)));
                int barY = top + 4 + (int) ((viewH - barH) * (scroll / max));
                g.fill(width - MARGIN - 4, barY, width - MARGIN - 1, barY + barH, 0xAAFFFFFF);
            }
        }
        super.render(g, mx, my, pt);
    }

    // ------------------------------------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= MARGIN && mx < MARGIN + LIST_W && my >= top() && my < bottom()) {
            int i = (int) ((my - top() - 4 + listScroll) / ROW);
            if (i >= 0 && i < chars.size()) { selected = i; return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < detailX() - 4) listScroll = Mth.clamp(listScroll - delta * ROW, 0, Math.max(0, chars.size() * ROW - (bottom() - top() - 8)));
        else scroll -= delta * 20;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (KeyBindings.CODEX.matches(key, scan)) { onClose(); return true; }
        if (key == 264 && selected < chars.size() - 1) { selected++; return true; }   // down arrow
        if (key == 265 && selected > 0) { selected--; return true; }                  // up arrow
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
