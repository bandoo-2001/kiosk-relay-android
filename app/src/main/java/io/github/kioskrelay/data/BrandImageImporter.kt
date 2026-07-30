package io.github.kioskrelay.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import io.github.kioskrelay.config.ConfigRules
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BrandImageKind(
    val maximumDimension: Int,
    val relativePath: String,
) {
    LOGO(maximumDimension = 1_024, relativePath = "logo.webp"),
    SPLASH(maximumDimension = 2_048, relativePath = "splash.webp"),
}

enum class ImageImportError {
    ACCESS_DENIED,
    FILE_TOO_LARGE,
    INVALID_IMAGE,
    UNSUPPORTED_DIMENSIONS,
    STORAGE_ERROR,
}

sealed interface ImageImportResult {
    data class Success(
        val relativePath: String,
        val width: Int,
        val height: Int,
        val originalByteCount: Int,
    ) : ImageImportResult

    data class Failure(
        val error: ImageImportError,
    ) : ImageImportResult
}

interface BrandImageImporter {
    suspend fun importImage(uri: Uri, kind: BrandImageKind): ImageImportResult

    fun resolve(relativePath: String): File?
}

object BrandAssetPaths {
    const val DIRECTORY_NAME = "branding"

    fun root(context: Context): File =
        File(context.applicationContext.filesDir, DIRECTORY_NAME)
}

class AndroidBrandImageImporter(
    context: Context,
    private val assetRoot: File = BrandAssetPaths.root(context),
) : BrandImageImporter {
    private val appContext = context.applicationContext

    override suspend fun importImage(
        uri: Uri,
        kind: BrandImageKind,
    ): ImageImportResult = withContext(Dispatchers.IO) {
        val declaredSize = querySize(uri)
        if (declaredSize != null && declaredSize > MAX_SOURCE_BYTES) {
            return@withContext ImageImportResult.Failure(ImageImportError.FILE_TOO_LARGE)
        }

        val encoded = try {
            readSource(uri)
        } catch (_: SecurityException) {
            return@withContext ImageImportResult.Failure(ImageImportError.ACCESS_DENIED)
        } catch (_: SizeLimitExceededException) {
            return@withContext ImageImportResult.Failure(ImageImportError.FILE_TOO_LARGE)
        } catch (_: IOException) {
            return@withContext ImageImportResult.Failure(ImageImportError.ACCESS_DENIED)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext ImageImportResult.Failure(ImageImportError.INVALID_IMAGE)
        }
        if (
            bounds.outWidth > MAX_SOURCE_DIMENSION ||
            bounds.outHeight > MAX_SOURCE_DIMENSION ||
            bounds.outWidth.toLong() * bounds.outHeight > MAX_SOURCE_PIXELS
        ) {
            return@withContext ImageImportResult.Failure(
                ImageImportError.UNSUPPORTED_DIMENSIONS,
            )
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maximumDimension = kind.maximumDimension,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            BitmapFactory.decodeByteArray(encoded, 0, encoded.size, decodeOptions)
        } catch (_: OutOfMemoryError) {
            null
        } ?: return@withContext ImageImportResult.Failure(ImageImportError.INVALID_IMAGE)

        val scaled = try {
            scaleDown(decoded, kind.maximumDimension)
        } catch (_: OutOfMemoryError) {
            decoded.recycle()
            return@withContext ImageImportResult.Failure(
                ImageImportError.UNSUPPORTED_DIMENSIONS,
            )
        }
        if (scaled !== decoded) decoded.recycle()
        val outputWidth = scaled.width
        val outputHeight = scaled.height
        try {
            storeBitmap(scaled, kind.relativePath)
        } catch (_: Exception) {
            return@withContext ImageImportResult.Failure(ImageImportError.STORAGE_ERROR)
        } finally {
            scaled.recycle()
        }
        ImageImportResult.Success(
            relativePath = kind.relativePath,
            width = outputWidth,
            height = outputHeight,
            originalByteCount = encoded.size,
        )
    }

    override fun resolve(relativePath: String): File? = runCatching {
        ConfigRules.resolveAsset(assetRoot, relativePath)
    }.getOrNull()?.takeIf(File::isFile)

    private fun querySize(uri: Uri): Long? = try {
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
        }
    } catch (_: RuntimeException) {
        null
    }

    private fun readSource(uri: Uri): ByteArray {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Content provider returned no stream")
        return input.buffered().use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count == -1) break
                total += count
                if (total > MAX_SOURCE_BYTES) throw SizeLimitExceededException()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        maximumDimension: Int,
    ): Int {
        var sample = 1
        while (width / (sample * 2) >= maximumDimension ||
            height / (sample * 2) >= maximumDimension
        ) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(source: Bitmap, maximumDimension: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maximumDimension) return source
        val ratio = maximumDimension.toFloat() / largest
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun storeBitmap(bitmap: Bitmap, relativePath: String) {
        if (!assetRoot.exists() && !assetRoot.mkdirs()) {
            throw IOException("Unable to create branding directory")
        }
        val target = ConfigRules.resolveAsset(assetRoot, relativePath)
        val temporary = File.createTempFile(".image-", ".tmp", assetRoot.canonicalFile)
        var backup: File? = null
        var installed = false
        try {
            FileOutputStream(temporary).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, output)) {
                    throw IOException("Bitmap encoder failed")
                }
                output.fd.sync()
            }
            if (target.exists()) {
                backup = File(
                    assetRoot.canonicalFile,
                    ".${target.name}.backup-${System.nanoTime()}",
                )
                if (!target.renameTo(backup)) {
                    throw IOException("Unable to back up existing image")
                }
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Unable to install imported image")
            }
            installed = true
            backup?.delete()
            backup = null
        } catch (exception: Exception) {
            if (installed && target.exists()) target.delete()
            backup?.let { previous ->
                if (previous.exists() && previous.renameTo(target)) backup = null
            }
            throw exception
        } finally {
            if (temporary.exists()) temporary.delete()
            // Keep an unrestored backup for manual recovery instead of deleting user data.
        }
    }

    private class SizeLimitExceededException : IOException()

    private companion object {
        const val MAX_SOURCE_BYTES = 10 * 1_024 * 1_024
        const val MAX_SOURCE_DIMENSION = 32_768
        const val MAX_SOURCE_PIXELS = 268_435_456L
        const val WEBP_QUALITY = 92
    }
}
