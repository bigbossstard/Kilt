# Work tracker

GitHub Issues are currently disabled in this fork (the Issues API returns HTTP 410), so the active engineering backlog is tracked here until the repository setting is enabled.

## Active branches

- `work/forge-fabric-26.3` — universal bridge development.
- `upstream/26.1.x` — untouched upstream Kilt source baseline.
- `baseline/thermal-1.20.1` — historical Forge/FML comparison build.
- `work/thermal-26.3` — earlier working branch retained for history.

## Build baselines

- [ ] Complete Kilt 26.1.2 build with the metadata adapter.
- [ ] Complete Kilt 1.20.1 historical baseline build.
- [x] Run generic Python JAR metadata/artifact-tool tests on the universal working branch.

## 26.3 migration

- [ ] Create user-owned fork of KiltMC/NeoForge for the 26.3 port.
- [ ] Rebase Kilt-specific NeoForge modifications onto upstream `26.3.x`.
- [ ] Create user-owned fork of KiltMC/Twill for the 26.3 port.
- [ ] Enable Twill Stonecutter version 26.3 and update FancyModLoader/FML 12.x.
- [ ] Update Kilt to Java 25, Gradle 9.6, Loom 1.17, Fabric Loader 0.19.5, Fabric API 0.166.2+26.3.
- [ ] Port Kilt mixins, remapping, runtime patches, and build integration to the new game/FML APIs.

## Generic Forge-family bridge

- [ ] Verify the legacy Forge metadata Mixin and dependency normalizer compile against the pinned FML source.
- [ ] Add focused unit tests for legacy TOML normalization (mandatory/type, forge/neoforge, JavaFML, and Minecraft version constraints).
- [ ] Confirm the Mixin intercepts both FML discovery and Twill's direct JAR-reader path.
- [x] Add initial legacy metadata normalization for dependency IDs/types and runtime version ranges; keep API compatibility as a separate layer.
- [ ] Add legacy Forge namespace remapping with explicit mappings and tests.
- [ ] Add transformation coverage for service files, reflection strings, access transformers, manifests, mixin configs, and refmaps.
- [ ] Add Jar-in-Jar recursive inspection to the analysis tool.
- [ ] Verify client/server isolation and dedicated-server launch.

## Thermal first integration

- [ ] Obtain official public CoFH 1.20.1 binaries locally and keep them out of Git.
- [ ] Record exact SHA-256 values and metadata for the three public JARs.
- [ ] Inspect Thermal Core embedded Jar-in-Jar component in Thermal Foundation.
- [ ] Establish a historical 1.20.1 comparison run.
- [ ] Bring the original Thermal implementation through the universal bridge on Minecraft 26.3.
- [ ] Verify machines, recipes, energy, fluids, screens, networking, persistence, and dedicated server.

## Repository operations

- [ ] Enable GitHub Issues for normal bug and milestone tracking.
- [ ] Create user-owned fork of KiltMC/NeoForge and KiltMC/Twill before committing source changes in those submodules.
