#!/usr/bin/env python3
"""
Bring an existing pack up to date: adds (without touching anything you already set)
  - a sound for every effect, class sounds for block/parry/dodge, sounds.json
  - hit_fx / hit_sound on every hitbox, plus the class default "hit"
  - impact_frames on the ultimates, with placeholder sequences
Placeholders: 0-byte .fx files, vanilla sounds, crude black/white frames. Replace them with your own under the same names.

  python tools/upgrade_pack.py "<minecraft folder>/enmitycombat/my_pack" my_namespace
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from enmity_scaffold import retrofit_all
if len(sys.argv) != 3:
    sys.exit(__doc__)
retrofit_all(sys.argv[1], sys.argv[2])
print("Done. In game: /reload, then F3+T.")
