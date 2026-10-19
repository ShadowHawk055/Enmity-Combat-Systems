package com.enmitycombat.network;

import com.enmitycombat.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** entityId >= 0: attach to that entity. Otherwise play at pos. */
public record PlayFxPacket(ResourceLocation fx, int entityId, Vec3 pos) {
    public void encode(FriendlyByteBuf b) {
        b.writeResourceLocation(fx); b.writeVarInt(entityId + 1);
        b.writeDouble(pos.x); b.writeDouble(pos.y); b.writeDouble(pos.z);
    }
    public static PlayFxPacket decode(FriendlyByteBuf b) {
        ResourceLocation fx = b.readResourceLocation(); int id = b.readVarInt() - 1;
        return new PlayFxPacket(fx, id, new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
    }
    public static void handle(PlayFxPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.playFx(m)));
        ctx.setPacketHandled(true);
    }
}
