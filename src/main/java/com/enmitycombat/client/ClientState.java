package com.enmitycombat.client;

import com.enmitycombat.combat.CombatData;

/** The local player's combat state as last synced by the server. */
public final class ClientState {
    public static final CombatData data = new CombatData();
    private ClientState() {}
}
