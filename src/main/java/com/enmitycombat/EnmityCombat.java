package com.enmitycombat;

import com.enmitycombat.effect.EnmityEffects;
import com.enmitycombat.network.NetworkHandler;
import com.enmitycombat.pack.PackFolder;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(EnmityCombat.MODID)
public class EnmityCombat {
    public static final String MODID = "enmitycombat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EnmityCombat() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        PackFolder.ensureCreated();
        EnmityEffects.EFFECTS.register(bus);
        bus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(NetworkHandler::register));
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EnmityConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
