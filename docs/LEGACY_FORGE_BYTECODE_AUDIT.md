# Legacy Forge bytecode and API remapping audit

Reviewed: 2026-11-16.

## Findings in current Kilt remapper

The current Kilt remapper is primarily a Minecraft mapping bridge. It uses the Mojang-to-Intermediary mapping resource and, when required in a development environment, an additional Intermediary-to-named mapping. It builds a class provider from Kilt/NeoForge classes, Fabric mods, mapped Minecraft classes, and the mods being loaded.

That is not the same as a complete legacy Forge API namespace migration.

The current workaround mapping resource, kilt_workaround_mappings.tiny, contains a small set of explicit class substitutions. Examples include:

| Legacy class | Current replacement |
|---|---|
| net/minecraftforge/common/IPlantable | Porting Lib IPlantable |
| net/minecraftforge/common/PlantType | Porting Lib PlantType |
| net/minecraftforge/fml/config/ModConfig | Kilt ModConfig remap |
| net/minecraftforge/client/RenderTypeGroup | Porting Lib RenderTypeGroup |
| net/minecraftforge/common/world/ForgeChunkManager | Porting Lib chunk manager |

These are targeted workarounds, not a complete package map for Forge 47.x.

The current KiltInsnConflictRemapProvider also contains a few specific conflict renames, for example methods on Forge FluidStack, MobEffectInstance, ServerEntity and PotionBrewing. These resolve known name collisions; they do not implement the full Forge API surface.

## Why a package-prefix replacement is unsafe

A global net.minecraftforge -> net.neoforged replacement would only rewrite symbolic names. It would not guarantee that:
- the destination class exists;
- constructor and method descriptors match;
- generic signatures and annotations remain valid;
- fields and method names match the target API;
- nested classes and service-provider entries are present;
- Mixin targets and refmaps resolve;
- access transformers and coremod scripts refer to valid runtime symbols;
- the runtime semantics remain equivalent.

Some Forge APIs were moved to Porting Lib, removed, split, renamed, or behaviorally changed. A class mapping may be valid for one Minecraft/Forge generation and wrong for another.

## Proposed architecture

Use a separate versioned mapping layer before/alongside the existing Minecraft remapper:

1. Detect source dialect and source generation from mod metadata and class references.
2. Select an explicit compatibility profile, initially Forge 47.x / Minecraft 1.20.1 -> NeoForge/Fabric 26.3.
3. Apply class/package mappings only where a verified target exists.
4. Apply owner+name+descriptor mappings for methods and fields, including conflict handling.
5. Transform descriptors, generic signatures, annotations, handles, invokedynamic bootstrap arguments, and constant-pool class references.
6. Transform non-class resources: service files, Mixin configs/refmaps, manifests, access transformers and coremod metadata.
7. Emit a report for every unresolved or ambiguous reference rather than guessing.
8. Add API shims for behavioral gaps, with tests for each contract.

Keep this mapping profile separate from Minecraft's Mojang/Intermediary mappings. These are different namespaces and different compatibility axes.

## Diagnostic work before broad remapping

Build a non-executing JAR inspector that reports:
- class counts by package namespace;
- all referenced classes in descriptors, signatures, annotations and constant-pool entries;
- external package references, including Forge, NeoForge, Minecraft, Kotlin and third-party APIs;
- Mixin configs/refmaps and target references;
- META-INF/services registrations;
- access transformer and coremod resources;
- unresolved class references when checked against a supplied target classpath.

Run this diagnostic against a minimal test mod first, then the original CoFH dependency chain locally. Never store proprietary JARs or extracted assets in the public repository.

## First API smoke-test suite

Before Thermal, create a tiny legacy Forge mod that exercises one feature at a time:

1. JavaFML metadata and entrypoint loading.
2. One event-bus listener and lifecycle callback.
3. One deferred registry entry (block and item).
4. One block entity with persistent data.
5. One basic menu and packet synchronization.
6. One capability/storage integration.

Every green test becomes an explicit supported compatibility contract. Thermal is then the first large end-to-end test, not the test harness for discovering every fundamental bridge failure at once.

## Current status

- Current Kilt remapper handles Minecraft mapping conversion and a limited collection of workarounds.
- Full Forge 47.x API namespace migration has not been implemented.
- Full Forge 47.x behavioral compatibility has not been implemented.
- The new legacy metadata normalizer does not claim either of those capabilities.
- The target remains a universal Forge/NeoForge-to-Fabric adapter; Thermal remains the first major integration profile.