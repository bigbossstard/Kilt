# Legacy Forge discovery audit

Last reviewed: 2026-11-16.

## Pinned runtime source inspected

The active working tree is still a Minecraft 26.1.2 baseline, not a completed 26.3 port.

| Component | Pinned revision | Role |
|---|---|---|
| Kilt main source | work/thermal-26.3 | Fabric-side bridge and compatibility hooks |
| Kilt NeoForge submodule | c7b2505c60f9cb597c49762a5792318ec51bb10f | Modified NeoForge source used by Kilt |
| Kilt Twill submodule | c5a87bffd19410209290e44d1e6abc18f0daccd8 | FML/Twill discovery and loader integration |
| Twill FancyModLoader submodule (fml/v11) | 00af2df2e7336f5a51d64df5985796dd1dd09293 | The FML reader currently used by this branch |

The submodule URLs are public upstream repositories. Before changing NeoForge/FML/Twill internals, create user-owned forks or keep a reproducible patch series; a commit to the Kilt repository cannot change an upstream submodule.

## What the current adapter does

The pinned FancyModLoader reader exposes:

- JarModsDotTomlModFileReader.createModFile(JarContents, ModFileDiscoveryAttributes);
- metadata path constant META-INF/neoforge.mods.toml;
- parsing through the normal FML ModFileParser path after that metadata file is found.

The Kilt hook intercepts that method and, for a JAR containing only the legacy Forge metadata path, creates a runtime-only cached JAR with META-INF/neoforge.mods.toml. The original source JAR is not modified. Signature sidecars are removed from the generated copy because its contents no longer match the original JAR signature.

The aliased TOML is parsed and rewritten through NightConfig, not regular expressions. The generic legacy-Forge metadata policy currently:
- rewrites a declared javafml loaderVersion range to the runtime FML language-provider version;
- maps a dependency whose modId is forge to the runtime mod ID neoforge and the currently pinned NeoForge API version;
- translates legacy mandatory=true/false dependency flags to type=required/optional.

Ordinary dependencies and Minecraft version declarations are left unchanged. The current pinned policy values are FML 11.0.17 and NeoForge API 26.1.2.109. These are derived from the current Twill/FML and Kilt source pins and must be updated together when the runtime moves to 26.3.

The original source JAR is not modified. The adapter closes the original JarContents after handing FML an independent normalized archive, and closes the normalized archive if FML declines or fails to parse it.

This is still metadata compatibility only. It does not provide the legacy net.minecraftforge.* classes or the historical API behavior.

## Remaining discovery blockers

Once the filename is recognized, the old descriptor still carries old loader and game assumptions. The CoFH 1.20.1 artifacts inspected so far include:

- modLoader = "javafml";
- loaderVersion in the Forge 47.x line;
- a required dependency with modId = "forge" and a Forge 47.x version range;
- a Minecraft dependency targeting 1.20.1.

The current Twill conversion preserves dependency identifiers and wraps their version ranges in NeoForgeVersionConstraint. It also asks TwillOverrides.getNativeModId(...) to resolve loader-specific dependency IDs.

The first metadata policy is now implemented and has focused Kotlin unit tests. It intentionally does not rewrite the Minecraft version declaration or ordinary dependency ranges. The forge-to-neoforge alias is applied only inside dependency tables from a legacy Forge metadata file; it is not a global dependency resolver substitution.

Do not interpret successful metadata normalization as proof that a 1.20.1 mod works against Minecraft 26.1/26.3. The old mod may still depend on removed Minecraft classes or Forge API behavior that the current runtime does not implement.

## Remaining runtime blockers

Even after discovery succeeds, the original CoFH JARs reference the legacy net.minecraftforge.* namespace and Forge 47 API behavior. The current NeoForge runtime exposes the newer net.neoforged.* API generation.

A universal bridge needs separate, versioned compatibility work for:

- package/class and method remapping;
- event bus semantics and lifecycle;
- registries and deferred registration;
- capabilities and storage;
- menus, networking, and synchronization;
- configuration, recipes, tags, data components, and resources;
- client rendering and dedicated-server isolation.

A package-prefix substitution is not sufficient where method signatures or behavior changed.

## Archive input limitation

The current normalizer uses JarContents.getPrimaryPath() and opens it as a regular JAR file. That is suitable for the installed-mod JAR test path, but JarContents can also represent folders or composite/multi-root content during development. Supporting those forms is a separate follow-up; do not assume the first implementation covers every FML discovery input.

## Test sequence

1. Compile the adapter and normalizer against the exact pinned FML submodule.
2. Run the TOML normalization unit tests.
3. Unit-test JAR alias creation with legacy-only, NeoForge-only, dual-metadata, malformed, and nested/Jar-in-Jar inputs.
4. Confirm that discovery accepts a minimal legacy test mod and reports unresolved API/game requirements explicitly.
5. Load a minimal mod that exercises registries and lifecycle events.
6. Only then introduce original CoFH artifacts as the first large integration suite.
