package com.enmitycombat.combat;

import com.enmitycombat.EnmityCombat;
import com.enmitycombat.data.DataManager;
import com.enmitycombat.network.NetworkHandler;
import com.enmitycombat.network.SyncDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EnmityCombat.MODID)
public final class ServerEvents {
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer sp) CombatController.tick(sp);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        DataManager.send(sp);
        CombatData d = CombatData.get(sp);
        PassiveManager.refreshAttributes(sp, d);
        NetworkHandler.sendTo(sp, new SyncDataPacket(d.syncTag()));
    }

    /** A player just started seeing another player: tell them what that player is wearing. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer) {
            CombatData d = CombatData.get(target);
            if (d.character != null)
                NetworkHandler.sendTo(viewer, new com.enmitycombat.network.CombatVisualPacket(target.getUUID(), d.character, d.visualFlags()));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CombatData d = CombatData.get(sp);
        d.cooldowns.clear(); d.energy = 0; d.transformTicks = 0;
        PassiveManager.refreshAttributes(sp, d);
        NetworkHandler.sendTo(sp, new SyncDataPacket(d.syncTag()));
    }
    private ServerEvents() {}
}
