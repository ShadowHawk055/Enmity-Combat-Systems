package com.enmitycombat.data;

import com.enmitycombat.EnmityCombat;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;

/** A datapack folder loaded into typed objects. The raw JSON is kept so it can be synced to clients. */
public class DataRegistry<T> extends SimpleJsonResourceReloadListener {
    private final String name;
    private final BiFunction<ResourceLocation, JsonObject, T> parser;
    private volatile Map<ResourceLocation, T> map = new HashMap<>();
    private volatile Map<ResourceLocation, String> raw = new HashMap<>();

    public DataRegistry(String folder, BiFunction<ResourceLocation, JsonObject, T> parser) {
        super(new Gson(), folder);
        this.name = folder;
        this.parser = parser;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> in, ResourceManager rm, ProfilerFiller pf) {
        Map<ResourceLocation, String> r = new HashMap<>();
        in.forEach((id, el) -> r.put(id, el.toString()));
        loadRaw(r);
    }

    /** Used by both the server reload and the client sync packet. */
    public void loadRaw(Map<ResourceLocation, String> in) {
        Map<ResourceLocation, T> m = new HashMap<>();
        Map<ResourceLocation, String> r = new HashMap<>();
        in.forEach((id, json) -> {
            try {
                JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                m.put(id, parser.apply(id, o));
                r.put(id, json);
            } catch (Exception ex) {
                EnmityCombat.LOGGER.error("[{}] Failed to parse {}: {}", name, id, ex.toString());
            }
        });
        this.map = m;
        this.raw = r;
        EnmityCombat.LOGGER.info("[{}] loaded {} entries", name, m.size());
    }

    @Nullable public T get(@Nullable ResourceLocation id) { return id == null ? null : map.get(id); }
    public Collection<T> values() { return map.values(); }
    public Set<ResourceLocation> ids() { return map.keySet(); }
    public Map<ResourceLocation, String> raw() { return raw; }
}
