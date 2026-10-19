package com.enmitycombat.combat;

import com.enmitycombat.EnmityCombat;
import com.enmitycombat.data.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class PassiveManager {

    /** Fire every passive of the current character that listens to this trigger. */
    public static void fire(ServerPlayer p, CombatData d, PassiveDef.Trigger trigger, @Nullable LivingEntity target) {
        CharacterDef ch = d.character();
        if (ch == null) return;
        for (PassiveDef pd : ch.passives) {
            if (pd.trigger != trigger) continue;
            if (pd.internalCooldown > 0) {
                if (d.passiveCooldowns.containsKey(pd.id)) continue;
                d.passiveCooldowns.put(pd.id, pd.internalCooldown);
            }
            Actions.Ctx ctx = new Actions.Ctx(p, d, ch, null, null, p.getLookAngle(), target);
            for (Actions.Action a : pd.effects) {
                try { a.run(ctx); } catch (Exception ex) { EnmityCombat.LOGGER.error("Passive {} failed", pd.id, ex); }
            }
        }
    }

    /** ALWAYS-trigger "attribute" effects: remove everyone's modifiers, then add the current character's. */
    public static void refreshAttributes(ServerPlayer p, CombatData d) {
        for (CharacterDef c : DataManager.CHARACTERS.values())
            for (PassiveDef pd : c.passives)
                for (PassiveDef.AttrMod m : pd.attributes) {
                    Attribute a = ForgeRegistries.ATTRIBUTES.getValue(m.attribute());
                    AttributeInstance inst = a == null ? null : p.getAttribute(a);
                    if (inst != null) inst.removeModifier(uuid(pd, m));
                }
        CharacterDef cur = d.isActive() ? d.character() : null;
        if (cur == null) return;
        for (PassiveDef pd : cur.passives)
            for (PassiveDef.AttrMod m : pd.attributes) {
                Attribute a = ForgeRegistries.ATTRIBUTES.getValue(m.attribute());
                AttributeInstance inst = a == null ? null : p.getAttribute(a);
                if (inst != null) inst.addTransientModifier(new AttributeModifier(uuid(pd, m), "enmitycombat:" + pd.id, m.amount(), m.op()));
            }
    }

    private static UUID uuid(PassiveDef pd, PassiveDef.AttrMod m) {
        return UUID.nameUUIDFromBytes(("enmitycombat:" + pd.id + ":" + m.attribute()).getBytes(StandardCharsets.UTF_8));
    }
    private PassiveManager() {}
}
