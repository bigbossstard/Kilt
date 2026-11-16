from __future__ import annotations

import tempfile
import zipfile
import unittest
from pathlib import Path

from tools.generate_class_mapping_candidates import (
    DEFAULT_PREFIXES,
    generate_candidates,
    load_exact_overrides,
    read_jar_classes,
)


class ReadJarClassesTests(unittest.TestCase):
    def test_reads_regular_and_multi_release_class_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            jar_path = Path(temp_dir) / "api.jar"
            with zipfile.ZipFile(jar_path, "w") as archive:
                archive.writestr(
                    "net/minecraftforge/common/Example.class",
                    b"not executed",
                )
                archive.writestr(
                    "META-INF/versions/17/net/minecraftforge/common/Versioned.class",
                    b"not executed",
                )
                archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0")

            self.assertEqual(
                read_jar_classes(jar_path),
                {
                    "net/minecraftforge/common/Example",
                    "net/minecraftforge/common/Versioned",
                },
            )

    def test_rejects_invalid_jar(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "bad.jar"
            path.write_bytes(b"not a zip")
            with self.assertRaises(ValueError):
                read_jar_classes(path)


class MappingCandidateTests(unittest.TestCase):
    def test_longest_prefix_rule_wins_and_checks_target_presence(self) -> None:
        source = {
            "net/minecraftforge/common/Example",
            "net/minecraftforge/fml/ModLoader",
            "net/minecraftforge/forgespi/IModInfo",
        }
        target = {
            "net/neoforged/neoforge/common/Example",
            "net/neoforged/fml/ModLoader",
        }

        candidates = generate_candidates(source, target, DEFAULT_PREFIXES)
        by_source = {candidate.source: candidate for candidate in candidates}

        self.assertEqual(
            by_source["net/minecraftforge/common/Example"].target,
            "net/neoforged/neoforge/common/Example",
        )
        self.assertEqual(
            by_source["net/minecraftforge/fml/ModLoader"].target,
            "net/neoforged/fml/ModLoader",
        )
        self.assertEqual(
            by_source["net/minecraftforge/forgespi/IModInfo"].status,
            "missing",
        )
        self.assertTrue(
            by_source["net/minecraftforge/common/Example"].target_exists
        )

    def test_exact_override_takes_precedence_over_prefix(self) -> None:
        candidates = generate_candidates(
            {"net/minecraftforge/common/OldClass"},
            {"com/example/NewClass"},
            DEFAULT_PREFIXES,
            {
                "net/minecraftforge/common/OldClass": "com/example/NewClass"
            },
        )

        self.assertEqual(len(candidates), 1)
        self.assertEqual(candidates[0].target, "com/example/NewClass")
        self.assertEqual(candidates[0].rule, "exact")
        self.assertEqual(candidates[0].status, "verified")

    def test_collision_is_reported_instead_of_silently_accepting_mapping(self) -> None:
        candidates = generate_candidates(
            {
                "source/One",
                "source/Two",
            },
            {"target/Shared"},
            [("source/", "target/")],
        )

        self.assertEqual(len(candidates), 2)
        self.assertTrue(all(item.status == "collision" for item in candidates))
        self.assertTrue(all(item.target_exists for item in candidates))

    def test_unmapped_classes_are_not_emitted(self) -> None:
        candidates = generate_candidates(
            {"com/example/Independent"},
            {"com/example/Independent"},
            DEFAULT_PREFIXES,
        )
        self.assertEqual(candidates, [])

    def test_loads_headerless_exact_override_tsv(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "overrides.tsv"
            path.write_text(
                "# old<TAB>new\n"
                "net/minecraftforge/Old\tnet/neoforged/neoforge/New\n",
                encoding="utf-8",
            )
            self.assertEqual(
                load_exact_overrides(path),
                {
                    "net/minecraftforge/Old": "net/neoforged/neoforge/New"
                },
            )


if __name__ == "__main__":
    unittest.main()
