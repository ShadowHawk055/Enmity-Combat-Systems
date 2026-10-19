package com.enmitycombat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PassiveDef {
    public enum Trigger { ALWAYS, ON_HIT, ON_BASIC_HIT, ON_SKILL_CAST, ON_ULTIMATE, ON_PARRY, ON_BLOCK, ON_PERFECT_DODGE, ON_TRANSFORM, ON_TRANSFORM_END }

    public record AttrMod(ResourceLocation attribute, double amount, AttributeModifier.Operation op) {}

    public final String id;
    public final String name;
    public final String description;
    public final Trigger trigger;
    public final int internalCooldown;
    public final List<Actions.Action> effects = new ArrayList<>();
    public final List<AttrMod> attributes = new ArrayList<>();

    public PassiveDef(JsonObject o) {
        this.id = GsonHelper.getAsString(o, "id");
        this.name = GsonHelper.getAsString(o, "name", id);
        this.description = GsonHelper.getAsString(o, "description", "");
        this.trigger = Trigger.valueOf(GsonHelper.getAsString(o, "trigger", "always").toUpperCase(Locale.ROOT));
        this.internalCooldown = GsonHelper.getAsInt(o, "cooldown", 0);
        JsonArray arr = GsonHelper.getAsJsonArray(o, "effects", new JsonArray());
        for (JsonElement el : arr) {
            JsonObject e = el.getAsJsonObject();
            if ("attribute".equals(GsonHelper.getAsString(e, "type"))) {
                AttributeModifier.Operation op = switch (GsonHelper.getAsString(e, "operation", "addition")) {
                    case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
                    case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
                    default -> AttributeModifier.Operation.ADDITION;
                };
                attributes.add(new AttrMod(new ResourceLocation(GsonHelper.getAsString(e, "attribute")),
                        GsonHelper.getAsDouble(e, "amount"), op));
            } else {
                effects.add(Actions.parse(e));
            }
        }
    }
}
