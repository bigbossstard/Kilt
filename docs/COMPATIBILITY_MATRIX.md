# Compatibility matrix

Last reviewed: 2026-11-16.

| Component | Source/runtime version | Status in this fork | Notes |
|---|---|---|---|
| Kilt working tree | Minecraft 26.1.2, Fabric | Imported baseline; build not yet verified in this fork | Starting point for the 26.3 port |
| Final runtime target | Minecraft 26.3, Fabric | Not implemented | Must update Kilt and the modified NeoForge source together |
| Kilt historical baseline | Minecraft 1.20.1 | Separate branch | Useful for comparing legacy Forge integration |
| Legacy Forge metadata | `META-INF/mods.toml` | First adapter in progress | Metadata normalization is not API compatibility |
| NeoForge metadata | `META-INF/neoforge.mods.toml` | Native discovery path in current Kilt/Twill | Must remain supported |
| Forge 47.x API | Minecraft 1.20.1 | Not supported by the 26.1.2 runtime yet | Historical API and package namespace |
| CoFH Core 11.1.0 | Minecraft 1.20.1 / Forge 47.x | Not tested | Original artifact; no binary stored here |
| Thermal Core 11.1.0 | Minecraft 1.20.1 / Forge 47.x | Not tested | Required by Thermal Foundation and Expansion |
| Thermal Foundation 11.1.0 | Minecraft 1.20.1 / Forge 47.x | Not tested | First integration profile |
| Thermal Expansion 11.1.0 | Minecraft 1.20.1 / Forge 47.x | Not tested | First end-to-end workload |

## Interpretation

- “Detected” means the metadata reader can identify a file.
- “Loaded” means the loader accepted the dependency graph and instantiated the mod.
- “Playable” requires registration, menus, networking, client rendering, server behavior, and persistence tests.
- “26.3 compatible” requires the above against Minecraft 26.3, not merely against the historical version used to build the source JAR.

No row should be promoted to “supported” without a reproducible test result.
