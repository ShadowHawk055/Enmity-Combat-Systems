package com.enmitycombat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One move: basic-attack step, skill, special, heavy, ultimate... Everything it does is a timeline of Actions. */
public class SkillDef {
    public record TimedAction(int tick, Actions.Action action) {}

    public final ResourceLocation id;
    public final String name;
    public final String description;   // shown in the codex
    public final int cooldown;          // ticks, ignored for basic attacks
    public final int duration;          // ticks until the move ends
    public final int cancelFrom;        // from this tick on, other inputs (next combo step, dodge, block, skills) can interrupt
    public final float energyCost;
    public final float energyGain;      // on cast
    public final float energyOnHit;     // per target hit
    public final boolean uninterruptible; // even dodge cannot cancel it (ultimates)
    public final boolean faceAim;
    public final boolean lockMovement;
    public final List<TimedAction> timeline;

    private SkillDef(ResourceLocation id, JsonObject o) {
        this.id = id;
        this.name = GsonHelper.getAsString(o, "name", id.getPath());
        this.description = GsonHelper.getAsString(o, "description", "");
        this.cooldown = GsonHelper.getAsInt(o, "cooldown", 0);
        this.duration = GsonHelper.getAsInt(o, "duration", 20);
        this.cancelFrom = GsonHelper.getAsInt(o, "cancel_from", duration);
        this.energyCost = GsonHelper.getAsFloat(o, "energy_cost", 0f);
        this.energyGain = GsonHelper.getAsFloat(o, "energy_gain", 0f);
        this.energyOnHit = GsonHelper.getAsFloat(o, "energy_on_hit", 0f);
        this.uninterruptible = GsonHelper.getAsBoolean(o, "uninterruptible", false);
        this.faceAim = GsonHelper.getAsBoolean(o, "face_aim", true);
        this.lockMovement = GsonHelper.getAsBoolean(o, "lock_movement", false);
        List<TimedAction> list = new ArrayList<>();
        JsonArray arr = GsonHelper.getAsJsonArray(o, "timeline", new JsonArray());
        for (JsonElement el : arr) {
            JsonObject e = el.getAsJsonObject();
            list.add(new TimedAction(GsonHelper.getAsInt(e, "tick", 0), Actions.parse(e)));
        }
        list.sort(Comparator.comparingInt(TimedAction::tick));
        this.timeline = List.copyOf(list);
    }

    public static SkillDef parse(ResourceLocation id, JsonObject o) { return new SkillDef(id, o); }
}
