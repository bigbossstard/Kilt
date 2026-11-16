#!/usr/bin/env python3
"""Inspect class-file references and loader metadata in a mod JAR without executing it."""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import tomllib
import zipfile
from collections import Counter
from pathlib import Path
from typing import Any


DESCRIPTOR_CLASS = re.compile(r"L([A-Za-z0-9_$/]+)(?=[;<])")
BINARY_NAME = re.compile(r"(?:[A-Za-z_$][\w$]*[./]){2,}[A-Za-z_$][\w$]*")


class ClassFileError(ValueError):
    """Raised when a class file is truncated or malformed."""


class Reader:
    def __init__(self, data: bytes) -> None:
        self.data = data
        self.offset = 0

    def take(self, size: int) -> bytes:
        end = self.offset + size
        if end > len(self.data):
            raise ClassFileError(
                f"Unexpected end of class file at offset {self.offset} "
                f"(needed {size} bytes)"
            )
        value = self.data[self.offset:end]
        self.offset = end
        return value

    def u1(self) -> int:
        return self.take(1)[0]

    def u2(self) -> int:
        return struct.unpack(">H", self.take(2))[0]

    def u4(self) -> int:
        return struct.unpack(">I", self.take(4))[0]

    def u8(self) -> int:
        return struct.unpack(">Q", self.take(8))[0]


def _decode_modified_utf8(raw: bytes) -> str:
    # JVM modified UTF-8 encodes NUL differently from standard UTF-8. For this
    # diagnostic report, replacement decoding is sufficient and non-executable.
    return raw.decode("utf-8", errors="replace")


def parse_class_references(data: bytes) -> dict[str, Any]:
    """Read the constant pool and class header, without loading the class."""
    reader = Reader(data)
    if reader.u4() != 0xCAFEBABE:
        raise ClassFileError("Invalid class-file magic")

    minor_version = reader.u2()
    major_version = reader.u2()
    cp_count = reader.u2()

    utf8: dict[int, str] = {}
    class_constants: dict[int, int] = {}
    string_constants: list[int] = []
    descriptor_constants: list[int] = []

    index = 1
    while index < cp_count:
        tag = reader.u1()
        if tag == 1:  # CONSTANT_Utf8
            length = reader.u2()
            utf8[index] = _decode_modified_utf8(reader.take(length))
        elif tag == 7:  # CONSTANT_Class
            class_constants[index] = reader.u2()
        elif tag == 8:  # CONSTANT_String
            string_constants.append(reader.u2())
        elif tag in (9, 10, 11):  # Field/Method/InterfaceMethod ref
            reader.u2()
            reader.u2()
        elif tag == 12:  # CONSTANT_NameAndType
            reader.u2()  # name_index
            descriptor_constants.append(reader.u2())
        elif tag in (3, 4):  # Integer / Float
            reader.u4()
        elif tag in (5, 6):  # Long / Double occupy two CP slots
            reader.u8()
            index += 1
        elif tag == 15:  # MethodHandle
            reader.u1()
            reader.u2()
        elif tag == 16:  # MethodType
            descriptor_constants.append(reader.u2())
        elif tag in (17, 18):  # Dynamic / InvokeDynamic
            reader.u2()
            reader.u2()
        elif tag in (19, 20):  # Module / Package
            reader.u2()
        else:
            raise ClassFileError(
                f"Unsupported constant-pool tag {tag} at index {index}"
            )
        index += 1

    def class_name(class_index: int) -> str | None:
        name_index = class_constants.get(class_index)
        return utf8.get(name_index) if name_index is not None else None

    access_flags = reader.u2()
    this_class_index = reader.u2()
    super_class_index = reader.u2()
    interface_count = reader.u2()
    interface_indices = [reader.u2() for _ in range(interface_count)]

    class_names = sorted(
        {
            name
            for name_index in class_constants.values()
            if (name := utf8.get(name_index)) is not None
        }
    )

    descriptor_texts = [
        utf8[index]
        for index in descriptor_constants
        if index in utf8
    ]
    # Signatures are Utf8 constants too, so scan all Utf8 values for object
    # references in descriptors and generic signatures.
    descriptor_texts.extend(utf8.values())
    descriptor_refs = sorted(
        {
            match
            for text in descriptor_texts
            for match in DESCRIPTOR_CLASS.findall(text)
        }
    )

    string_values = sorted(
        {
            utf8[index]
            for index in string_constants
            if index in utf8
        }
    )
    reflective_candidates = sorted(
        {
            candidate.replace(".", "/")
            for value in string_values
            for candidate in BINARY_NAME.findall(value)
        }
    )

    return {
        "class_name": class_name(this_class_index),
        "super_class": class_name(super_class_index),
        "interfaces": sorted(
            name
            for index in interface_indices
            if (name := class_name(index)) is not None
        ),
        "access_flags": access_flags,
        "major_version": major_version,
        "minor_version": minor_version,
        "class_references": class_names,
        "descriptor_references": descriptor_refs,
        "string_constants": string_values,
        "reflective_name_candidates": reflective_candidates,
    }


def _read_manifest(raw: bytes | None) -> dict[str, str]:
    if not raw:
        return {}
    text = raw.decode("utf-8", errors="replace").replace("\r\n", "\n")
    lines: list[str] = []
    for line in text.split("\n"):
        if line.startswith(" ") and lines:
            lines[-1] += line[1:]
        elif line.strip():
            lines.append(line)
    attributes: dict[str, str] = {}
    for line in lines:
        if ":" in line:
            key, value = line.split(":", 1)
            attributes[key.strip()] = value.strip()
    return attributes


def inspect_mod_jar(path: Path, include_all_strings: bool = False) -> dict[str, Any]:
    if not path.is_file():
        raise FileNotFoundError(f"JAR not found: {path}")

    class_refs: set[str] = set()
    descriptor_refs: set[str] = set()
    reflection_candidates: set[str] = set()
    class_errors: list[dict[str, str]] = []
    defined_classes = 0
    major_versions: Counter[int] = Counter()
    package_counts: Counter[str] = Counter()
    class_string_sample: list[str] = []
    service_providers: dict[str, list[str]] = {}
    mixin_configs: list[dict[str, Any]] = []
    metadata: dict[str, Any] = {}
    metadata_path: str | None = None
    manifest: dict[str, str] = {}

    try:
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            metadata_path = next(
                (
                    candidate
                    for candidate in (
                        "META-INF/neoforge.mods.toml",
                        "META-INF/mods.toml",
                    )
                    if candidate in names
                ),
                None,
            )
            if metadata_path:
                metadata = tomllib.loads(
                    archive.read(metadata_path).decode("utf-8")
                )

            manifest = _read_manifest(
                archive.read("META-INF/MANIFEST.MF")
                if "META-INF/MANIFEST.MF" in names
                else None
            )

            for entry_name in sorted(names):
                if entry_name.endswith(".class"):
                    defined_classes += 1
                    package = entry_name.rpartition("/")[0]
                    root = package.split("/", 2)[0:2]
                    package_counts["/".join(root) if root else "(default)"] += 1
                    try:
                        report = parse_class_references(archive.read(entry_name))
                        major_versions[report["major_version"]] += 1
                        class_refs.update(report["class_references"])
                        descriptor_refs.update(report["descriptor_references"])
                        reflection_candidates.update(
                            report["reflective_name_candidates"]
                        )
                        if include_all_strings:
                            class_string_sample.extend(
                                f"{entry_name}: {value}"
                                for value in report["string_constants"]
                            )
                    except (ClassFileError, OSError, KeyError) as exc:
                        class_errors.append(
                            {"entry": entry_name, "error": str(exc)}
                        )
                elif entry_name.startswith("META-INF/services/"):
                    service_name = entry_name.removeprefix("META-INF/services/")
                    providers = [
                        line.partition("#")[0].strip()
                        for line in archive.read(entry_name).decode(
                            "utf-8", errors="replace"
                        ).splitlines()
                    ]
                    service_providers[service_name] = [
                        provider for provider in providers if provider
                    ]
                elif entry_name.lower().endswith(
                    (".mixins.json", ".mixin.json")
                ):
                    try:
                        config = json.loads(
                            archive.read(entry_name).decode("utf-8")
                        )
                        mixin_configs.append(
                            {
                                "path": entry_name,
                                "package": config.get("package"),
                                "plugin": config.get("plugin"),
                                "mixins": config.get("mixins", []),
                                "client": config.get("client", []),
                                "server": config.get("server", []),
                                "refmap": config.get("refmap"),
                            }
                        )
                    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                        mixin_configs.append(
                            {"path": entry_name, "error": str(exc)}
                        )
    except zipfile.BadZipFile as exc:
        raise ValueError(f"Not a valid ZIP/JAR: {path}") from exc
    except tomllib.TOMLDecodeError as exc:
        raise ValueError(f"Invalid TOML metadata in {path}: {exc}") from exc

    all_references = class_refs | descriptor_refs
    legacy_forge = sorted(
        value for value in all_references
        if value.startswith("net/minecraftforge/")
    )
    modern_neo = sorted(
        value for value in all_references
        if value.startswith("net/neoforged/")
    )
    minecraft = sorted(
        value for value in all_references
        if value.startswith("net/minecraft/")
    )

    mods = metadata.get("mods", [])
    dependencies = metadata.get("dependencies", {})
    mod_ids = [
        item.get("modId")
        for item in mods
        if isinstance(item, dict) and isinstance(item.get("modId"), str)
    ]

    dependency_summary: list[dict[str, Any]] = []
    if isinstance(dependencies, dict):
        for owner, tables in dependencies.items():
            if not isinstance(tables, list):
                continue
            for table in tables:
                if isinstance(table, dict):
                    dependency_summary.append(
                        {
                            "owner": owner,
                            "modId": table.get("modId"),
                            "mandatory": table.get("mandatory"),
                            "type": table.get("type"),
                            "versionRange": table.get("versionRange"),
                            "side": table.get("side"),
                        }
                    )

    return {
        "file": str(path),
        "loader_family": (
            "fabric-or-hybrid"
            if "fabric.mod.json" in names
            else "legacy-forge"
            if metadata_path == "META-INF/mods.toml"
            else "neoforge"
            if metadata_path == "META-INF/neoforge.mods.toml"
            else "unknown-or-library"
        ),
        "metadata_path": metadata_path,
        "mod_loader": metadata.get("modLoader"),
        "loader_version": metadata.get("loaderVersion"),
        "mod_ids": mod_ids,
        "dependencies": dependency_summary,
        "class_count": defined_classes,
        "class_major_versions": dict(sorted(major_versions.items())),
        "defined_package_roots": dict(sorted(package_counts.items())),
        "referenced_legacy_forge_classes": legacy_forge,
        "referenced_neoforge_classes": modern_neo,
        "referenced_minecraft_classes": minecraft,
        "reflective_name_candidates": sorted(reflection_candidates),
        "service_providers": service_providers,
        "mixin_configs": mixin_configs,
        "manifest": {
            key: manifest[key]
            for key in (
                "Implementation-Title",
                "Implementation-Version",
                "Specification-Vendor",
                "FMLModType",
                "MixinConfigs",
            )
            if key in manifest
        },
        "class_parse_errors": class_errors,
        "string_constant_sample": sorted(class_string_sample),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Inspect metadata and bytecode references in mod JARs. "
            "This tool never loads or executes the JAR."
        )
    )
    parser.add_argument("jars", nargs="+", type=Path)
    parser.add_argument(
        "--include-strings",
        action="store_true",
        help="Include every class string constant in the output (can be very large).",
    )
    args = parser.parse_args(argv)

    reports: list[dict[str, Any]] = []
    failed = False
    for jar_path in args.jars:
        try:
            reports.append(inspect_mod_jar(jar_path, args.include_strings))
        except (OSError, ValueError) as exc:
            print(f"ERROR: {exc}", file=sys.stderr)
            failed = True

    print(json.dumps(reports, indent=2, sort_keys=True))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
