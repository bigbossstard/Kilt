# Thermal Series — first integration target

Thermal is the first end-to-end test workload for the generic bridge. It is not the reason the bridge core exists, and no Thermal-specific code should be added to the core unless it represents a generally valid Forge/NeoForge compatibility rule.

## Source repositories and module identifiers

| Module | Mod ID | Upstream source | 1.20.1 version declared by upstream |
|---|---|---|---|
| CoFH Core | `cofh_core` | https://github.com/CoFH/CoFHCore/tree/1.20.1 | 11.1.0 |
| Thermal Core | `thermal` | https://github.com/CoFH/ThermalCore/tree/1.20.x | 11.1.0 dependency in Thermal modules |
| Thermal Foundation | `thermal_foundation` | https://github.com/CoFH/ThermalFoundation/tree/1.20.1 | 11.1.0 |
| Thermal Expansion | `thermal_expansion` | https://github.com/CoFH/ThermalExpansion/tree/1.20.1 | 11.1.0 |

Thermal Expansion's 1.20.1 build declares dependencies on CoFH Core and Thermal Core. Thermal Foundation also depends on these shared components. Thermal Dynamics can be added after the machine and energy path works.

## Why this is a demanding test

Thermal exercises much more than simple block registration:
- JavaFML metadata and mod dependencies;
- lifecycle events and event buses;
- block/item registries;
- block entities and persistent inventory state;
- recipes and secondary outputs;
- menus, slots, client screens, and packet synchronization;
- energy and fluid storage/transfer;
- sided configuration, augments, and machine upgrades;
- client rendering and dedicated-server behavior.

## Version caveat

The public CoFH source branches inspected for these modules target Minecraft 1.20.1. Their original artifacts are not Minecraft 26.3 builds.

The intended final experiment is to run the original Thermal implementation on Fabric 26.3 through this bridge. That requires a cross-version compatibility layer in addition to Forge-to-NeoForge/Fabric adaptation. The old metadata's Minecraft and Forge dependency ranges must not simply be hidden: any override must be explicit, logged, and backed by API compatibility tests.

Use a 1.20.1 profile only as a historical control when comparing behavior or testing the legacy API surface. It is not the final user goal.

## Local test layout

Put original CoFH JARs in `tests/thermal/jars/` on the developer machine only. That directory is ignored by Git.

Run:

```bash
python3 tools/inspect_mod_jar.py tests/thermal/jars/*.jar
```

Record SHA-256 values in a local test report. Do not commit the original JARs, extracted classes, textures, or other restricted assets.

## Distribution and licensing

The CoFH source repositories state that they are not licensed for unrestricted redistribution: source and compiled binaries must not be redistributed in their entirety without express written permission. Public availability on GitHub is not a redistribution license.

This project may inspect public sources and use locally obtained official artifacts for private testing. It must not publish original CoFH JARs or copied source/assets. Any public compatibility release must be reviewed against CoFH's current terms and any written permission obtained.
