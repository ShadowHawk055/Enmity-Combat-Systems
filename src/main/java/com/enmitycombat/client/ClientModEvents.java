package com.enmitycombat.client;

import com.enmitycombat.EnmityCombat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = EnmityCombat.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { for (var k : KeyBindings.ALL) e.register(k); }
    @SubscribeEvent public static void overlays(RegisterGuiOverlaysEvent e) { e.registerAboveAll("hud", HudOverlay.HUD); }
    /** Rigs and rig animations are re-read on every resource reload (F3+T). */
    @SubscribeEvent
    public static void reloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.enmitycombat.client.rig.RigData::reload);
    }

    /** Adds the prop layer to both player skins (default + slim). */
    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            PlayerRenderer r = e.getSkin(skin);
            if (r != null) r.addLayer(new PropLayer(r));
        }
    }

    /** Registers every model under assets/<ns>/models/prop/ (from the mod AND from content packs) so props can use them. */
    @SubscribeEvent
    public static void propModels(ModelEvent.RegisterAdditional e) {
        var found = Minecraft.getInstance().getResourceManager().listResources("models/prop", rl -> rl.getPath().endsWith(".json"));
        for (ResourceLocation rl : found.keySet()) {
            String path = rl.getPath();
            e.register(new ResourceLocation(rl.getNamespace(), path.substring("models/".length(), path.length() - ".json".length())));
        }
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent e) { e.enqueueWork(() -> { AnimBridge.init(); ModCompat.logSummary(); }); }
    private ClientModEvents() {}
}
