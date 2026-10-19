package com.enmitycombat.network;

import com.enmitycombat.EnmityConfig;
import com.enmitycombat.combat.CombatController;
import com.enmitycombat.combat.CombatData;
import com.enmitycombat.data.DataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: "Equip" button in the codex. */
public record SelectCharacterPacket(ResourceLocation id) {
    public void encode(FriendlyByteBuf b) { b.writeResourceLocation(id); }
    public static SelectCharacterPacket decode(FriendlyByteBuf b) { return new SelectCharacterPacket(b.readResourceLocation()); }

    public static void handle(SelectCharacterPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null || DataManager.CHARACTERS.get(m.id()) == null) return;
            if (!EnmityConfig.ALLOW_SELF_SELECT.get() && !p.hasPermissions(2)) return;
            CombatController.setCharacter(p, CombatData.get(p), m.id());
        });
        ctx.setPacketHandled(true);
    }
}
