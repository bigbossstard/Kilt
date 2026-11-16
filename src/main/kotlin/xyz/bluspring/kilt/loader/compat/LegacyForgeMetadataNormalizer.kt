package xyz.bluspring.kilt.loader.compat

import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.toml.TomlFormat
import net.neoforged.fml.FMLVersion
import org.slf4j.LoggerFactory
import xyz.bluspring.kilt.loader.Constants
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets

/**
 * Converts the loader-specific parts of a legacy Forge mods.toml descriptor
 * into the equivalent requirements for the NeoForge runtime bundled by Kilt.
 *
 * This is a generic loader-dialect conversion, not a Thermal-specific rule:
 * - the javafml language-provider range is rewritten to the runtime FML version;
 * - the legacy loader dependency "forge" is represented by the runtime mod "neoforge";
 * - legacy dependency "mandatory" flags become the modern "type" field.
 *
 * Minecraft version declarations and ordinary third-party dependencies are
 * intentionally left unchanged. Passing discovery does not imply that the
 * mod's Minecraft or Forge API calls are binary/behavior compatible.
 */
internal object LegacyForgeMetadataNormalizer {
    private val logger = LoggerFactory.getLogger(LegacyForgeMetadataNormalizer::class.java)

    fun normalize(sourceBytes: ByteArray, sourceName: String): ByteArray {
        val text = sourceBytes.toString(StandardCharsets.UTF_8)
        val config = TomlFormat.instance().createParser().parse(StringReader(text))
        var changed = false

        val modLoader = config.get<String>("modLoader")
        if (modLoader == "javafml") {
            val oldLoaderRange = config.get<String>("loaderVersion")
            if (oldLoaderRange != null) {
                val runtimeLoaderRange = "[${FMLVersion.getVersion()},)"
                if (oldLoaderRange != runtimeLoaderRange) {
                    config.set("loaderVersion", runtimeLoaderRange)
                    logger.info(
                        "Kilt: adapted legacy javafml loader range for {} from {} to {}",
                        sourceName,
                        oldLoaderRange,
                        runtimeLoaderRange
                    )
                    changed = true
                }
            }
        }

        val dependencyRoot = config.get<Config>("dependencies")
        if (dependencyRoot != null) {
            for (ownerEntry in dependencyRoot.entrySet()) {
                val rawTables = ownerEntry.value
                val tables: List<Config> = when (rawTables) {
                    is List<*> -> rawTables.mapNotNull { it as? Config }
                    is Config -> listOf(rawTables)
                    else -> emptyList()
                }

                for (dependency in tables) {
                    val legacyMandatory = dependency.get<Boolean>("mandatory")
                    if (legacyMandatory != null && dependency.get<String>("type") == null) {
                        dependency.set(
                            "type",
                            if (legacyMandatory) "required" else "optional"
                        )
                        dependency.remove("mandatory")
                        changed = true
                    }

                    if (dependency.get<String>("modId") == "forge") {
                        val oldRange = dependency.get<String>("versionRange")
                        val runtimeRange = "[${Constants.NEOFORGE_API_VERSION},)"
                        dependency.set("modId", "neoforge")
                        dependency.set("versionRange", runtimeRange)
                        logger.info(
                            "Kilt: mapped legacy Forge dependency in {} from range {} to neoforge {}",
                            sourceName,
                            oldRange ?: "(unbounded)",
                            runtimeRange
                        )
                        changed = true
                    }
                }
            }
        }

        if (!changed) return sourceBytes

        val writer = StringWriter()
        TomlFormat.instance().createWriter().write(config, writer)
        return writer.toString().toByteArray(StandardCharsets.UTF_8)
    }
}
