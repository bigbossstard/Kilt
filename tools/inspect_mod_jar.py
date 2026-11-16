#!/usr/bin/env python3
"""Inspect Forge/NeoForge/Fabric metadata and API references in a mod JAR."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tomllib
import zipfile
from pathlib import Path
from typing import Any


METADATA_PATHS = (
    "META-INF/neoforge.mods.toml",
    "META-INF/mods.toml",
)

NAMESPACE_PREFIXES = (
    "net/minecraftforge/",
    "net/neoforged/",
    "net/minecraft/",
)


def _manifest_attributes(raw: bytes | None) -> dict[str, str]:
    if not raw:
        return {}

    # Manifest continuation lines begin with one space. This reader is diagnostic
    # only; it does not attempt to reimplement every JAR manifest edge case.
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


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


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
                                    "type": entry.get("type"),
                                    "mandatory": entry.get("mandatory"),
                                    "versionRange": entry.get("versionRange"),
                                    "ordering": entry.get("ordering"),
                                    "side": entry.get("side"),
                                }
                            )

            class_entries = [name for name in names if name.endswith(".class")]
            defined_class_counts = {prefix: 0 for prefix in NAMESPACE_PREFIXES}
            class_reference_counts = {prefix: 0 for prefix in NAMESPACE_PREFIXES}

            for name in class_entries:
                for prefix in NAMESPACE_PREFIXES:
                    if name.startswith(prefix):
                        defined_class_counts[prefix] += 1

                # Package references are stored as UTF-8 strings in Java class
                # constant pools. This byte scan is a fast triage signal, not a
                # replacement for ASM or a complete Java bytecode parser.
                class_bytes = archive.read(name)
                for prefix in NAMESPACE_PREFIXES:
                    class_reference_counts[prefix] += class_bytes.count(prefix.encode("ascii"))

            if "fabric.mod.json" in names:
                loader_family = "fabric-or-hybrid"
            elif metadata_path == "META-INF/mods.toml":
                loader_family = "legacy-forge"
            elif metadata_path == "META-INF/neoforge.mods.toml":
                loader_family = "neoforge"
            else:
                loader_family = "unknown-or-library"

            mixin_configs = set()
            manifest_mixins = manifest.get("MixinConfigs", "")
            mixin_configs.update(item.strip() for item in manifest_mixins.split(",") if item.strip())
            mixin_configs.update(
                name for name in names
                if name.endswith(".mixins.json") or name.endswith(".mixin.json")
            )

            features = {
                "mixin_configs": sorted(mixin_configs),
                "mixin_refmaps": sorted(name for name in names if name.endswith(".refmap.json")),
                "access_transformers": sorted(
                    name for name in names
                    if name.endswith("accesstransformer.cfg") or name.endswith("access_transformer.cfg")
                ),
                "coremod_descriptors": sorted(
                    name for name in names
                    if name.endswith("coremods.json") or name.endswith("coremod.json")
                ),
                "service_descriptors": sorted(
                    name for name in names if name.startswith("META-INF/services/")
                ),
                "has_manifest": "META-INF/MANIFEST.MF" in names,
                "has_fabric_metadata": "fabric.mod.json" in names,
            }

            return {
                "file": str(jar_path),
                "sha256": _sha256(jar_path.read_bytes()),
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
                "defined_class_counts": defined_class_counts,
                "class_reference_counts": class_reference_counts,
                "features": features,
                "class_file_count": len(class_entries),
                "entry_count": len(names),
            }
    except zipfile.BadZipFile as exc:
        raise ValueError(f"Not a valid ZIP/JAR: {jar_path}") from exc
    except tomllib.TOMLDecodeError as exc:
        raise ValueError(f"Invalid TOML metadata in {jar_path}: {exc}") from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Inspect mod metadata, API references and loader features; never executes the JAR."
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
