#!/usr/bin/env python3
"""
Create a complete, working class skeleton (data + placeholders + Blockbench rig) in one command.

  python tools/new_class.py hero                       -> ./hero/            (namespace "hero", class "hero")
  python tools/new_class.py hero --ns mypack --transform --out "<minecraft folder>/enmitycombat"

The result is a content pack: drop the folder into <minecraft folder>/enmitycombat/, press /reload and F3+T, then open the codex (J).
"""
import argparse, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from enmity_scaffold import scaffold_class

p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
p.add_argument("name", help="class id, lowercase letters/digits/underscores, e.g. hero")
p.add_argument("--ns", help="namespace (default: same as the class id)")
p.add_argument("--out", default=".", help="folder to create the pack in")
p.add_argument("--transform", action="store_true", help="also generate the hold-ultimate transform moveset")
a = p.parse_args()
if not re.fullmatch(r"[a-z0-9_]+", a.name):
    sys.exit("class id must be lowercase letters, digits and underscores")
ns = a.ns or a.name
root = os.path.join(a.out, a.name if not a.ns else ns)
scaffold_class(root, ns, a.name, a.name.replace("_", " ").title(), a.transform)
print(f"Created {root}\n  character id: {ns}:{a.name}\nNext: edit data/{ns}/characters/{a.name}.json and the skills, replace the placeholder animations/effects.")
