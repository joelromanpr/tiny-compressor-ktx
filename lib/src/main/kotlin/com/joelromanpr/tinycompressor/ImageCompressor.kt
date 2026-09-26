/*
 * Copyright (C) 2025 joelromanpr (Joel Roman)
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.joelromanpr.tinycompressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Compresses Android images while bounding dimensions and optionally output size.
 *
 * A [Destination.File] is replaced only after the complete result has been encoded. Compression
 * does not alter the input file, even if the input and destination refer to the same path.
 */
public object ImageCompressor {
    /**
     * Compresses [source] to [Options.destination] and returns the resulting file.
     *
     * [Options.maxBytes] is a strict limit on the final file, including retained EXIF metadata.
     * An [IOException] is thrown if the limit cannot be met, and an existing destination is left
     * intact. The library owns the returned cache file; callers may delete it when no longer needed.
     */
    public suspend fun compress(
        context: Context,
        source: Source,
        options: Options = Options(),
    ): File =
        withContext(Dispatchers.IO) {
            internalCompressToFile(context, source, options, null)
        }

    /**
     * Compresses [source] into memory. [Options.destination] is ignored and is never modified.
     *
     * When JPEG EXIF retention is requested, a private temporary file is needed to save metadata;
     * it is removed before this function returns. [Options.maxBytes] is a strict limit on the final
     * byte array, including metadata, and an [IOException] is thrown if it cannot be met.
     */
    public suspend fun compressToByteArray(
        context: Context,
        source: Source,
        options: Options = Options(),
    ): ByteArray =
        withContext(Dispatchers.IO) {
            validateOptions(options)
            val decoded = decodeBitmap(context, source, options)
            try {
                val format = options.format.resolveFor(decoded.mime)
                val stagingFile =
                    if (options.keepExif && format == CompressFormat.JPEG) {
                        File.createTempFile("tinycompressor-", ".jpg", context.cacheDir)
                    } else {
                        null
                    }
                try {
                    val bytes = encodeBitmap(context, source, decoded.bitmap, options, format, stagingFile, null)
                    currentCoroutineContext().ensureActive()
                    bytes ?: requireNotNull(stagingFile).readBytes()
                } finally {
                    stagingFile?.delete()
                }
            } finally {
                decoded.bitmap.recycle()
            }
        }

    /** Emits approximate progress from loading through writing, then the completed file. */
    public fun compressAsFlow(
        context: Context,
        source: Source,
        options: Options = Options(),
    ): Flow<Progress> =
        channelFlow {
            send(Progress(Step.Loading, 0))
            val progress =
                object : ProgressEmitter {
                    override suspend fun emit(
                        step: Step,
                        percent: Int,
                    ) {
                        send(Progress(step, percent.coerceIn(0, 100)))
                    }
                }
            val resultFile =
                withContext(Dispatchers.IO) {
                    internalCompressToFile(context, source, options, progress)
                }
            send(Progress(Step.Done, 100, resultFile))
        }

    private suspend fun internalCompressToFile(
        context: Context,
        source: Source,
        options: Options,
        progress: ProgressEmitter?,
    ): File {
        validateOptions(options)
        progress?.emit(Step.Decoding, 5)
        val decoded = decodeBitmap(context, source, options)
        try {
            progress?.emit(Step.Decoding, 40)
            val format = options.format.resolveFor(decoded.mime)
            val destination = resolveDestination(context, options.destination, format)
            val parent = requireNotNull(destination.parentFile)
            val stagingFile = File.createTempFile(".tinycompressor-", ".tmp", parent)
            try {
                progress?.emit(Step.Resizing, 55)
                encodeBitmap(context, source, decoded.bitmap, options, format, stagingFile, progress)
                progress?.emit(Step.Writing, 95)
                currentCoroutineContext().ensureActive()
                Files.move(
                    stagingFile.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                return destination
            } finally {
                stagingFile.delete()
            }
        } finally {
            decoded.bitmap.recycle()
        }
    }

    private data class DecodedImage(
        val bitmap: Bitmap,
        val mime: String,
    )

    private fun validateOptions(options: Options) {
        require(options.maxWidth > 0) { "maxWidth must be greater than zero" }
        require(options.maxHeight > 0) { "maxHeight must be greater than zero" }
        require(options.maxBytes == null || options.maxBytes > 0) { "maxBytes must be greater than zero" }
    }

    private fun decodeBitmap(
        context: Context,
        source: Source,
        options: Options,
    ): DecodedImage {
        val decoderSource =
            when (source) {
                is Source.File -> ImageDecoder.createSource(source.file)
                is Source.Uri -> ImageDecoder.createSource(context.contentResolver, source.uri)
                is Source.Bytes -> ImageDecoder.createSource(ByteBuffer.wrap(source.bytes))
            }
        var mime = "application/octet-stream"
        val bitmap =
            ImageDecoder.decodeBitmap(decoderSource) { decoder, info, _ ->
                mime = info.mimeType
                val target = computeTargetSize(info.size.width, info.size.height, options.maxWidth, options.maxHeight)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(target.width, target.height)
                decoder.setTargetColorSpace(options.colorSpace.toAndroid())
            }
        // Enforce both limits after orientation is applied. Some decoders report encoded dimensions
        // in the header, while the returned bitmap has width and height exchanged by EXIF rotation.
        val bounded = computeTargetSize(bitmap.width, bitmap.height, options.maxWidth, options.maxHeight)
        if (bitmap.width == bounded.width && bitmap.height == bounded.height) {
            return DecodedImage(bitmap, mime)
        }
        return try {
            val resized = Bitmap.createScaledBitmap(bitmap, bounded.width, bounded.height, true)
            bitmap.recycle()
            DecodedImage(resized, mime)
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    private fun resolveDestination(
        context: Context,
        destination: Destination,
        format: CompressFormat,
    ): File =
        when (destination) {
            is Destination.File -> {
                val file = destination.file.absoluteFile
                val extensionFormat =
                    when (file.extension.lowercase()) {
                        "jpg", "jpeg" -> CompressFormat.JPEG
                        "png" -> CompressFormat.PNG
                        "webp" -> CompressFormat.WEBP
                        else -> null
                    }
                require(extensionFormat == null || extensionFormat == format) {
                    "Destination extension does not match encoded $format image: $file"
                }
                ensureDirectory(requireNotNull(file.parentFile))
                require(!file.isDirectory) { "Destination must be a file: $file" }
                file
            }

            is Destination.Cache -> {
                val root = File(context.cacheDir, "tinycompressor").canonicalFile
                ensureDirectory(root)
                val directory = File(root, destination.subdir).canonicalFile
                require(directory.toPath().startsWith(root.toPath())) {
                    "Cache subdir must stay inside the tinycompressor cache directory"
                }
                ensureDirectory(directory)
                File(directory, "IMG_${UUID.randomUUID()}${format.defaultExtension()}")
            }
        }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create directory: $directory")
        }
    }

    internal fun computeTargetSize(
        srcWidth: Int,
        srcHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Size {
        if (srcWidth <= 0 || srcHeight <= 0) return Size(maxWidth, maxHeight)
        val ratio =
            min(
                maxWidth.toDouble() / srcWidth,
                maxHeight.toDouble() / srcHeight,
            ).coerceAtMost(1.0)
        val outW = max(1, (srcWidth * ratio).roundToInt())
        val outH = max(1, (srcHeight * ratio).roundToInt())
        return Size(outW, outH)
    }

    internal data class Size(
        val width: Int,
        val height: Int,
    )

    // Retained for clients of the module's internal tests and for assessing sample-size behavior.
    internal fun computeSampleSize(
        srcW: Int,
        srcH: Int,
        targetW: Int,
        targetH: Int,
    ): Int {
        if (srcW <= 0 || srcH <= 0 || targetW <= 0 || targetH <= 0) return 1
        var sample = 1
        var w = srcW
        var h = srcH
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    /** Chooses a proportional scale that makes progress on both rounded edges. */
    internal fun computeNextScale(
        baseWidth: Int,
        baseHeight: Int,
        currentWidth: Int,
        currentHeight: Int,
        proposedScale: Double,
    ): Double? {
        if (currentWidth <= 1 || currentHeight <= 1) return null
        return min(
            proposedScale,
            min(
                (currentWidth - 1).toDouble() / baseWidth,
                (currentHeight - 1).toDouble() / baseHeight,
            ),
        )
    }

    private suspend fun encodeBitmap(
        context: Context,
        source: Source,
        bitmap: Bitmap,
        options: Options,
        format: CompressFormat,
        stagingFile: File?,
        progress: ProgressEmitter?,
    ): ByteArray? {
        val androidFormat = format.toAndroid(options.quality.coerceIn(0, 100))
        progress?.emit(Step.Encoding, 70)
        currentCoroutineContext().ensureActive()

        if (options.maxBytes == null) {
            if (stagingFile != null) {
                BufferedOutputStream(FileOutputStream(stagingFile)).use { output ->
                    if (!bitmap.compress(androidFormat, options.quality.coerceIn(0, 100), output)) {
                        throw IOException("Bitmap encoder failed")
                    }
                }
                if (options.keepExif && format == CompressFormat.JPEG) copyExif(context, source, stagingFile)
                currentCoroutineContext().ensureActive()
                return null
            }
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(androidFormat, options.quality.coerceIn(0, 100), output)) {
                throw IOException("Bitmap encoder failed")
            }
            currentCoroutineContext().ensureActive()
            return output.toByteArray()
        }

        val maxBytes = options.maxBytes
        var quality = options.quality.coerceIn(0, 100)
        var current = bitmap
        var scale = 1.0
        try {
            repeat(128) { iteration ->
                currentCoroutineContext().ensureActive()
                progress?.emit(Step.Encoding, 70 + min(iteration, 20))
                val output = ByteArrayOutputStream()
                if (!current.compress(format.toAndroid(quality), quality, output)) {
                    throw IOException("Bitmap encoder failed")
                }
                var outputSize = output.size().toLong()
                if (outputSize <= maxBytes) {
                    if (stagingFile == null) {
                        return output.toByteArray()
                    }
                    BufferedOutputStream(FileOutputStream(stagingFile)).use { output.writeTo(it) }
                    if (options.keepExif && format == CompressFormat.JPEG) copyExif(context, source, stagingFile)
                    outputSize = stagingFile.length()
                    if (outputSize <= maxBytes) return null
                }

                if (format.isLossy() && quality > 30) {
                    quality = max(30, (quality * 0.8).roundToInt())
                } else {
                    val ratio = sqrt(maxBytes.toDouble() / outputSize).coerceIn(0.5, 0.85)
                    // Rounding can leave a thin edge unchanged (for example 1000x2 -> 850x2).
                    // Advance far enough to shrink both edges while scaling from the original.
                    scale =
                        computeNextScale(bitmap.width, bitmap.height, current.width, current.height, scale * ratio)
                            ?: throw IOException(
                                "Cannot compress image to $maxBytes bytes while preserving aspect ratio",
                            )
                    val nextWidth = max(1, (bitmap.width * scale).roundToInt())
                    val nextHeight = max(1, (bitmap.height * scale).roundToInt())
                    val next = Bitmap.createScaledBitmap(bitmap, nextWidth, nextHeight, true)
                    if (current !== bitmap) current.recycle()
                    current = next
                }
            }
            throw IOException("Cannot compress image to $maxBytes bytes within 128 attempts")
        } finally {
            if (current !== bitmap) current.recycle()
        }
    }

    private fun copyExif(
        context: Context,
        source: Source,
        output: File,
    ) {
        val sourceExif =
            try {
                when (source) {
                    is Source.File -> ExifInterface(source.file.absolutePath)
                    is Source.Uri ->
                        context.contentResolver.openInputStream(source.uri).use { input ->
                            if (input == null) return
                            ExifInterface(input)
                        }
                    is Source.Bytes -> ExifInterface(ByteArrayInputStream(source.bytes))
                }
            } catch (_: IOException) {
                // Formats without readable EXIF should still be compressible.
                return
            }
        val outputExif = ExifInterface(output.absolutePath)
        val tags =
            arrayOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
            )
        for (tag in tags) {
            sourceExif.getAttribute(tag)?.let { outputExif.setAttribute(tag, it) }
        }
        // ImageDecoder applies the source orientation to pixels; retaining its EXIF orientation
        // would cause viewers to rotate the already-oriented output again.
        outputExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        outputExif.saveAttributes()
    }

    internal fun guessMimeFromName(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            else -> null
        }
    }
}

private interface ProgressEmitter {
    suspend fun emit(
        step: Step,
        percent: Int,
    )
}
