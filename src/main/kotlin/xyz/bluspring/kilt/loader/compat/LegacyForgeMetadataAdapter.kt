package xyz.bluspring.kilt.loader.compat

import net.fabricmc.loader.api.FabricLoader
import net.neoforged.fml.FMLVersion
import net.neoforged.fml.jarcontents.JarContents
import net.neoforged.fml.jarcontents.JarFileContents
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader
import net.neoforged.neoforgespi.locating.IModFile
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
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
 * Adapts legacy Forge metadata for the NeoForge FML reader.
 *
 * The source JAR is never modified. A runtime-only cached copy is created with:
 * - META-INF/neoforge.mods.toml aliased from META-INF/mods.toml;
 * - legacy dependency mandatory flags converted to FML dependency types;
 * - the old Forge loader range updated to the embedded FML version;
 * - the old forge dependency mapped to neoforge;
 * - the old Minecraft version constraint mapped to the current runtime.
 *
 * These are metadata compatibility overrides, not proof that old bytecode or
 * Minecraft APIs are compatible. Every cross-loader/version override is logged.
 */
object LegacyForgeMetadataAdapter {
    private const val LEGACY_METADATA = "META-INF/mods.toml"
    private const val NEOFORGE_METADATA = "META-INF/neoforge.mods.toml"
    private const val ADAPTER_REVISION = 2

    private val logger = LoggerFactory.getLogger(LegacyForgeMetadataAdapter::class.java)

    private val tableHeader = Regex("""^\s*\[{1,2}[^\[\]]+\]{1,2}\s*(?:#.*)?$""")
    private val dependencyHeader = Regex("""^\s*\[\[dependencies\.([^\]]+)]]\s*(?:#.*)?$""")
    private val quotedValue = Regex("""^(\s*([A-Za-z0-9_-]+)\s*=\s*)(['"])(.*?)\3(\s*(?:#.*)?)$""")
    private val mandatoryValue = Regex("""^(\s*)mandatory(\s*=\s*)(true|false)(\s*(?:#.*)?)$""", RegexOption.IGNORE_CASE)
    private val referralUrlKey = Regex("""^(\s*)referralURL(?=\s*=)(.*)$""")

    private data class RuntimeVersions(
        val minecraft: String,
        val fml: String
    )

    private data class NormalizedMetadata(
        val bytes: ByteArray,
        val changes: List<String>
    )

    /**
     * Returns null for JARs that are not legacy Forge metadata JARs.
     *
     * The hook is entered again for the normalized archive, but returns null
     * because that archive already contains the NeoForge metadata entry.
     */
    @JvmStatic
    fun createModFile(
        contents: JarContents,
        attributes: ModFileDiscoveryAttributes
    ): IModFile? {
        if (!contents.containsFile(LEGACY_METADATA)) return null
        if (contents.containsFile(NEOFORGE_METADATA)) return null

        val source = contents.getPrimaryPath()
        val (normalized, changes) = normalizedArchive(source)

        logger.warn(
            "Kilt: adapting legacy Forge metadata from {}. Overrides: {}. " +
                "This does not imply that the mod's Minecraft or Forge API calls are compatible.",
            source.fileName,
            changes.ifEmpty { listOf("metadata filename only") }.joinToString("; ")
        )

        return JarModsDotTomlModFileReader.createModFile(
            JarFileContents(normalized),
            attributes
        )
    }

    private fun normalizedArchive(source: Path): Pair<Path, List<String>> {
        require(Files.isRegularFile(source)) {
            "Legacy Forge metadata adapter expected a regular JAR file: $source"
        }

        val runtime = RuntimeVersions(
            minecraft = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .orElseThrow()
                .metadata.version
                .friendlyString,
            fml = FMLVersion.getVersion()
        )

        val cacheDirectory = FabricLoader.getInstance().gameDir
            .resolve(".kilt")
            .resolve("legacy-forge-metadata")
        Files.createDirectories(cacheDirectory)

        // The same source JAR needs different metadata constraints on different
        // Minecraft/FML targets, so include both runtime versions in the cache key.
        val runtimeFingerprint = sha256(
            "${runtime.minecraft}:${runtime.fml}".toByteArray(StandardCharsets.UTF_8)
        )
        val fingerprint = "${sha256(source)}-$runtimeFingerprint"
        val target = cacheDirectory.resolve("${fingerprint}-r${ADAPTER_REVISION}.jar")

        JarFile(source.toFile()).use { input ->
            val metadataEntry = input.getJarEntry(LEGACY_METADATA)
                ?: throw IllegalStateException("Missing $LEGACY_METADATA in $source")
            val originalMetadata = input.getInputStream(metadataEntry).use { it.readBytes() }
            val normalizedMetadata = normalizeLegacyMetadata(
                originalMetadata.toString(StandardCharsets.UTF_8),
                runtime
            )

            if (isUsableNormalizedArchive(target)) {
                return target to normalizedMetadata.changes
            }

            val temporary = cacheDirectory.resolve("${fingerprint}-r${ADAPTER_REVISION}.jar.tmp")
            Files.deleteIfExists(temporary)

            try {
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
                            if (entry.name == LEGACY_METADATA) {
                                output.write(normalizedMetadata.bytes)
                            } else {
                                input.getInputStream(entry).use { it.copyTo(output) }
                            }
                        }
                        output.closeEntry()
                    }

                    if (!copiedNames.contains(NEOFORGE_METADATA)) {
                        val alias = JarEntry(NEOFORGE_METADATA)
                        output.putNextEntry(alias)
                        output.write(normalizedMetadata.bytes)
                        output.closeEntry()
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

            return target to normalizedMetadata.changes
        }
    }

    /**
     * Converts the legacy Forge fields that differ in the FML version embedded
     * by Kilt. This is deliberately limited to loader/dependency metadata.
     */
    private fun normalizeLegacyMetadata(
        source: String,
        runtime: RuntimeVersions
    ): NormalizedMetadata {
        val changes = linkedSetOf<String>()
        val output = mutableListOf<String>()
        var dependencyLines: MutableList<String>? = null
        var seenTable = false
        val hadTrailingNewline = source.endsWith("\n")
        val lines = source.removeSuffix("\n").split("\n")

        fun flushDependencyBlock() {
            val block = dependencyLines ?: return
            output.addAll(normalizeDependencyBlock(block, runtime, changes))
            dependencyLines = null
        }

        for (line in lines) {
            if (tableHeader.matches(line)) {
                flushDependencyBlock()
                seenTable = true
                if (dependencyHeader.matches(line)) {
                    dependencyLines = mutableListOf(line)
                } else {
                    output.add(line)
                }
                continue
            }

            val currentBlock = dependencyLines
            if (currentBlock != null) {
                currentBlock.add(line)
            } else if (!seenTable) {
                val loaderMatch = quotedValue.matchEntire(line)
                if (loaderMatch != null && loaderMatch.groupValues[2] == "loaderVersion") {
                    val oldRange = loaderMatch.groupValues[4]
                    val newRange = "[${runtime.fml},)"
                    output.add(replaceQuotedValue(line, newRange))
                    if (oldRange != newRange) {
                        changes.add("javafml loaderVersion $oldRange -> $newRange")
                    }
                } else {
                    output.add(line)
                }
            } else {
                output.add(line)
            }
        }
        flushDependencyBlock()

        val normalized = output.joinToString("\n") + if (hadTrailingNewline) "\n" else ""
        return NormalizedMetadata(normalized.toByteArray(StandardCharsets.UTF_8), changes.toList())
    }

    private fun normalizeDependencyBlock(
        lines: List<String>,
        runtime: RuntimeVersions,
        changes: MutableSet<String>
    ): List<String> {
        val modId = lines.firstNotNullOfOrNull { line ->
            quotedValue.matchEntire(line)
                ?.takeIf { it.groupValues[2] == "modId" }
                ?.groupValues
                ?.get(4)
        }

        val hasType = lines.any { line ->
            quotedValue.matchEntire(line)?.groupValues?.get(2) == "type"
        }

        val output = mutableListOf<String>()
        for (line in lines) {
            val mandatory = mandatoryValue.matchEntire(line)
            if (mandatory != null) {
                if (!hasType) {
                    val type = if (mandatory.groupValues[3].equals("false", ignoreCase = true)) {
                        "optional"
                    } else {
                        "required"
                    }
                    output.add(
                        mandatory.groupValues[1] +
                            "type" +
                            mandatory.groupValues[2] +
                            "\"$type\"" +
                            mandatory.groupValues[4]
                    )
                    changes.add("dependency $modId: mandatory -> type=$type")
                } else if (mandatory.groupValues[4].contains("#")) {
                    output.add(mandatory.groupValues[1] + mandatory.groupValues[4].trimStart())
                }
                continue
            }

            val referral = referralUrlKey.matchEntire(line)
            if (referral != null) {
                output.add(referral.groupValues[1] + "referralUrl" + referral.groupValues[2])
                changes.add("dependency $modId: referralURL -> referralUrl")
                continue
            }

            val value = quotedValue.matchEntire(line)
            if (value == null) {
                output.add(line)
                continue
            }

            val key = value.groupValues[2]
            when {
                key == "modId" && modId == "forge" -> {
                    output.add(replaceQuotedValue(line, "neoforge"))
                    changes.add("dependency forge -> neoforge")
                }

                key == "versionRange" && modId == "forge" -> {
                    // Forge 47.x and NeoForge use unrelated version schemes.
                    // Keep the NeoForge dependency, but do not compare those ranges.
                    output.add(replaceQuotedValue(line, " "))
                    changes.add("dependency forge versionRange -> unbounded NeoForge runtime")
                }

                key == "versionRange" && modId == "minecraft" -> {
                    val targetRange = "[${runtime.minecraft}]"
                    output.add(replaceQuotedValue(line, targetRange))
                    changes.add("dependency minecraft versionRange -> $targetRange")
                }

                else -> output.add(line)
            }
        }

        return output
    }

    private fun replaceQuotedValue(line: String, replacement: String): String {
        val match = quotedValue.matchEntire(line) ?: return line
        return match.groupValues[1] +
            match.groupValues[3] +
            replacement +
            match.groupValues[3] +
            match.groupValues[5]
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

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { byte ->
            "%02x".format(byte)
        }
    }
}
