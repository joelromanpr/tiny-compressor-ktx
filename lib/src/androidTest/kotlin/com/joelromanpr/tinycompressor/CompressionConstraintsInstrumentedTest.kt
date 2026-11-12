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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
public class CompressionConstraintsInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun qualitySearchKeepsDimensionsWhenTheBudgetAllows(): Unit =
        runBlocking {
            val source = Source.Bytes(noisyImage())
            val reference = ImageCompressor.compressToByteArray(context, source, Options(quality = 77, keepExif = false))
            val result =
                ImageCompressor.compressWithResult(
                    context,
                    source,
                    Options(quality = 90, maxBytes = reference.size.toLong(), keepExif = false),
                    CompressionConstraints(minQuality = 70, minWidth = 320, minHeight = 240),
                )
            try {
                assertEquals(320, result.width)
                assertEquals(240, result.height)
                assertTrue(requireNotNull(result.appliedQuality) in 77..90)
                assertTrue(result.outputBytes <= reference.size)
            } finally {
                result.file.delete()
            }
        }

    @Test
    public fun impossibleConstraintsPreserveTheDestinationAndCleanStaging(): Unit =
        runBlocking {
            val directory = File(context.cacheDir, "constraints-${System.nanoTime()}").apply { mkdirs() }
            val destination = File(directory, "photo.jpg").apply { writeText("original") }
            val original = destination.readBytes()
            try {
                try {
                    ImageCompressor.compress(
                        context,
                        Source.Bytes(noisyImage()),
                        Options(maxBytes = 100, keepExif = false, destination = Destination.File(destination)),
                        CompressionConstraints(minQuality = 70, minWidth = 320, minHeight = 240),
                    )
                    fail("Expected an unsatisfiable byte limit")
                } catch (_: IOException) {
                    assertArrayEquals(original, destination.readBytes())
                    assertEquals(listOf("photo.jpg"), directory.listFiles()?.map { it.name })
                }
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    public fun bytesAndFlowRespectMinimumDimensions(): Unit =
        runBlocking {
            val source = Source.Bytes(noisyImage())
            val options = Options(maxBytes = 10_000, quality = 90, keepExif = false)
            val constraints = CompressionConstraints(minQuality = 65, minWidth = 80, minHeight = 60)
            val bytes = ImageCompressor.compressToByteArray(context, source, options, constraints)
            assertTrue(bytes.size <= 10_000)
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                assertTrue(bitmap.width >= 80 && bitmap.height >= 60)
            } finally {
                bitmap.recycle()
            }
            val done = ImageCompressor.compressAsFlow(context, source, options, constraints).last()
            val file = requireNotNull(done.file)
            try {
                assertEquals(Step.Done, done.step)
                assertTrue(file.length() <= 10_000)
            } finally {
                file.delete()
            }
        }

    @Test
    public fun smallerInputsAreNotEnlargedToMeetMinimumDimensions(): Unit =
        runBlocking {
            try {
                ImageCompressor.compressToByteArray(
                    context,
                    Source.Bytes(noisyImage()),
                    Options(keepExif = false),
                    CompressionConstraints(minWidth = 640, minHeight = 480),
                )
                fail("Expected an input below the minimum dimensions to fail")
            } catch (expected: IOException) {
                assertTrue(expected.message.orEmpty().contains("minimum"))
            }
        }

    private fun noisyImage(): ByteArray {
        val random = Random(407)
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.setPixels(IntArray(320 * 240) { random.nextInt() or (0xff shl 24) }, 0, 320, 0, 0, 320, 240)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
