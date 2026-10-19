package com.enmitycombat.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Freezes mobs (NoAI) and roots any entity (-100% move speed). Players are also blocked from combat input server-side. */
public class StunEffect extends MobEffect {
    private static final String KEY = "ac_was_noai";

    public StunEffect() {
        super(MobEffectCategory.HARMFUL, 0xE5C84A);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, "7107DE5E-7CE8-4030-940E-514C1F160890", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void addAttributeModifiers(LivingEntity e, AttributeMap map, int amp) {
        super.addAttributeModifiers(e, map, amp);
        if (e instanceof Mob m) {
            if (m.isNoAi()) m.getPersistentData().putBoolean(KEY, true);
            m.setNoAi(true);
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity e, AttributeMap map, int amp) {
        super.removeAttributeModifiers(e, map, amp);
        if (e instanceof Mob m) {
            if (!m.getPersistentData().getBoolean(KEY)) m.setNoAi(false);
            m.getPersistentData().remove(KEY);
        }
    }
}
