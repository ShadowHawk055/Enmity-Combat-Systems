# Enmity Combat Systems

> **Renamed from "Action Combat".** The mod id is now `enmitycombat`, the pack folder is `<minecraft folder>/enmitycombat/`
> (the old `actioncombat/` folder is moved automatically on first launch), the command is `/enmity` (alias `/enmitycombat`),
> the config is `enmitycombat-common.toml`, and built-in ids changed from `actioncombat:...` to `enmitycombat:...`.
> Content packs that reference built-in content must be updated (search & replace `actioncombat:` -> `enmitycombat:`).
> Saved per-player data (equipped character, energy, cooldowns) and the old config file are not carried over.

## Overview (Forge 1.20.1)

Action-RPG / gacha-style combat for Minecraft: block, parry, dodge, 3 skills + special + heavy, ultimate (tap / hold-to-transform),
passives, camera cutscenes. **Everything a designer touches is datapack JSON**; Java is only the engine.

> **Status: UNCOMPILED SKELETON.** Written without access to Forge/Photon/PlayerAnimator jars, so expect a handful of
> compile errors on first build (mostly mapping names / third-party signatures). Third-party calls are isolated in
> `client/AnimBridge.java` and `client/FxBridge.java`; the only mixin is `mixin/CameraMixin.java`.

## New in this version
* **Config options**: `photosensitivityMode` (disables all impact frames; also a button in the Codex) and `debug.showHitboxes` (outlines skill hitboxes; `/enmity debug hitboxes true|false`). See docs/CONTENT_GUIDE.md section 6.
* **Hit effects + hit sounds** on every hitbox, and **hand-drawn impact frames** (image sequence in a folder, started by a skill with frame-accurate timing). See docs/CONTENT_GUIDE.md sections 3g and 3h; `tools/upgrade_pack.py` updates older packs.
* **Animating props**: weapon bones move on their own on top of the arm (wrist flicks, thrusts); real swing animations are included. Section 3f.
* **Sounds**: every effect has a paired sound (vanilla placeholders until you add your own `.ogg`), custom pack sounds now work, class sounds for block/parry/dodge. See docs/CONTENT_GUIDE.md section 3e and `tools/add_sounds.py`.
* **Standard class layout + `tools/new_class.py`**: every class uses the same file layout and naming, and every skill has an animation and an effect (placeholders until you replace them). One command scaffolds a whole class. See docs/CONTENT_GUIDE.md section 3.
* **Cutscenes are Blockbench camera groups** (`camera`, `camera_target`, `camera_fov`) in the skill's own animation file. The old `data/<ns>/cutscenes/*.json` files are gone. See section 3d.
* **Camera-mod compatibility** (Focus, Leawind's Third Person, Shoulder Surfing): cutscene camera hardened (late override, frozen mouse-look, forced third person, `blend_in`/`blend_out`), model faces the aim direction. See docs/CONTENT_GUIDE.md section 5.
* **Rigs**: one Blockbench rig = Emotecraft player skeleton + any prop bones, animated in one timeline (swappable/transforming weapons, thrusters...). See docs/CONTENT_GUIDE.md section 3c and `my_first_pack.zip`.
* **Props**: weapons/accessories on characters (`"props"` in the character JSON), vanilla items or your own models, sheathed/drawn placement, swapped on transform. See docs/CONTENT_GUIDE.md section 3b.
* **Content packs folder** `<minecraft folder>/enmitycombat/` (TACZ-style): drop `.zip`s or folders with `data/` + `assets/` and they load automatically. See docs/CONTENT_GUIDE.md section 0 and `my_first_pack.zip`.
* **Codex** (press **J**): read every character's lore, moves and passives, and equip one. Driven by `name` / `title` / `description` JSON fields.
* Second example character (**Vanguard Lancer**) as a copy-me template.
* Transformed ultimate (**Crimson Eclipse**) is now a different move from Moonfall, with its own cutscene.
* Missing animations / effects are logged once in `latest.log`.
* See **docs/CONTENT_GUIDE.md** for adding animations, effects and characters.

## Build
The Gradle wrapper (Gradle 8.8) and a `gradle.properties` with **known-working versions** are included (Forge 47.4.10, PlayerAnimator 1.0.2-rc1, LDLib 1.0.52, Photon 1.1.9). Needs JDK 17 or newer installed.
* Test in game: `gradlew runClient` (Mac/Linux: `./gradlew runClient`)
* Build the jar: `gradlew build` -> `build/libs/enmitycombat-<mod_version>.jar`
* In game: `/enmity character enmitycombat:example_swordsman`, or press **J** for the codex.

## Default controls (all rebindable)
| Action | Key | Action | Key |
|---|---|---|---|
| Basic attack chain | Left click (hold = keep chaining) | Special | E |
| Block / parry | Hold Right click | Heavy | Q |
| Skills 1-3 | 1 / 2 / 3 | Ultimate | R tap = ultimate, **hold = transform** |
| Dodge | Left Alt (direction = movement keys, relative to camera) | Combat mode on/off | V |
| | | Inventory while in combat mode | Tab |

In combat mode: 1/2/3/E/Q stop doing their vanilla thing, use-item/place/pick-block are disabled and block-breaking is off. Press V to go back to normal Minecraft.

## Rules (all numbers in `config/enmitycombat-common.toml`)
* **Block**: -70% damage. **Parry**: hit lands within 6 ticks (0.3 s) of *pressing* block -> no damage, attacker stunned 26 ticks (1.3 s). Short re-press lock stops parry spam.
* **Guard break**: a hitbox with `"guard_break": true`, any damage type in tag `enmitycombat:guard_break`, or any attacker in entity tag `enmitycombat:guard_break_attackers` breaks block *and* parry; full damage goes through and block is locked for 40 ticks.
* **Dodge**: 10 i-frame ticks. Getting hit by an attack during them = **perfect dodge**: damage negated, every cooldown refunded 30% of its total, +energy, dodge cooldown reset, `on_perfect_dodge` passives fire.
* **Basic chain** shaves `basic_cooldown_reduction` ticks off every skill cooldown per step. Cooldowns can also be sped up by `buff` (`cooldown_rate`) or cut by `cooldown_reduce` actions.
* **Ultimate**: tap = `ultimate` slot. Hold R >= 10 ticks = `ultimate_hold` slot, whose timeline contains a `transform` action. While transformed, any slot listed in `transformed_moveset` replaces the base one (unlisted slots fall back to base).

## Data layout (`data/<namespace>/...`, hot-reload with `/reload`)
```
characters/<id>.json     moveset, transformed_moveset, passives, animation + fx ids, stats
skills/<path>.json       one move = a timeline of actions
(cutscenes are no longer data files: they are camera groups in the animation files, see below)
tags/entity_types/guard_break_attackers.json , tags/damage_type/guard_break.json
```
### Skill JSON
```json
{ "cooldown": 120, "duration": 22, "cancel_from": 14, "energy_cost": 0, "energy_gain": 6, "energy_on_hit": 3,
  "uninterruptible": false, "face_aim": true, "lock_movement": false,
  "timeline": [ { "tick": 0, "type": "animation", "anim": "mymod:swing" },
                { "tick": 5, "type": "hitbox", "range": 3.5, "angle": 90, "damage": 2.2 } ] }
```
`cancel_from` = tick at which the next combo step / skill / dodge / block may interrupt it. Inputs pressed earlier are buffered (8 ticks).
`damage` is a multiplier of the character's `base_attack` x active buffs.

### Action types (`"type": ...`) — usable in skill timelines *and* passive `effects`
| type | fields |
|---|---|
| `animation` | `anim` (omit = clear) |
| `fx` | `fx`, `at`: self / target / front, `distance` |
| `sound` | `sound` (vanilla, mod or pack-defined id), `volume`, `pitch` |
| `camera` | `animation` (file with the camera groups), `hide_hud`, `letterbox`, `blend_in`, `blend_out` |
| `hitbox` | `range`, `angle` (360 = circle), `height`, `offset`, `damage`, `knockback`, `guard_break`, `stun`, `max_targets`, `hit_fx`, `hit_sound`, `hit_sound_volume`, `hit_sound_pitch` |
| `impact_frames` | `sequence` (folder of images), `start_frame`, `frame_duration`, `fps` |
| `dash` | `speed`, `vertical`, `direction`: forward / back |
| `invuln` | `ticks` |
| `energy` | `amount` |
| `cooldown_reduce` | `ticks`, `fraction` (of each skill's total CD) |
| `buff` | `id`, `damage_mult`, `cooldown_rate`, `energy_mult`, `duration` |
| `heal` | `amount` |
| `mob_effect` | `effect`, `duration`, `amplifier`, `on`: self / target |
| `transform` | `duration`, or `"end": true` |

**Add your own** in Java: `Actions.register("my_type", json -> ctx -> { ... });` (see `data/Actions.java`).

### Passives
`trigger`: `always`, `on_hit`, `on_basic_hit`, `on_skill_cast`, `on_ultimate`, `on_parry`, `on_block`, `on_perfect_dodge`, `on_transform`, `on_transform_end`.
`always` passives may use `{"type":"attribute","attribute":"minecraft:generic.max_health","amount":4,"operation":"addition"}`. Optional `"cooldown"` = internal cooldown in ticks.

### Cutscenes
Authored in Blockbench as animated groups `camera`, `camera_target`, `camera_fov` inside the skill's animation file, started by a `camera` action. Full details in docs/CONTENT_GUIDE.md section 3d. Input is locked during a cutscene; give the move `invuln` for its length.

## Animations (PlayerAnimator)
Chosen because it's what Better Combat-style mods use: works in first *and* third person, any limb, plain JSON. Make animations in Blender with the Emotecraft plugin (or the Emotecraft in-game creator), export JSON to `assets/<ns>/player_animations/<name>.json`, reference as `<ns>:<name>`. Missing animations are skipped silently, so the mod runs before you make any. Looping animations (block) should be marked looped in the export. *Not implemented yet:* directional dodge animations, fade blending.

## Effects (Photon)
Build in the Photon editor, save as `assets/<ns>/fx/<name>.fx`, reference as `<ns>:<name>` from `fx` actions or the character's `fx` map. Until an FX exists a vanilla sweep particle is spawned as a placeholder.

## Camera-mod compatibility (Shoulder Surfing, lock-on)
* **Aiming never uses player rotation.** The client ray-casts from the *camera* and sends "eyes -> point under crosshair" to the server (`AimResolver`). Lock-on mods work automatically (their camera looks at the target); for stronger auto-aim call `AimResolver.register(mc -> /* dir to locked target or null */)`.
* **Cutscene camera** is a mixin at the *end* of `Camera.setup` (priority 2000) so it overrides whatever the camera mod computed, only while a cutscene runs; afterwards the camera mod is simply in control again.
* Dodge direction is derived from the camera yaw.

## What is stubbed / TODO
* Character selection is a command only (`/enmity character <id>`, `clear`, `energy <n>`, `resetcooldowns`). Needs a GUI / item.
* HUD is placeholder rectangles; no icons yet.
* "Witch time" after a perfect dodge (hook in `DefenseHandler.perfectDodge`).
* Projectile skills (add a `projectile` action), hit-stop / screen shake, per-character block animation variants.
* Parry/dodge are server-authoritative using tick resolution (50 ms). Fine for singleplayer/LAN; high ping shifts the effective window.
* Unverified API points to check first if the build complains: `FXHelper.getFX`, `EntityEffect`/`BlockEffect` constructors (Photon), `PlayerAnimationFactory.ANIMATION_DATA_FACTORY` (PlayerAnimator), the Mixin refmap setup in `build.gradle`.
