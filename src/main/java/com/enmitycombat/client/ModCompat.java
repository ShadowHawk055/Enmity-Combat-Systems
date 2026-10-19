package com.enmitycombat.client;

import com.enmitycombat.EnmityCombat;
import net.minecraftforge.fml.ModList;

import java.util.Locale;

/** Detects third-person / lock-on camera mods. Detection only: no code from those mods is called. */
public final class ModCompat {
    private static Boolean focus, leawind, shoulder;

    public static boolean focus() { return focus != null ? focus : (focus = any("focus")); }
    public static boolean leawind() { return leawind != null ? leawind : (leawind = any("leawind_third_person", "leawind")); }
    public static boolean shoulderSurfing() { return shoulder != null ? shoulder : (shoulder = any("shouldersurfing", "shoulder")); }
    public static boolean cameraModLoaded() { return focus() || leawind() || shoulderSurfing(); }

    private static boolean any(String... needles) {
        ModList ml = ModList.get();
        if (ml == null) return false;
        for (String n : needles) {
            if (ml.isLoaded(n)) return true;
            // modid guess might be off, so also look at display names
            if (ml.getMods().stream().anyMatch(m -> m.getModId().toLowerCase(Locale.ROOT).equals(n)
                    || m.getDisplayName().toLowerCase(Locale.ROOT).contains(n.replace('_', ' ')))) return true;
        }
        return false;
    }

    public static void logSummary() {
        EnmityCombat.LOGGER.info("[Enmity] Camera mods detected: Focus={}, Leawind's Third Person={}, Shoulder Surfing={}",
                focus(), leawind(), shoulderSurfing());
    }
    private ModCompat() {}
}
