# Compatibility matrix

Last reviewed: 2026-11-16.

| Component | Source/runtime version | Status in this fork | Notes |
|---|---|---|---|
| Kilt working tree | Minecraft 26.1.2, Fabric | Imported baseline; Gradle build under CI validation | Starting point for the 26.3 port |
| Final runtime target | Minecraft 26.3, Fabric | Not implemented | Kilt and the modified NeoForge/Twill/FML source must move together |
| Kilt historical baseline | Minecraft 1.20.1 | Separate branch | Useful as a control for the legacy Forge API |
| Legacy Forge metadata filename | META-INF/mods.toml | Runtime alias implemented; build verification pending | Generates a cached JAR with META-INF/neoforge.mods.toml |
| Legacy JavaFML loaderVersion | Forge 47.x -> current FML provider | Normalization implemented; unit tests added, CI pending | Uses the FML version from the pinned runtime |
| Legacy forge dependency | Forge 47.x -> current NeoForge API | Normalization implemented; unit tests added, CI pending | Rewrites dependency identity and version range only in legacy dependency tables |
| Legacy mandatory dependency flag | mandatory=true/false | Normalization implemented; unit tests added, CI pending | Converted to type=required/optional |
| Minecraft dependency declaration | Source mod metadata | Preserved unchanged | No cross-version compatibility is implied by discovery |
| NeoForge metadata | META-INF/neoforge.mods.toml | Native discovery path in current Kilt/Twill | Must remain unchanged |
| Forge 47.x API | Minecraft 1.20.1 | Not supported by the 26.1.2 runtime yet | Historical namespace and behavior |
| CoFH Core 11.1.0 | Minecraft 1.20.1 / Forge 47.x | Not tested | Original artifact; no binary stored here |
| Thermal Core 11.1.0 | Minecraft 1.20.1 / Forge 47.x | Not tested | Required by Thermal Foundation and Expansion |
| Thermal Foundation 11.1.0 | Minecraft 1.20.1 / Forge 47.x | Not tested | First integration profile |
| Thermal Expansion 11.1.0 | Minecraft 1.20.1 / Forge 47.x | Not tested | First end-to-end workload |

## Interpretation

- “Detected” means the metadata reader can identify a file.
- “Loaded” means the loader accepted the dependency graph and instantiated the mod.
- “Playable” requires registration, menus, networking, client rendering, server behavior, and persistence tests.
- “26.3 compatible” requires the above against Minecraft 26.3, not merely against the historical version used to build the source JAR.

The metadata changes above are not proof that legacy Forge bytecode or Minecraft 1.20.1 API calls work on 26.3. No row should be promoted to “supported” without a reproducible runtime result.
