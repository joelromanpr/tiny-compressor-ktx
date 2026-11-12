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
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
public class CompressionResultInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun inPlaceResultKeepsOriginalSizeAndUploadPresetStripsGps(): Unit =
        runBlocking {
            val image = File.createTempFile("result-in-place-", ".jpg", context.cacheDir)
            image.writeBytes(imageBytes())
            ExifInterface(image).apply {
                setLatLong(18.4, -66.1)
                saveAttributes()
            }
            val originalBytes = image.length()
            try {
                val result =
                    ImageCompressor.compressWithResult(
                        context,
                        Source.File(image),
                        Options.forUpload(50_000, maxWidth = 80, maxHeight = 80, quality = 75).copy(
                            destination = Destination.File(image),
                        ),
                    )

                assertEquals(image.absolutePath, result.file.absolutePath)
                assertEquals(originalBytes, result.inputBytes)
                assertEquals(image.length(), result.outputBytes)
                assertEquals(80, result.width)
                assertEquals(40, result.height)
                assertEquals(CompressFormat.JPEG, result.format)
                assertEquals("image/jpeg", result.mimeType)
                assertEquals(75, result.appliedQuality)
                assertNull(ExifInterface(image).latLong)
            } finally {
                image.delete()
            }
        }

    @Test
    public fun cappedResultDescribesTheFinalEncodedImage(): Unit =
        runBlocking {
            val input = imageBytes()
            val result =
                ImageCompressor.compressWithResult(
                    context,
                    Source.Bytes(input),
                    Options(maxWidth = 256, maxHeight = 256, quality = 90, maxBytes = 1500, keepExif = false),
                )
            try {
                val decoded = BitmapFactory.decodeFile(result.file.absolutePath)
                try {
                    assertEquals(decoded.width, result.width)
                    assertEquals(decoded.height, result.height)
                } finally {
                    decoded.recycle()
                }
                assertEquals(input.size.toLong(), result.inputBytes)
                assertEquals(result.file.length(), result.outputBytes)
                assertTrue(result.outputBytes <= 1500)
                assertTrue(requireNotNull(result.appliedQuality) in 0..90)
                assertTrue(result.width < 256)
                assertTrue(requireNotNull(result.savingsPercent) > 0)
            } finally {
                result.file.delete()
            }
        }

    @Test
    public fun uriResultsUseReportedSizeAndResolvedFormatWithoutRequiringSizeMetadata(): Unit =
        runBlocking {
            for (kind in listOf("known", "unknown", "rejected")) {
                val uri = Uri.parse("content://com.joelromanpr.tinycompressor.test.result-image/$kind")
                val result =
                    ImageCompressor.compressWithResult(
                        context,
                        Source.Uri(uri),
                        Options(maxWidth = 40, maxHeight = 40, format = CompressFormat.JPEG, keepExif = false),
                    )
                try {
                    assertEquals(CompressFormat.PNG, result.format)
                    assertEquals("image/png", result.mimeType)
                    assertNull(result.appliedQuality)
                    assertEquals(40, result.width)
                    assertEquals(20, result.height)
                    assertEquals(result.file.length(), result.outputBytes)
                    if (kind == "known") {
                        val originalBytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
                        assertEquals(originalBytes.size.toLong(), result.inputBytes)
                    } else {
                        assertNull(result.inputBytes)
                        assertNull(result.savingsPercent)
                    }
                } finally {
                    result.file.delete()
                }
            }
        }

    private fun imageBytes(): ByteArray {
        val random = Random(42)
        val bitmap = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(256 * 128) { 0xff000000.toInt() or random.nextInt(0x1000000) }
            bitmap.setPixels(pixels, 0, 256, 0, 0, 256, 128)
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
