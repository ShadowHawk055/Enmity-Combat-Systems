package com.enmitycombat.network;

import com.enmitycombat.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Ships the raw datapack JSON (characters, skills, cutscenes) so the client can parse the same definitions. */
public record SyncDefsPacket(Map<ResourceLocation, String> characters, Map<ResourceLocation, String> skills, Map<ResourceLocation, String> cutscenes) {
    private static final int MAX = 262144;

    private static void write(FriendlyByteBuf b, Map<ResourceLocation, String> m) {
        b.writeVarInt(m.size());
        m.forEach((k, v) -> { b.writeResourceLocation(k); b.writeUtf(v, MAX); });
    }
    private static Map<ResourceLocation, String> read(FriendlyByteBuf b) {
        int n = b.readVarInt();
        Map<ResourceLocation, String> m = new HashMap<>();
        for (int i = 0; i < n; i++) m.put(b.readResourceLocation(), b.readUtf(MAX));
        return m;
    }
    public void encode(FriendlyByteBuf b) { write(b, characters); write(b, skills); write(b, cutscenes); }
    public static SyncDefsPacket decode(FriendlyByteBuf b) { return new SyncDefsPacket(read(b), read(b), read(b)); }
    public static void handle(SyncDefsPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.defs(m)));
        ctx.setPacketHandled(true);
    }
}
