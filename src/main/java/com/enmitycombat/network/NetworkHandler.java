package com.enmitycombat.network;

import com.enmitycombat.EnmityCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            EnmityCombat.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int i = 0;
        CHANNEL.registerMessage(i++, InputPacket.class, InputPacket::encode, InputPacket::decode, InputPacket::handle);
        CHANNEL.registerMessage(i++, SyncDataPacket.class, SyncDataPacket::encode, SyncDataPacket::decode, SyncDataPacket::handle);
        CHANNEL.registerMessage(i++, PlayAnimPacket.class, PlayAnimPacket::encode, PlayAnimPacket::decode, PlayAnimPacket::handle);
        CHANNEL.registerMessage(i++, PlayFxPacket.class, PlayFxPacket::encode, PlayFxPacket::decode, PlayFxPacket::handle);
        CHANNEL.registerMessage(i++, CutscenePacket.class, CutscenePacket::encode, CutscenePacket::decode, CutscenePacket::handle);
        CHANNEL.registerMessage(i++, SelectCharacterPacket.class, SelectCharacterPacket::encode, SelectCharacterPacket::decode, SelectCharacterPacket::handle);
        CHANNEL.registerMessage(i++, CombatVisualPacket.class, CombatVisualPacket::encode, CombatVisualPacket::decode, CombatVisualPacket::handle);
        CHANNEL.registerMessage(i++, SyncDefsPacket.class, SyncDefsPacket::encode, SyncDefsPacket::decode, SyncDefsPacket::handle);
    }

    public static void sendTo(ServerPlayer p, Object msg) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg); }
    public static void sendToTrackingAndSelf(Entity e, Object msg) { CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg); }
    private NetworkHandler() {}
}
