package io.github.kioskrelay.data

import io.github.kioskrelay.config.ConfigRules
import io.github.kioskrelay.config.KioskRelayConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ArchiveError {
    ARCHIVE_TOO_LARGE,
    ENTRY_TOO_LARGE,
    DUPLICATE_ENTRY,
    MISSING_CONFIG,
    UNKNOWN_ENTRY,
    UNSAFE_ENTRY_PATH,
    INVALID_ARCHIVE,
    INVALID_CONFIG,
    UNSUPPORTED_SCHEMA,
    INVALID_ASSET,
    ASSET_WRITE_FAILED,
}

class ConfigArchiveException(
    val error: ArchiveError,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

data class ImportedConfigArchive(
    val config: KioskRelayConfig,
    val importedAssetPaths: Set<String>,
)

/**
 * Reads and writes the portable `.kioskrelay` ZIP format. Administrator credentials,
 * cookies, WebView data and diagnostics are deliberately not represented by this API.
 */
class ConfigArchiveManager {
    fun export(
        config: KioskRelayConfig,
        assetRoot: File,
        output: OutputStream,
    ) {
        val safe = ConfigRules.validate(ConfigRules.canonicalize(config))
        val logo = safe.branding.logoRelativePath?.let {
            validateExportAsset(assetRoot, it)
        }
        val splash = safe.branding.splashRelativePath?.let {
            validateExportAsset(assetRoot, it)
        }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putBytes(CONFIG_ENTRY, ConfigJsonCodec.encode(safe).toByteArray(Charsets.UTF_8))
            logo?.let { addAsset(zip, it, LOGO_ENTRY) }
            splash?.let { addAsset(zip, it, SPLASH_ENTRY) }
        }
    }

    fun import(
        input: InputStream,
        assetRoot: File,
    ): ImportedConfigArchive {
        val entries = linkedMapOf<String, ByteArray>()
        val seen = hashSetOf<String>()
        var totalUncompressedBytes = 0L

        try {
            ZipInputStream(
                BufferedInputStream(LimitedInputStream(input, MAX_ARCHIVE_BYTES)),
            ).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = validateEntry(entry)
                    if (!seen.add(name)) {
                        throw ConfigArchiveException(
                            ArchiveError.DUPLICATE_ENTRY,
                            "Duplicate archive entry: $name",
                        )
                    }
                    val entryLimit = if (name == CONFIG_ENTRY) {
                        MAX_CONFIG_BYTES
                    } else {
                        MAX_ASSET_BYTES
                    }
                    if (entry.size > entryLimit) {
                        throw ConfigArchiveException(
                            ArchiveError.ENTRY_TOO_LARGE,
                            "Archive entry is too large: $name",
                        )
                    }
                    val bytes = readEntry(zip, entryLimit)
                    totalUncompressedBytes += bytes.size
                    if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw ConfigArchiveException(
                            ArchiveError.ARCHIVE_TOO_LARGE,
                            "Expanded archive is too large",
                        )
                    }
                    entries[name] = bytes
                    zip.closeEntry()
                }
            }
        } catch (exception: ConfigArchiveException) {
            throw exception
        } catch (exception: IOException) {
            throw ConfigArchiveException(
                ArchiveError.INVALID_ARCHIVE,
                "Unable to read configuration archive",
                exception,
            )
        }

        val configBytes = entries[CONFIG_ENTRY]
            ?: throw ConfigArchiveException(
                ArchiveError.MISSING_CONFIG,
                "Archive does not contain config.json",
            )
        val decoded = try {
            ConfigJsonCodec.decode(configBytes.toString(Charsets.UTF_8))
        } catch (exception: ConfigArchiveException) {
            throw exception
        } catch (exception: Exception) {
            throw ConfigArchiveException(
                ArchiveError.INVALID_CONFIG,
                "config.json is invalid",
                exception,
            )
        }

        val assetsToInstall = linkedMapOf<String, ByteArray>()
        entries[LOGO_ENTRY]?.let { assetsToInstall[LOGO_FILE] = it }
        entries[SPLASH_ENTRY]?.let { assetsToInstall[SPLASH_FILE] = it }
        assetsToInstall.forEach { (path, bytes) ->
            if (!isSafeWebpAsset(path, bytes)) {
                throw ConfigArchiveException(
                    ArchiveError.INVALID_ASSET,
                    "Imported asset is not a safe WebP image: $path",
                )
            }
        }
        installAssetsAtomically(assetRoot, assetsToInstall)

        val importedPaths = assetsToInstall.keys.toSet()
        val logoPath = LOGO_FILE.takeIf(importedPaths::contains)
        val splashPath = SPLASH_FILE.takeIf(importedPaths::contains)

        val imported = decoded.copy(
            branding = decoded.branding.copy(
                logoRelativePath = logoPath,
                splashRelativePath = splashPath,
            ),
        )
        return ImportedConfigArchive(
            config = ConfigRules.validate(ConfigRules.canonicalize(imported)),
            importedAssetPaths = importedPaths,
        )
    }

    private fun validateEntry(entry: ZipEntry): String {
        val name = entry.name
        if (
            name.isBlank() ||
            name.startsWith('/') ||
            name.contains('\\') ||
            name.split('/').any { it == "." || it == ".." }
        ) {
            throw ConfigArchiveException(
                ArchiveError.UNSAFE_ENTRY_PATH,
                "Unsafe archive entry path",
            )
        }
        if (entry.isDirectory || name !in ALLOWED_ENTRIES) {
            throw ConfigArchiveException(
                ArchiveError.UNKNOWN_ENTRY,
                "Unknown archive entry: $name",
            )
        }
        return name
    }

    private fun readEntry(input: InputStream, maximumBytes: Long): ByteArray {
        val initialCapacity = maximumBytes.coerceAtMost(16 * 1_024).toInt()
        val output = ByteArrayOutputStream(initialCapacity)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > maximumBytes) {
                throw ConfigArchiveException(
                    ArchiveError.ENTRY_TOO_LARGE,
                    "Archive entry exceeds its size limit",
                )
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun addAsset(
        zip: ZipOutputStream,
        asset: File,
        archivePath: String,
    ) {
        zip.putNextEntry(newEntry(archivePath))
        asset.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun validateExportAsset(assetRoot: File, relativePath: String): File {
        val asset = ConfigRules.resolveAsset(assetRoot, relativePath)
        if (!asset.isFile) {
            throw ConfigArchiveException(
                ArchiveError.INVALID_ASSET,
                "Configured asset does not exist: $relativePath",
            )
        }
        if (asset.length() > MAX_ASSET_BYTES) {
            throw ConfigArchiveException(
                ArchiveError.ENTRY_TOO_LARGE,
                "Asset exceeds its size limit: $relativePath",
            )
        }
        val bytes = asset.readBytes()
        if (!isSafeWebpAsset(relativePath, bytes)) {
            throw ConfigArchiveException(
                ArchiveError.INVALID_ASSET,
                "Configured asset is not a safe WebP image: $relativePath",
            )
        }
        return asset
    }

    private fun ZipOutputStream.putBytes(path: String, bytes: ByteArray) {
        putNextEntry(newEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun newEntry(path: String): ZipEntry = ZipEntry(path).apply {
        time = 0L
    }

    private fun installAssetsAtomically(root: File, assets: Map<String, ByteArray>) {
        if (!root.exists() && assets.isEmpty()) return
        val staged = linkedMapOf<File, File>()
        val backups = linkedMapOf<File, File>()
        val installed = mutableSetOf<File>()
        try {
            if (!root.exists() && !root.mkdirs()) {
                throw IOException("Unable to create asset directory")
            }
            val canonicalRoot = root.canonicalFile
            assets.forEach { (relativePath, bytes) ->
                val target = ConfigRules.resolveAsset(canonicalRoot, relativePath)
                val temporary = File.createTempFile(".import-", ".tmp", canonicalRoot)
                FileOutputStream(temporary).use { stream ->
                    stream.write(bytes)
                    stream.fd.sync()
                }
                staged[target] = temporary
            }

            MANAGED_ASSET_FILES.forEachIndexed { index, relativePath ->
                val target = ConfigRules.resolveAsset(canonicalRoot, relativePath)
                if (target.exists()) {
                    val backup = File(
                        canonicalRoot,
                        ".${target.name}.backup-${System.nanoTime()}-$index",
                    )
                    if (!target.renameTo(backup)) {
                        throw IOException("Unable to back up existing asset")
                    }
                    backups[target] = backup
                }
            }
            staged.forEach { (target, temporary) ->
                if (!temporary.renameTo(target)) {
                    throw IOException("Unable to install imported asset")
                }
                installed += target
            }
            backups.values.forEach(File::delete)
            backups.clear()
        } catch (exception: Exception) {
            installed.forEach { target ->
                if (target.exists()) target.delete()
            }
            backups.entries.toList().asReversed().forEach { (target, backup) ->
                if (backup.exists() && backup.renameTo(target)) {
                    backups.remove(target)
                }
            }
            throw ConfigArchiveException(
                ArchiveError.ASSET_WRITE_FAILED,
                "Unable to store imported assets",
                exception,
            )
        } finally {
            staged.values.forEach { temporary ->
                if (temporary.exists()) temporary.delete()
            }
            // A backup is intentionally retained if rollback could not restore it.
        }
    }

    /**
     * Validates the RIFF envelope and parses the canvas dimensions from all standardized WebP
     * bitstream variants. This prevents tiny archives from declaring enormous images that could
     * exhaust memory when the branding layer decodes them.
     */
    private fun isSafeWebpAsset(relativePath: String, bytes: ByteArray): Boolean {
        if (bytes.size !in MIN_WEBP_BYTES..MAX_ASSET_BYTES.toInt()) return false
        if (!bytes.matchesAscii(0, "RIFF") || !bytes.matchesAscii(8, "WEBP")) return false
        val declaredLength = bytes.readUInt32Le(4) + 8L
        if (declaredLength != bytes.size.toLong()) return false

        val dimensions = when {
            bytes.matchesAscii(12, "VP8X") -> {
                if (bytes.size < 30 || bytes.readUInt32Le(16) < 10L) return false
                val width = 1 + bytes.readUInt24Le(24)
                val height = 1 + bytes.readUInt24Le(27)
                width to height
            }
            bytes.matchesAscii(12, "VP8L") -> {
                if (bytes.size < 25 || bytes.readUInt32Le(16) < 5L) return false
                if (bytes[20].toInt() and 0xFF != 0x2F) return false
                val b1 = bytes[21].toInt() and 0xFF
                val b2 = bytes[22].toInt() and 0xFF
                val b3 = bytes[23].toInt() and 0xFF
                val b4 = bytes[24].toInt() and 0xFF
                val width = 1 + b1 + ((b2 and 0x3F) shl 8)
                val height = 1 +
                    ((b2 and 0xC0) shr 6) +
                    (b3 shl 2) +
                    ((b4 and 0x0F) shl 10)
                width to height
            }
            bytes.matchesAscii(12, "VP8 ") -> {
                if (
                    bytes.size < 30 ||
                    bytes.readUInt32Le(16) < 10L ||
                    bytes[23].toInt() and 0xFF != 0x9D ||
                    bytes[24].toInt() and 0xFF != 0x01 ||
                    bytes[25].toInt() and 0xFF != 0x2A
                ) {
                    return false
                }
                val width = bytes.readUInt16Le(26) and 0x3FFF
                val height = bytes.readUInt16Le(28) and 0x3FFF
                width to height
            }
            else -> return false
        }

        val maximumDimension = when (relativePath) {
            LOGO_FILE -> MAX_LOGO_DIMENSION
            SPLASH_FILE -> MAX_SPLASH_DIMENSION
            else -> return false
        }
        val (width, height) = dimensions
        return width in 1..maximumDimension &&
            height in 1..maximumDimension &&
            width.toLong() * height <= maximumDimension.toLong() * maximumDimension
    }

    private fun ByteArray.matchesAscii(offset: Int, expected: String): Boolean {
        if (offset < 0 || size - offset < expected.length) return false
        return expected.indices.all { index ->
            this[offset + index].toInt() and 0xFF == expected[index].code
        }
    }

    private fun ByteArray.readUInt16Le(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.readUInt24Le(offset: Int): Int =
        readUInt16Le(offset) or ((this[offset + 2].toInt() and 0xFF) shl 16)

    private fun ByteArray.readUInt32Le(offset: Int): Long =
        (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)

    private class LimitedInputStream(
        input: InputStream,
        private val maximumBytes: Long,
    ) : FilterInputStream(input) {
        private var consumed = 0L

        override fun read(): Int {
            val value = super.read()
            if (value != -1) account(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) account(count.toLong())
            return count
        }

        private fun account(count: Long) {
            consumed += count
            if (consumed > maximumBytes) {
                throw ConfigArchiveException(
                    ArchiveError.ARCHIVE_TOO_LARGE,
                    "Compressed archive exceeds its size limit",
                )
            }
        }
    }

    companion object {
        const val MAX_ARCHIVE_BYTES = 25L * 1_024 * 1_024
        const val MAX_TOTAL_UNCOMPRESSED_BYTES = 21L * 1_024 * 1_024
        const val MAX_CONFIG_BYTES = 128L * 1_024
        const val MAX_ASSET_BYTES = 10L * 1_024 * 1_024

        const val CONFIG_ENTRY = "config.json"
        const val LOGO_ENTRY = "assets/logo.webp"
        const val SPLASH_ENTRY = "assets/splash.webp"
        const val LOGO_FILE = "logo.webp"
        const val SPLASH_FILE = "splash.webp"

        private const val MIN_WEBP_BYTES = 30
        private const val MAX_LOGO_DIMENSION = 1_024
        private const val MAX_SPLASH_DIMENSION = 2_048
        private val ALLOWED_ENTRIES = setOf(CONFIG_ENTRY, LOGO_ENTRY, SPLASH_ENTRY)
        private val MANAGED_ASSET_FILES = listOf(LOGO_FILE, SPLASH_FILE)
    }
}
