#!/usr/bin/env python3
"""
Enmity Combat Systems - class scaffolding helpers (pure Python 3, no dependencies).

Standard layout of ONE class ("character") named <cid> in namespace <ns>:

  data/<ns>/characters/<cid>.json
  data/<ns>/skills/<cid>/<skill>.json                 skill ids:  <ns>:<cid>/<skill>
  assets/<ns>/player_animations/<cid>_<skill>.json    one animation per skill (player + props + camera, Blockbench export)
  assets/<ns>/player_animations/<cid>_block|parry|dodge.json
  assets/<ns>/fx/<cid>_<skill>_<effect>.fx            Photon effects; 0-byte files are placeholders
  assets/<ns>/sounds.json                              sound events: one per effect, named exactly like the effect (placeholders play vanilla sounds)
  assets/<ns>/sounds/<cid>_<skill>_<effect>.ogg        your real sound files (OGG Vorbis, mono)
  assets/<ns>/fx/<cid>_<skill>_hit.fx                 hit effect (on each target the skill's hitboxes really hurt); sound event: same name
  assets/<ns>/impact_frames/<cid>_<skill>/001.png ... hand-drawn impact frames (ultimates), sequence.json = timing defaults
  assets/<ns>/rigs/<cid>/model.geo.json               the Blockbench rig (player skeleton + props + camera groups)
  assets/<ns>/rigs/<cid>/texture.png
  assets/<ns>/rigs/<cid>/rig.json                     idle state animations + calibration
  assets/<ns>/rigs/<cid>/animations.json              idle / idle_drawn / idle_transformed (prop visibility)

Skill keys: basic_1..n, skill_1..3, special, heavy, ultimate, ultimate_hold, t_basic_1.., t_heavy, t_ultimate (transformed set).
"""
import json, math, os, struct, zlib

# ----------------------------------------------------------------------------- small io helpers
def w_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)

def touch(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").close()

def anim_id(ns, cid, key): return f"{ns}:{cid}_{key}"
def fx_id(ns, cid, key, effect): return f"{ns}:{cid}_{key}_{effect}"
def skill_id(ns, cid, key): return f"{ns}:{cid}/{key}"

# ----------------------------------------------------------------------------- skill event helpers
def hit(tick, dmg, rng=3.2, ang=110, kb=0.3, **kw):
    return {"tick": tick, "type": "hitbox", "range": rng, "angle": ang, "damage": dmg, "knockback": kb, **kw}
def dash(tick, speed, **kw): return {"tick": tick, "type": "dash", "speed": speed, **kw}
def invuln(tick, ticks): return {"tick": tick, "type": "invuln", "ticks": ticks}

# ----------------------------------------------------------------------------- camera: Blockbench groups -> bones
def camera_bones(keys):
    """
    keys: [{"tick":0, "pos":[right, up, forward], "look":[right, up, forward], "fov":70}, ...]  (blocks, relative to the caster's feet)
    returns the animation bones  camera / camera_target / camera_fov  in Blockbench units
    (x+ = character's left, y+ = up, z- = forward, 16 units = 1 block; fov in position X of camera_fov).
    """
    cam, tgt, fov = {}, {}, {}
    for k in keys:
        t = f"{k['tick'] / 20:.3f}".rstrip("0").rstrip(".") or "0"
        r, u, f = k["pos"]
        cam[t] = {"post": [round(-r * 16, 3), round(u * 16, 3), round(-f * 16, 3)], "lerp_mode": "catmullrom"}
        if "look" in k:
            r, u, f = k["look"]
            tgt[t] = {"post": [round(-r * 16, 3), round(u * 16, 3), round(-f * 16, 3)], "lerp_mode": "catmullrom"}
        if "fov" in k:
            fov[t] = {"post": [k["fov"], 0, 0], "lerp_mode": "catmullrom"}
    bones = {"camera": {"position": cam}}
    if tgt: bones["camera_target"] = {"position": tgt}
    if fov: bones["camera_fov"] = {"position": fov}
    return bones

CAM_ULT = [{"tick": 0, "pos": [-2.5, 1.0, 3.5], "look": [0, 1.3, 0], "fov": 70}, {"tick": 24, "pos": [0, 1.2, 2.0], "look": [0, 1.6, 0], "fov": 55},
           {"tick": 44, "pos": [3.0, 2.2, 4.5], "look": [0, 1.2, 4], "fov": 85}, {"tick": 70, "pos": [3.5, 3.0, -3.0], "look": [0, 1.2, 3], "fov": 70}]
CAM_TRANSFORM = [{"tick": 0, "pos": [0, 0.4, 2.8], "look": [0, 1.0, 0], "fov": 60}, {"tick": 22, "pos": [0, 1.8, 2.2], "look": [0, 1.7, 0], "fov": 45},
                 {"tick": 45, "pos": [2.5, 1.8, 3.5], "look": [0, 1.3, 0], "fov": 75}]
CAM_T_ULT = [{"tick": 0, "pos": [0, 1.0, 3.0], "look": [0, 1.3, 0], "fov": 75}, {"tick": 20, "pos": [-2.0, 1.6, 2.0], "look": [0, 1.4, 1], "fov": 58},
             {"tick": 30, "pos": [0.5, 1.2, -1.5], "look": [0, 1.3, 4], "fov": 92}, {"tick": 48, "pos": [3.0, 2.0, 4.0], "look": [0, 1.3, 5], "fov": 80},
             {"tick": 64, "pos": [-3.0, 1.5, 6.0], "look": [0, 1.3, 5], "fov": 80}, {"tick": 72, "pos": [0, 2.6, 3.0], "look": [0, 0.8, 5.5], "fov": 50},
             {"tick": 78, "pos": [0, 3.2, 1.0], "look": [0, 0.8, 5.5], "fov": 100}, {"tick": 90, "pos": [0, 2.2, -3.5], "look": [0, 1.2, 4], "fov": 70}]

# ----------------------------------------------------------------------------- placeholders
def placeholder_animation(root, ns, cid, key, length=0.5, bones=None):
    """A valid, empty Bedrock animation (marked placeholder so the log stays quiet). Replace it with your Blockbench export."""
    w_json(f"{root}/assets/{ns}/player_animations/{cid}_{key}.json", {
        "format_version": "1.8.0", "placeholder": True,
        "animations": {f"animation.{cid}.{key}": {"animation_length": length, "bones": bones or {}}}})

def placeholder_fx(root, ns, cid, key, effect):
    """0-byte file. Replace it with the .fx exported from the Photon editor (same file name)."""
    touch(f"{root}/assets/{ns}/fx/{cid}_{key}_{effect}.fx")

# ----------------------------------------------------------------------------- skills
def write_skill(root, ns, cid, key, base, fx, camera_path=None, camera_opts=None):
    """
    base : the skill dict (name, description, cooldown, duration, cancel_from, flags..., "timeline": [non-presentation events])
    fx   : [(tick, effect, "self"|"front", distance)]   -> one fx action + one placeholder .fx file each
    camera_path : optional camera keyframes (see camera_bones); the skill then plays a cutscene from its own animation file
    """
    tl = [{"tick": 0, "type": "animation", "anim": anim_id(ns, cid, key)}]
    if camera_path:
        opts = {"hide_hud": True, "letterbox": True, "blend_in": 0, "blend_out": 8}
        opts.update(camera_opts or {})
        tl.append({"tick": 0, "type": "camera", "animation": anim_id(ns, cid, key), **opts})
    for tick, effect, at, dist in fx:
        e = {"tick": tick, "type": "fx", "fx": fx_id(ns, cid, key, effect), "at": at}
        if at == "front": e["distance"] = dist
        tl.append(e)
        placeholder_fx(root, ns, cid, key, effect)
    tl += base.get("timeline", [])
    tl.sort(key=lambda e: e["tick"])                    # stable: same-tick order is preserved
    out = {k: v for k, v in base.items() if k != "timeline"}
    out["timeline"] = tl
    w_json(f"{root}/data/{ns}/skills/{cid}/{key}.json", out)

    if camera_path:
        length = max(k["tick"] for k in camera_path) / 20
        placeholder_animation(root, ns, cid, key, length, camera_bones(camera_path))
    else:
        placeholder_animation(root, ns, cid, key, base.get("duration", 20) / 20)

# ----------------------------------------------------------------------------- rig (Blockbench file) + texture
def _cube(origin, size, uv, color=None):
    c = {"origin": origin, "size": size, "uv": uv}
    if color: c["color"] = color
    return c

PLAYER_REFERENCE = [   # the Emotecraft-style player skeleton; kept so the file is easy to animate in Blockbench. Never drawn.
    {"name": "head", "pivot": [0, 24, 0], "cubes": [_cube([-4, 24, -4], [8, 8, 8], [0, 0])]},
    {"name": "torso", "pivot": [0, 24, 0], "cubes": [_cube([-4, 12, -2], [8, 12, 4], [16, 16])]},
    {"name": "right_arm", "pivot": [-5, 22, 0], "cubes": [_cube([-8, 12, -2], [4, 12, 4], [40, 16])]},
    {"name": "left_arm", "pivot": [5, 22, 0], "cubes": [_cube([4, 12, -2], [4, 12, 4], [32, 48])]},
    {"name": "right_leg", "pivot": [-1.9, 12, 0], "cubes": [_cube([-3.9, 0, -2], [4, 12, 4], [0, 16])]},
    {"name": "left_leg", "pivot": [1.9, 12, 0], "cubes": [_cube([-0.1, 0, -2], [4, 12, 4], [16, 48])]},
]
CAMERA_GROUPS = [      # cutscene camera groups: animate these in Blockbench. Never drawn.
    {"name": "camera", "pivot": [0, 0, 0], "cubes": [_cube([-1, -1, -1], [2, 2, 2], [0, 0])]},
    {"name": "camera_target", "pivot": [0, 0, 0], "cubes": [_cube([-1, -1, -1], [2, 2, 2], [0, 0])]},
    {"name": "camera_fov", "pivot": [0, 0, 0]},
]

def template_props(transform):
    """Generic weapon: drawn in the hand, sheathed on the hip, optional bigger transformed version."""
    props = [
        {"name": "weapon", "parent": "right_arm", "pivot": [-6, 13, -1], "cubes": [
            _cube([-6.5, 12.5, -13], [1, 1, 12], [0, 64], (220, 230, 240, 255)),
            _cube([-8, 12, -2], [4, 2, 1], [28, 64], (230, 190, 60, 255)),
            _cube([-6.5, 12.5, -1], [1, 1, 3], [40, 64], (120, 80, 40, 255))]},
        {"name": "scabbard", "parent": "torso", "pivot": [4.8, 12, 0], "cubes": [_cube([4.1, 3, -1.5], [1.4, 9, 3], [0, 80], (90, 60, 40, 255))]},
        {"name": "weapon_sheathed", "parent": "scabbard", "pivot": [4.8, 12, 0], "cubes": [_cube([4.4, 12, -0.5], [0.8, 3, 1], [12, 80], (120, 80, 40, 255))]},
    ]
    hidden = {"idle": ["weapon"], "drawn": ["weapon_sheathed"]}
    if transform:
        props.append({"name": "weapon_t", "parent": "right_arm", "pivot": [-6, 13, -1], "cubes": [
            _cube([-6.75, 12.25, -17], [1.5, 1.5, 16], [0, 96], (255, 120, 90, 255)),
            _cube([-9, 11.5, -2.5], [6, 3, 1.5], [40, 96], (230, 190, 60, 255))]})
        hidden = {"idle": ["weapon", "weapon_t"], "drawn": ["weapon_sheathed", "weapon_t"], "transformed": ["weapon", "weapon_sheathed"]}
    else:
        hidden["idle"] = ["weapon"]
    return props, hidden

def _png(path, size, rects):
    buf = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for x, y, w, h, c in rects:
        for j in range(y, min(size, y + h)):
            for i in range(x, min(size, x + w)):
                buf[j][i] = c
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in buf)
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))

def write_rig(root, ns, cid, props, hidden):
    """Writes model.geo.json, texture.png, rig.json and animations.json for the class."""
    base = f"{root}/assets/{ns}/rigs/{cid}"
    strip = lambda bones: [{**b, "cubes": [{k: v for k, v in c.items() if k != "color"} for c in b.get("cubes", [])]} if "cubes" in b else dict(b) for b in bones]
    bones = strip(PLAYER_REFERENCE) + strip(CAMERA_GROUPS) + strip(props)
    w_json(f"{base}/model.geo.json", {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": f"geometry.{cid}", "texture_width": 128, "texture_height": 128,
        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]}, "bones": bones}]})

    rects = [(0, 0, 64, 32, (60, 60, 70, 255)), (0, 32, 64, 32, (70, 70, 90, 255))]     # reference-player area (not drawn)
    for b in props:
        for c in b.get("cubes", []):
            if "color" in c:
                (sx, sy, sz), (u, v) = c["size"], c["uv"]
                rects.append((u, v, math.ceil(2 * (sx + sz)), math.ceil(sy + sz), tuple(c["color"])))
    _png(f"{base}/texture.png", 128, rects)

    toggles = sorted({b for lst in hidden.values() for b in lst})
    states = {"idle": "idle", "drawn": "idle_drawn", "transformed": "idle_transformed"}
    anims, rig = {}, {"rotation_signs": [-1, 1, -1], "position_signs": [1, -1, 1], "camera_rotation_signs": [1, 1, 1]}
    for st, name in states.items():
        if st not in hidden: continue
        anims[f"{cid}_{name}"] = {"loop": True, "animation_length": 1.0,
                                  "bones": {b: {"scale": [0, 0, 0] if b in hidden[st] else [1, 1, 1]} for b in toggles}}
        rig[name] = f"{ns}:{cid}_{name}"
    w_json(f"{base}/animations.json", {"format_version": "1.8.0", "animations": anims})
    w_json(f"{base}/rig.json", rig)

# ----------------------------------------------------------------------------- weapon swings (arm + torso + the WEAPON'S OWN wrist motion)
SWING_STYLES = {   # three poses after the rest pose: windup, strike (lands on the hit tick), follow-through.  Degrees, Blockbench axes.
    "slash_a":  {"arm": [[-60, 0, -35], [-100, 0, 20], [-70, 0, 35]], "wrist": [[35, 0, -20], [-45, 0, 15], [-15, 0, 10]], "torso": [[0, -20, 0], [0, 25, 0], [0, 30, 0]]},
    "slash_b":  {"arm": [[-70, 0, 40], [-90, 0, -30], [-60, 0, -40]], "wrist": [[30, 0, 25], [-40, 0, -20], [-10, 0, -10]], "torso": [[0, 25, 0], [0, -25, 0], [0, -30, 0]]},
    "overhead": {"arm": [[-170, 0, 0], [-60, 0, 0], [-40, 0, 0]],     "wrist": [[60, 0, 0], [-70, 0, 0], [-25, 0, 0]],      "torso": [[10, 0, 0], [-15, 0, 0], [-10, 0, 0]]},
    "thrust":   {"arm": [[-70, 0, 10], [-95, 0, 0], [-85, 0, 0]],     "wrist": [[0, 0, -15], [0, 0, 10], [0, 0, 5]],        "torso": [[0, 15, 0], [0, -20, 0], [0, -15, 0]],
                 "wrist_pos": [[0, 0, 5], [0, 0, -9], [0, 0, -3]]},
}

def _t(sec): return f"{sec:.3f}".rstrip("0").rstrip(".") or "0"

def first_hit(base, default=4):
    ticks = [e["tick"] for e in base.get("timeline", []) if e["type"] == "hitbox"]
    return min(ticks) if ticks else default

def write_swing(root, ns, cid, key, style, weapon_bone, hit_tick, duration):
    """
    A real (non-placeholder) animation: the right arm and torso swing, AND the weapon bone rotates on its own around its grip
    (the "moving wrist" feel). The strike pose lands exactly on the skill's hit tick. Replace it with your own Blockbench export.
    """
    st = SWING_STYLES[style]
    hit_s, len_s = max(hit_tick, 2) / 20, duration / 20
    times = [0.0, hit_s * 0.5, hit_s, hit_s + (len_s - hit_s) * 0.3, len_s]
    def track(poses):
        vals = [[0, 0, 0]] + [list(p) for p in poses] + [[0, 0, 0]]
        return {_t(tm): {"post": v, "lerp_mode": "catmullrom"} for tm, v in zip(times, vals)}
    bones = {"right_arm": {"rotation": track(st["arm"])}, "torso": {"rotation": track(st["torso"])},
             weapon_bone: {"rotation": track(st["wrist"])}}
    if "wrist_pos" in st: bones[weapon_bone]["position"] = track(st["wrist_pos"])
    w_json(f"{root}/assets/{ns}/player_animations/{cid}_{key}.json", {"format_version": "1.8.0",
           "animations": {f"animation.{cid}.{key}": {"animation_length": len_s, "bones": bones}}})

# ----------------------------------------------------------------------------- whole class from the generic template
def scaffold_class(root, ns, cid, name, transform=False):
    skills = {
        "basic_1": ({"name": "Basic Attack 1", "description": "TODO", "cooldown": 0, "duration": 14, "cancel_from": 8, "timeline": [hit(4, 0.8)]}, [(3, "slash", "self", 0)], None),
        "basic_2": ({"name": "Basic Attack 2", "description": "TODO", "cooldown": 0, "duration": 14, "cancel_from": 8, "timeline": [hit(4, 0.9)]}, [(3, "slash", "self", 0)], None),
        "basic_3": ({"name": "Basic Attack 3", "description": "TODO", "cooldown": 0, "duration": 22, "cancel_from": 14, "timeline": [dash(0, 0.35), hit(6, 1.5, 3.6, 110, 0.9)]}, [(4, "slash", "front", 1.5)], None),
        "skill_1": ({"name": "Skill 1", "description": "TODO", "cooldown": 120, "duration": 22, "cancel_from": 14, "energy_gain": 6, "energy_on_hit": 3,
                     "timeline": [dash(2, 0.9, vertical=0.1), hit(5, 2.2, 3.5, 90, 0.7)]}, [(4, "cast", "front", 1.5), (5, "impact", "front", 3)], None),
        "skill_2": ({"name": "Skill 2", "description": "TODO", "cooldown": 200, "duration": 28, "cancel_from": 18, "energy_gain": 8, "energy_on_hit": 4,
                     "timeline": [dash(3, 1.1), hit(6, 2.6, 4.2, 40, 0.3, guard_break=True, stun=20)]}, [(5, "cast", "front", 2), (6, "impact", "front", 4)], None),
        "skill_3": ({"name": "Skill 3", "description": "TODO", "cooldown": 260, "duration": 30, "cancel_from": 24, "energy_gain": 10, "energy_on_hit": 2, "lock_movement": True,
                     "timeline": [hit(8, 1.5, 4.5, 360, 0.5), hit(14, 1.5, 4.5, 360, 0.5), hit(20, 2.0, 4.5, 360, 1.0)]}, [(4, "cast", "self", 0), (14, "impact", "self", 0)], None),
        "special": ({"name": "Special", "description": "TODO", "cooldown": 400, "duration": 40, "cancel_from": 34, "energy_gain": 12, "energy_on_hit": 3,
                     "timeline": [invuln(0, 30), dash(2, 1.2), hit(8, 1.4, 5, 360), hit(14, 1.4, 5, 360), hit(20, 1.4, 5, 360), hit(26, 2.8, 5, 360, 1.2)]},
                    [(4, "cast", "self", 0), (26, "impact", "front", 3)], None),
        "heavy": ({"name": "Heavy Attack", "description": "TODO", "cooldown": 140, "duration": 34, "cancel_from": 24, "energy_on_hit": 5,
                   "timeline": [hit(12, 3.2, 3.8, 130, 1.3, stun=10)]}, [(2, "charge", "self", 0), (12, "impact", "front", 3)], None),
        "ultimate": ({"name": "Ultimate", "description": "TODO", "duration": 80, "cancel_from": 80, "energy_cost": 100, "uninterruptible": True, "lock_movement": True,
                      "timeline": [invuln(0, 90), hit(44, 10.0, 9, 360, 2.0, guard_break=True, stun=30)]}, [(20, "charge", "self", 0), (44, "burst", "front", 3)], CAM_ULT),
    }
    moveset = {"basic": [skill_id(ns, cid, f"basic_{i}") for i in (1, 2, 3)]}
    for k in ("skill_1", "skill_2", "skill_3", "special", "heavy", "ultimate"):
        moveset[k] = skill_id(ns, cid, k)
    character = {"name": name, "title": "TODO", "description": "TODO: who is this class and how does it play?",
                 "max_energy": 100, "base_attack": 6.0, "basic_cooldown_reduction": 6, "combo_reset_ticks": 30, "moveset": moveset,
                 "rig": f"{ns}:{cid}",
                 "animations": {k: anim_id(ns, cid, k) for k in ("block", "parry", "dodge")},
                 "fx": {"parry": f"{ns}:{cid}_parry", "perfect_dodge": f"{ns}:{cid}_perfect_dodge", "guard_break": f"{ns}:{cid}_guard_break"},
                 "passives": [{"id": f"{cid}_focus", "name": "Focus", "description": "TODO", "trigger": "on_hit", "cooldown": 20, "effects": [{"type": "energy", "amount": 1.0}]}]}
    if transform:
        skills["ultimate_hold"] = ({"name": "Transform", "description": "TODO", "duration": 45, "cancel_from": 45, "energy_cost": 60, "uninterruptible": True, "lock_movement": True,
                                    "timeline": [invuln(0, 50), hit(16, 1.2, 5, 360, 1.5), {"tick": 16, "type": "transform", "duration": 600}]}, [(10, "transform", "self", 0)], CAM_TRANSFORM)
        skills["t_basic_1"] = ({"name": "Transformed Attack 1", "description": "TODO", "cooldown": 0, "duration": 10, "cancel_from": 6, "timeline": [hit(3, 1.2, 3.6)]}, [(2, "slash", "self", 0)], None)
        skills["t_basic_2"] = ({"name": "Transformed Attack 2", "description": "TODO", "cooldown": 0, "duration": 12, "cancel_from": 7, "timeline": [hit(3, 1.4, 3.8, 110, 0.8)]}, [(2, "slash", "self", 0)], None)
        skills["t_heavy"] = ({"name": "Transformed Heavy", "description": "TODO", "cooldown": 100, "duration": 28, "cancel_from": 20, "energy_on_hit": 4,
                              "timeline": [hit(9, 4.5, 5, 160, 1.5, guard_break=True)]}, [(8, "swing", "front", 2), (9, "impact", "front", 3)], None)
        skills["t_ultimate"] = ({"name": "Transformed Ultimate", "description": "TODO", "duration": 100, "cancel_from": 100, "energy_cost": 50, "uninterruptible": True, "lock_movement": True,
                                 "timeline": [invuln(0, 105), hit(72, 14.0, 9, 360, 2.5, guard_break=True, stun=40), {"tick": 78, "type": "transform", "end": True}]},
                                [(16, "charge", "self", 0), (72, "burst", "front", 3)], CAM_T_ULT)
        character["moveset"]["ultimate_hold"] = skill_id(ns, cid, "ultimate_hold")
        character["transformed_moveset"] = {"basic": [skill_id(ns, cid, "t_basic_1"), skill_id(ns, cid, "t_basic_2")],
                                            "heavy": skill_id(ns, cid, "t_heavy"), "ultimate": skill_id(ns, cid, "t_ultimate")}
        character["fx"]["transform_end"] = f"{ns}:{cid}_transform_end"

    for key, (base, fx, cam) in skills.items():
        write_skill(root, ns, cid, key, base, fx, cam)
    for key, style in {"basic_1": "slash_a", "basic_2": "slash_b", "basic_3": "overhead", "heavy": "overhead",
                       "t_basic_1": "slash_a", "t_basic_2": "slash_b", "t_heavy": "overhead"}.items():
        if key in skills:
            write_swing(root, ns, cid, key, style, "weapon_t" if key.startswith("t_") else "weapon", first_hit(skills[key][0]), skills[key][0]["duration"])
    finish_class(root, ns, cid, character, transform)

PLACEHOLDER_SOUND = {   # effect name -> vanilla sound event used until you provide your own .ogg
    "slash": "entity.player.attack.sweep", "jab": "entity.player.attack.sweep", "swing": "entity.player.attack.sweep",
    "cast": "entity.player.attack.sweep", "sweep": "entity.player.attack.sweep", "lunge": "entity.player.attack.knockback",
    "leap": "entity.player.attack.knockback", "dash": "entity.player.attack.knockback", "impact": "entity.player.attack.strong",
    "barrage": "entity.player.attack.crit", "charge": "block.beacon.power_select", "transform": "block.beacon.activate",
    "bulwark": "item.armor.equip_iron", "skyfall": "entity.generic.explode", "burst": "entity.generic.explode"}
DEFAULT_SOUND = "entity.player.attack.sweep"
CHARACTER_SOUNDS = {"block": "item.shield.block", "parry": "block.anvil.land", "perfect_dodge": "entity.enderman.teleport",
                    "guard_break": "item.shield.break", "transform_end": "block.beacon.deactivate"}

def retrofit_sounds(root, ns):
    """
    Gives every skill a sound paired with each of its effects (same name as the fx file) and every class its block/parry/... sounds,
    and writes/merges assets/<ns>/sounds.json with placeholder entries that play vanilla sounds. Safe to run again; never overwrites
    anything you changed. Also works on packs made before sounds existed.
    """
    import glob
    events = {}
    for sf in sorted(glob.glob(f"{root}/data/{ns}/skills/*/*.json")):
        cid, key = os.path.basename(os.path.dirname(sf)), os.path.basename(sf)[:-5]
        d = json.load(open(sf)); have = {e["sound"] for e in d["timeline"] if e["type"] == "sound"}
        out, changed = [], False
        for e in d["timeline"]:
            out.append(e)
            if e["type"] == "fx" and e["fx"].startswith(ns + ":"):
                sid = e["fx"]
                effect = sid.split(":", 1)[1][len(f"{cid}_{key}_"):]
                events[sid.split(":", 1)[1]] = PLACEHOLDER_SOUND.get(effect, DEFAULT_SOUND)
                if sid not in have:
                    out.append({"tick": e["tick"], "type": "sound", "sound": sid, "volume": 1.0, "pitch": 1.0}); changed = True
        if changed:
            d["timeline"] = out; w_json(sf, d)
    for cf in sorted(glob.glob(f"{root}/data/{ns}/characters/*.json")):
        cid = os.path.basename(cf)[:-5]; c = json.load(open(cf)); changed = False
        for k, vanilla in CHARACTER_SOUNDS.items():
            if k == "transform_end" and "transformed_moveset" not in c: continue
            if k not in c.setdefault("sounds", {}):
                c["sounds"][k] = f"{ns}:{cid}_{k}"; changed = True
            if c["sounds"][k].startswith(ns + ":"):
                events[c["sounds"][k].split(":", 1)[1]] = vanilla
        if changed: w_json(cf, c)
    path = f"{root}/assets/{ns}/sounds.json"
    existing = json.load(open(path)) if os.path.exists(path) else {}
    for name, vanilla in events.items():
        existing.setdefault(name, {"sounds": [{"name": "minecraft:" + vanilla, "type": "event"}]})
    w_json(path, existing)

def _merge_sounds(root, ns, events):
    path = f"{root}/assets/{ns}/sounds.json"
    existing = json.load(open(path)) if os.path.exists(path) else {}
    for name, vanilla in events.items():
        existing.setdefault(name, {"sounds": [{"name": "minecraft:" + vanilla, "type": "event"}]})
    w_json(path, existing)

def _touch_if_missing(path):
    if not os.path.exists(path): touch(path)

def _hit_sound_for(key):
    if key.endswith("heavy"): return "entity.player.attack.knockback"
    if "ultimate" in key: return "entity.generic.explode"
    if "basic" in key: return "entity.player.attack.strong"
    return "entity.player.attack.crit"

def retrofit_hits(root, ns):
    """
    Every hitbox gets "hit_fx" + "hit_sound" named <cid>_<skill>_hit (plays on each target it really hurts), every class gets a default
    "hit" fx/sound, with a 0-byte .fx placeholder and a vanilla-sound placeholder in sounds.json. Never overwrites anything you set.
    """
    import glob
    events = {}
    for sf in sorted(glob.glob(f"{root}/data/{ns}/skills/*/*.json")):
        cid, key = os.path.basename(os.path.dirname(sf)), os.path.basename(sf)[:-5]
        d = json.load(open(sf)); changed = False
        for e in d["timeline"]:
            if e["type"] != "hitbox": continue
            if "hit_fx" not in e:
                e["hit_fx"] = e["hit_sound"] = f"{ns}:{cid}_{key}_hit"; changed = True
            if e["hit_fx"].startswith(ns + ":"):
                _touch_if_missing(f"{root}/assets/{ns}/fx/{e['hit_fx'].split(':', 1)[1]}.fx")
            if e.get("hit_sound", "").startswith(ns + ":"):
                events[e["hit_sound"].split(":", 1)[1]] = _hit_sound_for(key)
        if changed: w_json(sf, d)
    for cf in sorted(glob.glob(f"{root}/data/{ns}/characters/*.json")):
        cid = os.path.basename(cf)[:-5]; c = json.load(open(cf))
        if "hit" not in c:
            c["hit"] = {"fx": f"{ns}:{cid}_hit", "sound": f"{ns}:{cid}_hit"}; w_json(cf, c)
        if c["hit"].get("fx", "").startswith(ns + ":"): _touch_if_missing(f"{root}/assets/{ns}/fx/{c['hit']['fx'].split(':', 1)[1]}.fx")
        if c["hit"].get("sound", "").startswith(ns + ":"): events[c["hit"]["sound"].split(":", 1)[1]] = "entity.player.attack.strong"
    _merge_sounds(root, ns, events)

# ----------------------------------------------------------------------------- impact frames (hand-drawn image sequences)
def _png_gray(path, w, h, inside):
    rows = [b"\x00" + bytes(255 if inside(x / w, y / h) else 0 for x in range(w)) for y in range(h)]
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 0, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b""))

def write_impact_placeholder(folder, w=640, h=360):
    """Five crude black/white frames (slash, inverted slash, X, inverted X, burst). Replace them with your hand-drawn frames, same folder."""
    shapes = [lambda u, v: abs(u + v - 1) < 0.05,
              lambda u, v: not abs(u + v - 1.1) < 0.05,
              lambda u, v: abs(u + v - 1) < 0.07 or abs(u - v) < 0.07,
              lambda u, v: not (abs(u + v - 1) < 0.10 or abs(u - v) < 0.10),
              lambda u, v: ((u - 0.5) ** 2 + ((v - 0.5) * 0.5625) ** 2) ** 0.5 < 0.18]
    for i, fn in enumerate(shapes, 1):
        _png_gray(f"{folder}/{i:03d}.png", w, h, fn)
    w_json(f"{folder}/sequence.json", {"fps": 60, "frame_duration": 2, "fit": "cover"})

def retrofit_impact_frames(root, ns):
    """Ultimates get an impact_frames action (starting on their first hit, at 60 fps = 3 frames per tick) and a placeholder sequence."""
    import glob
    for sf in sorted(glob.glob(f"{root}/data/{ns}/skills/*/*.json")):
        cid, key = os.path.basename(os.path.dirname(sf)), os.path.basename(sf)[:-5]
        if key not in ("ultimate", "ultimate_hold", "t_ultimate"): continue
        d = json.load(open(sf))
        if any(e["type"] == "impact_frames" for e in d["timeline"]): continue
        d["timeline"].append({"tick": 0, "type": "impact_frames", "sequence": f"{ns}:{cid}_{key}", "start_frame": first_hit(d) * 3, "frame_duration": 2})
        d["timeline"].sort(key=lambda e: e["tick"])
        w_json(sf, d)
        folder = f"{root}/assets/{ns}/impact_frames/{cid}_{key}"
        if not os.path.isdir(folder): write_impact_placeholder(folder)

def retrofit_all(root, ns):
    """Everything a class needs on top of its skills: hit effects/sounds, effect sounds, class sounds, impact frames. Safe to re-run."""
    retrofit_hits(root, ns)
    retrofit_sounds(root, ns)
    retrofit_impact_frames(root, ns)

def finish_class(root, ns, cid, character, transform, props=None, hidden=None):
    """character-level placeholders (block / parry / dodge + character fx), the rig, and the character file."""
    for k in ("block", "parry", "dodge"):
        placeholder_animation(root, ns, cid, k, 0.5)
    for k, rl in character.get("fx", {}).items():
        touch(f"{root}/assets/{ns}/fx/{rl.split(':', 1)[1]}.fx")
    if props is None:
        props, hidden = template_props(transform)
    write_rig(root, ns, cid, props, hidden)
    w_json(f"{root}/data/{ns}/characters/{cid}.json", character)
    retrofit_all(root, ns)
