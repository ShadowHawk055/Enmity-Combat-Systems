package com.enmitycombat.combat;

import com.enmitycombat.data.Slot;

import javax.annotation.Nullable;

public enum CombatInput {
    BASIC(Slot.BASIC), SKILL1(Slot.SKILL1), SKILL2(Slot.SKILL2), SKILL3(Slot.SKILL3),
    SPECIAL(Slot.SPECIAL), HEAVY(Slot.HEAVY), ULT_TAP(Slot.ULTIMATE), ULT_HOLD(Slot.ULTIMATE_HOLD),
    DODGE(null), BLOCK_DOWN(null), BLOCK_UP(null), TOGGLE_COMBAT(null);

    @Nullable private final Slot slot;
    CombatInput(@Nullable Slot slot) { this.slot = slot; }
    @Nullable public Slot slot() { return slot; }
}
