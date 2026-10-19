package com.enmitycombat.combat;

import com.enmitycombat.EnmityConfig;
import com.enmitycombat.EnmityCombat;
import com.enmitycombat.data.CharacterDef;
import com.enmitycombat.data.PassiveDef;
import com.enmitycombat.effect.EnmityEffects;
import com.enmitycombat.network.NetworkHandler;
import com.enmitycombat.network.PlayFxPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/** Block, parry, guard break, i-frames and perfect dodge. All damage interception happens here. */
@Mod.EventBusSubscriber(modid = EnmityCombat.MODID)
public final class DefenseHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CombatData d = CombatData.get(p);
        if (!d.isActive()) return;

        DamageSource src = e.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;           // /kill, void
        boolean attack = src.getDirectEntity() != null;                        // mob/projectile/explosion, not fire/fall

        if (d.invulnTicks > 0) { e.setCanceled(true); return; }                // skill i-frames, ultimates

        if (d.dodgeTicks > 0 && attack) {                                      // PERFECT DODGE
            e.setCanceled(true);
            if (!d.dodgePerfected) { d.dodgePerfected = true; perfectDodge(p, d); }
            return;
        }

        if (d.blocking && attack) {
            if (GuardBreakContext.isGuardBreak(src)) { guardBreak(p, d); return; }   // damage goes through in full
            if (d.parryReady && d.blockTicks <= EnmityConfig.PARRY_WINDOW.get()) {       // PARRY
                e.setCanceled(true);
                parry(p, d, src);
            }
            // otherwise a normal block: reduction applied in onHurt
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CombatData d = CombatData.get(p);
        if (!d.isActive() || !d.blocking || e.getSource().getDirectEntity() == null) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        e.setAmount((float) (e.getAmount() * (1.0 - EnmityConfig.BLOCK_REDUCTION.get())));
        p.level().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1f);
        PassiveManager.fire(p, d, PassiveDef.Trigger.ON_BLOCK, e.getSource().getEntity() instanceof LivingEntity le ? le : null);
    }

    private static void parry(ServerPlayer p, CombatData d, DamageSource src) {
        Entity atk = src.getEntity() != null ? src.getEntity() : src.getDirectEntity();
        LivingEntity target = null;
        if (atk instanceof LivingEntity le) {
            target = le;
            le.addEffect(new MobEffectInstance(EnmityEffects.STUN.get(), EnmityConfig.PARRY_STUN.get(), 0, false, true));
            le.knockback(0.4, p.getX() - le.getX(), p.getZ() - le.getZ());
        }
        d.addEnergy(EnmityConfig.PARRY_ENERGY.get().floatValue());
        p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.8f);
        CharacterDef ch = d.character();
        if (ch != null) {
            CombatController.playAnim(p, ch.animations.get("parry"));
            fx(p, ch.fx.get("parry"));
        }
        PassiveManager.fire(p, d, PassiveDef.Trigger.ON_PARRY, target);
        d.markDirty();
    }

    private static void guardBreak(ServerPlayer p, CombatData d) {
        d.blockHeld = false;
        CombatController.stopBlock(p, d);
        d.guardBrokenTicks = EnmityConfig.GUARD_BREAK_LOCK.get();
        p.level().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1f, 1f);
        p.displayClientMessage(Component.translatable("enmitycombat.guard_broken"), true);
        CharacterDef ch = d.character();
        if (ch != null) fx(p, ch.fx.get("guard_break"));
    }

    private static void perfectDodge(ServerPlayer p, CombatData d) {
        d.reduceCooldowns(0, EnmityConfig.PERFECT_DODGE_CD_REFUND.get().floatValue());
        d.addEnergy(EnmityConfig.PERFECT_DODGE_ENERGY.get().floatValue());
        d.dodgeCooldown = 0;                      // reward: instant re-dodge
        CharacterDef ch = d.character();
        if (ch != null) fx(p, ch.fx.get("perfect_dodge"));
        PassiveManager.fire(p, d, PassiveDef.Trigger.ON_PERFECT_DODGE, null);
        // TODO "witch time": slow nearby mobs / send a client time-dilation packet here.
    }

    private static void fx(ServerPlayer p, @Nullable ResourceLocation fx) {
        if (fx != null) NetworkHandler.sendToTrackingAndSelf(p, new PlayFxPacket(fx, p.getId(), p.position()));
    }
    private DefenseHandler() {}
}
