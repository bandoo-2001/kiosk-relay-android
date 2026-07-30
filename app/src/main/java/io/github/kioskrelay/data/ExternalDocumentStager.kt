package io.github.kioskrelay.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class ExternalDocumentTooLargeException : IOException("External document exceeds its size limit")

/**
 * Copies untrusted ContentProvider input to a bounded private cache file.
 *
 * Callers deliberately perform this step before acquiring the branding transaction lock. Some
 * OEM, network-drive, or faulty document providers do not react to coroutine cancellation while a
 * blocking read is in progress; such a provider must not prevent a recreated Activity from
 * recovering its local transaction.
 */
class ExternalDocumentStager(context: Context) {
    private val applicationContext = context.applicationContext
    private val directory = File(applicationContext.cacheDir, DIRECTORY_NAME)

    fun newTarget(suffix: String): File {
        val safeSuffix = suffix.filter { it.isLetterOrDigit() }.take(12).ifEmpty { "bin" }
        return File(directory, "stage-${UUID.randomUUID()}.$safeSuffix")
    }

    fun copy(uri: Uri, target: File, maximumBytes: Long): Long {
        require(maximumBytes > 0) { "maximumBytes must be positive" }
        ensureSafeTarget(target)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create external document staging directory")
        }

        val input = applicationContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Content provider returned no stream")
        var total = 0L
        try {
            input.buffered().use { source ->
                FileOutputStream(target, false).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > maximumBytes) {
                            throw ExternalDocumentTooLargeException()
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            return total
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    fun discard(target: File) {
        ensureSafeTarget(target)
        if (target.exists() && !target.delete()) {
            throw IOException("Unable to delete staged external document")
        }
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    /**
     * Removes private staging files left behind by a process that was killed during provider I/O.
     */
    fun discardOrphans(): Int {
        if (!directory.isDirectory) return 0
        var discarded = 0
        directory.listFiles().orEmpty().forEach { candidate ->
            if (
                candidate.isFile &&
                candidate.name.startsWith("stage-") &&
                candidate.delete()
            ) {
                discarded++
            }
        }
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
        return discarded
    }

    private fun ensureSafeTarget(target: File) {
        val canonicalDirectory = directory.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (
            canonicalTarget.parentFile != canonicalDirectory ||
            !canonicalTarget.name.startsWith("stage-")
        ) {
            throw IOException("Unsafe external document staging target")
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "external-document-staging"
    }
}
