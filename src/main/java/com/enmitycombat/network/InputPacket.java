package com.enmitycombat.network;

import com.enmitycombat.combat.CombatController;
import com.enmitycombat.combat.CombatInput;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S. dir = world-space aim (skills) or desired dodge direction (dodge). Built from the CAMERA so camera mods just work. */
public record InputPacket(CombatInput input, Vec3 dir) {
    public void encode(FriendlyByteBuf b) { b.writeEnum(input); b.writeDouble(dir.x); b.writeDouble(dir.y); b.writeDouble(dir.z); }

    public static InputPacket decode(FriendlyByteBuf b) {
        CombatInput in = b.readEnum(CombatInput.class);
        double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
        Vec3 v = (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) ? new Vec3(x, y, z) : Vec3.ZERO;
        return new InputPacket(in, v);
    }

    public static void handle(InputPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p != null) CombatController.handleInput(p, m.input(), m.dir());
        });
        ctx.setPacketHandled(true);
    }
}
