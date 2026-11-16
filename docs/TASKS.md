# Work tracker

GitHub Issues are currently disabled in this fork (the Issues API returns HTTP 410), so the active engineering backlog is tracked here until the repository setting is enabled.

## In progress

- [ ] Verify the legacy Forge metadata Mixin compiles against the pinned FML source.
- [ ] Confirm the Mixin intercepts both FML discovery and Twill's direct JAR-reader path.
- [ ] Run the generic Python metadata inspector CI.
- [ ] Run the Gradle build and fix source/submodule issues.
- [ ] Port the modified NeoForge submodule and Kilt runtime from 26.1.2 to 26.3.x.

## Core bridge

- [ ] Add dependency normalization policies for legacy Forge IDs and version ranges.
- [ ] Add legacy Forge namespace remapping with explicit mappings and tests.
- [ ] Add transformation coverage for service files, reflection strings, access transformers, manifests, mixin configs, and refmaps.
- [ ] Verify client/server isolation and dedicated-server launch.

## Thermal acceptance

- [ ] Acquire the original CoFH 1.20.1 artifacts locally; keep binaries out of Git.
- [ ] Record metadata, dependency graph, class namespace counts, and SHA-256 values.
- [ ] Establish a historical 1.20.1 comparison run.
- [ ] Bring the original Thermal implementation through the bridge on Minecraft 26.3.
- [ ] Verify machines, recipes, energy, fluids, screens, networking, persistence, and dedicated server.

## Repository operations

- [ ] Enable GitHub Issues for normal bug and milestone tracking.
- [ ] Create a separate user-owned fork of the Kilt-modified NeoForge submodule before committing NeoForge-side changes.
