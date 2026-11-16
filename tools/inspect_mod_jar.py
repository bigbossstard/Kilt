#!/usr/bin/env python3
"""Inspect Forge/NeoForge/Fabric metadata in a mod JAR without executing it."""

from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
import zipfile
from pathlib import Path
from typing import Any


METADATA_PATHS = (
    "META-INF/neoforge.mods.toml",
    "META-INF/mods.toml",
)


def _manifest_attributes(raw: bytes | None) -> dict[str, str]:
    if not raw:
        return {}

    # Manifest continuation lines begin with one space. This reader is intentionally
    # diagnostic-only; it does not attempt to reimplement all JAR manifest rules.
    text = raw.decode("utf-8", errors="replace").replace("\r\n", "\n")
    unfolded: list[str] = []
    for line in text.split("\n"):
        if line.startswith(" ") and unfolded:
            unfolded[-1] += line[1:]
        elif line.strip():
            unfolded.append(line)

    attrs: dict[str, str] = {}
    for line in unfolded:
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        attrs[key.strip()] = value.strip()
    return attrs


def inspect_mod_jar(jar_path: Path) -> dict[str, Any]:
    if not jar_path.is_file():
        raise FileNotFoundError(f"JAR not found: {jar_path}")

    try:
        with zipfile.ZipFile(jar_path) as archive:
            names = set(archive.namelist())
            metadata_path = next((p for p in METADATA_PATHS if p in names), None)
            metadata: dict[str, Any] = {}
            if metadata_path:
                metadata = tomllib.loads(archive.read(metadata_path).decode("utf-8"))

            manifest = _manifest_attributes(
                archive.read("META-INF/MANIFEST.MF")
                if "META-INF/MANIFEST.MF" in names
                else None
            )

            mods = metadata.get("mods", [])
            mod_ids = [
                item.get("modId")
                for item in mods
                if isinstance(item, dict) and isinstance(item.get("modId"), str)
            ]

            dependencies: list[dict[str, Any]] = []
            raw_dependencies = metadata.get("dependencies", {})
            if isinstance(raw_dependencies, dict):
                for owner_mod_id, entries in raw_dependencies.items():
                    if not isinstance(entries, list):
                        continue
                    for entry in entries:
                        if isinstance(entry, dict):
                            dependencies.append(
                                {
                                    "owner": owner_mod_id,
                                    "modId": entry.get("modId"),
                                    "mandatory": entry.get("mandatory"),
                                    "versionRange": entry.get("versionRange"),
                                    "side": entry.get("side"),
                                }
                            )

            namespace_counts = {
                "net/minecraftforge/": 0,
                "net/neoforged/": 0,
                "net/minecraft/": 0,
            }
            for name in names:
                if not name.endswith(".class"):
                    continue
                for prefix in namespace_counts:
                    if name.startswith(prefix):
                        namespace_counts[prefix] += 1

            if "fabric.mod.json" in names:
                loader_family = "fabric-or-hybrid"
            elif metadata_path == "META-INF/mods.toml":
                loader_family = "legacy-forge"
            elif metadata_path == "META-INF/neoforge.mods.toml":
                loader_family = "neoforge"
            else:
                loader_family = "unknown-or-library"

            return {
                "file": str(jar_path),
                "loader_family": loader_family,
                "metadata_path": metadata_path,
                "mod_loader": metadata.get("modLoader"),
                "loader_version": metadata.get("loaderVersion"),
                "minecraft_version_ranges": [
                    dep.get("versionRange")
                    for dep in dependencies
                    if dep.get("modId") == "minecraft"
                ],
                "mod_ids": mod_ids,
                "dependencies": dependencies,
                "manifest": {
                    key: manifest.get(key)
                    for key in (
                        "Implementation-Title",
                        "Implementation-Version",
                        "Specification-Vendor",
                        "FMLModType",
                        "MixinConfigs",
                    )
                    if key in manifest
                },
                "class_namespace_counts": namespace_counts,
                "entry_count": len(names),
            }
    except zipfile.BadZipFile as exc:
        raise ValueError(f"Not a valid ZIP/JAR: {jar_path}") from exc
    except tomllib.TOMLDecodeError as exc:
        raise ValueError(f"Invalid TOML metadata in {jar_path}: {exc}") from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Inspect mod metadata and class namespaces; never executes the JAR."
    )
    parser.add_argument("jars", nargs="+", type=Path, help="One or more local mod JARs")
    parser.add_argument(
        "--require-mod-id",
        action="append",
        default=[],
        help="Fail if any inspected JAR does not declare this mod ID (repeatable)",
    )
    args = parser.parse_args(argv)

    reports: list[dict[str, Any]] = []
    had_error = False
    missing_ids: list[str] = []

    for jar_path in args.jars:
        try:
            report = inspect_mod_jar(jar_path)
            reports.append(report)
            declared = set(report["mod_ids"])
            missing_ids.extend(
                f"{jar_path}: {mod_id}"
                for mod_id in args.require_mod_id
                if mod_id not in declared
            )
        except (OSError, ValueError) as exc:
            print(f"ERROR: {exc}", file=sys.stderr)
            had_error = True

    print(json.dumps(reports, indent=2, sort_keys=True))
    if missing_ids:
        print("Missing required mod IDs:\n  " + "\n  ".join(missing_ids), file=sys.stderr)
        had_error = True
    return 1 if had_error else 0


if __name__ == "__main__":
    raise SystemExit(main())
