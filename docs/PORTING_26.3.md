# Minecraft 26.3 porting plan

Last reviewed: 2026-11-16.

## Verified target toolchain

The Fabric team's Minecraft 26.3 migration guidance specifies Loom 1.17, Gradle 9.6.0 (at publication time), and Fabric Loader 0.19.5. Minecraft 26.3 requires the Java 25 toolchain used by the current NeoForge 26.x source.

The latest Fabric API release listed for Minecraft 26.3 as of 2026-11-16 is:

- Fabric API: `0.166.2+26.3` (published 2026-11-11)
- Fabric Loader: `0.19.5`
- Fabric Loom: `1.17.x`
- Gradle: `9.6.0`
- Java: `25`

References:
- Fabric 26.3 migration notes: https://fabricmc.net/2026/09/15/263.html
- Fabric API release listing: https://www.curseforge.com/minecraft/mc-mods/fabric-api/files/all
- Fabric Loom Maven: https://maven.fabricmc.net/net/fabricmc/fabric-loom/
- Official NeoForge 26.3 branch: https://github.com/neoforged/NeoForge/tree/26.3.x

## Exact starting point in this fork

The current Kilt working source was imported from upstream Kilt commit `058538c64e135048e079d9438306a8df90925d33`.

That source is **Minecraft 26.1.2**, not 26.3:

| Setting | Current Kilt working source | 26.3 target |
|---|---|---|
| Minecraft | `26.1.2` | `26.3` |
| Java | `25` | `25` |
| Fabric Loader | `0.19.3` | `0.19.5` |
| Fabric API | `0.155.2+26.1.2` | `0.166.2+26.3` |
| Gradle wrapper | `9.5.1` | `9.6.0` |
| Loom | `1.17.+` | `1.17.x` |

Do not change only `minecraft_version`. Kilt's runtime, the modified NeoForge source, Twill, FancyModLoader, and their mappings must describe the same Minecraft version.

## Submodule compatibility gap

### Kilt's modified NeoForge source

Current submodule:
- Repository: https://github.com/KiltMC/NeoForge
- Branch lineage: `kilt/26.1.x`
- Pinned commit: `c7b2505c60f9cb597c49762a5792318ec51bb10f`
- Minecraft: `26.1.2`
- Java: `25`
- FancyModLoader: `11.0.15`

Official upstream target:
- Repository: https://github.com/neoforged/NeoForge
- Branch: `26.3.x`
- Current branch head observed during audit: `ac357cc57b285ce9b469d7fca9b01d9830ae8a25`
- Minecraft: `26.3`
- Java: `25`
- FancyModLoader: `12.0.12`

Kilt's modifications are a series of real source changes, not just version properties. The Kilt 26.1.x commits adapt client rendering/model hooks, registry behavior, event hooks, fluids, attachments, data maps, resource reloads, networking, and server lifecycle for the Fabric runtime. The relevant history starts with the Kilt porting commits in July/August 2026 and culminates in the October 2026 compile/runtime fixes.

Representative Kilt-specific commits:
- `3b7cd9ad66799bc9dba295b64c9cd30c2d4cde28` — early 26.1 port
- `4084e57c91c7a0ebabdda97209d81f2c0d43974b` — CommonHooks/EventHooks adaptation
- `6462a5eb97611966e5752b4b5b126d94fafa8868` — attachments, fluids, data maps, reload hooks
- `3608282e08e2873b315e3eba8240cf1e5b2329e3` — registry, recipe, data, and networking changes
- `e6946c45dbd0b47c621673b464c9aa67c48d41e1` — client rendering, resource reload, server lifecycle
- `2d69297bfedcda3d6c5092075eebb9b81d2416be` — model pipeline, client payloads, menus, queued packets
- `7fc99f69d0c9fc697fe18507df09c52230381362` — compile fixes
- `c7b2505c60f9cb597c49762a5792318ec51bb10f` — runtime mixin fixes

These changes must be reviewed and ported onto upstream NeoForge 26.3.x. Blindly replacing the submodule would remove them.

### Twill and FancyModLoader

Current Twill submodule:
- Repository: https://github.com/KiltMC/Twill
- Pinned commit: `c5a87bffd19410209290e44d1e6abc18f0daccd8`
- Stonecutter versions currently enabled in `settings.gradle.kts`: `26.1.2` and `26.2`
- A `26.3` entry is present but commented out.

Nested submodules in that Twill commit:
- FancyModLoader/FML: `00af2df2e7336f5a51d64df5985796dd1dd09293`
- KnitLoader: `dc08f05604c67aaf6ff30a48888af5a3809660f2`

The current FML reader API used by Kilt's metadata adapter is from this pinned FancyModLoader revision. The 26.3 branch uses FML 12.x and must be audited separately before changing adapter imports or Mixin descriptors.

## Migration sequence

1. **Keep a reproducible 26.1.2 Kilt build.** This isolates errors introduced by our first compatibility adapter from errors caused by the 26.3 port.
2. **Port Twill/FancyModLoader.** Enable a 26.3 Stonecutter target and update the nested FML/KnitLoader sources and runtime integration.
3. **Port the Kilt-modified NeoForge layer.** Rebase Kilt-specific patches onto official NeoForge 26.3.x; keep each subsystem change reviewable.
4. **Update Kilt itself.** Align Minecraft, Fabric Loader, Fabric API, Loom, Gradle wrapper, mappings, Mixin targets, and API usages.
5. **Revalidate the legacy Forge metadata bridge.** Its current implementation targets the pinned 26.1.2 FML reader and may need a 26.3-specific adaptation.
6. **Run generic Forge/NeoForge fixtures, then Thermal.** A discovery pass is not runtime compatibility.

## What must not be claimed yet

The repository is not yet a Minecraft 26.3 build, and it does not yet run the original Thermal JARs. Current automated tests validate the metadata-inspection tooling. The runtime metadata adapter is an initial implementation slice; its build and launch behavior still need confirmation.
