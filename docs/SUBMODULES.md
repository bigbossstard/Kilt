# Source submodules

The Kilt fork is a multi-repository source tree. The top-level Git commit alone does not fully describe the build: every submodule commit is part of the source state.

## Current working branch

| Path | Repository | Pinned commit | Role |
|---|---|---|---|
| `forge/` | https://github.com/KiltMC/NeoForge | `c7b2505c60f9cb597c49762a5792318ec51bb10f` | Kilt-modified NeoForge API/source |
| `twill/` | https://github.com/KiltMC/Twill | `c5a87bffd19410209290e44d1e6abc18f0daccd8` | FML/Twill runtime integration |
| `twill/fml/v11/` | https://github.com/KiltMC/FancyModLoader | `00af2df2e7336f5a51d64df5985796dd1dd09293` | Nested FML implementation |
| `twill/knit-loader/` | https://github.com/KiltMC/KnitLoader | `dc08f05604c67aaf6ff30a48888af5a3809660f2` | Loader-neutral mod discovery/dependency coordination |

The `forge` submodule is a Kilt-specific branch of NeoForge. It is not interchangeable with the official upstream submodule at the same path.

## Rules for changes

- Do not replace a submodule with an unrelated upstream branch without first inventorying the Kilt-specific patch set.
- Record every changed gitlink SHA in the parent repository commit.
- Keep source modifications in the submodule that owns the code; keep top-level orchestration in Kilt.
- Preserve all upstream license and copyright notices.
- Test recursive submodule checkout from a clean runner before relying on a local developer checkout.
- Do not commit generated Minecraft source/JARs, downloaded assets, build caches, or local game data.

## Planned 26.3 source branches

The 26.3 migration affects at least three connected repositories:

1. KiltMC/NeoForge fork: rebase Kilt-specific NeoForge changes onto official NeoForge `26.3.x`.
2. KiltMC/Twill fork: enable the 26.3 Stonecutter target and port Twill integration to FancyModLoader/FML 12.x.
3. Kilt top-level fork: update Kilt mixins, runtime adapters, and build configuration to match those submodule APIs.

The GitHub integration available to this project can create branches and commit files in existing repositories, but cannot create GitHub forks. Before source changes in either KiltMC-owned submodule are committed, a user-owned fork of that submodule must be created and its URL/pinned branch recorded in `.gitmodules`.

## Clean checkout command

```bash
git clone --recurse-submodules https://github.com/bigbossstard/Kilt.git
cd Kilt
git checkout work/forge-fabric-26.3
git submodule update --init --recursive
```
