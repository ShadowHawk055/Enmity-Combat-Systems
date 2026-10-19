package com.enmitycombat.effect;

import com.enmitycombat.EnmityCombat;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class EnmityEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, EnmityCombat.MODID);
    public static final RegistryObject<MobEffect> STUN = EFFECTS.register("stun", StunEffect::new);
    private EnmityEffects() {}
}
