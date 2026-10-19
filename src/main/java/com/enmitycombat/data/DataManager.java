package com.enmitycombat.data;

import com.enmitycombat.EnmityCombat;
import com.enmitycombat.network.NetworkHandler;
import com.enmitycombat.network.SyncDefsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EnmityCombat.MODID)
public final class DataManager {
    public static final DataRegistry<CharacterDef> CHARACTERS = new DataRegistry<>("characters", CharacterDef::parse);
    public static final DataRegistry<SkillDef> SKILLS = new DataRegistry<>("skills", SkillDef::parse);
    public static final DataRegistry<CutsceneDef> CUTSCENES = new DataRegistry<>("cutscenes", CutsceneDef::parse);

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent e) {
        e.addListener(CHARACTERS);
        e.addListener(SKILLS);
        e.addListener(CUTSCENES);
    }

    @SubscribeEvent
    public static void onSync(OnDatapackSyncEvent e) {
        if (e.getPlayer() != null) send(e.getPlayer());
        else e.getPlayerList().getPlayers().forEach(DataManager::send);
    }

    public static void send(ServerPlayer p) {
        NetworkHandler.sendTo(p, new SyncDefsPacket(CHARACTERS.raw(), SKILLS.raw(), CUTSCENES.raw()));
    }
    private DataManager() {}
}
