package io.github.kioskrelay.data

import io.github.kioskrelay.config.KioskRelayConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Persists a complete export before the system document picker is opened.
 *
 * Each request owns an independent private snapshot. The identifier can be kept in saved instance
 * state while the system document picker is open, and an old Activity can only copy or discard its
 * own generation.
 */
class PendingConfigExport(
    cacheRoot: File,
    private val archiveManager: ConfigArchiveManager,
) {
    private val directory = File(cacheRoot, DIRECTORY_NAME)

    fun newSnapshotId(): String = UUID.randomUUID().toString()

    fun prepare(snapshotId: String, config: KioskRelayConfig, assetRoot: File) {
        val pendingFile = snapshotFile(snapshotId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create configuration export cache")
        }
        if (pendingFile.exists() && !pendingFile.delete()) {
            throw IOException("Unable to replace pending configuration export")
        }
        val temporary = File.createTempFile(".pending-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                archiveManager.export(
                    config = config,
                    assetRoot = assetRoot,
                    output = output,
                )
            }
            FileOutputStream(temporary, true).use { stream ->
                stream.fd.sync()
            }
            if (!temporary.renameTo(pendingFile)) {
                throw IOException("Unable to publish pending configuration export")
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun markLaunched(snapshotId: String) {
        if (!snapshotFile(snapshotId).isFile) {
            throw IOException("Pending configuration export is unavailable")
        }
        publishState(snapshotId, STATE_LAUNCHED)
    }

    fun markResultReceived(snapshotId: String) {
        if (readState()?.first != snapshotId) {
            throw IOException("Pending configuration export ownership is unavailable")
        }
        publishState(snapshotId, STATE_RESULT_RECEIVED)
    }

    /**
     * Recovers the one picker request that can still deliver an Activity Result after process
     * recreation. A result that was already delivered cannot be resumed without its transient URI,
     * so that phase is cleaned safely and the administrator can retry.
     */
    fun recoverLaunchedSnapshotId(expectedSnapshotId: String?): String? {
        if (!directory.isDirectory) return null
        val state = readState()
        val recoverableId = state
            ?.takeIf { (snapshotId, phase) ->
                snapshotId == expectedSnapshotId &&
                    phase == STATE_LAUNCHED &&
                    snapshotFile(snapshotId).isFile
            }
            ?.first
        discardManagedFiles(retainSnapshotId = recoverableId)
        return recoverableId
    }

    fun copyTo(snapshotId: String, output: OutputStream) {
        val pendingFile = snapshotFile(snapshotId)
        if (!pendingFile.isFile) {
            throw IOException("Pending configuration export is unavailable")
        }
        pendingFile.inputStream().buffered().use { input ->
            input.copyTo(output)
        }
    }

    fun discard(snapshotId: String) {
        val pendingFile = snapshotFile(snapshotId)
        if (pendingFile.exists()) pendingFile.delete()
        if (readState()?.first == snapshotId) {
            stateFile().delete()
        }
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    /**
     * Called once when a new application process starts. In-memory export ownership cannot survive
     * process death, so any old private snapshots are no longer eligible for a picker callback.
     */
    fun discardOrphans(): Int {
        return discardManagedFiles(retainSnapshotId = null)
    }

    private fun publishState(snapshotId: String, phase: String) {
        snapshotFile(snapshotId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create configuration export cache")
        }
        val temporary = File.createTempFile(".active-", ".tmp", directory)
        try {
            FileOutputStream(temporary).bufferedWriter().use { writer ->
                writer.appendLine(snapshotId)
                writer.appendLine(phase)
            }
            FileOutputStream(temporary, true).use { stream ->
                stream.fd.sync()
            }
            val stateFile = stateFile()
            if (stateFile.exists() && !stateFile.delete()) {
                throw IOException("Unable to replace pending export ownership")
            }
            if (!temporary.renameTo(stateFile)) {
                throw IOException("Unable to publish pending export ownership")
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun readState(): Pair<String, String>? {
        val stateFile = stateFile()
        if (!stateFile.isFile || stateFile.length() > MAX_STATE_BYTES) return null
        val lines = runCatching { stateFile.readLines() }.getOrNull() ?: return null
        if (lines.size != 2 || !SNAPSHOT_ID.matches(lines[0])) return null
        if (lines[1] !in setOf(STATE_LAUNCHED, STATE_RESULT_RECEIVED)) return null
        return lines[0] to lines[1]
    }

    private fun discardManagedFiles(retainSnapshotId: String?): Int {
        if (!directory.isDirectory) return 0
        var discarded = 0
        directory.listFiles().orEmpty().forEach { candidate ->
            val retainedName = retainSnapshotId?.let {
                "pending-$it.kioskrelay"
            }
            val isSnapshot = PENDING_FILE.matches(candidate.name)
            val isTemporary =
                (candidate.name.startsWith(".pending-") ||
                    candidate.name.startsWith(".active-")) &&
                    candidate.name.endsWith(".tmp")
            val isDiscardableState =
                candidate.name == STATE_FILE_NAME && retainSnapshotId == null
            if (
                candidate.isFile &&
                candidate.name != retainedName &&
                (isSnapshot || isTemporary || isDiscardableState) &&
                candidate.delete()
            ) {
                discarded++
            }
        }
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
        return discarded
    }

    private fun stateFile(): File = File(directory, STATE_FILE_NAME)

    private fun snapshotFile(snapshotId: String): File {
        if (!SNAPSHOT_ID.matches(snapshotId)) {
            throw IOException("Invalid pending configuration export identifier")
        }
        return File(directory, "pending-$snapshotId.kioskrelay")
    }

    companion object {
        private const val DIRECTORY_NAME = "configuration-export"
        private const val STATE_FILE_NAME = "active.state"
        private const val STATE_LAUNCHED = "LAUNCHED"
        private const val STATE_RESULT_RECEIVED = "RESULT_RECEIVED"
        private const val MAX_STATE_BYTES = 256L
        private val SNAPSHOT_ID =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val PENDING_FILE = Regex("pending-${SNAPSHOT_ID.pattern}\\.kioskrelay")
    }
}
