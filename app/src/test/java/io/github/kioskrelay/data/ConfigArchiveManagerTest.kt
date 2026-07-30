package io.github.kioskrelay.data

import io.github.kioskrelay.config.ConfigDefaults
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigArchiveManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val manager = ConfigArchiveManager()

    @Test
    fun exportAndImport_roundTripsConfigAndAssetsWithoutCredentials() {
        val exportAssets = temporaryFolder.newFolder("export")
        val importAssets = temporaryFolder.newFolder("import")
        val logoBytes = minimalWebp()
        File(exportAssets, ConfigArchiveManager.LOGO_FILE).writeBytes(logoBytes)
        val config = configured().copy(
            branding = configured().branding.copy(
                logoRelativePath = ConfigArchiveManager.LOGO_FILE,
            ),
        )
        val output = ByteArrayOutputStream()

        manager.export(config, exportAssets, output)
        val archiveBytes = output.toByteArray()
        val entryNames = readEntryNames(archiveBytes)

        assertEquals(
            setOf(ConfigArchiveManager.CONFIG_ENTRY, ConfigArchiveManager.LOGO_ENTRY),
            entryNames,
        )
        assertFalse(archiveBytes.toString(Charsets.ISO_8859_1).contains("password"))

        val imported = manager.import(ByteArrayInputStream(archiveBytes), importAssets)
        assertEquals(config, imported.config)
        assertEquals(setOf(ConfigArchiveManager.LOGO_FILE), imported.importedAssetPaths)
        assertArrayEquals(
            logoBytes,
            File(importAssets, ConfigArchiveManager.LOGO_FILE).readBytes(),
        )
    }

    @Test
    fun import_removesManagedAssetsThatAreMissingFromArchive() {
        val assetRoot = temporaryFolder.newFolder("assets")
        val oldLogo = File(assetRoot, ConfigArchiveManager.LOGO_FILE).apply {
            writeBytes(minimalWebp())
        }
        val oldSplash = File(assetRoot, ConfigArchiveManager.SPLASH_FILE).apply {
            writeBytes(minimalWebp())
        }
        val replacementLogo = minimalWebp(width = 2, height = 2)
        val archive = zipOf(
            ConfigArchiveManager.CONFIG_ENTRY to
                ConfigJsonCodec.encode(configured()).toByteArray(),
            ConfigArchiveManager.LOGO_ENTRY to replacementLogo,
        )

        val imported = manager.import(ByteArrayInputStream(archive), assetRoot)

        assertArrayEquals(replacementLogo, oldLogo.readBytes())
        assertFalse(oldSplash.exists())
        assertEquals(ConfigArchiveManager.LOGO_FILE, imported.config.branding.logoRelativePath)
        assertEquals(null, imported.config.branding.splashRelativePath)
    }

    @Test
    fun import_rejectsPathTraversal() {
        val archive = zipOf("../config.json" to "{}".toByteArray())

        val exception = expectArchiveFailure {
            manager.import(ByteArrayInputStream(archive), temporaryFolder.newFolder("assets"))
        }

        assertEquals(ArchiveError.UNSAFE_ENTRY_PATH, exception.error)
    }

    @Test
    fun import_rejectsUnknownEntries() {
        val archive = zipOf("payload.bin" to byteArrayOf(1, 2, 3))

        val exception = expectArchiveFailure {
            manager.import(ByteArrayInputStream(archive), temporaryFolder.newFolder("assets"))
        }

        assertEquals(ArchiveError.UNKNOWN_ENTRY, exception.error)
    }

    @Test
    fun import_rejectsUnsupportedSchema() {
        val json = """{"schemaVersion":2}""".toByteArray()
        val validWebpHeader = minimalWebp()
        val archive = zipOf(
            ConfigArchiveManager.CONFIG_ENTRY to json,
            ConfigArchiveManager.LOGO_ENTRY to validWebpHeader,
        )
        val assetRoot = temporaryFolder.newFolder("assets")

        val exception = expectArchiveFailure {
            manager.import(ByteArrayInputStream(archive), assetRoot)
        }

        assertEquals(ArchiveError.UNSUPPORTED_SCHEMA, exception.error)
        assertFalse(File(assetRoot, ConfigArchiveManager.LOGO_FILE).exists())
    }

    @Test
    fun import_validatesEveryAssetBeforeWritingAnyAsset() {
        val validWebpHeader = minimalWebp()
        val archive = zipOf(
            ConfigArchiveManager.CONFIG_ENTRY to
                ConfigJsonCodec.encode(configured()).toByteArray(),
            ConfigArchiveManager.LOGO_ENTRY to validWebpHeader,
            ConfigArchiveManager.SPLASH_ENTRY to "not-an-image".toByteArray(),
        )
        val assetRoot = temporaryFolder.newFolder("assets")

        val exception = expectArchiveFailure {
            manager.import(ByteArrayInputStream(archive), assetRoot)
        }

        assertEquals(ArchiveError.INVALID_ASSET, exception.error)
        assertFalse(File(assetRoot, ConfigArchiveManager.LOGO_FILE).exists())
    }

    @Test
    fun import_rejectsOversizedConfigEntry() {
        val oversized = ByteArray(ConfigArchiveManager.MAX_CONFIG_BYTES.toInt() + 1)
        val archive = zipOf(ConfigArchiveManager.CONFIG_ENTRY to oversized)

        val exception = expectArchiveFailure {
            manager.import(ByteArrayInputStream(archive), temporaryFolder.newFolder("assets"))
        }

        assertEquals(ArchiveError.ENTRY_TOO_LARGE, exception.error)
    }

    @Test
    fun import_rejectsWebpCanvasLargerThanManagedLogoLimit() {
        val archive = zipOf(
            ConfigArchiveManager.CONFIG_ENTRY to
                ConfigJsonCodec.encode(configured()).toByteArray(),
            ConfigArchiveManager.LOGO_ENTRY to minimalWebp(width = 2_048, height = 1),
        )

        val exception = expectArchiveFailure {
            manager.import(ByteArrayInputStream(archive), temporaryFolder.newFolder("assets"))
        }

        assertEquals(ArchiveError.INVALID_ASSET, exception.error)
    }

    private fun configured() = ConfigDefaults.config.copy(
        onboardingCompleted = true,
        webView = ConfigDefaults.config.webView.copy(
            initialUrl = "https://display.example.com/",
            allowedOrigins = setOf("https://display.example.com"),
        ),
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun minimalWebp(width: Int = 1, height: Int = 1): ByteArray {
        require(width > 0 && height > 0)
        return ByteArray(30).apply {
            "RIFF".toByteArray().copyInto(this, destinationOffset = 0)
            this[4] = 22
            "WEBP".toByteArray().copyInto(this, destinationOffset = 8)
            "VP8X".toByteArray().copyInto(this, destinationOffset = 12)
            this[16] = 10
            writeUInt24Le(offset = 24, value = width - 1)
            writeUInt24Le(offset = 27, value = height - 1)
        }
    }

    private fun ByteArray.writeUInt24Le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
        this[offset + 2] = (value ushr 16).toByte()
    }

    private fun readEntryNames(bytes: ByteArray): Set<String> {
        val names = linkedSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                zip.closeEntry()
            }
        }
        return names
    }

    private fun expectArchiveFailure(block: () -> Unit): ConfigArchiveException {
        return try {
            block()
            throw AssertionError("Expected ConfigArchiveException")
        } catch (exception: ConfigArchiveException) {
            exception
        }
    }
}
