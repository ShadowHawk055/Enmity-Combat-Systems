package com.enmitycombat.network;

import com.enmitycombat.client.ClientVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/** S2C: which character a player has equipped + prop visibility flags, so everyone can render the props. */
public record CombatVisualPacket(UUID player, @Nullable ResourceLocation character, int flags) {
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(player); b.writeBoolean(character != null);
        if (character != null) b.writeResourceLocation(character);
        b.writeVarInt(flags);
    }
    public static CombatVisualPacket decode(FriendlyByteBuf b) {
        UUID id = b.readUUID();
        ResourceLocation ch = b.readBoolean() ? b.readResourceLocation() : null;
        return new CombatVisualPacket(id, ch, b.readVarInt());
    }
    public static void handle(CombatVisualPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVisuals.set(m.player(), m.character(), m.flags())));
        ctx.setPacketHandled(true);
    }
}
