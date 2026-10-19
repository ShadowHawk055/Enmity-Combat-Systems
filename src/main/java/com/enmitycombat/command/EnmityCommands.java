package com.enmitycombat.command;

import com.enmitycombat.EnmityCombat;
import com.enmitycombat.combat.CombatController;
import com.enmitycombat.combat.CombatData;
import com.enmitycombat.data.DataManager;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EnmityCombat.MODID)
public final class EnmityCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var root = e.getDispatcher().register(Commands.literal("enmity").requires(s -> s.hasPermission(2))
            .then(Commands.literal("character")
                .then(Commands.argument("id", ResourceLocationArgument.id())
                    .suggests((c, b) -> SharedSuggestionProvider.suggestResource(DataManager.CHARACTERS.ids(), b))
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        ResourceLocation id = ResourceLocationArgument.getId(c, "id");
                        if (DataManager.CHARACTERS.get(id) == null) { c.getSource().sendFailure(Component.literal("Unknown character " + id)); return 0; }
                        CombatController.setCharacter(p, CombatData.get(p), id);
                        c.getSource().sendSuccess(() -> Component.literal("Character set to " + id), false);
                        return 1;
                    })))
            .then(Commands.literal("clear").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                CombatController.setCharacter(p, CombatData.get(p), null);
                return 1;
            }))
            .then(Commands.literal("energy").then(Commands.argument("amount", FloatArgumentType.floatArg(0)).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                CombatData d = CombatData.get(p);
                d.energy = 0; d.addEnergy(FloatArgumentType.getFloat(c, "amount"));
                return 1;
            })))
            .then(Commands.literal("resetcooldowns").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                CombatData d = CombatData.get(p);
                d.cooldowns.clear(); d.markDirty();
                return 1;
            })));
        e.getDispatcher().register(Commands.literal("enmitycombat").requires(src -> src.hasPermission(2)).redirect(root));
    }
    private EnmityCommands() {}
}
