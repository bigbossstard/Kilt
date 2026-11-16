package xyz.bluspring.kilt.loader.compat

import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.toml.TomlFormat
import net.neoforged.fml.FMLVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xyz.bluspring.kilt.loader.Constants
import java.io.StringReader

class LegacyForgeMetadataNormalizerTest {
    @Test
    fun `legacy JavaFML version and Forge runtime dependency are mapped to current runtime`() {
        val original = """
            modLoader = "javafml"
            loaderVersion = "[47,)"
            license = "Test"

            [[mods]]
            modId = "sample_legacy"
            version = "1.0"

            [[dependencies.sample_legacy]]
            modId = "forge"
            mandatory = true
            versionRange = "[47.1.3,)"
            ordering = "AFTER"
            side = "BOTH"

            [[dependencies.sample_legacy]]
            modId = "minecraft"
            mandatory = true
            versionRange = "1.20.1"
            ordering = "AFTER"
            side = "BOTH"
        """.trimIndent().toByteArray()

        val normalized = LegacyForgeMetadataNormalizer.normalize(original, "sample_legacy.jar")
        val parsed = TomlFormat.instance().createParser().parse(StringReader(normalized.toString(Charsets.UTF_8)))

        assertEquals("[${FMLVersion.getVersion()},)", parsed.get<String>("loaderVersion"))

        val dependencies = parsed.get<Config>("dependencies")!!
        val tables = dependencies.get<List<*>>("sample_legacy")!!.filterIsInstance<Config>()

        val neoForge = tables.single { it.get<String>("modId") == "neoforge" }
        assertEquals("[${Constants.NEOFORGE_API_VERSION},)", neoForge.get<String>("versionRange"))
        assertEquals("required", neoForge.get<String>("type"))
        assertFalse(neoForge.contains("mandatory"))

        val minecraft = tables.single { it.get<String>("modId") == "minecraft" }
        assertEquals("1.20.1", minecraft.get<String>("versionRange"))
        assertEquals("required", minecraft.get<String>("type"))
    }

    @Test
    fun `legacy optional dependency becomes modern optional type`() {
        val original = """
            modLoader = "javafml"
            license = "Test"

            [[mods]]
            modId = "sample_optional"
            version = "1.0"

            [[dependencies.sample_optional]]
            modId = "optional_lib"
            mandatory = false
            versionRange = "[2,)"
            ordering = "AFTER"
            side = "BOTH"
        """.trimIndent().toByteArray()

        val normalized = LegacyForgeMetadataNormalizer.normalize(original, "sample_optional.jar")
        val parsed = TomlFormat.instance().createParser().parse(StringReader(normalized.toString(Charsets.UTF_8)))
        val dependencies = parsed.get<Config>("dependencies")!!
        val optional = dependencies.get<List<*>>("sample_optional")!!
            .filterIsInstance<Config>()
            .single()

        assertEquals("optional", optional.get<String>("type"))
        assertFalse(optional.contains("mandatory"))
        assertEquals("[2,)", optional.get<String>("versionRange"))
    }

    @Test
    fun `non JavaFML language version range is not rewritten`() {
        val original = """
            modLoader = "some_other_loader"
            loaderVersion = "[2,)"
            license = "Test"

            [[mods]]
            modId = "sample_other"
            version = "1.0"
        """.trimIndent().toByteArray()

        val normalized = LegacyForgeMetadataNormalizer.normalize(original, "sample_other.jar")
        val parsed = TomlFormat.instance().createParser().parse(StringReader(normalized.toString(Charsets.UTF_8)))

        assertEquals("[2,)", parsed.get<String>("loaderVersion"))
        assertTrue(normalized.isNotEmpty())
    }

    @Test
    fun `descriptor without compatibility fields is left byte for byte unchanged`() {
        val original = """
            modLoader = "javafml"
            license = "Test"

            [[mods]]
            modId = "sample_plain"
            version = "1.0"
        """.trimIndent().toByteArray()

        val normalized = LegacyForgeMetadataNormalizer.normalize(original, "sample_plain.jar")

        assertTrue(original.contentEquals(normalized))
    }
}
