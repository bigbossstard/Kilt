# Work tracker

GitHub Issues are not enabled for this fork, so the active engineering backlog is tracked here until repository settings are changed.

## Implemented, awaiting build/runtime verification

- [x] Add a generic legacy Forge metadata filename hook at the pinned FML reader boundary.
- [x] Create a runtime-only normalized archive; leave original mod JARs untouched.
- [x] Avoid carrying stale JAR signature sidecars into the normalized archive.
- [x] Close the original and failed/declined normalized JarContents paths.
- [x] Parse and rewrite legacy Forge metadata with NightConfig rather than regular expressions.
- [x] Adapt JavaFML loaderVersion to the pinned runtime language-provider version.
- [x] Map legacy Forge dependency identity/range to the current NeoForge runtime dependency.
- [x] Translate legacy mandatory flags to modern required/optional dependency types.
- [x] Add Kotlin unit tests for the metadata policy.
- [x] Add Python metadata-inspection tooling and synthetic JAR tests.
- [x] Add CI for Python validation and the active bridge build.
- [ ] Confirm the Kotlin adapter and normalizer compile against the exact pinned FML submodule.
- [ ] Run the Kotlin unit tests in CI.
- [ ] Confirm the original and normalized JarContents are closed exactly once in all paths.

## In progress — generic Forge-family compatibility

- [ ] Add profile selection and supported source/runtime version declarations to metadata policy.
- [ ] Support directory/composite JarContents inputs, not just regular JAR files.
- [ ] Add JAR-level fixtures for Forge-only, NeoForge-only, dual-metadata, invalid metadata, and Jar-in-Jar archives.
- [ ] Add a minimal legacy Forge test mod with no Thermal-specific code.
- [ ] Add a minimal NeoForge test mod to ensure the native path remains unchanged.
- [ ] Add class namespace and API reference diagnostics for supplied mod JARs.

## Runtime API compatibility

- [ ] Map supported legacy Forge packages and symbols to a runtime compatibility surface.
- [ ] Implement event bus and lifecycle compatibility.
- [ ] Implement registry and deferred-registration compatibility.
- [ ] Implement block entity, item, energy, fluid, and other capability/storage semantics.
- [ ] Implement menus, packets, and synchronization.
- [ ] Implement config, recipes, tags, loot, data components, and resources.
- [ ] Verify client/server isolation and dedicated-server launch.

## Minecraft 26.3 port

- [ ] Create user-owned forks or patch tracks for the Kilt-modified NeoForge and Twill/FML submodules.
- [ ] Port the 26.1.2 Kilt runtime and modified NeoForge/FML stack to 26.3.x together.
- [ ] Update mappings, loader/FML dependencies, mixins, and test fixtures.
- [ ] Keep a reproducible build from a clean checkout.

## Thermal acceptance

- [ ] Acquire the original CoFH 1.20.1 artifacts locally; keep binaries out of Git.
- [ ] Record metadata, dependency graph, class namespace counts, and SHA-256 values.
- [ ] Establish a historical 1.20.1 comparison run.
- [ ] Run Thermal discovery through the generic bridge and report all remaining compatibility failures.
- [ ] Bring the original Thermal implementation through the bridge on Minecraft 26.3.
- [ ] Verify machines, recipes, energy, fluids, screens, networking, persistence, and dedicated server.

## Repository operations

- [ ] Maintain public upstream attribution and preserve all submodule licenses.
- [ ] Enable GitHub Issues for normal bug and milestone tracking when desired.
- [ ] Do not commit Minecraft game files, original CoFH binaries, extracted proprietary assets, or local credentials.
