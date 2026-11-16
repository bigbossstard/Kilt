# Thermal Series — first integration target

Thermal is the first end-to-end test workload for the **generic Forge/NeoForge-to-Fabric bridge**. It is not the reason the bridge core exists, and no Thermal-specific code should be added to the core unless it represents a generally valid Forge/NeoForge compatibility rule.

## Public 1.20.1 Forge artifacts for the historical control

The latest public 1.20.1 release files shown on the official Team CoFH CurseForge projects are:

| Module | Mod ID | Public release JAR | Published | Role |
|---|---|---|---|---|
| CoFH Core | `cofh_core` | `cofh_core-1.20.1-11.0.2.56.jar` | 2024-05-26 | Required core library |
| Thermal Foundation | `thermal_foundation` | `thermal_foundation-1.20.1-11.0.6.70.jar` | 2024-06-19 | Shared ores, metals and materials; requires CoFH Core |
| Thermal Expansion | `thermal_expansion` | `thermal_expansion-1.20.1-11.0.1.29.jar` | 2024-05-26 | Original machines, dynamos and storage; requires Thermal Foundation |

Official download pages:
- [CoFH Core files](https://www.curseforge.com/minecraft/mc-mods/cofh-core/files/all)
- [Thermal Foundation files](https://www.curseforge.com/minecraft/mc-mods/thermal-foundation/files/all)
- [Thermal Expansion files](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion/files/all)

Team CoFH documents that Minecraft 1.20 and below use Forge, while Minecraft 1.21 and above use NeoForge. Its official downloads are distributed through CurseForge and Modrinth. See [Team CoFH Downloads](https://teamcofh.com/downloads/).

## Thermal Core is an embedded component

Thermal Core has mod ID `thermal` and artifact ID `thermal_core`. In the inspected 1.20.1 source, its version is declared as 11.1.0 and Thermal Foundation's Gradle build includes it through Jar-in-Jar. Therefore the baseline should start with the three public JARs above and inspect the embedded component inside Thermal Foundation; do not assume a separate standalone Thermal Core download is required.

Source references:
- [CoFH Core 1.20.1](https://github.com/CoFH/CoFHCore/tree/1.20.1)
- [Thermal Core 1.20.1](https://github.com/CoFH/ThermalCore/tree/1.20.1)
- [Thermal Foundation 1.20.1](https://github.com/CoFH/ThermalFoundation/tree/1.20.1)
- [Thermal Expansion 1.20.1](https://github.com/CoFH/ThermalExpansion/tree/1.20.1)

## Released binaries and current source versions are different

A significant version distinction was identified during the audit:

| Project | Public 1.20.1 release JAR version | Version declared in inspected 1.20.1 GitHub source |
|---|---:|---:|
| CoFH Core | 11.0.2.56 | 11.1.0 |
| Thermal Foundation | 11.0.6.70 | 11.1.0 |
| Thermal Expansion | 11.0.1.29 | 11.1.0 |
| Thermal Core (embedded) | Not a separate artifact in this baseline | 11.1.0 |

Do not combine a locally compiled source snapshot with released binaries in one test without recording that explicitly. The first historical-control run should use the official public JARs exactly as listed above. A separate source-build run can follow later.

## Why this is a demanding test

Thermal exercises much more than simple block registration:
- JavaFML metadata and dependency graph;
- lifecycle events and event buses;
- block/item registries;
- block entities and persistent inventory state;
- recipes and secondary outputs;
- menus, slots, client screens, and packet synchronization;
- energy and fluid storage/transfer;
- sided configuration, augments, and machine upgrades;
- client rendering and dedicated-server behavior.

Thermal Dynamics can be added after the machine, energy, and fluid path works.

## Version caveat

The original public Thermal artifacts described here target Minecraft 1.20.1 / Forge 47.x. Their original binaries are not Minecraft 26.3 builds.

The intended final experiment is to run the original Thermal implementation on Fabric 26.3 through this bridge. That requires:
1. Forge-family loader compatibility;
2. Minecraft 1.20.1-to-26.3 class/API/data migration;
3. Mod-specific fixes only where a general adapter cannot preserve the required behavior.

The old metadata's Minecraft and Forge dependency ranges must not simply be hidden. Any version override must be explicit, logged, and backed by API compatibility tests.

Use the 1.20.1 profile only as a historical control. It is not the final user goal.

## Local test layout

Put the three original public JARs in `tests/thermal/jars/` on the developer machine only. That directory is ignored by Git.

Run:

```bash
python3 tools/inspect_mod_jar.py tests/thermal/jars/*.jar
```

This reports mod metadata, dependency ranges, class namespace references, manifest features, mixin config/refmap files and SHA-256 checksums. Jar-in-Jar extraction/inspection is tracked separately.

Record SHA-256 values in a local test report. Do not commit the original JARs, extracted classes, textures, or other restricted assets.

## Distribution and licensing

The CoFH source repositories state that they are not licensed for unrestricted redistribution: source and compiled binaries must not be redistributed in their entirety without express written permission. Public availability on GitHub is not a redistribution license.

This project may inspect public sources and use locally obtained official artifacts for private testing. It must not publish original CoFH JARs or copied source/assets. Any public compatibility release must be reviewed against CoFH's current terms and any written permission obtained.
