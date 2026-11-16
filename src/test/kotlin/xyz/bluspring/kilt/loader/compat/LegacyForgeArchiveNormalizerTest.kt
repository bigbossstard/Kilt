package xyz.bluspring.kilt.loader.compat

import net.neoforged.fml.jarcontents.JarContents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class LegacyForgeArchiveNormalizerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val legacyMetadata = """
        modLoader = "javafml"
        loaderVersion = "[47,)"
        license = "Test"

        [[mods]]
        modId = "generic_test"
        version = "1.0"

        [[dependencies.generic_test]]
        modId = "forge"
        mandatory = true
        versionRange = "[47,)"
        ordering = "NONE"
        side = "BOTH"
    """.trimIndent()

    @Test
    fun regularJarIsNormalizedWithoutChangingSourceOrRetainingSignatures() {
        val source = temporaryDirectory.resolve("legacy-mod.jar")
        writeJar(
            source,
            mapOf(
                "META-INF/mods.toml" to legacyMetadata,
                "META-INF/TEST.SF" to "signature",
                "META-INF/TEST.RSA" to "signature-block",
                "assets/generic_test/payload.txt" to "payload"
            )
        )
        val sourceHashBefore = sha256(source)
        val cache = temporaryDirectory.resolve("cache")

        val normalized = JarContents.ofPath(source).use { contents ->
            LegacyForgeArchiveNormalizer.createNormalizedArchive(
                contents,
                cache,
                "legacy-mod.jar"
            )
        }

        assertNotEquals(source, normalized)
        assertEquals(sourceHashBefore, sha256(source))

        JarFile(normalized.toFile()).use { jar ->
            assertNotNull(jar.getEntry("META-INF/mods.toml"))
            assertNotNull(jar.getEntry("META-INF/neoforge.mods.toml"))
            assertNull(jar.getEntry("META-INF/TEST.SF"))
            assertNull(jar.getEntry("META-INF/TEST.RSA"))
            assertEquals(
                "payload",
                jar.getInputStream(jar.getEntry("assets/generic_test/payload.txt"))
                    .bufferedReader().use { it.readText() }
            )

            val normalizedMetadata = jar.getInputStream(
                jar.getEntry("META-INF/neoforge.mods.toml")
            ).bufferedReader().use { it.readText() }

            assertTrue(normalizedMetadata.contains("modId = \"neoforge\""))
            assertTrue(normalizedMetadata.contains("type = \"required\""))
        }
    }

    @Test
    fun developmentFolderInputIsSupported() {
        val source = temporaryDirectory.resolve("classes")
        Files.createDirectories(source.resolve("META-INF"))
        Files.createDirectories(source.resolve("assets/generic_test"))
        Files.writeString(source.resolve("META-INF/mods.toml"), legacyMetadata)
        Files.writeString(source.resolve("assets/generic_test/payload.txt"), "folder-payload")

        val normalized = JarContents.ofPath(source).use { contents ->
            LegacyForgeArchiveNormalizer.createNormalizedArchive(
                contents,
                temporaryDirectory.resolve("folder-cache"),
                "classes"
            )
        }

        JarFile(normalized.toFile()).use { jar ->
            assertNotNull(jar.getEntry("META-INF/neoforge.mods.toml"))
            assertEquals(
                "folder-payload",
                jar.getInputStream(jar.getEntry("assets/generic_test/payload.txt"))
                    .bufferedReader().use { it.readText() }
            )
        }
    }

    @Test
    fun compositeInputRespectsLaterOverlayRoots() {
        val base = temporaryDirectory.resolve("base")
        val overlay = temporaryDirectory.resolve("overlay")
        Files.createDirectories(base.resolve("META-INF"))
        Files.createDirectories(base.resolve("assets/generic_test"))
        Files.createDirectories(overlay.resolve("assets/generic_test"))
        Files.writeString(base.resolve("META-INF/mods.toml"), legacyMetadata)
        Files.writeString(base.resolve("assets/generic_test/value.txt"), "base")
        Files.writeString(overlay.resolve("assets/generic_test/value.txt"), "overlay")

        val composite = JarContents.ofPaths(listOf(base, overlay))
        val normalized = composite.use { contents ->
            LegacyForgeArchiveNormalizer.createNormalizedArchive(
                contents,
                temporaryDirectory.resolve("composite-cache"),
                "composite test input"
            )
        }

        JarFile(normalized.toFile()).use { jar ->
            assertEquals(
                "overlay",
                jar.getInputStream(jar.getEntry("assets/generic_test/value.txt"))
                    .bufferedReader().use { it.readText() }
            )
            assertNotNull(jar.getEntry("META-INF/neoforge.mods.toml"))
        }
    }

    @Test
    fun stableJarChecksumReusesNormalizedCacheEntry() {
        val source = temporaryDirectory.resolve("cache-test.jar")
        writeJar(source, mapOf("META-INF/mods.toml" to legacyMetadata))
        val cache = temporaryDirectory.resolve("cache-test")

        val first = JarContents.ofPath(source).use { contents ->
            LegacyForgeArchiveNormalizer.createNormalizedArchive(contents, cache, "cache-test.jar")
        }
        val second = JarContents.ofPath(source).use { contents ->
            LegacyForgeArchiveNormalizer.createNormalizedArchive(contents, cache, "cache-test.jar")
        }

        assertEquals(first, second)
        assertTrue(Files.isRegularFile(first))
    }

    private fun writeJar(path: Path, entries: Map<String, String>) {
        Files.createDirectories(path.parent)
        JarOutputStream(Files.newOutputStream(path)).use { output ->
            for ((name, content) in entries) {
                output.putNextEntry(JarEntry(name))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
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
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
