package com.enmitycombat.network;

import com.enmitycombat.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** anim == null clears the combat animation layer. */
public record PlayAnimPacket(int entityId, @Nullable ResourceLocation anim) {
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entityId); b.writeBoolean(anim != null);
        if (anim != null) b.writeResourceLocation(anim);
    }
    public static PlayAnimPacket decode(FriendlyByteBuf b) {
        int id = b.readVarInt();
        return new PlayAnimPacket(id, b.readBoolean() ? b.readResourceLocation() : null);
    }
    public static void handle(PlayAnimPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.playAnim(m.entityId(), m.anim())));
        ctx.setPacketHandled(true);
    }
}
