# Existing Forge-family ↔ Fabric bridge research

Reviewed: 2026-11-16.

## Decision

Keep this repository on the Fabric-hosted bridge track. Use Kilt as the architectural starting point and Forbric as a comparison/reference implementation. Do not replace Fabric Loader with a new sovereign loader unless the project direction is explicitly changed.

The first runtime proof is a stock NeoForge 26.3 mod loaded on Fabric 26.3. The test fixture is in compat-fixtures/neoforge-26.3-smoke/.

## Existing projects

### Kilt

Repository: https://github.com/KiltMC/Kilt

Kilt is the closest match to the required direction: a Fabric mod which brings Forge-family mods into the Fabric ecosystem. The upstream repository has a version/26.1.x branch, so it is not simply dead. However, the inspected branch currently targets Minecraft 26.1.2, Fabric Loader 0.19.3, Fabric API 0.155.2+26.1.2 and FML 11.0.17. No upstream 26.3 branch was present in the inspected branch inventory.

The Kilt branch is the best starting point for a Fabric-hosted runtime because it already contains the FML/NeoForge integration, event infrastructure and Minecraft injection strategy. The existing code should be ported and tested rather than recreated from scratch.

### Sinytra Connector

Repository: https://github.com/Sinytra/Connector

Connector solves the reverse direction: Fabric mods on NeoForge. It is not a Forge-to-Fabric loader bridge and cannot be used as the core of this project.

### Forbric

Repository: https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader

Forbric 0.3.0 targets Minecraft 26.2 and loads Fabric, MinecraftForge and NeoForge mods together. It is a much more ambitious alternative, but it replaces the loaders with a sovereign kernel and builds a merged Minecraft game JAR with both Forge families' patch sets. Fabric Loader/Knot and the genuine Forge loader lifecycles do not run.

The project reports its own mod-load testing figures in its README, but it explicitly describes itself as experimental, warns that partial mod behavior may be missing, and provides no compatibility guarantee. Its work on discovery, unified lifecycle, mixed-loader API semantics, bytecode transforms and patch merging is useful research material. Adopting it would mean accepting a different loader architecture and a 26.2-to-26.3 port, not simply adding a Fabric mod.

### Patchwork and legacy bridges

Patchwork is not a current solution for Minecraft 26.3. Fabricated-Legacy-Forge targets older Forge generations and does not replace a modern FML 12 bridge.

## 26.3 version boundary

There is no Minecraft-version gap in the first target: both the test mod and the host game are Minecraft 26.3. The bridge must adapt loader contracts and the Forge-family runtime, not translate an old Minecraft 1.20.1 API to 26.3.

Official reference versions inspected for the target:

| Component | Version |
|---|---:|
| Minecraft | 26.3 |
| Java | 25 |
| NeoForge | 26.3.0.103 |
| FancyModLoader / FML | 12.0.12 |
| NeoForge EventBus | 8.0.5 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.166.0+26.3 |

NeoForge 26.3 uses FML 12.0.12, while the current Kilt/Twill baseline still selects FML v11. Updating only the Minecraft version property is therefore insufficient. The coordinated port must include Twill's FML source-set selection, the Kilt-modified NeoForge integration, Fabric dependencies and Kilt's own injections.

Forge 26.3 is also available (66.1.x). It should be treated as a second compatibility target after the NeoForge smoke fixture succeeds, because Forge and NeoForge remain distinct loader/API families even when they target the same Minecraft version.

## Compatibility gates

1. Build and launch empty Fabric 26.3 + Kilt bridge.
2. Load the stock NeoForge 26.3 smoke fixture without modifying its JAR or metadata.
3. Require all three markers: mod constructor, common setup event, and item registry lookup.
4. Add a stock Forge 26.3 smoke fixture and test its JavaFML discovery path separately.
5. Only after both basic loader paths pass, expand coverage to configs, networking, mixins, client events, rendering and real mods.
6. Use Thermal Expansion as an integration workload later; its historical Minecraft 1.20.1 dependency remains a separate cross-version problem.

## Source references

- Kilt: https://github.com/KiltMC/Kilt
- Sinytra Connector: https://github.com/Sinytra/Connector
- Forbric: https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader
- NeoForge 26.3 source: https://github.com/neoforged/NeoForge/tree/26.3.x
- Fabric 26.3 development versions: https://fabricmc.net/develop/
- Forge 26.3 downloads: https://files.minecraftforge.net/net/minecraftforge/forge/
