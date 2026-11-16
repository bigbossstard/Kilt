package xyz.bluspring.kilt.loader.compat

import net.fabricmc.loader.api.FabricLoader
import net.neoforged.fml.jarcontents.JarContents
import net.neoforged.fml.jarcontents.JarFileContents
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader
import net.neoforged.neoforgespi.locating.IModFile
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Makes legacy Forge's metadata filename visible to the NeoForge FML reader.
 *
 * This bridges the discovery filename and the legacy loader-specific fields.
 * It does not translate Forge API classes, Minecraft version constraints, or
 * runtime behavior; those are separate compatibility layers.
 *
 * The source JAR/directory/composite is never modified. A runtime-only cached
 * archive is created with META-INF/neoforge.mods.toml as an alias of the
 * normalized META-INF/mods.toml descriptor.
 */
object LegacyForgeMetadataAdapter {
    private const val LEGACY_METADATA = "META-INF/mods.toml"
    private const val NEOFORGE_METADATA = "META-INF/neoforge.mods.toml"

    private val logger = LoggerFactory.getLogger(LegacyForgeMetadataAdapter::class.java)

    /**
     * Returns null for inputs that are not legacy-Forge-only metadata inputs.
     *
     * For a legacy Forge input, delegates creation to the existing NeoForge
     * FML reader using a temporary normalized archive. The Mixin hook is entered
     * again for that archive, but returns null because the NeoForge metadata
     * entry is then already present.
     */
    @JvmStatic
    fun createModFile(
        contents: JarContents,
        attributes: ModFileDiscoveryAttributes
    ): IModFile? {
        if (!contents.containsFile(LEGACY_METADATA)) return null
        if (contents.containsFile(NEOFORGE_METADATA)) return null

        val source = contents.getPrimaryPath()

        try {
            val normalized = normalizedArchive(contents, source)
            val normalizedContents = JarFileContents(normalized)

            logger.info(
                "Kilt: normalized legacy Forge metadata for {} (loader metadata adapted; Minecraft/API compatibility is still required)",
                source.fileName
            )

            try {
                val modFile = JarModsDotTomlModFileReader.createModFile(
                    normalizedContents,
                    attributes
                )

                // The FML reader normally transfers ownership of JarContents to
                // the returned ModFile. If it declines the archive, close it here.
                if (modFile == null) {
                    closeQuietly(normalizedContents, "normalized archive " + normalized)
                }
                return modFile
            } catch (failure: Throwable) {
                closeQuietly(normalizedContents, "normalized archive for " + source)
                throw failure
            }
        } catch (failure: Throwable) {
            throw IllegalStateException(
                "Kilt could not create a metadata-normalized copy of legacy Forge mod " + source,
                failure
            )
        } finally {
            // The returned ModFile owns the normalized JarContents, not the
            // original contents passed to the mixin. Close the original here
            // so discovery does not leak an open JarFile for every adapted mod.
            closeQuietly(contents, "original archive " + source)
        }
    }

    private fun closeQuietly(contents: JarContents, description: String) {
        runCatching { contents.close() }
            .onFailure { failure ->
                logger.warn("Kilt: failed to close " + description + " after metadata normalization", failure)
            }
    }

    private fun normalizedArchive(contents: JarContents, source: Path): Path {
        val cacheDirectory = FabricLoader.getInstance().gameDir
            .resolve(".kilt")
            .resolve("legacy-forge-metadata")

        return LegacyForgeArchiveNormalizer.createNormalizedArchive(
            contents,
            cacheDirectory,
            source.fileName?.toString() ?: source.toString()
        )
    }
}
