# Roadmap

The project is intentionally staged. A milestone is complete only when its acceptance checks pass.

## M0 — Repository and reproducible development baseline

- [x] Create a working branch from the Kilt 26.1.2-era code.
- [x] Preserve a separate historical baseline branch.
- [ ] Make the fork build reproducibly from GitHub Actions.
- [ ] Record every submodule commit and build prerequisite.
- [ ] Port the working tree from Minecraft 26.1.2 to 26.3.

## M1 — Generic Forge-family discovery

- [ ] Detect NeoForge metadata.
- [ ] Detect legacy Forge `mods.toml`.
- [ ] Normalize legacy metadata for FML discovery without editing the original JAR.
- [ ] Preserve dependency semantics and provide actionable diagnostics.
- [ ] Test nested/Jar-in-Jar artifacts and manifests.
- [ ] Test Fabric/Forge hybrid JAR handling and duplicate mod IDs.

## M2 — Bytecode and namespace compatibility

- [ ] Produce a class-reference report for a supplied mod JAR.
- [ ] Remap supported legacy Forge namespaces.
- [ ] Remap SRG/official/Mojang/Fabric runtime names where required.
- [ ] Handle mixin configs/refmaps, service descriptors, access transformers, coremods, reflection strings, and manifests.
- [ ] Add remapper regression fixtures.

## M3 — Runtime API compatibility

- [ ] Lifecycle and language-provider compatibility.
- [ ] Event bus compatibility.
- [ ] Registry and deferred-registration compatibility.
- [ ] Block entity and item capability/attachment compatibility.
- [ ] Menus, containers, networking, and synchronization.
- [ ] Config, recipes, tags, loot, resources, and data components.
- [ ] Client rendering and server-side isolation.

## M4 — Thermal integration (first end-to-end workload)

- [ ] Discover the original CoFH modules and dependency graph.
- [ ] Load the original CoFH Core.
- [ ] Load Thermal Core and Thermal Foundation.
- [ ] Load Thermal Expansion.
- [ ] Verify machine registration and item/block models.
- [ ] Open machine screens and test inventory interaction.
- [ ] Test processing, energy, fluids, augments, and sided configuration.
- [ ] Save, restart, and reload a world without data loss.
- [ ] Test a dedicated server and a remote client.
- [ ] Repeat the suite on the final Minecraft 26.3 runtime.

## M5 — Additional Forge/NeoForge test mods

Only after Thermal provides a stable integration suite, add small representative mods to validate that the bridge architecture is not coupled to Thermal.

## Current priority

The immediate priority is the build baseline and M1 discovery support. Do not begin large API rewrites until CI can compile the project and a minimal test mod can be discovered.
