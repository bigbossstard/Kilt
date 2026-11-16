# NeoForge 26.3 bridge smoke fixture

This is an ordinary NeoForge 26.3 mod. It is intentionally not patched for Kilt and does not import any Kilt classes.

It checks the smallest useful end-to-end compatibility surface:

1. @Mod discovery and JavaFML constructor injection.
2. Mod event-bus listener registration and delivery of FMLCommonSetupEvent.
3. Deferred registration of kilt_bridge_smoke:probe_item, followed by lookup in the live item registry.

## Target toolchain

- Minecraft: 26.3
- NeoForge: 26.3.0.103
- FML: 12.x
- Java: 25
- ModDevGradle: 2.0.148

## Build

From this directory, use the repository Gradle wrapper:

    ../../gradlew --no-daemon -p . build

The resulting mod jar is under build/libs/.

## Expected runtime markers

A successful run through the bridge must include all three markers:

    [KILT-BRIDGE-SMOKE] MOD_CONSTRUCTED
    [KILT-BRIDGE-SMOKE] COMMON_SETUP
    [KILT-BRIDGE-SMOKE] ITEM_REGISTERED=kilt_bridge_smoke:probe_item

The fixture is a test input, not proof by itself. Compatibility is proven only when this exact built jar is loaded by a Minecraft 26.3 Fabric instance with the bridge installed and all markers are present.

Do not add Kilt-specific dependencies or change the mod metadata to make it pass. The point is to preserve a genuine third-party NeoForge artifact as the test subject.
