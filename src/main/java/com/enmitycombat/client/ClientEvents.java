package com.enmitycombat.client;

import com.enmitycombat.EnmityConfig;
import com.enmitycombat.EnmityCombat;
import com.enmitycombat.client.cutscene.CutsceneManager;
import com.enmitycombat.combat.CombatInput;
import com.enmitycombat.network.InputPacket;
import com.enmitycombat.network.NetworkHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EnmityCombat.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean blockWasDown, ultWasDown, ultHoldSent;
    private static int ultTicks, attackRepeat;

    /** Resolve the aim from the camera, turn the player model to it, send it. */
    static void sendAim(CombatInput in, Minecraft mc) {
        Vec3 aim = AimResolver.resolve(mc);
        faceClient(mc, aim);
        send(in, aim);
    }

    /**
     * The client is authoritative for the player's facing. With a vanilla camera the aim already equals the player's yaw,
     * but with decoupled cameras (Shoulder Surfing, Focus, Leawind's Third Person) the model would otherwise keep facing
     * wherever that mod turned it, so skills would visibly swing the wrong way.
     */
    static void faceClient(Minecraft mc, Vec3 aim) {
        LocalPlayer p = mc.player;
        if (p == null || Math.abs(aim.x) + Math.abs(aim.z) < 1e-4) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        p.setYRot(yaw); p.yRotO = yaw;
        p.setYHeadRot(yaw); p.yHeadRotO = yaw;
        p.setYBodyRot(yaw); p.yBodyRotO = yaw;
    }

    static void send(CombatInput in, Vec3 dir) { NetworkHandler.CHANNEL.sendToServer(new InputPacket(in, dir)); }

    private static void keyPress(KeyMapping k, CombatInput in, boolean enabled, Minecraft mc) {
        while (k.consumeClick()) if (enabled) sendAim(in, mc);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();

        if (e.phase == TickEvent.Phase.END) {
            ClientState.data.tickCooldowns(ClientState.data.cooldownRate);
            CutsceneManager.tick();
            if (ClientState.data.isActive() && mc.gameMode != null) mc.gameMode.stopDestroyBlock();   // no block breaking in combat mode
            return;
        }
        if (mc.player == null || mc.level == null) { blockWasDown = ultWasDown = false; return; }

        boolean active = ClientState.data.isActive();
        boolean enabled = active && mc.screen == null && !CutsceneManager.isActive();

        // Combat mode takes over the vanilla keys that share a binding with ours (1/2/3 hotbar, E inventory, Q drop).
        // Our tick START runs before vanilla's handleKeybinds, so we swallow their clicks first.
        if (active) {
            for (KeyMapping k : mc.options.keyHotbarSlots) while (k.consumeClick()) { }
            while (mc.options.keyInventory.consumeClick()) { }
            while (mc.options.keyDrop.consumeClick()) { }
        }

        while (KeyBindings.TOGGLE.consumeClick()) send(CombatInput.TOGGLE_COMBAT, Vec3.ZERO);
        while (KeyBindings.CODEX.consumeClick()) if (mc.screen == null && !CutsceneManager.isActive()) mc.setScreen(new CodexScreen());
        while (KeyBindings.INVENTORY.consumeClick()) if (active && mc.screen == null) mc.setScreen(new InventoryScreen(mc.player));

        keyPress(KeyBindings.SKILL1, CombatInput.SKILL1, enabled, mc);
        keyPress(KeyBindings.SKILL2, CombatInput.SKILL2, enabled, mc);
        keyPress(KeyBindings.SKILL3, CombatInput.SKILL3, enabled, mc);
        keyPress(KeyBindings.SPECIAL, CombatInput.SPECIAL, enabled, mc);
        keyPress(KeyBindings.HEAVY, CombatInput.HEAVY, enabled, mc);
        while (KeyBindings.DODGE.consumeClick()) if (enabled) send(CombatInput.DODGE, AimResolver.dodgeDir(mc));
        while (KeyBindings.BLOCK.consumeClick()) { }

        // hold-to-block: send edges only
        boolean blockDown = enabled && KeyBindings.BLOCK.isDown();
        if (blockDown != blockWasDown) { send(blockDown ? CombatInput.BLOCK_DOWN : CombatInput.BLOCK_UP, Vec3.ZERO); blockWasDown = blockDown; }

        // ultimate: tap = ultimate, hold = transform ultimate
        while (KeyBindings.ULTIMATE.consumeClick()) { }
        boolean ultDown = enabled && KeyBindings.ULTIMATE.isDown();
        if (ultDown) {
            if (!ultWasDown) { ultTicks = 0; ultHoldSent = false; }
            if (!ultHoldSent && ++ultTicks >= EnmityConfig.ULT_HOLD_TICKS.get()) { sendAim(CombatInput.ULT_HOLD, mc); ultHoldSent = true; }
        } else if (ultWasDown && enabled && !ultHoldSent) {
            sendAim(CombatInput.ULT_TAP, mc);
        }
        ultWasDown = ultDown;

        // hold left mouse to keep chaining basic attacks
        if (enabled && mc.options.keyAttack.isDown()) { if (++attackRepeat % 5 == 0) sendAim(CombatInput.BASIC, mc); }
        else attackRepeat = 0;

        // vanilla camera only: camera mods + MouseHandlerMixin already stop the turning
        if (CutsceneManager.isActive()) CutsceneManager.lockPlayerRotation(mc.player);
    }

    /** Left click = basic attack. Everything else (use item / place / pick) is disabled in combat mode. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (!ClientState.data.isActive()) return;
        if (e.isAttack() && !CutsceneManager.isActive()) sendAim(CombatInput.BASIC, Minecraft.getInstance());
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onMove(MovementInputUpdateEvent e) {
        if (!CutsceneManager.isActive() && !(ClientState.data.isActive() && ClientState.data.lockFlag)) return;
        var in = e.getInput();
        in.forwardImpulse = 0; in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false; in.shiftKeyDown = false;
    }

    /** LOWEST priority = runs last, so camera mods that also edit FOV / angles cannot override the cutscene. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (!e.usedConfiguredFov() || !CutsceneManager.isOverriding()) return;
        e.setFOV(CutsceneManager.fov((float) e.getFOV()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        CutsceneManager.Pose p = CutsceneManager.appliedPose();
        if (p == null) return;
        e.setYaw(p.yaw());
        e.setPitch(p.pitch());
        e.setRoll(0f);
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        ClientVisuals.clear();
        com.enmitycombat.client.rig.RigPlayback.clear();
        ClientState.data.character = null;
    }

    @SubscribeEvent
    public static void onHideHand(RenderHandEvent e) { if (CutsceneManager.isActive()) e.setCanceled(true); }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (CutsceneManager.isActive() && CutsceneManager.hidesHud() && !e.getOverlay().id().equals(EnmityCombat.id("hud"))) e.setCanceled(true);
    }
    private ClientEvents() {}
}
