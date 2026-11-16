#!/usr/bin/env python3
"""Download official Minecraft client/server JARs and mappings for local analysis."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
ARTIFACTS = {
    "client": ".jar",
    "server": ".jar",
    "client_mappings": ".txt",
    "server_mappings": ".txt",
}


def fetch_json(url: str) -> dict[str, Any]:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Kilt-Bridge-Minecraft-Research/1.0"},
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        return json.loads(response.read().decode("utf-8"))


def sha1_file(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def download_verified(url: str, target: Path, expected_sha1: str, expected_size: int | None) -> dict[str, Any]:
    target.parent.mkdir(parents=True, exist_ok=True)

    if target.is_file():
        existing_sha1 = sha1_file(target)
        if existing_sha1 == expected_sha1 and (expected_size is None or target.stat().st_size == expected_size):
            return {
                "path": str(target),
                "sha1": existing_sha1,
                "size": target.stat().st_size,
                "status": "cached",
            }

    temporary = target.with_suffix(target.suffix + ".part")
    temporary.unlink(missing_ok=True)
    digest = hashlib.sha1()
    total = 0

    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Kilt-Bridge-Minecraft-Research/1.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response, temporary.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
                digest.update(chunk)
                total += len(chunk)

        actual_sha1 = digest.hexdigest()
        if actual_sha1.lower() != expected_sha1.lower():
            raise ValueError(
                f"SHA-1 mismatch for {target.name}: expected {expected_sha1}, got {actual_sha1}"
            )
        if expected_size is not None and total != expected_size:
            raise ValueError(
                f"Size mismatch for {target.name}: expected {expected_size}, got {total}"
            )

        temporary.replace(target)
        return {
            "path": str(target),
            "sha1": actual_sha1,
            "size": total,
            "status": "downloaded",
        }
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


def resolve_version(version: str) -> dict[str, Any]:
    manifest = fetch_json(VERSION_MANIFEST_URL)
    entry = next(
        (item for item in manifest.get("versions", []) if item.get("id") == version),
        None,
    )
    if entry is None:
        raise ValueError(f"Minecraft version {version!r} was not found in the official version manifest")
    version_json = fetch_json(entry["url"])
    return {"manifest_entry": entry, "version_json": version_json}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Fetch official Minecraft jars/mappings to an ignored build/ directory for research."
    )
    parser.add_argument("--version", default="26.3", help="Minecraft Java version (default: 26.3)")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/analysis/minecraft"),
        help="Output directory (default: build/analysis/minecraft; ignored by Git)",
    )
    parser.add_argument(
        "--include",
        nargs="+",
        choices=sorted(ARTIFACTS),
        default=["client", "server", "client_mappings", "server_mappings"],
        help="Artifacts to download",
    )
    args = parser.parse_args(argv)

    try:
        resolved = resolve_version(args.version)
        version_json = resolved["version_json"]
        downloads = version_json.get("downloads", {})
        version_dir = args.output / args.version
        report: dict[str, Any] = {
            "minecraft_version": args.version,
            "version_json_url": resolved["manifest_entry"]["url"],
            "artifacts": {},
        }

        for name in args.include:
            info = downloads.get(name)
            if not info:
                print(f"Skipping {name}: this Minecraft version manifest does not publish it", file=sys.stderr)
                continue
            target = version_dir / f"{name}{ARTIFACTS[name]}"
            artifact_report = download_verified(
                info["url"],
                target,
                info["sha1"],
                info.get("size"),
            )
            report["artifacts"][name] = {
                **artifact_report,
                "url": info["url"],
                "expected_sha1": info["sha1"],
            }

        version_dir.mkdir(parents=True, exist_ok=True)
        manifest_path = version_dir / "artifact-manifest.json"
        manifest_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2, sort_keys=True))
        print(f"\nLocal artifact manifest: {manifest_path}")
        return 0
    except (OSError, urllib.error.URLError, json.JSONDecodeError, KeyError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
