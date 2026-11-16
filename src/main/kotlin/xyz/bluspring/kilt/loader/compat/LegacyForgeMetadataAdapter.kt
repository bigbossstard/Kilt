package xyz.bluspring.kilt.loader.compat

import net.neoforged.fml.jarcontents.JarContents
import net.neoforged.fml.jarcontents.JarFileContents
import net.fabricmc.loader.api.FabricLoader
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader
import net.neoforged.neoforgespi.locating.IModFile
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes
import org.slf4j.LoggerFactory
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Makes legacy Forge's metadata filename visible to the NeoForge FML reader.
 *
 * This only bridges the discovery filename. It does not translate Forge API
 * classes, Forge dependency versions, Minecraft version constraints, or runtime
 * behavior. Those are separate compatibility layers.
 *
 * The source JAR is never modified. A deterministic, runtime-only copy is
 * written to the Kilt cache with META-INF/neoforge.mods.toml as an alias of
 * META-INF/mods.toml. Signature sidecars are omitted because the generated
 * copy is no longer byte-for-byte signed.
 */
object LegacyForgeMetadataAdapter {
    private const val LEGACY_METADATA = "META-INF/mods.toml"
    private const val NEOFORGE_METADATA = "META-INF/neoforge.mods.toml"
    private const val ADAPTER_REVISION = 1

    private val logger = LoggerFactory.getLogger(LegacyForgeMetadataAdapter::class.java)

    /**
     * Returns null for JARs that are not legacy Forge metadata JARs.
     *
     * For a legacy Forge JAR, delegates creation to the existing NeoForge FML
     * reader using a temporary normalized archive. The Mixin hook will be
     * entered again for that archive, but returns null because the NeoForge
     * metadata entry is then already present.
     */
    @JvmStatic
    fun createModFile(
        contents: JarContents,
        attributes: ModFileDiscoveryAttributes
    ): IModFile? {
        if (!contents.containsFile(LEGACY_METADATA)) return null
        if (contents.containsFile(NEOFORGE_METADATA)) return null

        val source = contents.getPrimaryPath()
        val normalized = normalizedArchive(source)

        logger.info(
            "Kilt: normalized legacy Forge metadata for {} (metadata only; API compatibility is still required)",
            source.fileName
        )

        return JarModsDotTomlModFileReader.createModFile(
            JarFileContents(normalized),
            attributes
        )
    }

    private fun normalizedArchive(source: Path): Path {
        require(Files.isRegularFile(source)) {
            "Legacy Forge metadata adapter expected a regular JAR file: $source"
        }

        val cacheDirectory = FabricLoader.getInstance().gameDir
            .resolve(".kilt")
            .resolve("legacy-forge-metadata")
        Files.createDirectories(cacheDirectory)

        val fingerprint = sha256(source)
        val target = cacheDirectory.resolve(
            "${fingerprint}-r${ADAPTER_REVISION}.jar"
        )

        if (isUsableNormalizedArchive(target)) return target

        val temporary = cacheDirectory.resolve(
            "${fingerprint}-r${ADAPTER_REVISION}.jar.tmp"
        )
        Files.deleteIfExists(temporary)

        try {
            JarFile(source.toFile()).use { input ->
                val metadataEntry = input.getJarEntry(LEGACY_METADATA)
                    ?: throw IllegalStateException("Missing $LEGACY_METADATA in $source")
                val metadataBytes = input.getInputStream(metadataEntry).use { it.readBytes() }

                JarOutputStream(Files.newOutputStream(temporary)).use { output ->
                    val copiedNames = HashSet<String>()

                    val entries = input.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!copiedNames.add(entry.name)) {
                            logger.warn(
                                "Kilt: skipped duplicate JAR entry {} while normalizing {}",
                                entry.name,
                                source.fileName
                            )
                            continue
                        }
                        if (isSignatureSidecar(entry.name)) continue

                        val copy = JarEntry(entry.name)
                        if (entry.time >= 0L) copy.time = entry.time
                        output.putNextEntry(copy)
                        if (!entry.isDirectory) {
                            input.getInputStream(entry).use { it.copyTo(output) }
                        }
                        output.closeEntry()
                    }

                    if (!copiedNames.contains(NEOFORGE_METADATA)) {
                        val alias = JarEntry(NEOFORGE_METADATA)
                        output.putNextEntry(alias)
                        output.write(metadataBytes)
                        output.closeEntry()
                    }
                }
            }

            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (failure: Throwable) {
            Files.deleteIfExists(temporary)
            throw IllegalStateException(
                "Kilt could not create a metadata-normalized copy of legacy Forge mod $source",
                failure
            )
        }

        return target
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

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte)
        }
    }
}
