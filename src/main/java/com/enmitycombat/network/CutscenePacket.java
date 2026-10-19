package com.enmitycombat.network;

import com.enmitycombat.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** id == null stops the running cutscene. */
public record CutscenePacket(@Nullable ResourceLocation id) {
    public void encode(FriendlyByteBuf b) { b.writeBoolean(id != null); if (id != null) b.writeResourceLocation(id); }
    public static CutscenePacket decode(FriendlyByteBuf b) { return new CutscenePacket(b.readBoolean() ? b.readResourceLocation() : null); }
    public static void handle(CutscenePacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.cutscene(m.id())));
        ctx.setPacketHandled(true);
    }
}
