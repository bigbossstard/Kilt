# Minecraft files for local research

Gradle/Loom downloads the Minecraft artifacts it needs for compilation and development runs. Those files are not source code and must not be committed to this repository.

For manual inspection or decompilation, use the helper:

```bash
python3 tools/fetch_minecraft_artifacts.py --version 26.3
```

By default it downloads the official Minecraft client and server JARs plus client/server mappings into:

```text
build/analysis/minecraft/26.3/
```

The destination is under `build/`, which is ignored by Git. An `artifact-manifest.json` records the official URLs, sizes, and SHA-1 checksums. The downloader verifies the SHA-1 and size from Mojang's version JSON before accepting each file.

You can request only selected artifacts, for example:

```bash
python3 tools/fetch_minecraft_artifacts.py --version 26.3 --include client client_mappings
```

This helper does not download Minecraft assets or third-party libraries; Loom resolves those as part of the normal Gradle workflow. Do not upload client/server JARs, assets, local saves, or game directories to GitHub.
