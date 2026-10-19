# Content guide: animations, effects, characters, codex

Time cheat-sheet: **20 ticks = 1 second**. Every `tick` in a skill is counted from the moment the move starts.

---------------------------------------------------------------------------------------------------------

## 0. Content packs: the `enmitycombat/` folder (TACZ-style)

On first launch the mod creates **`<minecraft folder>/enmitycombat/`** (next to `mods/` and `saves/`). Drop packs into it:

```
.minecraft/enmitycombat/
    my_first_pack.zip        <- a .zip with data/ and/or assets/ at its TOP level
    another_pack/            <- or a plain folder
        data/mypack/characters/hero.json
        data/mypack/skills/hero/slash.json
        data/mypack/cutscenes/hero_ultimate.json
        assets/mypack/player_animations/hero_slash.json
        assets/mypack/fx/hero_slash.fx
        assets/mypack/lang/en_us.json
```
* Use your **own namespace** (`mypack` above): lowercase, no spaces. Ids then look like `mypack:hero`, `mypack:hero/slash`.
* Everything in this guide (sections 1-4) works the same inside a pack; just use the pack's `data/` and `assets/` instead of the mod's `src/main/resources`.
* **Reloading:** `/reload` re-reads characters, skills and cutscenes. **F3+T** reloads animations and effects. New packs may need a full restart the first time.
* A folder must have `assets/` or `data/` directly inside it; if you unzipped into an extra sub-folder the log says `has no assets/ or data/ at its top level`.
* Disable a pack by renaming it with a leading underscore (`_my_pack.zip`).
* Packs can reference built-in content (for example `enmitycombat:example_ultimate` cutscene) and can **override** a built-in character by using the same id.
* **Multiplayer:** the server needs the pack (defines characters/skills; the server syncs those definitions to players, so the codex works), and every player needs the pack too, because animations and effects are client assets.
* On Windows, close/avoid editing a `.zip` while the game has it open; use a plain folder while developing.
* A ready-made example is `my_first_pack.zip` (character "Rookie"). Drop it in the folder, `/reload`, press **J**.

---------------------------------------------------------------------------------------------------------

## 1. Adding an animation (PlayerAnimator)

**What you need:** an animation JSON in PlayerAnimator/Emotecraft format. Ways to make one:
* **Blockbench**: animate the template player model, export the animation JSON (recommended: easiest to see what you're doing).
* **Blender**: the `emote_creator` rig from Emotecraft's GitHub (`KosmX/emotes`, folder `blender`).
* Keep the player model standard (no custom skeleton) or the animation won't line up.

**Steps**
1. Name the file lowercase with no spaces, e.g. `sword_slash_1.json`.
2. Put it in `src/main/resources/assets/enmitycombat/player_animations/`.
   *If the log later says the animation is not found, also try the folder `player_animation` (singular): older versions of the library used that name.*
3. Reference it from a skill timeline as `enmitycombat:sword_slash_1` (file name without `.json`):
   ```json
   { "tick": 0, "type": "animation", "anim": "enmitycombat:sword_slash_1" }
   ```
4. For block / parry / dodge, put the id in the character's `"animations"` block (`block`, `parry`, `dodge`). The **block animation should be exported as looping**.
5. `gradlew runClient` (or rebuild the jar).

**Timing tips**
* Put the `hitbox` action on the tick where the swing visually connects, not tick 0. If the swing connects at animation tick 6, use `"tick": 6`.
* Set the skill's `duration` to about the animation length, and `cancel_from` to the tick where the recovery starts (when the player may chain into the next move).
* A move with no animation still works. Missing animations are skipped and **logged once** in `logs/latest.log` as `Player animation '...' not found`; search the log for that text to see what's missing.

**Faster testing (no rebuild):** copy the same folder structure (`assets/enmitycombat/player_animations/...`) into a resource pack and press **F3+T** in game. This should work because the library reloads with resources, but if it doesn't, rebuild.

---------------------------------------------------------------------------------------------------------

## 2. Adding an effect (Photon)

1. Singleplayer, creative world. Open the editor with `/photon particle_editor` (some versions also accept `/pe`; use tab-completion).
2. Build the effect. **Save** keeps an editable project; **Export** produces the playable `.fx`. (If you forget a name, nothing saves.)
3. Copy the exported `.fx` into `src/main/resources/assets/enmitycombat/fx/` (e.g. `slash_blue.fx`).
   * Custom textures/materials the effect uses must ship too. Photon keeps them under its own `ldlib` assets folder; copy anything your effect references into your `assets/` folder, or use Photon's pack export if your version has one.
4. Reference it as `enmitycombat:slash_blue`:
   ```json
   { "tick": 4, "type": "fx", "fx": "enmitycombat:slash_blue", "at": "front", "distance": 1.5 }
   ```
   `at`: `self` (follows the player), `front` (a point in front, at `distance`), `target` (follows the entity that was hit, passive/hit effects only).
5. Rebuild or `runClient`.

**Quick preview without rebuilding:** exported effects live in Photon's own folder under the `photon` namespace, so an FX you just exported can be referenced as `photon:<name>` straight away. Look for the folder the editor exported to (it's under `.minecraft/ldlib/...` for Photon 1.x). Photon also has a `/photon fx ...` command for test-playing an effect; use tab-completion to see its arguments.

**If an effect doesn't show:** a vanilla sweep particle appears as a placeholder and `FX '...' not found` is logged once. Check the id spelling, lowercase file names, and that the file is in `assets/<namespace>/fx/`.

Effect hooks that already exist besides skill timelines (character JSON `"fx"` block): `parry`, `perfect_dodge`, `guard_break`, `transform_end`.

---------------------------------------------------------------------------------------------------------

## 3. Adding a new character

**Fastest way: copy the Lancer** (`characters/example_lancer.json` + `skills/lancer/*`), a complete character with no transformation.

Where files go (in the mod: `src/main/resources/data/<ns>/...`; or in a datapack: `<world>/datapacks/mypack/data/<ns>/...` with a `pack.mcmeta` containing `{"pack":{"description":"mine","pack_format":15}}`; datapacks reload with `/reload`):
```
data/<ns>/characters/hero.json            -> id  <ns>:hero
data/<ns>/skills/hero/slash.json          -> id  <ns>:hero/slash      (sub-folders become part of the id)
data/<ns>/cutscenes/hero_ultimate.json    -> id  <ns>:hero_ultimate
```

**Steps**
1. Create the skills. Minimum: a few `basic_*`, and whichever of `skill_1..3`, `special`, `heavy`, `ultimate` you want. Any slot you leave out is simply unavailable.
2. Create the character file and list the skill ids in `"moveset"` (the basic chain is a list, in order).
3. Give everything a `"name"` and `"description"` so the codex can show it.
4. Optional: `"transformed_moveset"` + an `ultimate_hold` skill containing a `{"type":"transform","duration":600}` action (see the Swordsman).
5. Optional: passives (see README for triggers / effect types).
6. `/reload`, then press **J** to open the codex and click **Equip** (or `/enmity character <ns>:hero`).

**Reuse is free.** Two characters can point at the same skill file, and any skill can use any cutscene.

**Common mistakes** (the log will say `Failed to parse <id>`):
* Missing comma / bracket in the JSON.
* A skill id in the character file that doesn't exist (the codex shows "missing skill ...").
* `hitbox` placed at a tick later than `duration`: it never fires.
* No `invuln` on an ultimate that has a cutscene: you can be hit while locked.

**Balance knobs** per character: `base_attack`, `max_energy`, `basic_cooldown_reduction`, `combo_reset_ticks`. Per skill: `cooldown`, `energy_cost`, `energy_gain`, `energy_on_hit`, `damage` multipliers on hitboxes. Global numbers (parry window, block %, dodge timings) are in `config/enmitycombat-common.toml`.

---------------------------------------------------------------------------------------------------------

## 3b. Props: weapons and accessories

Props are things you can *see* on the character: a sword that sits on the back and jumps into the hand when you attack, a shield on the hip, a hat. They are listed in the character JSON:

```json
"props": [
  { "id": "blade", "item": "minecraft:iron_sword", "form": "base",
    "attach": "right_hand",
    "idle": { "attach": "back", "offset": [0, 0, 0], "rotation": [0, 0, -40] } },
  { "id": "blade_awakened", "item": "minecraft:netherite_sword", "form": "transformed",
    "attach": "right_hand",
    "idle": { "attach": "back", "rotation": [0, 0, -40] } }
]
```
**What to show** (pick one): `"item": "minecraft:iron_sword"` (any item, add `"custom_model_data": 5` to use a resource-pack model override) or `"model": "mypack:rookie_blade"` (your own model, see below).

| Field | Meaning |
|---|---|
| `attach` | `right_hand`, `left_hand`, `back`, `chest`, `hip_left`, `hip_right`, `head`. Used while the character is **drawn** (attacking, blocking, or within 3 seconds of either). |
| `idle` | Optional placement when *not* drawn (e.g. sheathed on the back). Missing fields inherit from the main placement. No `idle` = the prop stays where it is. |
| `offset` | `[x, y, z]` in pixels (16 = 1 block). |
| `rotation` | `[x, y, z]` degrees, applied Z then Y then X. |
| `scale` | Size multiplier. |
| `form` | `any` (default), `base` (hidden while transformed) or `transformed` (shown only while transformed): this is how you swap weapons on transform. |
| `when` | `combat` (default: only while combat mode is on) or `always` (whenever the character is equipped). |
| `only_when_drawn` | `true` = hide it completely while sheathed. |

**Axes** for everything except hands: `x+` = the character's left, `y+` = up, `z+` = forward, origin = middle of that body part. For **hands** the frame is the same one Minecraft uses for held items, so `rotation: [0,0,0]` looks like a normal held item and you only nudge it. Because props are attached to the hand/body bone, **your PlayerAnimator animations move them automatically**.

**Tuning loop:** put the prop JSON in your pack, `/reload` (re-sends the character to the client), look at it in third person, tweak the numbers, repeat. The placement numbers need no rebuild.

**Your own weapon model**
1. In Blockbench make a Java Block/Item model (a 16x16 pixel sword, or a full 3D model), set its `display` transforms if you like, and export to `assets/<namespace>/models/prop/<name>.json`, with its textures in `assets/<namespace>/textures/prop/`.
2. Use `"model": "<namespace>:<name>"` in the prop (the `prop/` folder is added for you).
3. Press **F3+T**. Every JSON under `models/prop/` in the mod *or any content pack* is registered automatically.
`my_first_pack.zip` has a complete example (`rookie_blade`, a 16x16 texture with a handheld model).

**Limits (current version)**
* Props render in **third person only** (no first-person view of them).
* A vanilla item you are actually holding still renders as usual, so hold an empty hand to avoid a double weapon.
* A missing model/item logs one warning in `latest.log` and the prop is skipped.

---------------------------------------------------------------------------------------------------------

## 3c. Rigs: one Blockbench rig for the player AND all your props (multiple weapons, transforming parts, thrusters...)

A **rig** is a Blockbench model that contains the Emotecraft-style player skeleton **plus any extra bones you like**. You model and animate everything in ONE file and ONE timeline. The mod splits it at runtime: PlayerAnimator moves the player, the mod moves your props, both start on the same tick.

### Folder layout (like a gunpack: fixed names)
```
assets/<ns>/rigs/<name>/
    model.geo.json      Bedrock geometry, format 1.12.0   (Blockbench: File > Export > Export Bedrock Geometry)
    texture.png
    rig.json            optional settings (see below)
    animations.json     optional prop-only animations (idle states etc.)
assets/<ns>/player_animations/<anim>.json     animations that move player + props together (Bedrock/GeckoLib animation format)
```
and in the character JSON: `"rig": "<ns>:<name>"`.

### The positioning bones (reserved names)
These six bones are the **real player's skeleton**. Keep the reference player cubes in them so you can see what you are animating, but they are **never drawn** by the mod:

| Bone name | Follows |
|---|---|
| `head` | the player's head |
| `torso` (or `body`) | the player's torso |
| `right_arm`, `left_arm` | the arms |
| `right_leg`, `left_leg` | the legs |

**Everything else is a prop bone and is drawn.** Rules:
* A prop bone follows whichever player bone it is **parented under** (drag a group into `right_arm` in Blockbench and it follows the real, animated right arm). A prop bone with no player ancestor follows the **torso**. Bones can be nested (a blade inside a sword inside the arm, a hinged panel inside a thruster...).
* Model props **where they should be on the standing player**, in the rig's own coordinates: a sword at the hand, a thruster on the back, a halo above the head. Start from the rig in `my_first_pack.zip` (open its `model.geo.json` in Blockbench).
* Use **Box UV** for cubes (per-face UV is not supported yet).

### Animating
* **Skill animations**: make the animation in Blockbench with the rig, animating player bones *and* prop bones together, export it as an animation JSON into `assets/<ns>/player_animations/<anim>.json`, and use it exactly like before: `{ "type": "animation", "anim": "<ns>:<anim>" }`. PlayerAnimator reads the player bones; the mod reads the other bones from the same file. (One animation per file.)
* **State layer (`idle`, `idle_drawn`, `idle_transformed`)**: in `rig.json` point these at looping animations (put them in the rig's `animations.json`, whose entry names become `<ns>:<name>`). The mod plays the right one by state: *idle* normally, *idle_drawn* while attacking/blocking (and for 3 s after), *idle_transformed* while transformed. The skill animation runs **on top**, and only replaces the channels it animates.
* **Switching and transforming weapons**: do it with `scale`. A bone with scale `[0,0,0]` is hidden. In `idle` set the drawn weapon to scale 0 and the sheathed one to 1; in `idle_drawn` the reverse; in `idle_transformed` show the transformed weapon. Skill animations then never need to repeat the hide/show keys. Thrusters, wings, panels: just animate them.
* Supported keyframes: `position`, `rotation`, `scale`; linear and catmullrom; `loop`: true / false / `"hold_on_last_frame"`. Not supported: molang expressions, sound/particle keyframes.

`rig.json`:
```json
{ "idle": "mypack:rookie_idle", "idle_drawn": "mypack:rookie_idle_drawn", "idle_transformed": "mypack:rookie_idle_t",
  "rotation_signs": [-1, 1, -1], "position_signs": [1, -1, 1] }
```

### If rotations look wrong (calibration)
The Blockbench -> Minecraft axis conversion is derived, not verified in game. If a swing goes the wrong way, flip the matching entry in `rotation_signs` (x, y, z) or `position_signs`, then press F3+T. The example rig's `rookie_basic_1` swings the sword bone about X, which makes it a good test.

### If the player part of the animation doesn't play
PlayerAnimator documents GeckoLib/Bedrock-format animations as "not guaranteed". If your player does not move but the props do (check `latest.log`), export the **player** part in Emotecraft format to `player_animations/<anim>.json` instead, and put the **props** part in the rig's `animations.json` under the same name (`"<anim>"`): same ids, same skills, two files.

### Limits (v1)
Third person only. Props follow the player's bones but the player's hands do **not** follow prop bones (no IK / two-handed grips). The standard player skeleton is assumed (slim arms are off by half a pixel).

---------------------------------------------------------------------------------------------------------

## 4. The codex

Press **J** (rebindable). Left: all loaded characters. Right: lore, stats, every move with cooldown / energy cost / description, the transformed moveset, and passives. **Equip** switches character (arrow keys also change selection; mouse wheel scrolls).

Everything it shows is JSON:

| Where | Field | Shown as |
|---|---|---|
| character | `name`, `title`, `description` | header + lore paragraph |
| skill | `name`, `description` | move name + text; cooldown and cost are read from the skill automatically |
| passive | `name`, `description`, `trigger` | passive list |

Set `allowSelfSelect = false` in the config on servers if only ops should be able to equip characters.

---------------------------------------------------------------------------------------------------------

## 5. The two ultimates (Swordsman)

| | **Moonfall** (tap) | **Crimson Eclipse** (transformed) |
|---|---|---|
| Cost | 100 energy | 50 energy |
| Cutscene | `example_ultimate` (circling shots) | `example_t_ultimate` (follows the dash, orbits the barrage, slams in low-angle close-up) |
| Shape | Charge, then **one** huge blast | Dash into enemies, **8-hit barrage** that pins them in place (no knockback), then a finishing slam |
| Extras | guard break + stun | guard break + stun, **heals 6**, **+50% damage and 2x cooldown recovery for 10 s**, refunds 50% of cooldowns, **ends the transformation** |

To make your own ultimate feel different, vary three things: the **timeline shape** (one hit vs many), the **after-effect** (heal / buff / cooldown refund / ending a transform), and the **cutscene**.

---------------------------------------------------------------------------------------------------------

## 5. Camera mods: Focus, Leawind's Third Person, Shoulder Surfing

Cutscenes and skills are built so a camera mod cannot break them. What the mod does, and what to know.

**Cutscene camera (what changed in 0.6)**
* The cutscene camera is applied **after** the camera mod has positioned the camera, at two separate points (end of `Camera.setup` and right after it inside `GameRenderer.renderLevel`), so it still works if a mod cancels `Camera.setup` early. Final yaw/pitch/FOV are re-applied by LOWEST-priority events, so nothing else can win the last word.
* **Mouse-look is frozen** during a cutscene (this stops the camera mods too, because they all turn through the same path), and movement input is zeroed.
* If you were in **first person**, the camera switches to third person for the cutscene and goes back afterwards.
* **Blending**: new optional fields in the cutscene JSON: `"blend_in": 6` (ticks easing from the live camera into the first keyframe, default 0 = cut) and `"blend_out": 8` (ticks easing back to the camera mod's view, default 8). This hides the snap you would otherwise see when a smoothing camera takes over again.

**Aiming and facing**
* Aim always comes from the **camera ray**, then from the player's eyes to the point under the crosshair, which is the "corrected crosshair" idea, so it works with offset shoulder cameras. (In vanilla front-facing third person it uses the player's look direction.)
* On every skill input the **player model is turned to the aim direction** on the client (the client owns the player's facing). Without this, decoupled cameras leave the model facing the wrong way while the hitbox goes where you aimed.

**Per mod**
| Mod | Notes |
|---|---|
| **Focus** (Z-targeting lock-on; its camera is derived from Shoulder Surfing's) | Lock-on stays active during a cutscene, so the character may keep turning toward the target and Focus's lock-on indicator / cinematic bars can still be visible. If bars double up, turn off Focus's cinematic bars. If the player fades out in close-ups, turn off Focus's "fade player when in the way", or keep that cutscene's camera farther than ~1.5 blocks. |
| **Leawind's Third Person** | Its camera smoothing is covered by `blend_out`. If the camera jumps to first person when you attack, check its options about aiming / using items. If the player vanishes in close-ups, disable its "hide player when camera is close" style option. |
| **Shoulder Surfing Reloaded** | Same behaviour as before, plus the extra late hook. |

**What I could not do:** neither mod publishes a documented API I could use, and I could not read Focus's source, so there is **no direct hook into their lock-on target or camera state** (for example "aim at the locked target" or "release lock-on when a cutscene starts"). Everything above works by being the last thing to touch the camera. If you want deeper integration, tell me what exactly misbehaves and share the log lines.

**Checking it works:** on startup the log prints `Camera mods detected: Focus=..., Leawind's Third Person=..., Shoulder Surfing=...`. If the mod you installed shows `false`, tell me the exact mod id from its `mods.toml` / Mods screen and I will add it.

The new mixins (`GameRendererMixin`, `MouseHandlerMixin`) are marked non-fatal: if they fail to apply the game still runs, and the log shows which one failed.
