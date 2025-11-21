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

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tiny, modern image compression library for Android.
 * Small API, sensible defaults, coroutine- and compose-friendly.
 */
public object ImageCompressor {
    public suspend fun compress(
        context: Context,
        source: Source,
        options: Options = Options(),
    ): File =
        withContext(Dispatchers.IO) {
            internalCompressToFile(context, source, options, progress = null)
        }

    public suspend fun compressToByteArray(
        context: Context,
        source: Source,
        options: Options = Options(),
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val tmp = internalCompressToFile(context, source, options, progress = null)
            tmp.readBytes().also { tmp.delete() }
        }

    /**
     * Emits coarse-grained progress for UX feedback. Percent is approximate.
     * Steps: Loading -> Decoding -> Resizing -> Encoding -> Writing -> Done
     */
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

    // region Internal

    private suspend fun internalCompressToFile(
        context: Context,
        source: Source,
        options: Options,
        progress: ProgressEmitter?,
    ): File {
        progress?.emit(Step.Decoding, 5)

        val resolver = context.contentResolver
        val (srcWidth, srcHeight, mime) = probeSizeAndMime(context, source, resolver)

        val target =
            computeTargetSize(
                srcWidth = srcWidth,
                srcHeight = srcHeight,
                maxWidth = options.maxWidth,
                maxHeight = options.maxHeight,
            )

        val bitmap =
            decodeBitmap(
                context = context,
                source = source,
                targetWidth = target.width,
                targetHeight = target.height,
                colorSpace = options.colorSpace,
                resolver = resolver,
            )

        progress?.emit(Step.Decoding, 40)

        val resized =
            if (bitmap.width != target.width || bitmap.height != target.height) {
                progress?.emit(Step.Resizing, 55)
                bitmap.scaleTo(target.width, target.height)
            } else {
                bitmap
            }

        // Determine destination
        val outFile =
            when (val dest = options.destination) {
                is Destination.File -> {
                    ensureParent(dest.file)
                    dest.file
                }

                is Destination.Cache -> {
                    val ext = options.format.defaultExtension()
                    val dir = File(context.cacheDir, "tinycompressor/${dest.subdir}").apply { mkdirs() }
                    File(dir, "IMG_${System.currentTimeMillis()}$ext")
                }
            }

        // Encode with optional maxBytes loop
        progress?.emit(Step.Encoding, 70)
        val finalFormat = options.format.resolveFor(mimeHint = mime)
        encodeAdaptive(
            context = context,
            source = source,
            srcBitmap = resized,
            options = options,
            format = finalFormat,
            outFile = outFile,
            progress = progress,
        )

        // Preserve EXIF for JPEG if requested
        if (options.keepExif && finalFormat == CompressFormat.JPEG) {
            runCatching { copyExif(context, source, outFile) }
        }

        progress?.emit(Step.Writing, 95)

        if (resized !== bitmap) bitmap.recycle()

        return outFile
    }

    private fun ensureParent(file: File) {
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
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
                maxWidth.toFloat() / srcWidth,
                maxHeight.toFloat() / srcHeight,
            ).coerceAtMost(1f)

        val outW = max(1, (srcWidth * ratio).roundToInt())
        val outH = max(1, (srcHeight * ratio).roundToInt())
        return Size(outW, outH)
    }

    internal data class Size(
        val width: Int,
        val height: Int,
    )

    private fun decodeBitmap(
        context: Context,
        source: Source,
        targetWidth: Int,
        targetHeight: Int,
        colorSpace: ColorSpace,
        resolver: ContentResolver,
    ): Bitmap =
        if (Build.VERSION.SDK_INT >= 28) {
            val s =
                when (source) {
                    is Source.File -> ImageDecoder.createSource(source.file)
                    is Source.Uri -> ImageDecoder.createSource(resolver, source.uri)
                    is Source.Bytes -> ImageDecoder.createSource(ByteBuffer.wrap(source.bytes))
                }
            ImageDecoder.decodeBitmap(s) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                if (targetWidth > 0 && targetHeight > 0) {
                    decoder.setTargetSize(targetWidth, targetHeight)
                }
                decoder.setTargetColorSpace(colorSpace.toAndroid())
                decoder.isMutableRequired = false
            }
        } else {
            // Pre-28 path using BitmapFactory with sampling
            val (boundsW, boundsH) = decodeBounds(context, source, resolver)
            val sampleSize = computeSampleSize(boundsW, boundsH, targetWidth, targetHeight)

            val opts =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    if (Build.VERSION.SDK_INT >= 26) {
                        inPreferredColorSpace = colorSpace.toAndroid()
                    }
                }

            when (source) {
                is Source.File -> BitmapFactory.decodeFile(source.file.absolutePath, opts)
                is Source.Uri ->
                    resolver.openInputStream(source.uri).use { input ->
                        BitmapFactory.decodeStream(input, null, opts)
                    }

                is Source.Bytes ->
                    BitmapFactory.decodeByteArray(
                        source.bytes,
                        0,
                        source.bytes.size,
                        opts,
                    )
            } ?: error("Failed to decode bitmap")
        }

    private fun decodeBounds(
        context: Context,
        source: Source,
        resolver: ContentResolver,
    ): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        when (source) {
            is Source.File -> BitmapFactory.decodeFile(source.file.absolutePath, opts)
            is Source.Uri ->
                resolver.openInputStream(source.uri).use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }

            is Source.Bytes ->
                BitmapFactory.decodeByteArray(
                    source.bytes,
                    0,
                    source.bytes.size,
                    opts,
                )
        }
        return (opts.outWidth to opts.outHeight)
    }

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

    private fun Bitmap.scaleTo(
        w: Int,
        h: Int,
    ): Bitmap {
        if (this.width == w && this.height == h) return this
        return Bitmap.createScaledBitmap(this, w, h, true)
    }

    private suspend fun encodeAdaptive(
        context: Context,
        source: Source,
        srcBitmap: Bitmap,
        options: Options,
        format: CompressFormat,
        outFile: File,
        progress: ProgressEmitter?,
    ) {
        if (options.maxBytes == null) {
            FileOutputStream(outFile).use { fos ->
                srcBitmap.compress(format.toAndroid(options), options.quality.coerceIn(0, 100), fos)
            }
            return
        }

        var quality = options.quality.coerceIn(0, 100)
        var width = srcBitmap.width
        var height = srcBitmap.height
        var current = srcBitmap

        val minQuality = 30
        val minEdge = 320
        val maxIterations = 10
        var iteration = 0

        while (iteration < maxIterations) {
            progress?.emit(Step.Encoding, 70 + (iteration * 2))

            val baos = ByteArrayOutputStream()
            baos.use {
                current.compress(format.toAndroid(options), quality, it)
            }
            val bytes = baos.toByteArray()

            if (bytes.size.toLong() <= options.maxBytes) {
                BufferedOutputStream(FileOutputStream(outFile)).use { it.write(bytes) }
                if (current !== srcBitmap) current.recycle()
                return
            }

            if (format.isLossy() && quality > minQuality) {
                quality = (quality * 0.85f).roundToInt().coerceAtLeast(minQuality)
            } else {
                val newW = (width * 0.85f).roundToInt().coerceAtLeast(minEdge)
                val newH = (height * 0.85f).roundToInt().coerceAtLeast(minEdge)
                if (newW == width && newH == height) {
                    BufferedOutputStream(FileOutputStream(outFile)).use { it.write(bytes) }
                    if (current !== srcBitmap) current.recycle()
                    return
                }
                val next = current.scaleTo(newW, newH)
                if (current !== srcBitmap) current.recycle()
                current = next
                width = newW
                height = newH
            }

            iteration++
        }

        FileOutputStream(outFile).use { fos ->
            current.compress(format.toAndroid(options), quality, fos)
        }
        if (current !== srcBitmap) current.recycle()
    }

    private fun copyExif(
        context: Context,
        source: Source,
        outFile: File,
    ) {
        val srcExif =
            when (source) {
                is Source.File -> ExifInterface(source.file.absolutePath)
                is Source.Uri ->
                    context.contentResolver.openInputStream(source.uri).use { input ->
                        if (input == null) return
                        ExifInterface(input)
                    }

                is Source.Bytes -> ExifInterface(ByteArrayInputStream(source.bytes))
            } ?: return

        val dstExif = ExifInterface(outFile.absolutePath)
        val tags =
            arrayOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
            )
        for (tag in tags) {
            val value = srcExif.getAttribute(tag)
            if (value != null) dstExif.setAttribute(tag, value)
        }
        dstExif.saveAttributes()
    }

    private fun probeSizeAndMime(
        context: Context,
        source: Source,
        resolver: ContentResolver,
    ): Triple<Int, Int, String?> {
        // Use lightweight bounds decode for all API levels to avoid full decode
        val (w, h) = decodeBounds(context, source, resolver)
        val mime =
            when (source) {
                is Source.Uri -> resolver.getType(source.uri)
                is Source.File -> guessMimeFromName(source.file.name)
                is Source.Bytes -> null
            }
        return Triple(w, h, mime)
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

    // endregion
}

private interface ProgressEmitter {
    suspend fun emit(
        step: Step,
        percent: Int,
    )
}
