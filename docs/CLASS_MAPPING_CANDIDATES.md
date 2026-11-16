# Generating legacy Forge class mapping candidates

The bridge needs a versioned class migration map between the API generation used by a legacy mod and the API classes present in the target NeoForge runtime.

The tool in `tools/generate_class_mapping_candidates.py` is a **candidate generator and target-class validator**. It does not modify a mod JAR and does not claim that mapped methods, fields, signatures, or behavior are compatible.

## Inputs

- A local source API JAR (for example, the Forge 47.x API/universal archive used by a Forge 1.20.1 development profile).
- One or more local target runtime JARs (for example, the NeoForge 26.3/FML 12 runtime classpath).
- Optional explicit class rename overrides in a two-column, headerless TSV file.

JARs are supplied by the developer from the official repositories/build caches. Do not commit Minecraft, Forge, NeoForge, FML, or CoFH JARs to this repository.

## Example

```bash
python tools/generate_class_mapping_candidates.py \
  --source-jar /path/to/forge-1.20.1-api.jar \
  --target-jar /path/to/neoforge-26.3-universal.jar \
  --target-jar /path/to/fancymodloader-12.0.12.jar \
  --overrides /path/to/forge-to-neoforge-overrides.tsv \
  --format json \
  --output build/reports/forge47-to-neoforge263-candidates.json
```

For a machine-readable TSV:

```bash
python tools/generate_class_mapping_candidates.py \
  --source-jar /path/to/forge-api.jar \
  --target-jar /path/to/neoforge-runtime.jar \
  --format tsv \
  --output build/reports/class-map-candidates.tsv
```

The tool includes a small set of default package-prefix candidates:

| Source prefix | Candidate target prefix |
|---|---|
| `net/minecraftforge/forgespi/` | `net/neoforged/neoforgespi/` |
| `net/minecraftforge/eventbus/` | `net/neoforged/bus/` |
| `net/minecraftforge/fml/` | `net/neoforged/fml/` |
| `net/minecraftforge/` | `net/neoforged/neoforge/` |

Longest prefix wins. Additional `--prefix OLD=NEW` entries can be supplied. Exact overrides are read from a TSV with one source class and one target class per line; comments start with `#`.

## Result states

- `verified`: the candidate target class exists in at least one supplied target JAR.
- `missing`: the candidate target class was not found; do not silently apply it.
- `collision`: multiple source classes map to the same candidate target; an explicit decision is required.

A `verified` result only confirms target class presence. It does **not** validate constructors, method/field descriptors, generic signatures, mixins, access transformers, class initialization, or runtime semantics.

## Next stages

1. Build the Forge 47.x -> NeoForge 26.3 candidate map from real API archives.
2. Review missing/ambiguous entries and add exact overrides only when the mapping is evidenced.
3. Validate every mapping against actual class references found by `tools/inspect_bytecode_refs.py`.
4. Extend the mapping engine to transform ASM class nodes and resources.
5. Add API shims and behavioral tests for differences that cannot be solved by names alone.

The mapping profile must be versioned by source loader/API generation and target runtime generation. Never apply a mapping table globally to an unrelated Forge or NeoForge version.