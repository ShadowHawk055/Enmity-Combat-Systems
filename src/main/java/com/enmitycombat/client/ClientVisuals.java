package com.enmitycombat.client;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Equipped character + flags for every player this client can see (flags: 1 combat, 2 transformed, 4 drawn). */
public final class ClientVisuals {
    public record Visual(ResourceLocation character, int flags) {}
    private static final Map<UUID, Visual> MAP = new HashMap<>();

    public static void set(UUID id, @Nullable ResourceLocation character, int flags) {
        if (character == null) MAP.remove(id); else MAP.put(id, new Visual(character, flags));
    }
    @Nullable public static Visual get(UUID id) { return MAP.get(id); }
    public static void clear() { MAP.clear(); }
    private ClientVisuals() {}
}
