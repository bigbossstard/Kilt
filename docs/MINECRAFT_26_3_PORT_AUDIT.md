# Minecraft 26.3 port audit

Reviewed: 2026-11-16.

## Summary

The current Kilt fork is not yet a Minecraft 26.3 project. It is based on the Kilt 26.1.2 line. The 26.3 target requires a coordinated port of Kilt, Twill, the Kilt-modified NeoForge source, and FancyModLoader (FML).

Do not change only the Minecraft version property. The current loader code is compiled against a specific NeoForge/FML API generation, and several of those APIs and dependencies changed between 26.1 and 26.3.

## Current working baseline in this fork

| Layer | Current baseline | Evidence |
|---|---|---|
| Kilt game version | 26.1.2 | Kilt gradle.properties |
| Fabric Loader | 0.19.3 | Kilt gradle.properties |
| Fabric API | 0.155.2+26.1.2 | Kilt gradle.properties |
| Java | 25 | Kilt Kotlin/Java toolchain |
| Kilt NeoForge API | 26.1.2.109 | src/main/kotlin/xyz/bluspring/kilt/loader/Constants.kt |
| Twill FML generation | v11 | Twill source-set path fml/v11 |
| FML runtime version | 11.0.17 | TwillConstants.FML_VERSION |
| Gradle wrapper | 9.5.1 | gradle/wrapper/gradle-wrapper.properties |

Relevant pinned submodules in the current branch:

| Submodule | Repository | Current revision |
|---|---|---|
| forge | https://github.com/KiltMC/NeoForge | c7b2505c60f9cb597c49762a5792318ec51bb10f |
| twill | https://github.com/KiltMC/Twill | c5a87bffd19410209290e44d1e6abc18f0daccd8 |
| twill/fml/v11 | https://github.com/KiltMC/FancyModLoader | 00af2df2e7336f5a51d64df5985796dd1dd09293 |

## Official 26.3 reference baseline

The official NeoForge branch 26.3.x currently points to commit ac357cc57b285ce9b469d7fca9b01d9830ae8a25.

Its gradle.properties identifies this reference stack:

| Layer | 26.3 reference |
|---|---|
| Minecraft | 26.3 |
| Java | 25 |
| FancyModLoader | 12.0.12 |
| ModDevGradle | 2.0.148 |
| JST | 2.0.11 |
| ASM | 9.11 |
| Access Transformers | 11.0.2 |
| EventBus | 8.0.5 |
| NightConfig | 3.9.0 |
| Mergetool | 2.0.7 |
| Mixin | 0.17.4+mixin.0.8.7 |
| JarJar | 0.5.3 |

This is the upstream NeoForge reference, not yet a Kilt-compatible drop-in replacement.

Reference: https://github.com/neoforged/NeoForge/tree/26.3.x

## Twill is also a porting blocker

The current KiltMC/Twill main branch is a Stonecutter multi-version build. Its settings.gradle.kts currently lists 26.1.2 and 26.2. The 26.3 entry is explicitly commented out.

Twill also selects an FML generation through its FML submodule and includes that generation's Java sources in its own source set. Current Kilt points at FML v11. The official 26.3 reference uses FancyModLoader 12.0.12, so moving Twill to 26.3 requires an FML v12 integration rather than simply changing the game version.

Relevant source: https://github.com/KiltMC/Twill/blob/main/settings.gradle.kts

## Kilt-specific NeoForge modifications

Kilt does not consume an untouched NeoForge checkout. Its forge submodule points to KiltMC/NeoForge, a modified source fork containing changes necessary for the Kilt/Fabric launch environment.

Therefore:
- Do not replace the submodule with official NeoForge 26.3.x without reviewing the Kilt patches.
- Preserve the Kilt-specific changes and their attribution.
- Port those changes onto the official 26.3.x source generation.
- Update the FML/Twill integration in the same coordinated branch.
- Keep each submodule revision pinned and reproducible.

The current KiltMC/NeoForge fork has a kilt/26.1.x branch. A matching Kilt-specific kilt/26.3.x integration was not present in the branch inventory inspected for this audit.

## Recommended port sequence

### Phase A — reproduce current baseline
1. Build the current Kilt 26.1.2 branch from a clean checkout, including all submodules.
2. Run existing Kilt smoke tests without third-party mods.
3. Record exact Gradle, JDK, submodule and output artifact versions.

### Phase B — port the platform substrate
1. Create a dedicated Kilt/Twill 26.3 integration branch.
2. Create a Kilt-compatible FancyModLoader v12 source integration based on official FML 12.0.12.
3. Port Twill's 26.1.2/26.2 implementation to 26.3 and update its FML source-set selection.
4. Port Kilt's NeoForge modifications from the Kilt 26.1.x source fork to official NeoForge 26.3.x.
5. Update Kilt's own Minecraft/Fabric dependencies and mixins.
6. Build and launch an empty Fabric + Kilt 26.3 profile on client and dedicated server.

### Phase C — validate the legacy Forge bridge
1. Keep the generic legacy Forge metadata normalizer independent of mod-specific profiles.
2. Add synthetic Forge and NeoForge fixtures.
3. Verify discovery and dependency resolution.
4. Add a tiny legacy Forge smoke mod that exercises the JavaFML entrypoint, event bus and one registry.
5. Implement missing API compatibility based on actual failures.

### Phase D — Thermal as the first full integration
1. Inspect locally obtained official CoFH JARs and produce a metadata/class-reference report.
2. Run the CoFH dependency chain through the generic bridge.
3. Fix generic compatibility failures in the bridge, not by adding Thermal-specific behavior to its core.
4. Verify registries, machines, recipes, energy, fluids, GUI, packets, persistence, client rendering and dedicated server.
5. Do not claim compatibility until the real original artifacts launch and behave correctly on Minecraft 26.3.

## Critical distinction: loader compatibility versus game-version compatibility

There are two separate compatibility problems:

1. Forge/NeoForge to Fabric: emulate/adapt the loader APIs, metadata, lifecycle, registries, event buses and bytecode expectations.
2. Minecraft 1.20.1 to 26.3: adapt Minecraft classes, methods, mappings, data formats and runtime behavior expected by a much older mod.

The metadata normalizer in this repository addresses only a small part of the first problem. It does not upgrade CoFH's Minecraft 1.20.1 code to 26.3.

The target remains a universal Forge/NeoForge-to-Fabric adapter. Thermal is the first high-value integration workload, not the only supported mod family.