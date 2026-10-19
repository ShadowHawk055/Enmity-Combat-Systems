package com.enmitycombat.client.rig;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;

/**
 * A rig turned into vanilla ModelParts.
 *
 * Coordinate mapping (Blockbench/Bedrock -> vanilla player model space):  x -> x,  y -> 24 - y,  z -> z.
 * The six player bones are cube-less ModelParts that copy the REAL player's posed parts every frame, so any bone
 * parented under them (or, if it has no player ancestor, under the torso) follows the real, animated player.
 */
public final class RigInstance {
    private static final String[] PLAYER_KEYS = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final float[][] PLAYER_PIVOT = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};

    private static final class Node {
        final String name; final ModelPart part; final float[] restPos, restRot;
        Node(String name, ModelPart part, float[] restPos, float[] restRot) { this.name = name; this.part = part; this.restPos = restPos; this.restRot = restRot; }
    }

    public final ResourceLocation texture;
    public final RigData.Settings settings;
    final ModelPart root;
    private final List<Node> nodes = new ArrayList<>();

    private RigInstance(ModelPart root, ResourceLocation texture, RigData.Settings settings) {
        this.root = root; this.texture = texture; this.settings = settings;
    }

    @Nullable
    static String playerKey(String n) {
        return switch (n.toLowerCase(Locale.ROOT)) {
            case "head" -> "head";
            case "torso", "body" -> "torso";
            case "right_arm", "rightarm" -> "rightArm";
            case "left_arm", "leftarm" -> "leftArm";
            case "right_leg", "rightleg" -> "rightLeg";
            case "left_leg", "leftleg" -> "leftLeg";
            default -> null;
        };
    }

    // ------------------------------------------------------------------------------------------------ bake
    private static final class Ctx {
        final RigData.Settings s; final Map<String, RigData.Bone> byName = new HashMap<>();
        final Map<String, PartDefinition> defs = new HashMap<>();
        final Map<String, float[]> abs = new HashMap<>();
        final Map<String, String[]> paths = new HashMap<>();
        final Map<String, float[][]> rest = new LinkedHashMap<>();   // prop bone -> {restPos, restRot}
        Ctx(RigData.Settings s) { this.s = s; }
    }

    static RigInstance bake(RigData.Geo geo, ResourceLocation texture, RigData.Settings s) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();
        Ctx c = new Ctx(s);
        for (int i = 0; i < 6; i++) {
            String k = PLAYER_KEYS[i];
            float[] p = PLAYER_PIVOT[i];
            c.defs.put(k, rootDef.addOrReplaceChild(k, CubeListBuilder.create(), PartPose.offset(p[0], p[1], p[2])));
            c.abs.put(k, p.clone());
            c.paths.put(k, new String[]{k});
        }
        for (RigData.Bone b : geo.bones) c.byName.put(b.name, b);
        for (RigData.Bone b : geo.bones) if (playerKey(b.name) == null) create(c, b, 0);

        ModelPart root = LayerDefinition.create(mesh, geo.texW, geo.texH).bakeRoot();
        RigInstance inst = new RigInstance(root, texture, s);
        for (var e : c.rest.entrySet()) {
            ModelPart p = root;
            for (String seg : c.paths.get(e.getKey())) p = p.getChild(seg);
            inst.nodes.add(new Node(e.getKey(), p, e.getValue()[0], e.getValue()[1]));
        }
        return inst;
    }

    private static void create(Ctx c, RigData.Bone b, int depth) {
        if (c.defs.containsKey(b.name) || depth > 64) return;
        String parentKey = "torso";
        if (b.parent != null) {
            String pk = playerKey(b.parent);
            if (pk != null) parentKey = pk;
            else if (c.byName.containsKey(b.parent)) { parentKey = b.parent; create(c, c.byName.get(b.parent), depth + 1); }
        }
        PartDefinition parentDef = c.defs.get(parentKey);
        float[] parentAbs = c.abs.get(parentKey);
        float[] abs = {b.pivot[0], 24 - b.pivot[1], b.pivot[2]};
        float[] relPos = {abs[0] - parentAbs[0], abs[1] - parentAbs[1], abs[2] - parentAbs[2]};
        float[] relRot = rot(b.rot, c.s);

        CubeListBuilder cl = CubeListBuilder.create();
        List<RigData.Cube> rotated = new ArrayList<>();
        for (RigData.Cube cube : b.cubes) {
            if (cube.rot != null) rotated.add(cube); else addCube(cl, cube, abs);
        }
        PartDefinition def = parentDef.addOrReplaceChild(b.name, cl,
                PartPose.offsetAndRotation(relPos[0], relPos[1], relPos[2], relRot[0], relRot[1], relRot[2]));
        int i = 0;
        for (RigData.Cube cube : rotated) {      // cubes with their own rotation get a tiny child part
            float[] cp = {cube.pivot[0], 24 - cube.pivot[1], cube.pivot[2]};
            CubeListBuilder one = CubeListBuilder.create();
            addCube(one, cube, cp);
            float[] cr = rot(cube.rot, c.s);
            def.addOrReplaceChild(b.name + "#" + (i++), one,
                    PartPose.offsetAndRotation(cp[0] - abs[0], cp[1] - abs[1], cp[2] - abs[2], cr[0], cr[1], cr[2]));
        }
        c.defs.put(b.name, def);
        c.abs.put(b.name, abs);
        String[] pp = c.paths.get(parentKey);
        String[] path = Arrays.copyOf(pp, pp.length + 1);
        path[pp.length] = b.name;
        c.paths.put(b.name, path);
        c.rest.put(b.name, new float[][]{relPos, relRot});
    }

    private static void addCube(CubeListBuilder cl, RigData.Cube c, float[] pivotJava) {
        float x0 = c.origin[0] - pivotJava[0];
        float y0 = (24 - (c.origin[1] + c.size[1])) - pivotJava[1];
        float z0 = c.origin[2] - pivotJava[2];
        cl.mirror(c.mirror).texOffs((int) c.uv[0], (int) c.uv[1])
          .addBox(x0, y0, z0, c.size[0], c.size[1], c.size[2], new CubeDeformation(c.inflate));
    }

    private static float[] rot(float[] deg, RigData.Settings s) {
        return new float[]{(float) Math.toRadians(deg[0]) * s.rotSigns[0], (float) Math.toRadians(deg[1]) * s.rotSigns[1], (float) Math.toRadians(deg[2]) * s.rotSigns[2]};
    }

    // ------------------------------------------------------------------------------------------------ per frame
    void apply(PlayerModel<?> m, Map<String, RigPlayback.Pose> pose) {
        root.getChild("head").copyFrom(m.head);
        root.getChild("torso").copyFrom(m.body);
        root.getChild("rightArm").copyFrom(m.rightArm);
        root.getChild("leftArm").copyFrom(m.leftArm);
        root.getChild("rightLeg").copyFrom(m.rightLeg);
        root.getChild("leftLeg").copyFrom(m.leftLeg);

        for (Node n : nodes) {
            RigPlayback.Pose p = pose.get(n.name);
            float[] pos = n.restPos.clone(), rot = n.restRot.clone();
            float[] sc = {1, 1, 1};
            if (p != null) {
                if (p.pos != null) for (int i = 0; i < 3; i++) pos[i] += p.pos[i] * settings.posSigns[i];
                if (p.rot != null) for (int i = 0; i < 3; i++) rot[i] += (float) Math.toRadians(p.rot[i]) * settings.rotSigns[i];
                if (p.scale != null) sc = p.scale;
            }
            n.part.setPos(pos[0], pos[1], pos[2]);
            n.part.setRotation(rot[0], rot[1], rot[2]);
            n.part.xScale = sc[0]; n.part.yScale = sc[1]; n.part.zScale = sc[2];
            n.part.visible = Math.min(Math.abs(sc[0]), Math.min(Math.abs(sc[1]), Math.abs(sc[2]))) > 1e-3f;   // scale 0 = hidden (weapon swap trick)
        }
    }
}
