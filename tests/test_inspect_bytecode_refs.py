from __future__ import annotations

import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.inspect_bytecode_refs import (
    ClassFileError,
    inspect_mod_jar,
    parse_class_references,
)


def _utf8(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return b"\x01" + struct.pack(">H", len(encoded)) + encoded


def _class(name_index: int) -> bytes:
    return b"\x07" + struct.pack(">H", name_index)


def _string(string_index: int) -> bytes:
    return b"\x08" + struct.pack(">H", string_index)


def _minimal_class() -> bytes:
    # Constant-pool layout:
    #  1 Utf8 sample/Test
    #  2 Class #1
    #  3 Utf8 java/lang/Object
    #  4 Class #3
    #  5 Utf8 ()Lnet/minecraftforge/common/Example;
    #  6 Utf8 net/minecraftforge/common/Example
    #  7 Class #6
    #  8 Utf8 Lnet/neoforged/neoforge/Example;
    #  9 Utf8 com.example.ReflectionTarget
    # 10 String #9
    constant_pool = b"".join(
        [
            _utf8("sample/Test"),
            _class(1),
            _utf8("java/lang/Object"),
            _class(3),
            _utf8("()Lnet/minecraftforge/common/Example;"),
            _utf8("net/minecraftforge/common/Example"),
            _class(6),
            _utf8("Lnet/neoforged/neoforge/Example;"),
            _utf8("com.example.ReflectionTarget"),
            _string(9),
        ]
    )
    cp_count = 11
    class_header = struct.pack(
        ">HHHHH",
        0x0021,  # access_flags
        2,       # this_class
        4,       # super_class
        0,       # interfaces_count
        0,       # fields_count (not read by the inspector)
    )
    return (
        struct.pack(">IHHH", 0xCAFEBABE, 0, 61, cp_count)
        + constant_pool
        + class_header
    )


class ClassFileReferenceTests(unittest.TestCase):
    def test_reads_class_header_and_namespace_references(self) -> None:
        report = parse_class_references(_minimal_class())

        self.assertEqual(report["class_name"], "sample/Test")
        self.assertEqual(report["super_class"], "java/lang/Object")
        self.assertIn(
            "net/minecraftforge/common/Example",
            report["class_references"],
        )
        self.assertIn(
            "net/minecraftforge/common/Example",
            report["descriptor_references"],
        )
        self.assertIn(
            "net/neoforged/neoforge/Example",
            report["descriptor_references"],
        )
        self.assertIn(
            "com/example/ReflectionTarget",
            report["reflective_name_candidates"],
        )
        self.assertEqual(report["major_version"], 61)

    def test_rejects_invalid_class_file(self) -> None:
        with self.assertRaises(ClassFileError):
            parse_class_references(b"not a class file")


class ModJarInspectionTests(unittest.TestCase):
    def test_reports_metadata_bytecode_services_and_mixins(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            jar_path = Path(temp_dir) / "legacy-forge-test.jar"
            metadata = """
                modLoader = "javafml"
                loaderVersion = "[47,)"

                [[mods]]
                modId = "legacy_sample"
                version = "1.0.0"

                [[dependencies.legacy_sample]]
                modId = "forge"
                mandatory = true
                versionRange = "[47.1.3,)"
            """.strip()
            mixins = """
                {
                  "package": "example.mixin",
                  "mixins": ["CommonMixin"],
                  "client": ["ClientMixin"],
                  "server": [],
                  "refmap": "sample.refmap.json"
                }
            """.strip()

            with zipfile.ZipFile(jar_path, "w") as archive:
                archive.writestr("META-INF/mods.toml", metadata)
                archive.writestr("sample/Test.class", _minimal_class())
                archive.writestr(
                    "META-INF/services/example.Service",
                    "example.Provider\n# comment\n",
                )
                archive.writestr("sample.mixins.json", mixins)
                archive.writestr(
                    "META-INF/MANIFEST.MF",
                    "Manifest-Version: 1.0\r\n"
                    "Implementation-Version: 1.2.3\r\n"
                    "MixinConfigs: sample.mixins.json\r\n\r\n",
                )

            report = inspect_mod_jar(jar_path)

            self.assertEqual(report["loader_family"], "legacy-forge")
            self.assertEqual(report["metadata_path"], "META-INF/mods.toml")
            self.assertEqual(report["mod_loader"], "javafml")
            self.assertEqual(report["mod_ids"], ["legacy_sample"])
            self.assertEqual(report["class_count"], 1)
            self.assertIn(
                "net/minecraftforge/common/Example",
                report["referenced_legacy_forge_classes"],
            )
            self.assertIn(
                "net/neoforged/neoforge/Example",
                report["referenced_neoforge_classes"],
            )
            self.assertEqual(
                report["service_providers"]["example.Service"],
                ["example.Provider"],
            )
            self.assertEqual(
                report["mixin_configs"][0]["mixins"],
                ["CommonMixin"],
            )
            self.assertEqual(
                report["manifest"]["Implementation-Version"],
                "1.2.3",
            )
            self.assertEqual(report["class_parse_errors"], [])


if __name__ == "__main__":
    unittest.main()
