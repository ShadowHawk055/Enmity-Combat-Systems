package com.enmitycombat.client;

import com.enmitycombat.client.cutscene.CutsceneManager;
import com.enmitycombat.data.DataManager;
import com.enmitycombat.network.PlayFxPacket;
import com.enmitycombat.network.SyncDefsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

public final class ClientPacketHandler {
    public static void syncData(CompoundTag tag) { ClientState.data.loadSync(tag); }

    public static void playAnim(int entityId, @Nullable ResourceLocation anim) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(entityId) instanceof AbstractClientPlayer p) AnimBridge.play(p, anim);
    }

    public static void playFx(PlayFxPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = m.entityId() >= 0 ? mc.level.getEntity(m.entityId()) : null;
        FxBridge.play(mc.level, m.fx(), e, m.pos());
    }

    public static void cutscene(@Nullable ResourceLocation id) {
        if (id == null) CutsceneManager.stop(); else CutsceneManager.start(id);
    }

    public static void defs(SyncDefsPacket m) {
        DataManager.CHARACTERS.loadRaw(m.characters());
        DataManager.SKILLS.loadRaw(m.skills());
        DataManager.CUTSCENES.loadRaw(m.cutscenes());
    }
    private ClientPacketHandler() {}
}
