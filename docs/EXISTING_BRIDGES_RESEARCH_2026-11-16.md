# Existing Forge-family ↔ Fabric bridge research

Reviewed: 2026-11-16.

## Decision

There are now two distinct paths:

- **Use Plexus Mod Loader if the practical goal is to run Fabric mods alongside Forge or NeoForge mods on Minecraft 26.3.** It already does this in a standalone loader profile and is far more complete than a new adapter prototype.
- **Continue Kilt only if the requirement is specifically to keep the real Fabric Loader/Knot as the host.** Plexus does not start Fabric Loader itself; it implements the public Fabric Loader API. Kilt remains the closer architectural starting point for a true Fabric-hosted bridge.

The first Kilt-side runtime proof remains a stock NeoForge 26.3 mod loaded on a Fabric Loader 26.3 instance. Its test fixture is in compat-fixtures/neoforge-26.3-smoke/.

## Existing projects

### Plexus Mod Loader 2.3.0 — strongest current practical solution

Repository: https://github.com/rigelx2b/Plexus-Mod-Loader  
Installer: https://www.plexusmc.net/download/  
License: MIT

Plexus 2.3.0 is stable (release date in its changelog: 2026-11-15) and supports Minecraft 26.3 on Java 25. It runs Fabric, Quilt, Forge and NeoForge mods in one JVM with a single transformed game class loader. Forge and NeoForge are launched through their real FML implementations, not through metadata spoofing.

Important architectural distinction: Plexus does not start Fabric Loader itself. It provides the public Fabric Loader API and compatible entrypoint/Mixin/class-tweaker behavior. Mods that depend on private Fabric Loader implementation classes are excluded. In this sense Plexus is a practical Fabric-compatible multi-loader, not "Fabric Loader plus a bridge mod".

For Minecraft 26.3 the source pins:
- Forge 66.1.0.
- NeoForge 26.3.0.98 and FML 12.0.12.
- Java 25.

The official Forge download page has moved on to Forge 66.1.8, and the current official NeoForge 26.3 branch has moved beyond the Plexus-pinned 26.3.0.98. This means the tested runtime is the exact pinned build, not automatically every later hotfix. FML 12 itself is handled: the source has an FML 12 shared-Mixin processor adaptation, and the 2.3.0 changelog reports 26.3 server/client verification.

The project's 26.3 compatibility report is unusually detailed. It reports real client-window runs reaching a saved world, ticking 200 ticks, saving and quitting; separate server and GameTest runs; and explicit reasons for mods it declines to load. It is not universal compatibility: Fabric/Quilt alongside Forge/NeoForge is marked Partial, because some incompatible mixins and their dependents are left out before launch. The project's reported numbers are its own test results, not an independent audit.

Plexus supports Windows, macOS and Linux through an installer JAR and installs a profile into the official Minecraft Launcher. The game itself uses Java 25. Its source can be studied or forked under the stated MIT license.

Plexus 2.3.0 also adds experimental Forge-on-NeoForge mode, disabled by default and enabled with -Dplexus.forgeOnNeoForge=on. This is not full Forge API compatibility: it currently admits only a limited subset of Forge mods. Unsupported Forge events, registry APIs, and client functionality remain limitations; the release notes say no real Forge mod corpus has yet been run in this mode. Do not treat this opt-in as a finished way to mix arbitrary Forge and NeoForge mods together.

### Kilt

Repository: https://github.com/KiltMC/Kilt

Kilt is the closest match to the strict requirement: a Fabric mod which brings Forge-family mods into a real Fabric Loader environment. The upstream repository has a version/26.1.x branch, so it is not simply dead. The inspected branch targets Minecraft 26.1.2, Fabric Loader 0.19.3, Fabric API 0.155.2+26.1.2 and FML 11.0.17. No upstream 26.3 branch was present in the inspected branch inventory.

Kilt already contains substantial FML/NeoForge integration, event infrastructure and Minecraft injection strategy. The project should be ported and tested rather than recreated from scratch. However, the 26.3 port needs a coordinated FML 12 update, not just a Minecraft version property change.

### Sinytra Connector

Repository: https://github.com/Sinytra/Connector

Connector solves the reverse direction: Fabric mods on NeoForge. It is not a Forge-to-Fabric loader bridge and cannot be used as the core of this project.

### Forbric

Repository: https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader

Forbric 0.3.0 targets Minecraft 26.2 and loads Fabric, MinecraftForge and NeoForge mods together. It replaces the loaders with a sovereign kernel and builds a merged Minecraft game JAR with both Forge families' patch sets. Fabric Loader/Knot and the genuine Forge loader lifecycles do not run.

The project reports its own mod-load testing figures, but describes itself as experimental and warns that partial mod behavior may be missing. Its work on discovery, unified lifecycle, mixed-loader API semantics, bytecode transforms and patch merging is useful research material. It is not a drop-in adapter for a real Fabric Loader.

### Patchwork and legacy bridges

Patchwork is not a current solution for Minecraft 26.3. Fabricated-Legacy-Forge targets older Forge generations and does not replace a modern FML 12 bridge.

## 26.3 version boundary

There is no Minecraft-version gap in the first target: both the test mod and host game are Minecraft 26.3. The bridge must adapt loader contracts and the Forge-family runtime, not translate an old Minecraft 1.20.1 API to 26.3.

Official reference versions inspected for the Kilt target:

| Component | Version |
|---|---:|
| Minecraft | 26.3 |
| Java | 25 |
| NeoForge | 26.3.0.103 |
| FancyModLoader / FML | 12.0.12 |
| NeoForge EventBus | 8.0.5 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.166.0+26.3 |

NeoForge 26.3 uses FML 12.0.12, while the current Kilt/Twill baseline still selects FML v11. Updating only the Minecraft version property is insufficient. The coordinated port must include Twill's FML source-set selection, the Kilt-modified NeoForge integration, Fabric dependencies and Kilt's own injections.

## Compatibility gates for the Kilt branch

1. Build and launch an empty Fabric 26.3 + Kilt bridge.
2. Load the stock NeoForge 26.3 smoke fixture without modifying its JAR or metadata.
3. Require all three markers: mod constructor, common setup event, and item registry lookup.
4. Add a stock Forge 26.3 smoke fixture and test its JavaFML discovery path separately.
5. Only after both basic loader paths pass, expand coverage to configs, networking, mixins, client events, rendering and real mods.
6. Use Thermal Expansion as an integration workload later; its historical Minecraft 1.20.1 dependency remains a separate cross-version problem.

## Sources

- Kilt: https://github.com/KiltMC/Kilt
- Sinytra Connector: https://github.com/Sinytra/Connector
- Forbric: https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader
- Plexus README: https://github.com/rigelx2b/Plexus-Mod-Loader
- Plexus compatibility report: https://www.plexusmc.net/en/docs/reference/compatibility/
- Plexus 2.3.0 changelog: https://github.com/rigelx2b/Plexus-Mod-Loader/blob/main/CHANGELOG.md
- NeoForge 26.3 source: https://github.com/neoforged/NeoForge/tree/26.3.x
- Fabric 26.3 development versions: https://fabricmc.net/develop/
- Forge 26.3 downloads: https://files.minecraftforge.net/net/minecraftforge/forge/
