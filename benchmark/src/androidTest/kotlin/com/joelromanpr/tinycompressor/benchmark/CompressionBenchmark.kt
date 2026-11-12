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
package com.joelromanpr.tinycompressor.benchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joelromanpr.tinycompressor.CompressFormat
import com.joelromanpr.tinycompressor.ImageCompressor
import com.joelromanpr.tinycompressor.Options
import com.joelromanpr.tinycompressor.Source
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** An opt-in measurement harness, not a performance assertion in the library's test suite. */
@RunWith(AndroidJUnit4::class)
class CompressionBenchmark {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.filesDir, "benchmark").apply { mkdirs() }

    @Test
    fun measureCompression() =
        runBlocking {
            val arguments = InstrumentationRegistry.getArguments()
            val iterations = (arguments.getString("iterations")?.toIntOrNull() ?: 5).coerceIn(1, 100)
            val warmups = (arguments.getString("warmups")?.toIntOrNull() ?: 2).coerceIn(0, 20)
            val rows = JSONArray()
            for (fixture in createFixtures()) {
                val engines = listOf("platform-fixed", "library-fixed", "library-100kb")
                for (engine in engines) {
                    repeat(warmups) { compress(fixture, engine).delete() }
                    val times = mutableListOf<Double>()
                    repeat(iterations) {
                        val start = SystemClock.elapsedRealtimeNanos()
                        val output = compress(fixture, engine)
                        times += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                        output.delete()
                    }
                    val (output, memory) = measureMemory { compress(fixture, engine) }
                    try {
                        val decoded =
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(output)) { decoder, _, _ ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                            }
                        try {
                            val quality = comparePixels(fixture.file, decoded)
                            rows.put(
                                JSONObject()
                                    .put("fixture", fixture.name)
                                    .put("inputSha256", sha256(fixture.file))
                                    .put("inputBytes", fixture.file.length())
                                    .put("engine", engine)
                                    .put("requestedFormat", fixture.format.name)
                                    .put("requestedQuality", 80)
                                    .put("maxWidth", 1280)
                                    .put("maxHeight", 1280)
                                    .put("maxBytes", if (engine == "library-100kb") 100_000 else JSONObject.NULL)
                                    .put("outputBytes", output.length())
                                    .put("outputWidth", decoded.width)
                                    .put("outputHeight", decoded.height)
                                    .put("outputMime", mimeType(output))
                                    .put("targetMet", if (engine == "library-100kb") output.length() <= 100_000 else JSONObject.NULL)
                                    .put("durationMs", JSONArray(times))
                                    .put("medianMs", percentile(times, 0.5))
                                    .put("p90Ms", percentile(times, 0.9))
                                    .put("memory", memory)
                                    .put("quality", quality),
                            )
                        } finally {
                            decoded.recycle()
                        }
                    } finally {
                        output.delete()
                    }
                }
            }
            val report =
                JSONObject()
                    .put("schemaVersion", 1)
                    .put("revision", BuildConfig.REVISION)
                    .put("libraryVersion", BuildConfig.LIBRARY_VERSION)
                    .put(
                        "device",
                        JSONObject()
                            .put("manufacturer", Build.MANUFACTURER)
                            .put("model", Build.MODEL)
                            .put("api", Build.VERSION.SDK_INT)
                            .put("fingerprint", Build.FINGERPRINT)
                            .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList())),
                    ).put("iterations", iterations)
                    .put("warmups", warmups)
                    .put("buildType", "debug")
                    .put("results", rows)
            File(directory, "results.json").writeText(report.toString(2))
            println("BENCHMARK_REPORT=files/benchmark/results.json")
        }

    private suspend fun compress(
        fixture: Fixture,
        engine: String,
    ): File {
        if (engine != "platform-fixed") {
            val output =
                ImageCompressor.compress(
                    context,
                    Source.File(fixture.file),
                    Options(
                        maxWidth = 1280,
                        maxHeight = 1280,
                        format = fixture.format,
                        quality = 80,
                        keepExif = false,
                        maxBytes = if (engine == "library-100kb") 100_000 else null,
                    ),
                )
            if (engine == "library-100kb") {
                check(output.length() <= 100_000) { "Byte cap exceeded: ${output.length()} bytes" }
            }
            return output
        }
        val bitmap = decodeReference(fixture.file, 1280, 1280)
        val output = File.createTempFile("platform-", fixture.format.defaultExtension(), directory)
        try {
            output.outputStream().buffered().use { stream ->
                check(bitmap.compress(platformFormat(fixture.format), 80, stream))
            }
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun measureMemory(block: suspend () -> File): Pair<File, JSONObject> {
        val before = processPss()
        val maximum = AtomicInteger(before)
        val samples = AtomicInteger(1)
        val running = AtomicBoolean(true)
        val sampler =
            thread(name = "benchmark-pss") {
                while (running.get()) {
                    maximum.accumulateAndGet(processPss(), ::max)
                    samples.incrementAndGet()
                    Thread.sleep(25)
                }
            }
        val output =
            try {
                block()
            } finally {
                running.set(false)
                sampler.join()
            }
        val after = processPss()
        maximum.accumulateAndGet(after, ::max)
        samples.incrementAndGet()
        return output to
            JSONObject()
                .put("processPssBeforeKiB", before)
                .put("processPssAfterKiB", after)
                .put("observedProcessPssMaxKiB", maximum.get())
                .put("sampleIntervalMs", 25)
                .put("samples", samples.get())
    }

    private fun processPss(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss

    private fun comparePixels(
        source: File,
        output: Bitmap,
    ): JSONObject {
        val reference = decodeReference(source, output.width, output.height)
        val aligned =
            if (reference.width == output.width && reference.height == output.height) {
                reference
            } else {
                Bitmap.createScaledBitmap(reference, output.width, output.height, true).also { reference.recycle() }
            }
        try {
            val expected = IntArray(output.width)
            val actual = IntArray(output.width)
            var squaredError = 0.0
            var alphaError = 0.0
            for (y in 0 until output.height) {
                aligned.getPixels(expected, 0, output.width, 0, y, output.width, 1)
                output.getPixels(actual, 0, output.width, 0, y, output.width, 1)
                for (x in expected.indices) {
                    val a = expected[x]
                    val b = actual[x]
                    alphaError += kotlin.math.abs(Color.alpha(a) - Color.alpha(b))
                    for (shift in intArrayOf(16, 8, 0)) {
                        val channelA = whiteComposite(a, shift)
                        val channelB = whiteComposite(b, shift)
                        val difference = channelA - channelB
                        squaredError += difference * difference
                    }
                }
            }
            val pixels = output.width.toDouble() * output.height
            val mse = squaredError / (pixels * 3)
            return JSONObject()
                .put("rgbPsnrDbOverWhite", if (mse == 0.0) JSONObject.NULL else 10 * log10(255.0 * 255.0 / mse))
                .put("identicalRgbOverWhite", mse == 0.0)
                .put("alphaMeanAbsoluteError", alphaError / pixels)
                .put("referenceWidth", aligned.width)
                .put("referenceHeight", aligned.height)
        } finally {
            aligned.recycle()
        }
    }

    private fun whiteComposite(
        pixel: Int,
        shift: Int,
    ): Double {
        val alpha = Color.alpha(pixel) / 255.0
        return ((pixel shr shift) and 255) * alpha + 255 * (1 - alpha)
    }

    private fun decodeReference(
        file: File,
        maxWidth: Int,
        maxHeight: Int,
    ): Bitmap {
        val bitmap =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                val ratio = min(1.0, min(maxWidth.toDouble() / info.size.width, maxHeight.toDouble() / info.size.height))
                decoder.setTargetSize(max(1, (info.size.width * ratio).roundToInt()), max(1, (info.size.height * ratio).roundToInt()))
            }
        val ratio = min(1.0, min(maxWidth.toDouble() / bitmap.width, maxHeight.toDouble() / bitmap.height))
        if (ratio == 1.0) return bitmap
        return Bitmap
            .createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * ratio).roundToInt()),
                max(1, (bitmap.height * ratio).roundToInt()),
                true,
            ).also { bitmap.recycle() }
    }

    private fun createFixtures(): List<Fixture> {
        val earth = File(directory, "earth-apollo17.jpg")
        context.assets.open("earth-apollo17.jpg").use { input -> earth.outputStream().use(input::copyTo) }
        val screenshot =
            createImage("screenshot", 1440, 2560, CompressFormat.PNG) { canvas, paint ->
                canvas.drawColor(Color.rgb(247, 246, 242))
                paint.color = Color.rgb(28, 67, 57)
                canvas.drawRect(0f, 0f, 1440f, 340f, paint)
                paint.color = Color.WHITE
                paint.textSize = 72f
                canvas.drawText("Project photos", 80f, 180f, paint)
                for (row in 0 until 8) {
                    val top = 440f + row * 240
                    paint.color = Color.rgb(220 - row * 4, 226 - row * 3, 219)
                    canvas.drawRect(80f, top, 360f, top + 180, paint)
                    paint.color = Color.rgb(44, 48, 45)
                    paint.textSize = 48f
                    canvas.drawText("Image ${row + 1}", 420f, top + 60, paint)
                    paint.color = Color.rgb(144, 148, 145)
                    canvas.drawRect(420f, top + 100, 1280f, top + 124, paint)
                }
            }
        val transparent =
            createImage("transparent", 1800, 1200, CompressFormat.PNG) { canvas, paint ->
                canvas.drawColor(Color.TRANSPARENT)
                for (i in 0 until 12) {
                    paint.color = Color.argb(40 + i * 17, 20 + i * 15, 120, 210 - i * 9)
                    canvas.drawCircle(160f + i * 130, 600f, 260f, paint)
                }
            }
        val panorama =
            createImage("panorama", 6000, 600, CompressFormat.JPEG) { canvas, paint ->
                canvas.drawColor(Color.rgb(126, 180, 210))
                for (i in 0 until 240) {
                    paint.color = Color.rgb(20 + (i * 7) % 130, 70 + (i * 11) % 130, 30 + (i * 3) % 90)
                    canvas.drawRect(i * 25f, 180f + (i * 31) % 220, i * 25f + 50, 600f, paint)
                }
            }
        val rotated = File(directory, "rotated.jpg")
        earth.copyTo(rotated, overwrite = true)
        ExifInterface(rotated).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        return listOf(
            Fixture("earth-photo", earth, CompressFormat.JPEG),
            Fixture("earth-webp", earth, CompressFormat.WEBP),
            screenshot,
            transparent,
            panorama,
            Fixture("rotated-photo", rotated, CompressFormat.JPEG),
        )
    }

    private fun createImage(
        name: String,
        width: Int,
        height: Int,
        format: CompressFormat,
        draw: (Canvas, Paint) -> Unit,
    ): Fixture {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val file = File(directory, "$name${format.defaultExtension()}")
        try {
            draw(Canvas(bitmap), Paint(Paint.ANTI_ALIAS_FLAG))
            file.outputStream().use { check(bitmap.compress(platformFormat(format), 95, it)) }
        } finally {
            bitmap.recycle()
        }
        return Fixture(name, file, format)
    }

    private fun platformFormat(format: CompressFormat): Bitmap.CompressFormat =
        when (format) {
            CompressFormat.JPEG -> Bitmap.CompressFormat.JPEG
            CompressFormat.PNG -> Bitmap.CompressFormat.PNG
            CompressFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
        }

    private fun mimeType(file: File): String {
        var mime = "unknown"
        ImageDecoder
            .decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                mime = info.mimeType
                decoder.setTargetSize(1, 1)
            }.recycle()
        return mime
    }

    private fun percentile(
        values: List<Double>,
        quantile: Double,
    ): Double =
        values.sorted()[
            (
                kotlin.math.ceil(values.size * quantile).toInt() -
                    1
            ).coerceIn(values.indices),
        ]

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Fixture(
        val name: String,
        val file: File,
        val format: CompressFormat,
    )
}
