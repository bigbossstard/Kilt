# Project scope

## Goal

Develop a Fabric runtime bridge that can load unmodified Forge and NeoForge mod JARs where their loader APIs and Minecraft-version assumptions can be satisfied.

This is a **loader compatibility project**, not a Thermal Expansion rewrite and not a Thermal-only adapter.

## First integration target

The first end-to-end compatibility target is the original CoFH Thermal Series:

- CoFH Core
- Thermal Core
- Thermal Foundation
- Thermal Expansion

Thermal is the first acceptance workload because it exercises metadata discovery, dependency ordering, registries, lifecycle events, menus, networking, data storage, recipes, block entities, rendering, and capability-style APIs.

Thermal-specific work belongs in test profiles, compatibility shims, or isolated modules. It must not become a hard-coded dependency of the bridge core.

## Active development branch

The universal bridge is developed on `work/forge-fabric-26.3`. The earlier `work/thermal-26.3` branch is retained as a historical working branch; it is not a Thermal-only project.

## Runtime target

- Minecraft Java: 26.3
- Runtime loader: Fabric
- Compatibility inputs: NeoForge and legacy Forge mod JARs

The working branch currently starts from Kilt's 26.1.2-era code. The target is 26.3; 26.1.2 is a development baseline, not the final target.

## Important version distinction

A loader bridge does not automatically port a mod between Minecraft releases. The original Thermal 1.20.1 artifacts were built against Minecraft 1.20.1 and Forge 47.x. Making those exact artifacts run on Minecraft 26.3 additionally requires cross-version bytecode/API adaptation and may expose incompatible game assumptions.

The project therefore tracks three independent concerns:

1. Loader dialect compatibility: Forge versus NeoForge metadata, APIs, events, registries, and lifecycle.
2. Minecraft version compatibility: mappings, descriptors, changed game APIs, data formats, and behavior across versions.
3. Mod-specific compatibility: gaps that remain for a particular mod after the first two layers.

No stage will be marked successful merely because a JAR is discovered or the title screen opens.

## Non-goals for the first milestone

- General support for every mod.
- A replacement implementation of Thermal machines.
- Redistributing Minecraft game files or original CoFH binaries.
- Claiming compatibility before client, dedicated-server, and world-persistence tests pass.

## Repository hygiene

This repository contains bridge source, tests, scripts, dependency coordinates, and technical notes. It must not contain:

- Minecraft client/server JARs or assets.
- Original CoFH JARs or extracted proprietary resources.
- User game directories, saves, logs containing personal information, or local credentials.
- Generated build caches.

Minecraft and mod dependencies should be resolved locally by Gradle or supplied by the tester in ignored test directories. Record artifact coordinates and SHA-256 hashes in local test results; do not commit the binary artifacts.
