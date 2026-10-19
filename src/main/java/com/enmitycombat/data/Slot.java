package com.enmitycombat.data;

import javax.annotation.Nullable;

public enum Slot {
    BASIC("basic"), SKILL1("skill_1"), SKILL2("skill_2"), SKILL3("skill_3"),
    SPECIAL("special"), HEAVY("heavy"), ULTIMATE("ultimate"), ULTIMATE_HOLD("ultimate_hold");

    public final String key;
    Slot(String key) { this.key = key; }

    public boolean isUltimate() { return this == ULTIMATE || this == ULTIMATE_HOLD; }

    @Nullable
    public static Slot byKey(String k) {
        for (Slot s : values()) if (s.key.equals(k)) return s;
        return null;
    }
}
