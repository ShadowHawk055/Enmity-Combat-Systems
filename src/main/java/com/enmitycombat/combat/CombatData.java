package com.enmitycombat.combat;

import com.enmitycombat.data.CharacterDef;
import com.enmitycombat.data.DataManager;
import com.enmitycombat.data.SkillDef;
import com.enmitycombat.data.Slot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Per-player combat state. Server holds the truth; the client keeps a copy (ClientState) for HUD / input decisions.
 * "Persistent" fields are saved. "Runtime" fields exist only on the server and reset on relog.
 */
public class CombatData {
    public static final class Cd {
        public float remaining; public int total;
        Cd(float r, int t) { remaining = r; total = t; }
    }
    public static final class Buff {
        public final String id; public final float damageMult, cooldownRate, energyMult; public int ticks;
        Buff(String id, float d, float c, float e, int t) { this.id = id; damageMult = d; cooldownRate = c; energyMult = e; ticks = t; }
    }
    public static final class ActiveAction {
        public final SkillDef skill; public final Slot slot; public final Vec3 aim; public int tick;
        public ActiveAction(SkillDef skill, Slot slot, Vec3 aim) { this.skill = skill; this.slot = slot; this.aim = aim; }
    }

    // ---- persistent + synced -------------------------------------------------------------------------------
    @Nullable public ResourceLocation character;
    public boolean combatMode;
    public float energy;
    public int transformTicks;
    public final Map<ResourceLocation, Cd> cooldowns = new HashMap<>();

    // ---- synced, not saved ---------------------------------------------------------------------------------
    public float cooldownRate = 1f;
    public boolean lockFlag;
    public boolean blocking;

    // ---- server runtime -----------------------------------------------------------------------------------
    public ActiveAction action;
    public int comboIndex, comboTimer;
    @Nullable public Slot buffered; public Vec3 bufferedAim = Vec3.ZERO; public int bufferTimer;
    public boolean blockHeld, parryReady; public int blockTicks, parryLockTicks, guardBrokenTicks;
    public int dodgeTicks, dodgeCooldown, invulnTicks; public boolean dodgePerfected;
    public final List<Buff> buffs = new ArrayList<>();
    public final Map<String, Integer> passiveCooldowns = new HashMap<>();
    public boolean dirty;
    public int drawTimer;                       // keeps props "drawn" for a moment after an action
    @Nullable public ResourceLocation lastVisChar; public int lastVisFlags;

    public static CombatData get(Player p) {
        return p.getCapability(CombatCaps.COMBAT).orElseThrow(() -> new IllegalStateException("CombatData capability missing"));
    }

    // ---- queries -------------------------------------------------------------------------------------------
    public boolean isActive() { return character != null && combatMode; }
    public boolean transformed() { return transformTicks > 0; }
    @Nullable public CharacterDef character() { return DataManager.CHARACTERS.get(character); }
    public void markDirty() { dirty = true; }

    /** bit0 combat mode, bit1 transformed, bit2 drawn (attacking / blocking / just did). Used for prop visibility. */
    public int visualFlags() {
        int f = 0;
        if (combatMode) f |= 1;
        if (transformed()) f |= 2;
        if (action != null || blocking || drawTimer > 0) f |= 4;
        return f;
    }

    public float cooldownRemaining(ResourceLocation skill) { Cd cd = cooldowns.get(skill); return cd == null ? 0 : cd.remaining; }

    public float damageMultiplier() { float m = 1; for (Buff b : buffs) m *= b.damageMult; return m; }
    public float buffCooldownRate() { float m = 1; for (Buff b : buffs) m *= b.cooldownRate; return m; }
    public float energyMultiplier() { float m = 1; for (Buff b : buffs) m *= b.energyMult; return m; }

    // ---- mutation ------------------------------------------------------------------------------------------
    public void setCooldown(ResourceLocation id, int ticks) {
        if (ticks > 0) cooldowns.put(id, new Cd(ticks, ticks));
        markDirty();
    }

    public void reduceCooldowns(float ticks, float fractionOfTotal) {
        for (Cd cd : cooldowns.values()) cd.remaining -= ticks + cd.total * fractionOfTotal;
        cooldowns.values().removeIf(cd -> cd.remaining <= 0);
        markDirty();
    }

    public void tickCooldowns(float rate) {
        if (cooldowns.isEmpty()) return;
        for (Cd cd : cooldowns.values()) cd.remaining -= rate;
        cooldowns.values().removeIf(cd -> cd.remaining <= 0);
    }

    public void addEnergy(float amount) {
        CharacterDef ch = character();
        float max = ch == null ? 100f : ch.maxEnergy;
        if (amount > 0) amount *= energyMultiplier();
        energy = Math.max(0, Math.min(max, energy + amount));
        markDirty();
    }

    public void addBuff(String id, float dmg, float cdr, float en, int ticks) {
        buffs.removeIf(b -> b.id.equals(id));
        buffs.add(new Buff(id, dmg, cdr, en, ticks));
        markDirty();
    }

    public void tickBuffs() {
        if (buffs.isEmpty()) return;
        buffs.forEach(b -> b.ticks--);
        if (buffs.removeIf(b -> b.ticks <= 0)) markDirty();
    }

    // ---- (de)serialisation -----------------------------------------------------------------------------------
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (character != null) t.putString("character", character.toString());
        t.putBoolean("combatMode", combatMode);
        t.putFloat("energy", energy);
        t.putInt("transform", transformTicks);
        ListTag list = new ListTag();
        cooldowns.forEach((id, cd) -> {
            CompoundTag c = new CompoundTag();
            c.putString("id", id.toString()); c.putFloat("r", cd.remaining); c.putInt("t", cd.total);
            list.add(c);
        });
        t.put("cd", list);
        return t;
    }

    public void load(CompoundTag t) {
        character = t.contains("character") ? new ResourceLocation(t.getString("character")) : null;
        combatMode = t.getBoolean("combatMode");
        energy = t.getFloat("energy");
        transformTicks = t.getInt("transform");
        cooldowns.clear();
        for (Tag x : t.getList("cd", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            cooldowns.put(new ResourceLocation(c.getString("id")), new Cd(c.getFloat("r"), c.getInt("t")));
        }
    }

    public CompoundTag syncTag() {
        lockFlag = action != null && action.skill.lockMovement;
        CompoundTag t = save();
        t.putFloat("rate", cooldownRate);
        t.putBoolean("lock", lockFlag);
        t.putBoolean("blocking", blocking);
        return t;
    }

    public void loadSync(CompoundTag t) {
        load(t);
        cooldownRate = t.getFloat("rate");
        lockFlag = t.getBoolean("lock");
        blocking = t.getBoolean("blocking");
    }

    public void copyPersistent(CombatData o) { load(o.save()); }
}
