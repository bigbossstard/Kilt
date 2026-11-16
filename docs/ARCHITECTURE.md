# Architecture and compatibility boundaries

## Current Kilt runtime path

The working tree is based on Kilt's newer architecture:

1. Fabric Loader starts Kilt.
2. Kilt installs its `TwillOverrides` implementation.
3. Twill/FancyModLoader provides the NeoForge-style discovery and lifecycle runtime.
4. Knit coordinates Fabric-native and bridged mod definitions.
5. Kilt remaps mod bytecode and applies targeted ASM/Mixin fixers.
6. NeoForge API implementations and Kilt mixins bridge runtime behavior to Fabric/Minecraft.

Relevant upstream projects:

- Kilt: https://github.com/KiltMC/Kilt
- Twill: https://github.com/KiltMC/Twill
- Kilt's modified NeoForge source: https://github.com/KiltMC/NeoForge
- FancyModLoader: https://github.com/KiltMC/FancyModLoader
- Fabric Loader: https://github.com/FabricMC/fabric-loader
- Fabric API: https://github.com/FabricMC/fabric

## Two input dialects

A universal bridge must not assume that all Forge-family mods target the same API generation.

| Input family | Metadata marker | Typical Java package | Representative source generation |
|---|---|---|---|
| Legacy Forge | `META-INF/mods.toml` | `net.minecraftforge.*` | Forge 47.x / Minecraft 1.20.1 |
| NeoForge | `META-INF/neoforge.mods.toml` | `net.neoforged.*` | NeoForge 1.21+ and 26.x |

A modern NeoForge runtime does not automatically provide the old Forge package namespace or its historical behavior. Metadata discovery is only the first compatibility gate.

## Planned bridge layers

### 1. Discovery and metadata normalization

Recognize both metadata markers, parse each dialect without changing the source JAR, normalize dependency identifiers and loader constraints through explicit policies, and preserve the original metadata for diagnostics.

The first code slice adds a runtime-only normalized copy for legacy Forge metadata so the existing NeoForge FML reader can inspect it. This is deliberately narrow: it does not claim old Forge dependencies or APIs are already satisfied.

### 2. Namespace and bytecode remapping

Inspect class references and map supported legacy namespaces to runtime namespaces. The remapper must distinguish:
- Minecraft mapping changes;
- Forge-to-NeoForge package and symbol changes;
- Fabric intermediary/runtime mapping;
- reflection strings, service files, mixin configs/refmaps, access transformers, and manifests.

Blind package-prefix replacement is not a complete remapper. Every transformation needs tests and diagnostics.

### 3. API compatibility

Implement or adapt the API surface required by actual mods:
- mod loading lifecycle and language providers;
- event bus and event dispatch semantics;
- registries and deferred registration;
- block entities, capabilities/attachments, menus, and networking;
- configuration, data generation, recipes, loot, tags, and resources;
- client rendering and dedicated-server class isolation.

Where APIs differ semantically, use explicit shims. Do not silently treat a method rename as behavioral equivalence.

### 4. Minecraft-version compatibility

The runtime target is Minecraft 26.3. The current working branch is based on Kilt's 26.1.2 line, so the target game and the modified NeoForge submodule must be moved together.

A mod authored for Minecraft 1.20.1 may require more than remapping to run on 26.3. This is tracked separately from the Forge/NeoForge loader bridge.

### 5. Integration profiles

Mod-specific compatibility tests live outside the core:
- generic metadata fixtures;
- small Forge test mods;
- small NeoForge test mods;
- Thermal Series profile;
- later additional mod profiles.

A failure in one integration profile must not force Thermal-specific behavior into the core loader.

## Submodules

The Kilt project includes source submodules. Before modifying one, preserve its upstream identity and license. The modified NeoForge submodule is a critical build input; moving it to a user-owned branch will require a separate fork or a documented patch workflow.

## Design rule

Prefer a small, explicit compatibility adapter at a boundary over modifying the original mod or silently changing its logic. Every adapter must state:
- supported input versions;
- runtime version;
- behavior changed;
- known limitations;
- automated tests;
- whether the transformation is reversible.
