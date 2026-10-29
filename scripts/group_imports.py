#!/usr/bin/env python3
"""Splits Java imports into blank-line-separated groups, keeping the sorted order within each group.

Run after google-java-format (which sorts imports into one block), or run google-java-format with
--skip-sorting-imports afterwards. Usage: scripts/group_imports.py [files or directories...]
(default: src).
"""
import re
import sys
from pathlib import Path

# Static imports first (as Google style), then these prefixes in order; anything else goes in its own
# group (by top-level package) after "org.", and the project's own packages come last.
GROUPS = ["java.", "javax.", "com.google.", "com.mojang.", "net.minecraft.", "net.neoforged.", "org."]
OWN = "zone.moddev."
IMPORT = re.compile(r"^import (static )?([\w.]+(?:\.\*)?);$")


def group_key(line):
    static, name = IMPORT.match(line).groups()
    if static: return (0, "", name)
    if name.startswith(OWN): return (3, "", name)
    for index, prefix in enumerate(GROUPS):
        if name.startswith(prefix): return (1, f"{index:02d}", name)
    return (2, name.split(".")[0], name)


def regroup(text):
    lines = text.split("\n")
    indices = [i for i, line in enumerate(lines) if IMPORT.match(line)]
    if not indices: return text
    first, last = indices[0], indices[-1]
    # Only blank lines may sit between imports; anything else means an unusual file, left alone.
    if any(lines[i].strip() and not IMPORT.match(lines[i]) for i in range(first, last + 1)): return text
    imports = sorted({lines[i] for i in indices}, key=group_key)
    block, previous = [], None
    for line in imports:
        key = group_key(line)[:2]
        if previous is not None and key != previous: block.append("")
        block.append(line); previous = key
    return "\n".join(lines[:first] + block + lines[last + 1:])


def main():
    targets = [Path(arg) for arg in sys.argv[1:]] or [Path("src")]
    files = [f for t in targets for f in (sorted(t.rglob("*.java")) if t.is_dir() else [t])]
    changed = 0
    for file in files:
        text = file.read_text()
        updated = regroup(text)
        if updated != text: file.write_text(updated); changed += 1
    print(f"Regrouped imports in {changed} of {len(files)} files")


if __name__ == "__main__":
    main()
