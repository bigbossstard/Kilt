package xyz.bluspring.kilt.loader.compat

import net.neoforged.fml.jarcontents.JarContents
import net.neoforged.fml.jarcontents.JarResourceVisitor
import org.slf4j.LoggerFactory
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.HashSet
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Builds the runtime-only archive used to expose legacy Forge metadata to the
 * current FML reader.
 *
 * JarContents is deliberately used as the input abstraction. FML supplies
 * regular JARs in production, but may also supply folders or composites of
 * several roots in development. getPrimaryPath() is only an identifier for a
 * composite and must not be treated as the whole archive.
 */
internal object LegacyForgeArchiveNormalizer {
    private const val LEGACY_METADATA = "META-INF/mods.toml"
    private const val NEOFORGE_METADATA = "META-INF/neoforge.mods.toml"
    private const val ADAPTER_REVISION = 4

    private val logger = LoggerFactory.getLogger(LegacyForgeArchiveNormalizer::class.java)

    fun createNormalizedArchive(
        contents: JarContents,
        cacheDirectory: Path,
        sourceName: String
    ): Path {
        Files.createDirectories(cacheDirectory)

        // Stable JARs and unfiltered JAR composites expose a checksum. Folder
        // and filtered inputs intentionally do not, so they receive a unique
        // temporary archive rather than a stale persistent cache entry.
        val checksum = contents.checksum.orElse(null)
        val cachedTarget = checksum?.let {
            cacheDirectory.resolve(it + "-r" + ADAPTER_REVISION + ".jar")
        }

        if (cachedTarget != null && isUsableNormalizedArchive(cachedTarget)) {
            return cachedTarget
        }

        val temporary = Files.createTempFile(cacheDirectory, "legacy-forge-normalized-", ".jar")
        try {
            var normalizedMetadata: ByteArray? = null
            val copiedNames = HashSet<String>()

            JarOutputStream(Files.newOutputStream(temporary)).use { output ->
                contents.visitContent("", JarResourceVisitor { relativePath, resource ->
                    if (isSignatureSidecar(relativePath)) {
                        return@JarResourceVisitor
                    }

                    if (!copiedNames.add(relativePath)) {
                        logger.warn(
                            "Kilt: skipped duplicate resource {} while normalizing {}",
                            relativePath,
                            sourceName
                        )
                        return@JarResourceVisitor
                    }

                    output.putNextEntry(JarEntry(relativePath))

                    if (relativePath == LEGACY_METADATA) {
                        val originalMetadata = resource.readAllBytes()
                        normalizedMetadata = LegacyForgeMetadataNormalizer.normalize(
                            originalMetadata,
                            sourceName
                        )
                        // Keep the original Forge descriptor as well. The
                        // normalized alias is added under the NeoForge name.
                        output.write(originalMetadata)
                    } else {
                        resource.open().use { it.copyTo(output) }
                    }

                    output.closeEntry()
                })

                val metadataAlias = normalizedMetadata
                    ?: throw IllegalStateException(
                        "Legacy Forge metadata disappeared while normalizing " + sourceName
                    )

                if (copiedNames.contains(NEOFORGE_METADATA)) {
                    throw IllegalStateException(
                        "Cannot add a NeoForge metadata alias because " +
                            NEOFORGE_METADATA + " already exists in " + sourceName
                    )
                }

                output.putNextEntry(JarEntry(NEOFORGE_METADATA))
                output.write(metadataAlias)
                output.closeEntry()
            }

            // Inputs without a stable checksum (for example development
            // folders) must not reuse a cache entry across changing contents.
            if (cachedTarget == null) {
                temporary.toFile().deleteOnExit()
                return temporary
            }

            if (isUsableNormalizedArchive(cachedTarget)) {
                Files.deleteIfExists(temporary)
                return cachedTarget
            }

            try {
                Files.move(
                    temporary,
                    cachedTarget,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, cachedTarget, StandardCopyOption.REPLACE_EXISTING)
            }

            return cachedTarget
        } catch (failure: Throwable) {
            Files.deleteIfExists(temporary)
            throw failure
        }
    }

    private fun isUsableNormalizedArchive(path: Path): Boolean {
        if (!Files.isRegularFile(path)) return false

        return runCatching {
            JarFile(path.toFile()).use { it.getJarEntry(NEOFORGE_METADATA) != null }
        }.getOrDefault(false)
    }

    private fun isSignatureSidecar(entryName: String): Boolean {
        val upperName = entryName.uppercase(Locale.ROOT)
        if (!upperName.startsWith("META-INF/")) return false

        val leaf = upperName.substringAfterLast('/')
        return leaf.endsWith(".SF") ||
            leaf.endsWith(".RSA") ||
            leaf.endsWith(".DSA") ||
            leaf.endsWith(".EC") ||
            leaf.startsWith("SIG-")
    }
}
