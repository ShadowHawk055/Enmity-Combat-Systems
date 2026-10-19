package com.enmitycombat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A visible prop (weapon, shield, accessory) attached to a character. Rendered client-side by PropLayer.
 * Either "item" (any registered item, optionally with custom_model_data) or "model" (a model JSON you ship at
 * assets/<ns>/models/prop/<name>.json, referenced as "<ns>:<name>").
 */
public class PropDef {
    /** Where and how a prop is placed. Offset is in pixels (1/16 block), rotation in degrees. */
    public record Xform(String attach, Vec3 offset, Vec3 rotation, float scale) {
        static Xform parse(JsonObject o, Xform base) {
            return new Xform(GsonHelper.getAsString(o, "attach", base.attach),
                    o.has("offset") ? vec(o.getAsJsonArray("offset")) : base.offset,
                    o.has("rotation") ? vec(o.getAsJsonArray("rotation")) : base.rotation,
                    GsonHelper.getAsFloat(o, "scale", base.scale));
        }
    }

    public final String id;
    @Nullable public final ResourceLocation item;
    public final int customModelData;
    @Nullable public final ResourceLocation model;   // expanded to <ns>:prop/<name>
    public final String when;          // "combat" (only while combat mode is on) | "always"
    public final String form;          // "any" | "base" | "transformed"
    public final boolean onlyWhenDrawn;
    public final Xform main;           // placement while the character is attacking / blocking ("drawn")
    @Nullable public final Xform idle; // placement otherwise (e.g. sheathed on the back); if absent, same as main

    public PropDef(JsonObject o) {
        this.id = GsonHelper.getAsString(o, "id", "prop");
        this.item = o.has("item") ? new ResourceLocation(GsonHelper.getAsString(o, "item")) : null;
        this.customModelData = GsonHelper.getAsInt(o, "custom_model_data", -1);
        if (o.has("model")) {
            ResourceLocation m = new ResourceLocation(GsonHelper.getAsString(o, "model"));
            this.model = new ResourceLocation(m.getNamespace(), "prop/" + m.getPath());
        } else this.model = null;
        if (item == null && model == null) throw new JsonSyntaxException("prop '" + id + "' needs \"item\" or \"model\"");
        this.when = GsonHelper.getAsString(o, "when", "combat");
        this.form = GsonHelper.getAsString(o, "form", "any");
        this.onlyWhenDrawn = GsonHelper.getAsBoolean(o, "only_when_drawn", false);
        this.main = Xform.parse(o, new Xform("right_hand", Vec3.ZERO, Vec3.ZERO, 1f));
        this.idle = o.has("idle") ? Xform.parse(GsonHelper.getAsJsonObject(o, "idle"), main) : null;
    }

    public boolean visible(boolean combat, boolean transformed, boolean drawn) {
        if (when.equals("combat") && !combat) return false;
        if (form.equals("base") && transformed) return false;
        if (form.equals("transformed") && !transformed) return false;
        return !onlyWhenDrawn || drawn;
    }

    public Xform xform(boolean drawn) { return (!drawn && idle != null) ? idle : main; }

    private static Vec3 vec(JsonArray a) { return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()); }
}
