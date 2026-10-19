package com.enmitycombat.data;

import com.enmitycombat.EnmityConfig;
import com.enmitycombat.combat.CombatController;
import com.enmitycombat.combat.CombatData;
import com.enmitycombat.combat.GuardBreakContext;
import com.enmitycombat.network.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;

/**
 * THE EXTENSION POINT.
 * Everything a skill or passive can do is an Action created from a JSON object with a "type".
 * To add your own: Actions.register("my_type", json -> ctx -> { ... });
 */
public final class Actions {
    public interface Action { void run(Ctx ctx); }
    public interface Parser { Action parse(JsonObject json); }

    /** target is the entity that was just hit (hit-based passives) or null. slot/skill are null for passives. */
    public record Ctx(ServerPlayer player, CombatData data, CharacterDef character, @Nullable SkillDef skill,
                      @Nullable Slot slot, Vec3 aim, @Nullable LivingEntity target) {}

    private static final Map<String, Parser> TYPES = new LinkedHashMap<>();

    public static void register(String type, Parser parser) { TYPES.put(type, parser); }

    public static Action parse(JsonObject o) {
        String type = GsonHelper.getAsString(o, "type");
        Parser p = TYPES.get(type);
        if (p == null) throw new JsonSyntaxException("Unknown action type '" + type + "'. Known: " + TYPES.keySet());
        return p.parse(o);
    }

    private static ResourceLocation rl(JsonObject j, String key) { return new ResourceLocation(GsonHelper.getAsString(j, key)); }

    private static Vec3 flat(Vec3 aim, ServerPlayer p) {
        Vec3 f = new Vec3(aim.x, 0, aim.z);
        if (f.lengthSqr() < 1e-4) f = Vec3.directionFromRotation(0, p.getYRot());
        return f.normalize();
    }

    static {
        // ---- presentation -------------------------------------------------------------------------------------
        register("animation", j -> {
            ResourceLocation anim = j.has("anim") ? rl(j, "anim") : null;  // omit "anim" to clear the layer
            return c -> NetworkHandler.sendToTrackingAndSelf(c.player(), new PlayAnimPacket(c.player().getId(), anim));
        });

        register("fx", j -> {
            ResourceLocation fx = rl(j, "fx");
            String at = GsonHelper.getAsString(j, "at", "self");        // self | target | front
            double dist = GsonHelper.getAsDouble(j, "distance", 2.0);
            return c -> {
                ServerPlayer p = c.player();
                PlayFxPacket pkt;
                if (at.equals("target")) {
                    if (c.target() == null) return;
                    pkt = new PlayFxPacket(fx, c.target().getId(), c.target().position());
                } else if (at.equals("front")) {
                    pkt = new PlayFxPacket(fx, -1, p.position().add(flat(c.aim(), p).scale(dist)));
                } else {
                    pkt = new PlayFxPacket(fx, p.getId(), p.position());
                }
                NetworkHandler.sendToTrackingAndSelf(p, pkt);
            };
        });

        register("sound", j -> {
            ResourceLocation id = rl(j, "sound");
            float vol = GsonHelper.getAsFloat(j, "volume", 1f), pitch = GsonHelper.getAsFloat(j, "pitch", 1f);
            return c -> {
                SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(id);
                if (se != null) c.player().level().playSound(null, c.player().blockPosition(), se, SoundSource.PLAYERS, vol, pitch);
            };
        });

        register("camera", j -> {
            ResourceLocation id = rl(j, "cutscene");
            return c -> NetworkHandler.sendTo(c.player(), new CutscenePacket(id));
        });

        // ---- movement / defence ------------------------------------------------------------------------------
        register("dash", j -> {
            double speed = GsonHelper.getAsDouble(j, "speed", 1.0), up = GsonHelper.getAsDouble(j, "vertical", 0.0);
            boolean back = "back".equals(GsonHelper.getAsString(j, "direction", "forward"));
            return c -> {
                Vec3 f = flat(c.aim(), c.player());
                if (back) f = f.scale(-1);
                c.player().setDeltaMovement(f.x * speed, up, f.z * speed);
                c.player().hurtMarked = true;
                c.player().fallDistance = 0;
            };
        });

        register("invuln", j -> {
            int ticks = GsonHelper.getAsInt(j, "ticks", 20);
            return c -> c.data().invulnTicks = Math.max(c.data().invulnTicks, ticks);
        });

        // ---- resources ---------------------------------------------------------------------------------------
        register("energy", j -> {
            float amt = GsonHelper.getAsFloat(j, "amount");
            return c -> c.data().addEnergy(amt);
        });

        register("cooldown_reduce", j -> {
            float ticks = GsonHelper.getAsFloat(j, "ticks", 0f), frac = GsonHelper.getAsFloat(j, "fraction", 0f);
            return c -> c.data().reduceCooldowns(ticks, frac);
        });

        register("buff", j -> {
            String id = GsonHelper.getAsString(j, "id", "buff");
            float dmg = GsonHelper.getAsFloat(j, "damage_mult", 1f), cdr = GsonHelper.getAsFloat(j, "cooldown_rate", 1f),
                    en = GsonHelper.getAsFloat(j, "energy_mult", 1f);
            int dur = GsonHelper.getAsInt(j, "duration", 100);
            return c -> c.data().addBuff(id, dmg, cdr, en, dur);
        });

        register("heal", j -> {
            float amt = GsonHelper.getAsFloat(j, "amount");
            return c -> c.player().heal(amt);
        });

        register("mob_effect", j -> {
            ResourceLocation id = rl(j, "effect");
            int dur = GsonHelper.getAsInt(j, "duration", 100), amp = GsonHelper.getAsInt(j, "amplifier", 0);
            boolean onTarget = "target".equals(GsonHelper.getAsString(j, "on", "self"));
            return c -> {
                MobEffect me = ForgeRegistries.MOB_EFFECTS.getValue(id);
                LivingEntity who = onTarget ? c.target() : c.player();
                if (me != null && who != null) who.addEffect(new MobEffectInstance(me, dur, amp));
            };
        });

        register("transform", j -> {
            int dur = GsonHelper.getAsInt(j, "duration", 600);
            boolean end = GsonHelper.getAsBoolean(j, "end", false);
            return c -> {
                if (end) CombatController.endTransform(c.player(), c.data());
                else CombatController.beginTransform(c.player(), c.data(), dur);
            };
        });

        // ---- damage ------------------------------------------------------------------------------------------
        register("hitbox", Actions::parseHitbox);
    }

    private static Action parseHitbox(JsonObject j) {
        double range = GsonHelper.getAsDouble(j, "range", 3.0), angle = GsonHelper.getAsDouble(j, "angle", 120.0);
        double height = GsonHelper.getAsDouble(j, "height", 2.5), offset = GsonHelper.getAsDouble(j, "offset", 0.0);
        float mult = GsonHelper.getAsFloat(j, "damage", 1f), kb = GsonHelper.getAsFloat(j, "knockback", 0.4f);
        boolean guardBreak = GsonHelper.getAsBoolean(j, "guard_break", false);
        int stun = GsonHelper.getAsInt(j, "stun", 0), max = GsonHelper.getAsInt(j, "max_targets", 16);
        double cosHalf = Math.cos(Math.toRadians(angle / 2.0));
        boolean full = angle >= 359;

        return c -> {
            ServerPlayer p = c.player();
            Vec3 flat = flat(c.aim(), p);
            Vec3 origin = p.position().add(flat.scale(offset));
            AABB box = new AABB(origin.x - range, origin.y - 1, origin.z - range, origin.x + range, origin.y + height, origin.z + range);
            List<LivingEntity> targets = new ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != p && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(p) && !(e instanceof ArmorStand a && a.isMarker())));
            targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));

            int n = 0;
            for (LivingEntity t : targets) {
                if (n >= max) break;
                double dx = t.getX() - origin.x, dz = t.getZ() - origin.z, dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > range + t.getBbWidth() / 2) continue;
                if (!full && dist > 0.01 && (dx * flat.x + dz * flat.z) / dist < cosHalf) continue;
                n++;

                float dmg = (float) (c.character().baseAttack * mult * c.data().damageMultiplier());
                if (EnmityConfig.IGNORE_HURT_COOLDOWN.get()) t.invulnerableTime = 0;
                GuardBreakContext.with(guardBreak, () -> t.hurt(p.damageSources().playerAttack(p), dmg));
                if (kb > 0) t.knockback(kb, p.getX() - t.getX(), p.getZ() - t.getZ());
                if (stun > 0) t.addEffect(new MobEffectInstance(com.enmitycombat.effect.EnmityEffects.STUN.get(), stun, 0, false, true));
                CombatController.onHit(c, t);
            }
        };
    }

    private Actions() {}
}
