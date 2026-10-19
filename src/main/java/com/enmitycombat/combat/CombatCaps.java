package com.enmitycombat.combat;

import com.enmitycombat.EnmityCombat;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;

public final class CombatCaps {
    public static final Capability<CombatData> COMBAT = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation KEY = EnmityCombat.id("combat");

    private static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final CombatData data = new CombatData();
        private final LazyOptional<CombatData> opt = LazyOptional.of(() -> data);
        @Nonnull @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) { return COMBAT.orEmpty(cap, opt); }
        @Override public CompoundTag serializeNBT() { return data.save(); }
        @Override public void deserializeNBT(CompoundTag nbt) { data.load(nbt); }
    }

    @Mod.EventBusSubscriber(modid = EnmityCombat.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent e) { e.register(CombatData.class); }
    }

    @Mod.EventBusSubscriber(modid = EnmityCombat.MODID)
    public static class ForgeBus {
        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> e) {
            if (e.getObject() instanceof Player) e.addCapability(KEY, new Provider());
        }

        @SubscribeEvent
        public static void clone(PlayerEvent.Clone e) {
            e.getOriginal().reviveCaps();
            CombatData.get(e.getEntity()).copyPersistent(CombatData.get(e.getOriginal()));
            e.getOriginal().invalidateCaps();
        }
    }
    private CombatCaps() {}
}
