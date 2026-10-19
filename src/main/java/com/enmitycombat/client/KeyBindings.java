package com.enmitycombat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final String CAT = "key.categories.enmitycombat";
    private static KeyMapping key(String n, int code) {
        return new KeyMapping("key.enmitycombat." + n, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CAT);
    }

    public static final KeyMapping TOGGLE = key("toggle", GLFW.GLFW_KEY_V);
    public static final KeyMapping INVENTORY = key("inventory", GLFW.GLFW_KEY_TAB);
    public static final KeyMapping CODEX = key("codex", GLFW.GLFW_KEY_J);
    public static final KeyMapping SKILL1 = key("skill1", GLFW.GLFW_KEY_1);
    public static final KeyMapping SKILL2 = key("skill2", GLFW.GLFW_KEY_2);
    public static final KeyMapping SKILL3 = key("skill3", GLFW.GLFW_KEY_3);
    public static final KeyMapping SPECIAL = key("special", GLFW.GLFW_KEY_E);
    public static final KeyMapping HEAVY = key("heavy", GLFW.GLFW_KEY_Q);
    public static final KeyMapping ULTIMATE = key("ultimate", GLFW.GLFW_KEY_R);
    public static final KeyMapping DODGE = key("dodge", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping BLOCK = new KeyMapping("key.enmitycombat.block", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CAT);

    public static final KeyMapping[] ALL = {TOGGLE, INVENTORY, CODEX, SKILL1, SKILL2, SKILL3, SPECIAL, HEAVY, ULTIMATE, DODGE, BLOCK};
    private KeyBindings() {}
}
