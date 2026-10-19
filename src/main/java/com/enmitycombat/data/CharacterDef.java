package com.enmitycombat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nullable;
import java.util.*;

public class CharacterDef {
    /** A full set of moves. The character has a base one and optionally a "transformed" one (ultimate hold). */
    public static class Moveset {
        public final List<ResourceLocation> basic = new ArrayList<>();
        public final Map<Slot, ResourceLocation> slots = new EnumMap<>(Slot.class);

        static Moveset parse(JsonObject o) {
            Moveset m = new Moveset();
            for (JsonElement e : GsonHelper.getAsJsonArray(o, "basic", new JsonArray())) m.basic.add(new ResourceLocation(e.getAsString()));
            for (Slot s : Slot.values()) {
                if (s != Slot.BASIC && o.has(s.key)) m.slots.put(s, new ResourceLocation(GsonHelper.getAsString(o, s.key)));
            }
            return m;
        }
    }

    public final ResourceLocation id;
    public final String name;
    public final String title;         // subtitle in the codex, e.g. "Wandering Blade"
    public final String description;   // lore / playstyle text in the codex
    public final float maxEnergy;
    public final float baseAttack;
    public final int basicCooldownReduce;   // ticks removed from every skill CD each basic-attack step
    public final int comboResetTicks;
    public final Moveset base;
    @Nullable public final Moveset transformed;
    public final List<PassiveDef> passives = new ArrayList<>();
    public final List<PropDef> props = new ArrayList<>();
    @Nullable public final ResourceLocation rig;               // Blockbench rig (props with their own bones + animations)      // visible weapons / accessories
    public final Map<String, ResourceLocation> animations = new HashMap<>(); // block, parry, dodge
    public final Map<String, ResourceLocation> fx = new HashMap<>();         // parry, perfect_dodge, guard_break, transform_end

    private CharacterDef(ResourceLocation id, JsonObject o) {
        this.id = id;
        this.name = GsonHelper.getAsString(o, "name", id.getPath());
        this.title = GsonHelper.getAsString(o, "title", "");
        this.description = GsonHelper.getAsString(o, "description", "");
        this.maxEnergy = GsonHelper.getAsFloat(o, "max_energy", 100f);
        this.baseAttack = GsonHelper.getAsFloat(o, "base_attack", 6f);
        this.basicCooldownReduce = GsonHelper.getAsInt(o, "basic_cooldown_reduction", 6);
        this.comboResetTicks = GsonHelper.getAsInt(o, "combo_reset_ticks", 30);
        this.base = Moveset.parse(GsonHelper.getAsJsonObject(o, "moveset"));
        this.transformed = o.has("transformed_moveset") ? Moveset.parse(GsonHelper.getAsJsonObject(o, "transformed_moveset")) : null;
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "passives", new JsonArray())) passives.add(new PassiveDef(e.getAsJsonObject()));
        this.rig = o.has("rig") ? new ResourceLocation(GsonHelper.getAsString(o, "rig")) : null;
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "props", new JsonArray())) props.add(new PropDef(e.getAsJsonObject()));
        readMap(o, "animations", animations);
        readMap(o, "fx", fx);
    }

    private static void readMap(JsonObject o, String key, Map<String, ResourceLocation> out) {
        if (!o.has(key)) return;
        GsonHelper.getAsJsonObject(o, key).entrySet().forEach(e -> out.put(e.getKey(), new ResourceLocation(e.getValue().getAsString())));
    }

    public static CharacterDef parse(ResourceLocation id, JsonObject o) { return new CharacterDef(id, o); }

    /** Resolve a slot to a skill id. Transformed movesets override the slots they define; the rest fall back to base. */
    @Nullable
    public ResourceLocation resolve(Slot slot, boolean isTransformed) {
        if (isTransformed && transformed != null && transformed.slots.containsKey(slot)) return transformed.slots.get(slot);
        return base.slots.get(slot);
    }

    public List<ResourceLocation> basicChain(boolean isTransformed) {
        if (isTransformed && transformed != null && !transformed.basic.isEmpty()) return transformed.basic;
        return base.basic;
    }
}
