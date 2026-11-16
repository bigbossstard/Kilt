#!/usr/bin/env python3
"""Generate and validate legacy Forge -> NeoForge class-name mapping candidates.

This tool reads local JAR archives only. It never loads or executes classes and
never downloads or redistributes Minecraft/mod binaries.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


DEFAULT_PREFIXES = (
    ("net/minecraftforge/forgespi/", "net/neoforged/neoforgespi/"),
    ("net/minecraftforge/eventbus/", "net/neoforged/bus/"),
    ("net/minecraftforge/fml/", "net/neoforged/fml/"),
    ("net/minecraftforge/", "net/neoforged/neoforge/"),
)


@dataclass(frozen=True)
class MappingCandidate:
    source: str
    target: str
    rule: str
    target_exists: bool
    collision: bool = False

    @property
    def status(self) -> str:
        if self.collision:
            return "collision"
        return "verified" if self.target_exists else "missing"


def _normalize_multi_release_entry(entry: str) -> str:
    """Convert META-INF/versions/N/pkg/Type.class to pkg/Type.class."""
    if not entry.startswith("META-INF/versions/"):
        return entry
    parts = entry.split("/", 3)
    if len(parts) == 4 and parts[2].isdigit():
        return parts[3]
    return entry


def read_jar_classes(jar_path: Path) -> set[str]:
    """Return internal class names defined by a JAR, without the .class suffix."""
    if not jar_path.is_file():
        raise FileNotFoundError(f"JAR not found: {jar_path}")

    try:
        with zipfile.ZipFile(jar_path) as archive:
            names: set[str] = set()
            for entry in archive.namelist():
                if not entry.endswith(".class"):
                    continue
                normalized = _normalize_multi_release_entry(entry)
                if normalized.endswith(".class"):
                    names.add(normalized[:-6])
            return names
    except zipfile.BadZipFile as exc:
        raise ValueError(f"Not a valid JAR: {jar_path}") from exc


def parse_prefix_rule(value: str) -> tuple[str, str]:
    if "=" not in value:
        raise argparse.ArgumentTypeError(
            "Prefix rules must use OLD_INTERNAL_PREFIX=NEW_INTERNAL_PREFIX"
        )
    old, new = value.split("=", 1)
    old = old.strip().replace(".", "/")
    new = new.strip().replace(".", "/")
    if not old or not new:
        raise argparse.ArgumentTypeError("Both mapping prefixes must be non-empty")
    if not old.endswith("/") or not new.endswith("/"):
        raise argparse.ArgumentTypeError("Both mapping prefixes must end with '/'")
    return old, new


def load_exact_overrides(path: Path | None) -> dict[str, str]:
    """Read a headerless TSV with source internal class and target internal class."""
    if path is None:
        return {}
    if not path.is_file():
        raise FileNotFoundError(f"Override TSV not found: {path}")

    result: dict[str, str] = {}
    for line_number, raw_line in enumerate(
        path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 2 or not all(field.strip() for field in fields):
            raise ValueError(
                f"{path}:{line_number}: expected exactly two non-empty TSV fields"
            )
        source, target = (field.strip().replace(".", "/") for field in fields)
        if source in result and result[source] != target:
            raise ValueError(
                f"{path}:{line_number}: conflicting overrides for {source}"
            )
        result[source] = target
    return result


def map_class_name(
    source: str,
    prefixes: Iterable[tuple[str, str]],
    exact_overrides: dict[str, str],
) -> tuple[str, str] | None:
    if source in exact_overrides:
        return exact_overrides[source], "exact"

    for old_prefix, new_prefix in prefixes:
        if source.startswith(old_prefix):
            return new_prefix + source[len(old_prefix):], (
                f"prefix:{old_prefix}={new_prefix}"
            )
    return None


def generate_candidates(
    source_classes: set[str],
    target_classes: set[str],
    prefixes: Iterable[tuple[str, str]],
    exact_overrides: dict[str, str] | None = None,
) -> list[MappingCandidate]:
    overrides = exact_overrides or {}
    sorted_prefixes = sorted(prefixes, key=lambda item: len(item[0]), reverse=True)

    provisional: list[tuple[str, str, str, bool]] = []
    for source in sorted(source_classes):
        mapped = map_class_name(source, sorted_prefixes, overrides)
        if mapped is None:
            continue
        target, rule = mapped
        provisional.append((source, target, rule, target in target_classes))

    counts = Counter(target for _, target, _, _ in provisional)
    return [
        MappingCandidate(
            source=source,
            target=target,
            rule=rule,
            target_exists=target_exists,
            collision=counts[target] > 1,
        )
        for source, target, rule, target_exists in provisional
    ]


def render_report(
    source_jar: Path,
    target_jars: list[Path],
    prefixes: list[tuple[str, str]],
    candidates: list[MappingCandidate],
    output_format: str,
) -> str:
    counts = Counter(candidate.status for candidate in candidates)
    if output_format == "tsv":
        lines = [
            "source\ttarget\tstatus\ttarget_exists\trule"
        ]
        lines.extend(
            "\t".join(
                (
                    candidate.source,
                    candidate.target,
                    candidate.status,
                    str(candidate.target_exists).lower(),
                    candidate.rule,
                )
            )
            for candidate in candidates
        )
        return "\n".join(lines) + "\n"

    report = {
        "source_jar": str(source_jar),
        "target_jars": [str(path) for path in target_jars],
        "prefix_rules": [
            {"source_prefix": old, "target_prefix": new}
            for old, new in prefixes
        ],
        "summary": {
            "candidate_count": len(candidates),
            "verified_count": counts["verified"],
            "missing_count": counts["missing"],
            "collision_count": counts["collision"],
        },
        "candidates": [
            {**asdict(candidate), "status": candidate.status}
            for candidate in candidates
        ],
    }
    return json.dumps(report, indent=2, sort_keys=True) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Build a candidate legacy Forge -> NeoForge class map from local API "
            "JARs and validate every target class against the supplied runtime JARs."
        )
    )
    parser.add_argument("--source-jar", required=True, type=Path)
    parser.add_argument(
        "--target-jar",
        required=True,
        type=Path,
        action="append",
        help="Target runtime JAR; may be repeated to supply a classpath.",
    )
    parser.add_argument(
        "--prefix",
        type=parse_prefix_rule,
        action="append",
        default=[],
        help="Additional OLD_PREFIX=NEW_PREFIX rule; may be repeated.",
    )
    parser.add_argument(
        "--no-default-prefixes",
        action="store_true",
        help="Use only explicit --prefix rules.",
    )
    parser.add_argument(
        "--overrides",
        type=Path,
        help="Optional headerless TSV of exact source<TAB>target class mappings.",
    )
    parser.add_argument(
        "--format",
        choices=("json", "tsv"),
        default="json",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Write the report here instead of stdout.",
    )
    parser.add_argument(
        "--fail-on-missing",
        action="store_true",
        help="Return exit code 2 if any mapped target class is missing or colliding.",
    )
    args = parser.parse_args(argv)

    try:
        source_classes = read_jar_classes(args.source_jar)
        target_classes: set[str] = set()
        for target_jar in args.target_jar:
            target_classes.update(read_jar_classes(target_jar))

        prefixes = [] if args.no_default_prefixes else list(DEFAULT_PREFIXES)
        prefixes.extend(args.prefix)
        prefixes.sort(key=lambda item: len(item[0]), reverse=True)
        overrides = load_exact_overrides(args.overrides)

        candidates = generate_candidates(
            source_classes,
            target_classes,
            prefixes,
            overrides,
        )
        report = render_report(
            args.source_jar,
            args.target_jar,
            prefixes,
            candidates,
            args.format,
        )

        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(report, encoding="utf-8")
        else:
            sys.stdout.write(report)

        has_unresolved = any(
            candidate.status in ("missing", "collision")
            for candidate in candidates
        )
        if args.fail_on_missing and has_unresolved:
            return 2
        return 0
    except (OSError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
