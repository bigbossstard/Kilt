from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
INSPECTOR = REPO_ROOT / "tools" / "inspect_mod_jar.py"


def make_jar(
    path: Path,
    metadata_path: str,
    metadata: str,
    class_entries: tuple[str, ...],
    class_bytes: bytes = b"\xca\xfe\xba\xbe",
) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(metadata_path, metadata)
        archive.writestr(
            "META-INF/MANIFEST.MF",
            "Manifest-Version: 1.0\r\nImplementation-Title: Test Mod\r\n"
            "Implementation-Version: 1.2.3\r\nMixinConfigs: example.mixins.json\r\n\r\n",
        )
        archive.writestr("example.mixins.json", '{"required": true}')
        archive.writestr("example.refmap.json", '{"mappings": {}}')
        archive.writestr("META-INF/accesstransformer.cfg", "public example.Test")
        for entry in class_entries:
            archive.writestr(entry, class_bytes)


class ModJarInspectorTests(unittest.TestCase):
    def test_legacy_forge_metadata_and_api_references_are_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            jar = Path(temp_dir) / "legacy-forge.jar"
            make_jar(
                jar,
                "META-INF/mods.toml",
                """
modLoader = "javafml"
loaderVersion = "[47,)"
license = "Test"
[[mods]]
modId = "sample_legacy"
version = "1.0"
[[dependencies.sample_legacy]]
modId = "forge"
mandatory = true
versionRange = "[47,)"
ordering = "NONE"
side = "BOTH"
""",
                ("cofh/example/LegacyMachine.class",),
                b"\xca\xfe\xba\xbeLnet/minecraftforge/eventbus/api/IEventBus;Lnet/minecraft/world/level/Level;",
            )

            result = subprocess.run(
                [sys.executable, str(INSPECTOR), str(jar)],
                check=True,
                capture_output=True,
                text=True,
            )
            report = json.loads(result.stdout)[0]
            self.assertEqual(report["loader_family"], "legacy-forge")
            self.assertEqual(report["mod_ids"], ["sample_legacy"])
            self.assertEqual(report["dependencies"][0]["modId"], "forge")
            self.assertEqual(report["defined_class_counts"]["net/minecraftforge/"], 0)
            self.assertEqual(report["class_reference_counts"]["net/minecraftforge/"], 1)
            self.assertEqual(report["class_reference_counts"]["net/minecraft/"], 1)
            self.assertEqual(report["features"]["mixin_configs"], ["example.mixins.json"])
            self.assertEqual(report["features"]["mixin_refmaps"], ["example.refmap.json"])
            self.assertEqual(len(report["sha256"]), 64)

    def test_neoforge_metadata_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            jar = Path(temp_dir) / "modern-neo.jar"
            make_jar(
                jar,
                "META-INF/neoforge.mods.toml",
                """
modLoader = "javafml"
loaderVersion = "[1,)"
license = "Test"
[[mods]]
modId = "sample_neo"
version = "1.0"
[[dependencies.sample_neo]]
modId = "neoforge"
type = "required"
versionRange = "[1,)"
ordering = "NONE"
side = "BOTH"
""",
                ("sample/neo/Example.class",),
                b"\xca\xfe\xba\xbeLnet/neoforged/neoforge/registries/DeferredRegister;",
            )

            result = subprocess.run(
                [sys.executable, str(INSPECTOR), str(jar)],
                check=True,
                capture_output=True,
                text=True,
            )
            report = json.loads(result.stdout)[0]
            self.assertEqual(report["loader_family"], "neoforge")
            self.assertEqual(report["mod_ids"], ["sample_neo"])
            self.assertEqual(report["class_reference_counts"]["net/neoforged/"], 1)

    def test_required_mod_id_check_fails_cleanly(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            jar = Path(temp_dir) / "empty.jar"
            make_jar(
                jar,
                "META-INF/neoforge.mods.toml",
                """
modLoader = "javafml"
loaderVersion = "[1,)"
license = "Test"
[[mods]]
modId = "present"
version = "1.0"
""",
                (),
            )

            result = subprocess.run(
                [sys.executable, str(INSPECTOR), "--require-mod-id", "thermal_expansion", str(jar)],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 1)
            self.assertIn("thermal_expansion", result.stderr)


if __name__ == "__main__":
    unittest.main()
