# Compatibility matrix

Last reviewed: 2026-11-16.

| Component | Source/runtime version | Status in this fork | Notes |
|---|---|---|---|
| Kilt working tree | Minecraft 26.1.2, Fabric | Imported baseline; build validation in progress | Starting point for the 26.3 port |
| Final runtime target | Minecraft 26.3, Fabric | Not implemented | Must update Kilt, Twill/FML, and modified NeoForge source together |
| Fabric 26.3 toolchain | Java 25, Loader 0.19.5, API 0.166.2+26.3, Loom 1.17.x, Gradle 9.6.0 | Verified from release documentation/listing | See `docs/PORTING_26.3.md` |
| Kilt historical baseline | Minecraft 1.20.1 | Separate branch; Gradle build currently running | Useful for comparing legacy Forge integration |
| Legacy Forge metadata | `META-INF/mods.toml` | Runtime normalizer added; build validation pending | Filename, dependency type, loader id and selected version constraints are normalized; API compatibility remains unimplemented |
| NeoForge metadata | `META-INF/neoforge.mods.toml` | Native discovery path in current Kilt/Twill | Must remain supported |
| Forge 47.x API | Minecraft 1.20.1 | Not supported by the 26.1.2 runtime yet | Historical API and package namespace |
| CoFH Core public release | 1.20.1 / Forge | `11.0.2.56`; not yet tested | Official CurseForge release |
| Thermal Core | 1.20.1 / Forge | Source version `11.1.0`; embedded Jar-in-Jar component | Not a separate JAR in the initial public baseline |
| Thermal Foundation public release | 1.20.1 / Forge | `11.0.6.70`; not yet tested | Official CurseForge release |
| Thermal Expansion public release | 1.20.1 / Forge | `11.0.1.29`; not yet tested | Official CurseForge release |

## Interpretation

- “Detected” means the metadata reader can identify a file.
- “Loaded” means the loader accepted the dependency graph and instantiated the mod.
- “Playable” requires registration, menus, networking, client rendering, server behavior, and persistence tests.
- “26.3 compatible” requires the above against Minecraft 26.3, not merely against the historical version used to build the source JAR.

No row should be promoted to “supported” without a reproducible test result.
