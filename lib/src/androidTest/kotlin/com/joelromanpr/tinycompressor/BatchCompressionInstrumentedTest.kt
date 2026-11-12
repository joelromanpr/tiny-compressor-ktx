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
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
public class BatchCompressionInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun unreadableInputDoesNotStopOtherImagesAndListKeepsInputOrder(): Unit =
        runBlocking {
            val sources =
                listOf(
                    Source.Bytes(jpegBytes()),
                    Source.Bytes(byteArrayOf(0, 1, 2)),
                    Source.Uri(Uri.parse("content://com.joelromanpr.tinycompressor.test.result-image/unknown")),
                )
            val results =
                ImageCompressor.compressBatch(
                    context,
                    sources,
                    Options.forUpload(50_000, maxWidth = 40, maxHeight = 40),
                    concurrency = 2,
                )
            try {
                assertEquals(listOf(0, 1, 2), results.map { it.index })
                assertTrue(results[1] is BatchItemResult.Failure)
                val jpeg = (results[0] as BatchItemResult.Success).result
                val png = (results[2] as BatchItemResult.Success).result
                assertEquals(CompressFormat.JPEG, jpeg.format)
                assertEquals(CompressFormat.PNG, png.format)
                for (result in listOf(jpeg, png)) {
                    assertTrue(result.file.exists())
                    assertEquals(40, result.width)
                    assertEquals(20, result.height)
                    assertEquals(result.file.length(), result.outputBytes)
                }
            } finally {
                results.filterIsInstance<BatchItemResult.Success>().forEach { it.result.file.delete() }
            }
        }

    @Test
    public fun publicFlowReportsAllCompletedItems(): Unit =
        runBlocking {
            val progress =
                ImageCompressor
                    .compressBatchAsFlow(
                        context,
                        listOf(Source.Bytes(jpegBytes()), Source.Bytes(jpegBytes())),
                        Options(keepExif = false),
                    ).toList()
            try {
                assertEquals(listOf(1, 2), progress.map { it.completed })
                assertTrue(progress.all { it.total == 2 })
                assertEquals(listOf(0, 1), progress.map { it.item.index })
                assertTrue(progress.all { it.item is BatchItemResult.Success })
            } finally {
                progress.map { it.item }.filterIsInstance<BatchItemResult.Success>().forEach { it.result.file.delete() }
            }
        }

    private fun jpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        try {
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
