package com.enmitycombat.network;

import com.enmitycombat.client.ClientPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncDataPacket(CompoundTag tag) {
    public void encode(FriendlyByteBuf b) { b.writeNbt(tag); }
    public static SyncDataPacket decode(FriendlyByteBuf b) { return new SyncDataPacket(b.readNbt()); }
    public static void handle(SyncDataPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.syncData(m.tag())));
        ctx.setPacketHandled(true);
    }
}
