package com.enmitycombat.client;

import com.enmitycombat.EnmityCombat;
import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * ALL Photon calls live in this one class.
 * FX files are made in the Photon editor, exported as .fx and referenced as "<namespace>:<name>"
 * (assets/<namespace>/fx/<name>.fx). If an FX does not exist yet, a vanilla particle is spawned instead
 * and a warning is logged ONCE per id, so you can see in latest.log which ids are still missing.
 */
public final class FxBridge {
    private static final Set<ResourceLocation> WARNED = new HashSet<>();

    public static void play(Level level, ResourceLocation id, @Nullable Entity entity, Vec3 pos) {
        FX fx = null;
        try { fx = FXHelper.getFX(id); } catch (Throwable t) { EnmityCombat.LOGGER.debug("Photon lookup failed for {}: {}", id, t.toString()); }

        if (fx == null) {
            if (WARNED.add(id)) EnmityCombat.LOGGER.warn("[Enmity] FX '{}' not found (expected assets/{}/fx/{}.fx) - using placeholder particle",
                    id, id.getNamespace(), id.getPath());
            Vec3 at = entity != null ? entity.position() : pos;
            level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y + 1.0, at.z, 0, 0, 0);
            return;
        }
        if (entity != null) startEntityEffect(fx, level, entity);
        else new BlockEffect(fx, level, BlockPos.containing(pos)).start();
    }

    /** Photon's EntityEffect wants an AutoRotate option whose exact enum differs between versions, so pick NONE by reflection. */
    private static void startEntityEffect(FX fx, Level level, Entity entity) {
        try {
            for (var c : EntityEffect.class.getConstructors()) {
                Class<?>[] t = c.getParameterTypes();
                if (t.length == 4 && t[0] == FX.class && t[3].isEnum()) {
                    Object[] options = t[3].getEnumConstants();
                    Object pick = options[0];
                    for (Object o : options) if (((Enum<?>) o).name().equals("NONE")) pick = o;
                    ((EntityEffect) c.newInstance(fx, level, entity, pick)).start();
                    return;
                }
            }
            EnmityCombat.LOGGER.warn("No usable EntityEffect constructor found");
        } catch (Exception ex) {
            EnmityCombat.LOGGER.warn("Failed to start entity effect: {}", ex.toString());
        }
    }
    private FxBridge() {}
}
