#!/usr/bin/env python3
"""Kept for older instructions: same as upgrade_pack.py (adds sounds, hit effects and impact-frame placeholders)."""
import os, sys, runpy
runpy.run_path(os.path.join(os.path.dirname(os.path.abspath(__file__)), "upgrade_pack.py"), run_name="__main__")
