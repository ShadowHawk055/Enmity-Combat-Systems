package com.enmitycombat.combat;

import com.enmitycombat.EnmityCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Marks damage as "guard break". Three ways:
 *  - our hitbox action with "guard_break": true (flag set while the hurt() call runs)
 *  - damage type in tag  enmitycombat:guard_break           (data/enmitycombat/tags/damage_type)
 *  - attacker type in tag enmitycombat:guard_break_attackers (data/enmitycombat/tags/entity_types)
 */
public final class GuardBreakContext {
    private static final TagKey<DamageType> DAMAGE_TAG = TagKey.create(Registries.DAMAGE_TYPE, EnmityCombat.id("guard_break"));
    private static final TagKey<EntityType<?>> ENTITY_TAG = TagKey.create(Registries.ENTITY_TYPE, EnmityCombat.id("guard_break_attackers"));
    private static boolean flag;

    public static void with(boolean guardBreak, Runnable r) {
        boolean prev = flag;
        flag = guardBreak;
        try { r.run(); } finally { flag = prev; }
    }

    public static boolean isGuardBreak(DamageSource src) {
        if (flag || src.is(DAMAGE_TAG)) return true;
        Entity e = src.getEntity();
        return e != null && e.getType().is(ENTITY_TAG);
    }
    private GuardBreakContext() {}
}
