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
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
public class ImageCompressorInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun byteArrayOutputDoesNotReplaceOrDeleteCallerFile(): Unit =
        runBlocking {
            val destination = File.createTempFile("preserve-", ".jpg", context.cacheDir)
            val original = "keep this file".toByteArray()
            destination.writeBytes(original)
            try {
                val encoded =
                    ImageCompressor.compressToByteArray(
                        context,
                        Source.Bytes(imageBytes(48, 32, Bitmap.CompressFormat.JPEG)),
                        Options(keepExif = false, destination = Destination.File(destination)),
                    )

                assertTrue(encoded.size > 2)
                assertEquals(0xff, encoded[0].toInt() and 0xff)
                assertEquals(0xd8, encoded[1].toInt() and 0xff)
                assertArrayEquals(original, destination.readBytes())
            } finally {
                destination.delete()
            }
        }

    @Test
    public fun pngBytesAndCacheExtensionAgree(): Unit =
        runBlocking {
            val input = File.createTempFile("transparent-", ".png", context.cacheDir)
            input.writeBytes(imageBytes(48, 32, Bitmap.CompressFormat.PNG))
            var output: File? = null
            try {
                val result =
                    ImageCompressor.compress(
                        context,
                        Source.File(input),
                        Options(
                            format = CompressFormat.JPEG,
                            keepExif = false,
                            destination = Destination.Cache("instrumentation"),
                        ),
                    )
                output = result

                assertTrue(result.name.endsWith(".png"))
                assertArrayEquals(
                    byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10),
                    result.readBytes().copyOfRange(0, 8),
                )
                val decoded = BitmapFactory.decodeFile(result.absolutePath)
                assertEquals(0, android.graphics.Color.alpha(decoded.getPixel(0, 0)))
                decoded.recycle()
            } finally {
                output?.delete()
                input.delete()
            }
        }

    @Test
    public fun impossibleByteLimitKeepsExistingDestination(): Unit =
        runBlocking {
            val destination = File.createTempFile("preserve-limit-", ".jpg", context.cacheDir)
            val original = "unchanged".toByteArray()
            destination.writeBytes(original)
            try {
                var failedForLimit = false
                try {
                    ImageCompressor.compress(
                        context,
                        Source.Bytes(imageBytes(64, 64, Bitmap.CompressFormat.JPEG)),
                        Options(
                            keepExif = false,
                            maxBytes = 1,
                            destination = Destination.File(destination),
                        ),
                    )
                } catch (_: IOException) {
                    failedForLimit = true
                }
                assertTrue("An impossible strict byte limit must fail", failedForLimit)
                assertArrayEquals(original, destination.readBytes())
            } finally {
                destination.delete()
            }
        }

    @Test
    public fun allExifOrientationsAreAppliedExactlyOnce(): Unit =
        runBlocking {
            for (orientation in 1..8) {
                val input = File.createTempFile("orientation-$orientation-", ".jpg", context.cacheDir)
                val output = File.createTempFile("upright-$orientation-", ".jpg", context.cacheDir)
                input.writeBytes(imageBytes(40, 20, Bitmap.CompressFormat.JPEG))
                ExifInterface(input).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                try {
                    ImageCompressor.compress(
                        context,
                        Source.File(input),
                        Options(
                            maxWidth = 100,
                            maxHeight = 100,
                            keepExif = true,
                            destination = Destination.File(output),
                        ),
                    )

                    val expectedWidth = if (orientation >= 5) 20 else 40
                    val expectedHeight = if (orientation >= 5) 40 else 20
                    val bitmap = BitmapFactory.decodeFile(output.absolutePath)
                    try {
                        assertEquals("Orientation $orientation width", expectedWidth, bitmap.width)
                        assertEquals("Orientation $orientation height", expectedHeight, bitmap.height)
                    } finally {
                        bitmap.recycle()
                    }
                    assertEquals(
                        "Orientation $orientation output EXIF",
                        ExifInterface.ORIENTATION_NORMAL,
                        ExifInterface(output).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        ),
                    )
                } finally {
                    input.delete()
                    output.delete()
                }
            }
        }

    @Test
    public fun concurrentCacheCallsGetDistinctFiles(): Unit =
        runBlocking {
            val input = imageBytes(48, 32, Bitmap.CompressFormat.JPEG)
            val outputs =
                coroutineScope {
                    (1..12)
                        .map {
                            async {
                                ImageCompressor.compress(
                                    context,
                                    Source.Bytes(input),
                                    Options(
                                        keepExif = false,
                                        destination = Destination.Cache("instrumentation-concurrent"),
                                    ),
                                )
                            }
                        }.awaitAll()
                }
            try {
                assertEquals(outputs.size, outputs.map { it.absolutePath }.toSet().size)
                assertTrue(outputs.all { it.length() > 0 })
            } finally {
                outputs.forEach { it.delete() }
            }
        }

    @Test
    public fun sourceAndDestinationMayBeTheSameFile(): Unit =
        runBlocking {
            val image = File.createTempFile("in-place-", ".jpg", context.cacheDir)
            image.writeBytes(imageBytes(80, 40, Bitmap.CompressFormat.JPEG))
            try {
                val result =
                    ImageCompressor.compress(
                        context,
                        Source.File(image),
                        Options(
                            maxWidth = 40,
                            maxHeight = 40,
                            destination = Destination.File(image),
                        ),
                    )
                assertEquals(image.absolutePath, result.absolutePath)
                val decoded = BitmapFactory.decodeFile(result.absolutePath)
                assertEquals(40, decoded.width)
                assertEquals(20, decoded.height)
                decoded.recycle()
            } finally {
                image.delete()
            }
        }

    @Test
    public fun byteLimitPreservesPanoramaShape(): Unit =
        runBlocking {
            val output =
                ImageCompressor.compress(
                    context,
                    Source.Bytes(noisyJpegBytes(1000, 100)),
                    Options(
                        maxWidth = 1000,
                        maxHeight = 1000,
                        quality = 90,
                        maxBytes = 3_000,
                        keepExif = false,
                    ),
                )
            try {
                assertTrue(output.length() <= 3_000)
                val bitmap = BitmapFactory.decodeFile(output.absolutePath)
                assertTrue(abs(bitmap.width.toDouble() / bitmap.height - 10.0) <= 1.0)
                bitmap.recycle()
            } finally {
                output.delete()
            }
        }

    @Test
    public fun mismatchedExplicitExtensionKeepsExistingDestination(): Unit =
        runBlocking {
            val input = File.createTempFile("transparent-source-", ".png", context.cacheDir)
            val destination = File.createTempFile("preserve-format-", ".jpg", context.cacheDir)
            val original = "do not replace".toByteArray()
            input.writeBytes(imageBytes(48, 32, Bitmap.CompressFormat.PNG))
            destination.writeBytes(original)
            try {
                var rejected = false
                try {
                    ImageCompressor.compress(
                        context,
                        Source.File(input),
                        Options(keepExif = false, destination = Destination.File(destination)),
                    )
                } catch (_: IllegalArgumentException) {
                    rejected = true
                }
                assertTrue("The file extension must match the encoded format", rejected)
                assertArrayEquals(original, destination.readBytes())
            } finally {
                input.delete()
                destination.delete()
            }
        }

    private fun noisyJpegBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = Random(1234)
        val pixels =
            IntArray(width * height) {
                android.graphics.Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun imageBytes(
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = if (format == Bitmap.CompressFormat.PNG && x < width / 2) 0 else 255
                bitmap.setPixel(x, y, android.graphics.Color.argb(alpha, x * 255 / width, y * 255 / height, 120))
            }
        }
        return try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(format, 95, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
