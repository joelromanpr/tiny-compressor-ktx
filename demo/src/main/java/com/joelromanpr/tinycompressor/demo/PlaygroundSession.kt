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
package com.joelromanpr.tinycompressor.demo

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joelromanpr.tinycompressor.Destination
import com.joelromanpr.tinycompressor.ImageCompressor
import com.joelromanpr.tinycompressor.Options
import com.joelromanpr.tinycompressor.Progress
import com.joelromanpr.tinycompressor.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.time.TimeSource

internal data class ImageInfo(
    val bytes: Long,
    val width: Int,
    val height: Int,
    val mime: String,
)

internal data class DemoInput(
    val file: File,
    val info: ImageInfo,
    val isSample: Boolean,
)

internal data class DemoOutput(
    val file: File,
    val info: ImageInfo,
    val elapsedMillis: Long,
    val targetBytes: Long?,
)

/** Owns only this screen's cache files. Picker images are copied before processing. */
internal class PlaygroundSession(
    private val context: Context,
) {
    private val directory = File(context.cacheDir, "playground-${UUID.randomUUID()}")
    private var job: Job? = null
    private var generation = 0

    var input: DemoInput? by mutableStateOf(null)
        private set
    var output: DemoOutput? by mutableStateOf(null)
        private set
    var progress: Progress? by mutableStateOf(null)
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var message: String? by mutableStateOf(null)
        private set
    var failed: Boolean by mutableStateOf(false)
        private set

    fun run(
        scope: CoroutineScope,
        options: Options,
        pickedUri: Uri? = null,
        sample: Boolean = false,
    ) {
        if (input == null && pickedUri == null && !sample) return
        val current = ++generation
        val previousJob = job
        previousJob?.cancel()
        val previousOutput = output
        val previousInput = input
        val newInput = if (sample || pickedUri != null) File(directory, "input-${UUID.randomUUID()}") else null
        // An extensionless destination allows the library to resolve PNG preservation correctly.
        val outputDirectory = File(directory, "output-${UUID.randomUUID()}")
        val outputFile = File(outputDirectory, "image")
        output = null
        progress = null
        message = null
        failed = false
        busy = true
        job =
            scope.launch {
                var publishedOutput = false
                var publishedInput = false
                try {
                    // Wait for a cancelled encoder before removing any file it might still read.
                    previousJob?.join()
                    withContext(Dispatchers.IO) {
                        previousOutput?.file?.parentFile?.deleteRecursively()
                    }
                    val selected =
                        if (newInput != null) {
                            withContext(Dispatchers.IO) {
                                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cache unavailable")
                                val stream =
                                    if (sample) {
                                        context.assets.open("earth-apollo17.jpg")
                                    } else {
                                        context.contentResolver.openInputStream(requireNotNull(pickedUri))
                                            ?: throw IOException("Image unavailable")
                                    }
                                stream.use { source -> newInput.outputStream().use { source.copyTo(it) } }
                                DemoInput(newInput, imageInfo(newInput), sample)
                            }
                        } else {
                            requireNotNull(previousInput)
                        }
                    if (current != generation) return@launch
                    input = selected
                    publishedInput = true
                    if (newInput != null) {
                        withContext(Dispatchers.IO) { previousInput?.file?.delete() }
                    }
                    val started = TimeSource.Monotonic.markNow()
                    ImageCompressor
                        .compressAsFlow(
                            context,
                            Source.File(selected.file),
                            options.copy(destination = Destination.File(outputFile)),
                        ).collect { update ->
                            if (current == generation) progress = update
                        }
                    val elapsed = started.elapsedNow().inWholeMilliseconds
                    val info = withContext(Dispatchers.IO) { imageInfo(outputFile) }
                    if (current == generation) {
                        output = DemoOutput(outputFile, info, elapsed, options.maxBytes)
                        publishedOutput = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (current == generation) {
                        failed = true
                        message = "Could not prepare this image. Try another photo, a larger limit, or a different format."
                    }
                } finally {
                    if (current == generation) busy = false
                    withContext(NonCancellable + Dispatchers.IO) {
                        previousJob?.join()
                        previousOutput?.file?.parentFile?.deleteRecursively()
                        if (!publishedOutput) outputDirectory.deleteRecursively()
                        if (!publishedInput) newInput?.delete()
                    }
                }
            }
    }

    fun cancel() {
        generation++
        job?.cancel()
        busy = false
        progress = null
        failed = false
        message = "Cancelled. You can change the settings and try again."
    }

    fun close() {
        generation++
        val activeJob = job
        activeJob?.cancel()
        // This short cleanup must outlive the composition's cancelled coroutine scope.
        CoroutineScope(Dispatchers.IO).launch {
            activeJob?.join()
            directory.deleteRecursively()
        }
    }
}

private fun imageInfo(file: File): ImageInfo {
    var width = 0
    var height = 0
    var mime = "image/*"
    val thumbnail =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            width = info.size.width
            height = info.size.height
            mime = info.mimeType
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(1, 1)
        }
    thumbnail.recycle()
    return ImageInfo(file.length(), width, height, mime)
}
