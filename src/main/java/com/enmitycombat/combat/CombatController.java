package com.enmitycombat.combat;

import com.enmitycombat.EnmityConfig;
import com.enmitycombat.EnmityCombat;
import com.enmitycombat.combat.CombatData.ActiveAction;
import com.enmitycombat.data.*;
import com.enmitycombat.effect.EnmityEffects;
import com.enmitycombat.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Server-side brain: input -> state machine -> timeline playback. Everything is server authoritative. */
public final class CombatController {

    // =================================================================================================
    //  INPUT
    // =================================================================================================
    public static void handleInput(ServerPlayer p, CombatInput in, Vec3 dir) {
        CombatData d = CombatData.get(p);

        if (in == CombatInput.TOGGLE_COMBAT) {
            if (d.character != null) setCombatMode(p, d, !d.combatMode);
            return;
        }
        if (!d.isActive() || !p.isAlive() || p.isSpectator()) return;
        if (in == CombatInput.BLOCK_UP) { d.blockHeld = false; stopBlock(p, d); return; }
        if (p.hasEffect(EnmityEffects.STUN.get())) return;

        Vec3 aim = dir.lengthSqr() < 1e-6 ? p.getLookAngle() : dir.normalize();
        switch (in) {
            case BLOCK_DOWN -> d.blockHeld = true;
            case DODGE -> tryDodge(p, d, aim);
            default -> { if (in.slot() != null) trySlot(p, d, in.slot(), aim); }
        }
    }

    private static void trySlot(ServerPlayer p, CombatData d, Slot slot, Vec3 aim) {
        ActiveAction cur = d.action;
        if (cur != null && (cur.skill.uninterruptible || cur.tick < cur.skill.cancelFrom)) {
            d.buffered = slot; d.bufferedAim = aim; d.bufferTimer = EnmityConfig.INPUT_BUFFER_TICKS.get();
            return;
        }
        if (d.blocking) stopBlock(p, d);
        start(p, d, slot, aim);
    }

    private static boolean start(ServerPlayer p, CombatData d, Slot slot, Vec3 aim) {
        CharacterDef ch = d.character();
        if (ch == null) return false;

        ResourceLocation id;
        int comboStep = 0;
        if (slot == Slot.BASIC) {
            List<ResourceLocation> chain = ch.basicChain(d.transformed());
            if (chain.isEmpty()) return false;
            comboStep = d.comboIndex % chain.size();
            id = chain.get(comboStep);
        } else {
            id = ch.resolve(slot, d.transformed());
        }
        SkillDef skill = DataManager.SKILLS.get(id);
        if (skill == null) return false;

        if (slot != Slot.BASIC && d.cooldownRemaining(id) > 0) { p.displayClientMessage(Component.translatable("enmitycombat.on_cooldown"), true); return false; }
        if (skill.energyCost > 0 && d.energy < skill.energyCost) { p.displayClientMessage(Component.translatable("enmitycombat.no_energy"), true); return false; }

        d.energy -= skill.energyCost;
        if (skill.energyGain > 0) d.addEnergy(skill.energyGain);

        if (slot == Slot.BASIC) {
            d.comboIndex = (comboStep + 1) % ch.basicChain(d.transformed()).size();
            d.comboTimer = ch.comboResetTicks;
            if (ch.basicCooldownReduce > 0) d.reduceCooldowns(ch.basicCooldownReduce, 0);   // "basic chain shaves skill CDs"
        } else {
            d.setCooldown(id, skill.cooldown);
        }

        if (skill.faceAim) face(p, aim);
        d.action = new ActiveAction(skill, slot, aim);
        d.buffered = null;
        d.markDirty();

        PassiveManager.fire(p, d, PassiveDef.Trigger.ON_SKILL_CAST, null);
        if (slot.isUltimate()) PassiveManager.fire(p, d, PassiveDef.Trigger.ON_ULTIMATE, null);
        return true;
    }

    private static void face(ServerPlayer p, Vec3 aim) {
        float yaw = (float) (Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        p.setYRot(yaw); p.setYHeadRot(yaw); p.setYBodyRot(yaw);
    }

    // =================================================================================================
    //  BLOCK / DODGE
    // =================================================================================================
    static void startBlock(ServerPlayer p, CombatData d) {
        d.blocking = true; d.blockTicks = 0; d.parryReady = d.parryLockTicks <= 0;
        CharacterDef ch = d.character();
        if (ch != null) playAnim(p, ch.animations.get("block"));
        d.markDirty();
    }

    static void stopBlock(ServerPlayer p, CombatData d) {
        if (!d.blocking) return;
        d.blocking = false;
        d.parryLockTicks = EnmityConfig.PARRY_REPRESS_LOCK.get();
        playAnim(p, null);
        d.markDirty();
    }

    private static void tryDodge(ServerPlayer p, CombatData d, Vec3 aim) {
        if (d.dodgeCooldown > 0) return;
        if (d.action != null) {
            if (d.action.skill.uninterruptible) return;
            d.action = null;
        }
        stopBlock(p, d);
        d.buffered = null;
        d.dodgeTicks = EnmityConfig.DODGE_IFRAMES.get();
        d.dodgePerfected = false;
        d.dodgeCooldown = EnmityConfig.DODGE_COOLDOWN.get();

        Vec3 dir = new Vec3(aim.x, 0, aim.z);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()).scale(-1) : dir.normalize();
        double s = EnmityConfig.DODGE_SPEED.get();
        p.setDeltaMovement(dir.x * s, p.onGround() ? 0.1 : p.getDeltaMovement().y, dir.z * s);
        p.hurtMarked = true;

        CharacterDef ch = d.character();
        if (ch != null) playAnim(p, ch.animations.get("dodge"));
        d.markDirty();
    }

    // =================================================================================================
    //  TRANSFORM (ultimate hold)
    // =================================================================================================
    public static void beginTransform(ServerPlayer p, CombatData d, int ticks) {
        d.transformTicks = ticks; d.comboIndex = 0; d.markDirty();
        PassiveManager.fire(p, d, PassiveDef.Trigger.ON_TRANSFORM, null);
    }

    public static void endTransform(ServerPlayer p, CombatData d) {
        if (d.transformTicks <= 0) return;
        d.transformTicks = 0; d.comboIndex = 0; d.markDirty();
        CharacterDef ch = d.character();
        if (ch != null && ch.fx.get("transform_end") != null)
            NetworkHandler.sendToTrackingAndSelf(p, new PlayFxPacket(ch.fx.get("transform_end"), p.getId(), p.position()));
        PassiveManager.fire(p, d, PassiveDef.Trigger.ON_TRANSFORM_END, null);
    }

    public static void setCombatMode(ServerPlayer p, CombatData d, boolean on) {
        d.combatMode = on;
        if (!on) { d.action = null; d.buffered = null; d.blockHeld = false; stopBlock(p, d); }
        PassiveManager.refreshAttributes(p, d);
        d.markDirty();
    }

    public static void setCharacter(ServerPlayer p, CombatData d, @Nullable ResourceLocation id) {
        d.character = id; d.combatMode = id != null; d.action = null; d.buffered = null; d.comboIndex = 0;
        d.transformTicks = 0; d.cooldowns.clear(); d.energy = 0; d.buffs.clear();
        d.blockHeld = false; d.blocking = false;
        PassiveManager.refreshAttributes(p, d);
        d.markDirty();
    }

    // =================================================================================================
    //  HIT CALLBACK (from hitbox actions)
    // =================================================================================================
    public static void onHit(Actions.Ctx c, LivingEntity target) {
        CombatData d = c.data();
        if (c.skill() != null && c.skill().energyOnHit > 0) d.addEnergy(c.skill().energyOnHit);
        PassiveManager.fire(c.player(), d, PassiveDef.Trigger.ON_HIT, target);
        if (c.slot() == Slot.BASIC) PassiveManager.fire(c.player(), d, PassiveDef.Trigger.ON_BASIC_HIT, target);
    }

    // =================================================================================================
    //  SERVER TICK
    // =================================================================================================
    public static void tick(ServerPlayer p) {
        CombatData d = CombatData.get(p);
        syncVisual(p, d);
        if (d.character == null) return;

        d.cooldownRate = d.buffCooldownRate();
        d.tickCooldowns(d.cooldownRate);
        d.tickBuffs();
        d.passiveCooldowns.replaceAll((k, v) -> v - 1);
        d.passiveCooldowns.values().removeIf(v -> v <= 0);

        if (d.transformTicks > 0 && --d.transformTicks == 0) { d.transformTicks = 1; endTransform(p, d); }
        if (d.dodgeTicks > 0) d.dodgeTicks--;
        if (d.dodgeCooldown > 0) d.dodgeCooldown--;
        if (d.invulnTicks > 0) d.invulnTicks--;
        if (d.parryLockTicks > 0) d.parryLockTicks--;
        if (d.guardBrokenTicks > 0) d.guardBrokenTicks--;
        if (d.comboTimer > 0 && d.action == null && --d.comboTimer == 0) d.comboIndex = 0;

        if (d.combatMode) {
            tickBlock(p, d);
            tickBuffer(p, d);
            tickAction(p, d);
        }
        if (d.dirty || p.tickCount % 40 == 0) {
            NetworkHandler.sendTo(p, new SyncDataPacket(d.syncTag()));
            d.dirty = false;
        }
    }

    /** Tells every nearby client which character this player has equipped and whether the weapon is drawn. */
    private static void syncVisual(ServerPlayer p, CombatData d) {
        if (d.action != null || d.blocking) d.drawTimer = 60;
        else if (d.drawTimer > 0) d.drawTimer--;
        int flags = d.character == null ? 0 : d.visualFlags();
        if (flags != d.lastVisFlags || !java.util.Objects.equals(d.character, d.lastVisChar)) {
            d.lastVisFlags = flags;
            d.lastVisChar = d.character;
            NetworkHandler.sendToTrackingAndSelf(p, new CombatVisualPacket(p.getUUID(), d.character, flags));
        }
    }

    private static void tickBlock(ServerPlayer p, CombatData d) {
        if (p.hasEffect(EnmityEffects.STUN.get())) { stopBlock(p, d); return; }
        if (d.blockHeld && !d.blocking && d.guardBrokenTicks <= 0) {
            if (d.action == null) startBlock(p, d);
            else if (!d.action.skill.uninterruptible && d.action.tick >= d.action.skill.cancelFrom) { d.action = null; startBlock(p, d); }
        }
        if (d.blocking) {
            d.blockTicks++;
            if (!d.blockHeld) stopBlock(p, d);
        }
    }

    private static void tickBuffer(ServerPlayer p, CombatData d) {
        if (d.buffered == null) return;
        if (--d.bufferTimer <= 0) { d.buffered = null; return; }
        ActiveAction a = d.action;
        if (a == null || (!a.skill.uninterruptible && a.tick >= a.skill.cancelFrom)) {
            Slot s = d.buffered;
            Vec3 aim = d.bufferedAim;
            d.buffered = null;
            if (d.blocking) stopBlock(p, d);
            start(p, d, s, aim);
        }
    }

    private static void tickAction(ServerPlayer p, CombatData d) {
        ActiveAction a = d.action;
        if (a == null) return;
        CharacterDef ch = d.character();
        for (SkillDef.TimedAction te : a.skill.timeline) {
            if (te.tick() != a.tick) continue;
            try {
                te.action().run(new Actions.Ctx(p, d, ch, a.skill, a.slot, a.aim, null));
            } catch (Exception ex) {
                EnmityCombat.LOGGER.error("Error running action in {} @tick {}", a.skill.id, a.tick, ex);
            }
        }
        if (d.action == a && ++a.tick >= a.skill.duration) { d.action = null; d.markDirty(); }
    }

    static void playAnim(ServerPlayer p, @Nullable ResourceLocation anim) {
        NetworkHandler.sendToTrackingAndSelf(p, new PlayAnimPacket(p.getId(), anim));
    }
    private CombatController() {}
}
