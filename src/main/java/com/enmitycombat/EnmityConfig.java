package com.enmitycombat;

import net.minecraftforge.common.ForgeConfigSpec;

/** All the numbers from the design doc live here. Times are in game ticks (20 = 1s). */
public final class EnmityConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue BLOCK_REDUCTION;   // 0.7 = -70% damage
    public static final ForgeConfigSpec.IntValue PARRY_WINDOW;         // 6 ticks  = 0.3s
    public static final ForgeConfigSpec.IntValue PARRY_STUN;           // 26 ticks = 1.3s
    public static final ForgeConfigSpec.IntValue PARRY_REPRESS_LOCK;   // anti-spam after releasing block
    public static final ForgeConfigSpec.IntValue GUARD_BREAK_LOCK;     // can't block for this long after guard break

    public static final ForgeConfigSpec.IntValue DODGE_IFRAMES;
    public static final ForgeConfigSpec.IntValue DODGE_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue DODGE_SPEED;
    public static final ForgeConfigSpec.DoubleValue PERFECT_DODGE_CD_REFUND; // fraction of each skill's total CD
    public static final ForgeConfigSpec.DoubleValue PERFECT_DODGE_ENERGY;

    public static final ForgeConfigSpec.DoubleValue PARRY_ENERGY;
    public static final ForgeConfigSpec.BooleanValue IGNORE_HURT_COOLDOWN;
    public static final ForgeConfigSpec.IntValue ULT_HOLD_TICKS;       // how long to hold R for the transform ultimate
    public static final ForgeConfigSpec.IntValue INPUT_BUFFER_TICKS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SELF_SELECT; // codex Equip button usable by non-ops

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("defense");
        BLOCK_REDUCTION = b.defineInRange("blockReduction", 0.7, 0.0, 1.0);
        PARRY_WINDOW = b.comment("Ticks after pressing block in which a hit is parried (6 = 0.3s)").defineInRange("parryWindowTicks", 6, 1, 40);
        PARRY_STUN = b.defineInRange("parryStunTicks", 26, 1, 400);
        PARRY_REPRESS_LOCK = b.defineInRange("parryRepressLockTicks", 12, 0, 100);
        GUARD_BREAK_LOCK = b.defineInRange("guardBreakLockTicks", 40, 0, 400);
        PARRY_ENERGY = b.defineInRange("parryEnergy", 10.0, 0.0, 1000.0);
        b.pop();
        b.push("dodge");
        DODGE_IFRAMES = b.defineInRange("iFrameTicks", 10, 1, 40);
        DODGE_COOLDOWN = b.defineInRange("cooldownTicks", 24, 0, 200);
        DODGE_SPEED = b.defineInRange("speed", 1.15, 0.1, 4.0);
        PERFECT_DODGE_CD_REFUND = b.defineInRange("perfectDodgeCooldownRefund", 0.3, 0.0, 1.0);
        PERFECT_DODGE_ENERGY = b.defineInRange("perfectDodgeEnergy", 8.0, 0.0, 1000.0);
        b.pop();
        b.push("misc");
        IGNORE_HURT_COOLDOWN = b.comment("Skill hits ignore the 10-tick vanilla i-frames so multi-hit skills work")
                .define("ignoreHurtCooldown", true);
        ULT_HOLD_TICKS = b.defineInRange("ultimateHoldTicks", 10, 2, 60);
        INPUT_BUFFER_TICKS = b.defineInRange("inputBufferTicks", 8, 0, 40);
        ALLOW_SELF_SELECT = b.comment("Let every player equip any character from the codex. Turn off on servers.").define("allowSelfSelect", true);
        b.pop();
        SPEC = b.build();
    }
    private EnmityConfig() {}
}
