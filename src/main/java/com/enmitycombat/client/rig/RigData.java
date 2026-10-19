package com.enmitycombat.client.rig;

import com.enmitycombat.EnmityCombat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.*;

/**
 * Loads rigs (Blockbench Bedrock geometry) and rig animations (Bedrock/GeckoLib animation JSON).
 *
 *   assets/<ns>/rigs/<name>/model.geo.json      geometry (format 1.12.0, "minecraft:geometry")
 *   assets/<ns>/rigs/<name>/texture.png
 *   assets/<ns>/rigs/<name>/rig.json            optional settings
 *   assets/<ns>/rigs/<name>/animations.json     optional: prop-only / extra animations (key's last ".segment" = the name)
 *   assets/<ns>/player_animations/<file>.json   GeckoLib-format files here are ALSO read for their non-player bones
 */
public final class RigData {
    // ------------------------------------------------------------------------------------------ geometry
    public static final class Cube {
        public float[] origin = new float[3], size = new float[3], uv = new float[2];
        public float inflate;
        public boolean mirror;
        @Nullable public float[] rot, pivot;
    }
    public static final class Bone {
        public String name;
        @Nullable public String parent;
        public float[] pivot = new float[3], rot = new float[3];
        public boolean mirror;
        public final List<Cube> cubes = new ArrayList<>();
    }
    public static final class Geo {
        public int texW = 64, texH = 64;
        public final List<Bone> bones = new ArrayList<>();
    }
    /** rig.json. The signs are the calibration knobs if your rotations look mirrored. */
    public static final class Settings {
        public float[] rotSigns = {-1, 1, -1};
        public float[] posSigns = {1, -1, 1};
        @Nullable public ResourceLocation idle, idleDrawn, idleTransformed;
    }

    // ------------------------------------------------------------------------------------------ animation
    public static final class Track {
        final float[] t; final float[][] v; final boolean[] smooth;
        Track(float[] t, float[][] v, boolean[] smooth) { this.t = t; this.v = v; this.smooth = smooth; }
        float last() { return t[t.length - 1]; }

        public float[] sample(float time) {
            int n = t.length;
            if (n == 1 || time <= t[0]) return v[0];
            if (time >= t[n - 1]) return v[n - 1];
            int i = 0;
            while (i < n - 2 && time >= t[i + 1]) i++;
            float f = (time - t[i]) / Math.max(1e-6f, t[i + 1] - t[i]);
            float[] out = new float[3];
            boolean cr = smooth[i] || smooth[i + 1];
            float[] p0 = v[Math.max(i - 1, 0)], p1 = v[i], p2 = v[i + 1], p3 = v[Math.min(i + 2, n - 1)];
            for (int k = 0; k < 3; k++) {
                out[k] = cr
                    ? 0.5f * ((2 * p1[k]) + (-p0[k] + p2[k]) * f + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * f * f
                              + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * f * f * f)
                    : p1[k] + (p2[k] - p1[k]) * f;
            }
            return out;
        }
    }
    public static final class BoneAnim { @Nullable public Track pos, rot, scale; }
    public static final class Anim {
        public final String name; public float length; public int loop;   // 0 once, 1 loop, 2 hold last frame
        public final Map<String, BoneAnim> bones = new HashMap<>();
        Anim(String name) { this.name = name; }
    }

    // ------------------------------------------------------------------------------------------ registries
    /** key = "<namespace>:<name>", the same id you put in a skill's "animation" action. */
    public static final Map<String, Anim> ANIMS = new HashMap<>();
    private static final Map<ResourceLocation, Optional<RigInstance>> INSTANCES = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    @Nullable
    public static RigInstance instance(ResourceLocation id) {
        Optional<RigInstance> o = INSTANCES.get(id);
        if (o == null) { o = Optional.ofNullable(load(id)); INSTANCES.put(id, o); }
        return o.orElse(null);
    }

    private static RigInstance load(ResourceLocation id) {
        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            String base = "rigs/" + id.getPath() + "/";
            Geo geo = parseGeo(read(rm, new ResourceLocation(id.getNamespace(), base + "model.geo.json")));
            Settings s = new Settings();
            ResourceLocation setRl = new ResourceLocation(id.getNamespace(), base + "rig.json");
            if (rm.getResource(setRl).isPresent()) parseSettings(read(rm, setRl), s);
            return RigInstance.bake(geo, new ResourceLocation(id.getNamespace(), base + "texture.png"), s);
        } catch (Exception e) {
            if (WARNED.add(id.toString())) EnmityCombat.LOGGER.warn("[Enmity] Could not load rig '{}' (expected assets/{}/rigs/{}/model.geo.json): {}",
                    id, id.getNamespace(), id.getPath(), e.toString());
            return null;
        }
    }

    /** Called on every resource reload (F3+T). */
    public static void reload(ResourceManager rm) {
        INSTANCES.clear();
        ANIMS.clear();
        WARNED.clear();
        for (String folder : new String[]{"player_animations", "player_animation"}) {
            for (var e : rm.listResources(folder, p -> p.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation rl = e.getKey();
                String file = rl.getPath().substring(folder.length() + 1, rl.getPath().length() - 5);
                try (Reader r = e.getValue().openAsReader()) {
                    JsonElement el = JsonParser.parseReader(r);
                    if (!el.isJsonObject() || !el.getAsJsonObject().has("animations")) continue;   // Emotecraft-format file: not ours
                    for (var a : el.getAsJsonObject().getAsJsonObject("animations").entrySet()) {
                        ANIMS.put(rl.getNamespace() + ":" + file, parseAnim(a.getKey(), a.getValue().getAsJsonObject()));
                        break;   // one animation per file
                    }
                } catch (Exception ex) { /* not for us */ }
            }
        }
        for (var e : rm.listResources("rigs", p -> p.getPath().endsWith("/animations.json")).entrySet()) {
            ResourceLocation rl = e.getKey();
            try (Reader r = e.getValue().openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                for (var a : o.getAsJsonObject("animations").entrySet()) {
                    String key = a.getKey();
                    ANIMS.put(rl.getNamespace() + ":" + key.substring(key.lastIndexOf('.') + 1), parseAnim(key, a.getValue().getAsJsonObject()));
                }
            } catch (Exception ex) {
                EnmityCombat.LOGGER.warn("[Enmity] Bad rig animation file {}: {}", rl, ex.toString());
            }
        }
        EnmityCombat.LOGGER.info("[Enmity] Loaded {} rig animation(s)", ANIMS.size());
    }

    // ------------------------------------------------------------------------------------------ parsing
    private static JsonObject read(ResourceManager rm, ResourceLocation rl) throws Exception {
        try (Reader r = rm.getResourceOrThrow(rl).openAsReader()) { return JsonParser.parseReader(r).getAsJsonObject(); }
    }

    private static void parseSettings(JsonObject o, Settings s) {
        if (o.has("rotation_signs")) s.rotSigns = f3(o.get("rotation_signs"), s.rotSigns);
        if (o.has("position_signs")) s.posSigns = f3(o.get("position_signs"), s.posSigns);
        if (o.has("idle")) s.idle = new ResourceLocation(o.get("idle").getAsString());
        if (o.has("idle_drawn")) s.idleDrawn = new ResourceLocation(o.get("idle_drawn").getAsString());
        if (o.has("idle_transformed")) s.idleTransformed = new ResourceLocation(o.get("idle_transformed").getAsString());
    }

    static Geo parseGeo(JsonObject root) {
        if (!root.has("minecraft:geometry"))
            throw new IllegalArgumentException("unsupported geometry format: export as Bedrock geometry version 1.12.0 (needs a \"minecraft:geometry\" list)");
        JsonObject g = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Geo geo = new Geo();
        JsonObject desc = g.has("description") ? g.getAsJsonObject("description") : new JsonObject();
        if (desc.has("texture_width")) geo.texW = desc.get("texture_width").getAsInt();
        if (desc.has("texture_height")) geo.texH = desc.get("texture_height").getAsInt();
        for (JsonElement be : g.getAsJsonArray("bones")) {
            JsonObject bo = be.getAsJsonObject();
            Bone b = new Bone();
            b.name = bo.get("name").getAsString();
            b.parent = bo.has("parent") ? bo.get("parent").getAsString() : null;
            b.pivot = bo.has("pivot") ? f3(bo.get("pivot"), b.pivot) : b.pivot;
            b.rot = bo.has("rotation") ? f3(bo.get("rotation"), b.rot) : b.rot;
            b.mirror = bo.has("mirror") && bo.get("mirror").getAsBoolean();
            if (bo.has("cubes")) for (JsonElement ce : bo.getAsJsonArray("cubes")) {
                JsonObject co = ce.getAsJsonObject();
                Cube c = new Cube();
                c.origin = f3(co.get("origin"), c.origin);
                c.size = f3(co.get("size"), c.size);
                if (co.has("uv") && co.get("uv").isJsonArray()) {
                    JsonArray u = co.getAsJsonArray("uv");
                    c.uv = new float[]{u.get(0).getAsFloat(), u.get(1).getAsFloat()};
                } else if (WARNED.add("uv:" + b.name)) {
                    EnmityCombat.LOGGER.warn("[Enmity] Bone '{}' uses per-face UV, which is not supported yet. Switch the cube to Box UV in Blockbench.", b.name);
                }
                c.inflate = co.has("inflate") ? co.get("inflate").getAsFloat() : 0f;
                c.mirror = co.has("mirror") ? co.get("mirror").getAsBoolean() : b.mirror;
                if (co.has("rotation")) { c.rot = f3(co.get("rotation"), new float[3]); c.pivot = co.has("pivot") ? f3(co.get("pivot"), b.pivot) : b.pivot; }
                b.cubes.add(c);
            }
            geo.bones.add(b);
        }
        return geo;
    }

    static Anim parseAnim(String name, JsonObject o) {
        Anim a = new Anim(name);
        if (o.has("loop")) {
            JsonElement l = o.get("loop");
            if (l.isJsonPrimitive() && l.getAsJsonPrimitive().isBoolean()) a.loop = l.getAsBoolean() ? 1 : 0;
            else if (l.isJsonPrimitive() && "hold_on_last_frame".equals(l.getAsString())) a.loop = 2;
        }
        float maxT = 0;
        if (o.has("bones")) for (var e : o.getAsJsonObject("bones").entrySet()) {
            JsonObject bo = e.getValue().getAsJsonObject();
            BoneAnim ba = new BoneAnim();
            if (bo.has("position")) ba.pos = parseTrack(bo.get("position"));
            if (bo.has("rotation")) ba.rot = parseTrack(bo.get("rotation"));
            if (bo.has("scale")) ba.scale = parseTrack(bo.get("scale"));
            for (Track t : new Track[]{ba.pos, ba.rot, ba.scale}) if (t != null) maxT = Math.max(maxT, t.last());
            a.bones.put(e.getKey(), ba);
        }
        a.length = o.has("animation_length") ? o.get("animation_length").getAsFloat() : maxT;
        return a;
    }

    private static Track parseTrack(JsonElement e) {
        if (!e.isJsonObject()) return new Track(new float[]{0}, new float[][]{vec(e)}, new boolean[]{false});
        TreeMap<Float, float[]> keys = new TreeMap<>();
        Map<Float, Boolean> smooth = new HashMap<>();
        for (var kv : e.getAsJsonObject().entrySet()) {
            float time;
            try { time = Float.parseFloat(kv.getKey()); } catch (NumberFormatException ex) { continue; }
            JsonElement val = kv.getValue();
            boolean cr = false;
            if (val.isJsonObject()) {
                JsonObject vo = val.getAsJsonObject();
                cr = vo.has("lerp_mode") && "catmullrom".equals(vo.get("lerp_mode").getAsString());
                val = vo.has("post") ? vo.get("post") : vo.has("pre") ? vo.get("pre") : null;
                if (val == null) continue;
            }
            keys.put(time, vec(val));
            smooth.put(time, cr);
        }
        if (keys.isEmpty()) return new Track(new float[]{0}, new float[][]{{0, 0, 0}}, new boolean[]{false});
        int n = keys.size(), i = 0;
        float[] t = new float[n]; float[][] v = new float[n][]; boolean[] s = new boolean[n];
        for (var kv : keys.entrySet()) { t[i] = kv.getKey(); v[i] = kv.getValue(); s[i] = smooth.get(kv.getKey()); i++; }
        return new Track(t, v, s);
    }

    private static float[] vec(JsonElement e) {
        if (e.isJsonArray()) { JsonArray a = e.getAsJsonArray(); return new float[]{num(a.get(0)), num(a.get(1)), num(a.get(2))}; }
        float n = num(e);
        return new float[]{n, n, n};
    }
    private static float num(JsonElement e) {
        try { return e.getAsFloat(); } catch (Exception ex) { return 0f; }   // molang strings are not supported
    }
    private static float[] f3(JsonElement e, float[] def) {
        if (e == null || !e.isJsonArray()) return def;
        JsonArray a = e.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
    private RigData() {}
}
