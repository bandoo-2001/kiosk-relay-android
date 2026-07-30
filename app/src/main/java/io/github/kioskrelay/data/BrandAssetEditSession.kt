package io.github.kioskrelay.data

import io.github.kioskrelay.config.ConfigRules
import io.github.kioskrelay.config.KioskRelayConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Makes branding edits cancelable even though the image importer writes fixed managed filenames.
 *
 * A tiny transaction directory also lets the next process restore an edit interrupted by process
 * death before the settings screen was saved.
 */
class BrandAssetEditSession private constructor(
    private val root: File,
    private val transactionDirectory: File,
) {
    @Synchronized
    fun remove(kind: BrandImageKind) {
        if (!transactionDirectory.isDirectory) {
            throw IOException("Branding edit transaction is missing")
        }
        val target = File(root, kind.relativePath)
        if (target.exists() && !target.delete()) {
            throw IOException("Unable to remove ${kind.relativePath}")
        }
    }

    @Synchronized
    fun prepareCommit(config: KioskRelayConfig) {
        if (!transactionDirectory.isDirectory) {
            throw IOException("Branding edit transaction is missing")
        }
        val marker = File(transactionDirectory, COMMIT_MARKER)
        FileOutputStream(marker).use { stream ->
            stream.write(configurationFingerprint(config).toByteArray(Charsets.US_ASCII))
            stream.fd.sync()
        }
    }

    @Synchronized
    fun commit() {
        if (!transactionDirectory.exists()) return
        if (!File(transactionDirectory, COMMIT_MARKER).isFile) {
            throw IOException("Branding edit was not prepared for commit")
        }
        deleteTransactionDirectory(transactionDirectory)
    }

    @Synchronized
    fun rollback() {
        restoreInterruptedSession(root, transactionDirectory)
    }

    companion object {
        private const val TRANSACTION_DIRECTORY = ".settings-branding-edit"
        private const val COMMIT_MARKER = ".committed"
        private val MANAGED_FILES = listOf(
            ConfigArchiveManager.LOGO_FILE,
            ConfigArchiveManager.SPLASH_FILE,
        )

        fun begin(root: File): BrandAssetEditSession {
            if (!root.exists() && !root.mkdirs()) {
                throw IOException("Unable to create branding directory")
            }
            val canonicalRoot = root.canonicalFile
            val transaction = File(canonicalRoot, TRANSACTION_DIRECTORY)
            if (transaction.exists()) {
                throw IOException("An interrupted branding edit must be recovered first")
            }
            if (!transaction.mkdir()) {
                throw IOException("Unable to start branding edit")
            }
            try {
                MANAGED_FILES.forEach { name ->
                    val source = File(canonicalRoot, name)
                    if (source.isFile) {
                        source.copyTo(File(transaction, "$name.backup"))
                    } else if (!File(transaction, "$name.absent").createNewFile()) {
                        throw IOException("Unable to record missing branding asset")
                    }
                }
            } catch (exception: Exception) {
                restoreInterruptedSession(canonicalRoot, transaction)
                throw IOException("Unable to snapshot branding assets", exception)
            }
            return BrandAssetEditSession(canonicalRoot, transaction)
        }

        fun recoverIfNeeded(root: File, currentConfig: KioskRelayConfig) {
            val canonicalRoot = root.canonicalFile
            val transaction = File(canonicalRoot, TRANSACTION_DIRECTORY)
            if (transaction.exists()) {
                val expectedFingerprint = runCatching {
                    File(transaction, COMMIT_MARKER)
                        .takeIf(File::isFile)
                        ?.readText(Charsets.US_ASCII)
                        ?.trim()
                }.getOrNull()
                if (
                    expectedFingerprint != null &&
                    MessageDigest.isEqual(
                        expectedFingerprint.toByteArray(Charsets.US_ASCII),
                        configurationFingerprint(currentConfig)
                            .toByteArray(Charsets.US_ASCII),
                    )
                ) {
                    deleteTransactionDirectory(transaction)
                } else {
                    restoreInterruptedSession(canonicalRoot, transaction)
                }
            }
        }

        internal fun configurationFingerprint(config: KioskRelayConfig): String {
            val safe = ConfigRules.validate(ConfigRules.canonicalize(config))
            val stable = safe.copy(
                webView = safe.webView.copy(
                    allowedOrigins = safe.webView.allowedOrigins.toSortedSet(),
                ),
            )
            return MessageDigest.getInstance("SHA-256")
                .digest(ConfigJsonCodec.encode(stable).toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte ->
                    "%02x".format(byte.toInt() and 0xFF)
                }
        }

        private fun restoreInterruptedSession(root: File, transaction: File) {
            MANAGED_FILES.forEach { name ->
                val target = File(root, name)
                val backup = File(transaction, "$name.backup")
                val absent = File(transaction, "$name.absent")
                when {
                    backup.isFile -> installBackup(backup, target)
                    absent.isFile && target.exists() -> target.delete()
                }
            }
            deleteTransactionDirectory(transaction)
        }

        private fun installBackup(backup: File, target: File) {
            val temporary = File(target.parentFile, ".${target.name}.rollback")
            try {
                backup.copyTo(temporary, overwrite = true)
                if (target.exists() && !target.delete()) {
                    throw IOException("Unable to replace edited branding asset")
                }
                if (!temporary.renameTo(target)) {
                    throw IOException("Unable to restore branding asset")
                }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }

        private fun deleteTransactionDirectory(transaction: File) {
            val payloadDeleted = transaction.listFiles()
                ?.filterNot { it.name == COMMIT_MARKER }
                ?.all { !it.exists() || it.delete() }
                ?: true
            if (payloadDeleted) {
                val marker = File(transaction, COMMIT_MARKER)
                if (!marker.exists() || marker.delete()) {
                    transaction.delete()
                }
            }
        }
    }
}
