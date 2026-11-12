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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.joelromanpr.tinycompressor.CompressFormat
import com.joelromanpr.tinycompressor.Options
import com.joelromanpr.tinycompressor.Step
import java.util.Locale
import kotlin.math.roundToInt

private enum class Target(
    val label: String,
    val bytes: Long?,
) {
    NONE("No cap", null),
    SMALL("100 KB", 100_000),
    MEDIUM("500 KB", 500_000),
    LARGE("1 MB", 1_000_000),
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun CompressionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val session = remember(context) { PlaygroundSession(context) }
    var target by remember { mutableStateOf(Target.MEDIUM) }
    var format by remember { mutableStateOf(CompressFormat.JPEG) }
    var quality by remember { mutableStateOf(82f) }
    val options =
        Options(
            maxWidth = 1600,
            maxHeight = 1600,
            format = format,
            quality = quality.roundToInt(),
            maxBytes = target.bytes,
            keepExif = false,
        )

    DisposableEffect(session) { onDispose { session.close() } }
    val completedFile = session.output?.file
    LaunchedEffect(completedFile) {
        if (completedFile != null) scrollState.animateScrollTo(0)
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) session.run(scope, options, pickedUri = uri)
        }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TINY COMPRESSOR KTX", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Smaller photos. Ready to upload.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Pick a photo, set a limit, and compare the result.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f)) {
                Text("Choose photo")
            }
            OutlinedButton(onClick = { session.run(scope, options, sample = true) }, modifier = Modifier.weight(1f)) {
                Text("Try sample")
            }
        }

        val input = session.input
        val output = session.output
        if (input != null) {
            Comparison(input, output)
            if (output != null) {
                Results(input, output)
            } else if (session.busy) {
                LinearProgressIndicator(progress = { (session.progress?.percent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stageLabel(session.progress?.step), style = MaterialTheme.typography.bodySmall)
            }
            if (input.isSample) {
                Text(
                    "Sample: Earth, Apollo 17 · NASA / Apollo 17 crew",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (session.busy) "Opening image…" else "Your photo stays on your device.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "No server, API key, or photo-library permission needed. The sample is a real NASA photograph.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        session.message?.let { message ->
            Text(
                message,
                color = if (session.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Output settings", style = MaterialTheme.typography.titleMedium)
                Text("Maximum file size", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Target.values().forEach { option ->
                        FilterChip(selected = target == option, onClick = { target = option }, label = { Text(option.label) })
                    }
                }
                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompressFormat.values().forEach { option ->
                        FilterChip(selected = format == option, onClick = { format = option }, label = {
                            Text(
                                if (option ==
                                    CompressFormat.WEBP
                                ) {
                                    "WebP"
                                } else {
                                    option.name
                                },
                            )
                        })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Quality", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (format ==
                            CompressFormat.PNG
                        ) {
                            "Lossless"
                        } else {
                            "${quality.roundToInt()}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Slider(
                    value = quality,
                    onValueChange = { quality = it },
                    valueRange = 1f..100f,
                    enabled = format != CompressFormat.PNG,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Compression quality"
                        },
                )
                Text(
                    when (format) {
                        CompressFormat.PNG -> "PNG is lossless. Quality does not apply."
                        CompressFormat.JPEG -> "PNG inputs stay PNG. Choose WebP to convert them."
                        CompressFormat.WEBP -> "WebP supports transparency. Quality 100 uses lossless encoding on Android 10+, unless a size cap lowers quality."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Fits within 1600 × 1600. EXIF metadata, including GPS, is removed. A size cap may reduce quality and dimensions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.busy) {
                    OutlinedButton(onClick = { session.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                } else {
                    Button(
                        onClick = { session.run(scope, options) },
                        enabled = input != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Compress image") }
                }
            }
        }
        Text(
            "KB = 1,000 bytes. An impossible size limit fails instead of returning an oversized file. Demo files are temporary app-cache files.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun Comparison(
    input: DemoInput,
    output: DemoOutput?,
) {
    var split by remember(input.file, output?.file) { mutableStateOf(0.5f) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF101A20))
                .pointerInput(output?.file) {
                    if (output != null) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            split = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
                },
        ) {
            if (output != null) {
                Image(rememberAsyncImagePainter(output.file), "Compressed image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Image(
                rememberAsyncImagePainter(input.file),
                "Original image",
                Modifier.fillMaxSize().drawWithContent {
                    clipRect(
                        right =
                            size.width *
                                if (output ==
                                    null
                                ) {
                                    1f
                                } else {
                                    split
                                },
                    ) { this@drawWithContent.drawContent() }
                },
                contentScale = ContentScale.Fit,
            )
            Text(
                "Original",
                Modifier
                    .align(
                        Alignment.TopStart,
                    ).padding(
                        12.dp,
                    ).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            if (output != null) {
                Text(
                    "Compressed",
                    Modifier
                        .align(
                            Alignment.TopEnd,
                        ).padding(
                            12.dp,
                        ).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Canvas(Modifier.fillMaxSize()) {
                    val x = size.width * split
                    drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                    drawCircle(Color.White, 14.dp.toPx(), Offset(x, size.height / 2))
                    drawLine(
                        Color(0xFF163F3A),
                        Offset(x - 4.dp.toPx(), size.height / 2),
                        Offset(x + 4.dp.toPx(), size.height / 2),
                        2.dp.toPx(),
                    )
                }
            }
        }
        if (output != null) {
            Slider(
                value = split,
                onValueChange = { split = it },
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Compare original on the left and compressed image on the right"
                    },
            )
            Text(
                "Drag to compare · original left / compressed right",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun Results(
    input: DemoInput,
    output: DemoOutput,
) {
    val saved = (1.0 - output.info.bytes.toDouble() / input.info.bytes.coerceAtLeast(1)) * 100
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                output.targetBytes?.let { "Within ${formatBytes(it)} · target met" } ?: "Compression complete",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Original", formatBytes(input.info.bytes), "${input.info.width} × ${input.info.height}", Modifier.weight(1f))
                Metric("Output", formatBytes(output.info.bytes), "${output.info.width} × ${output.info.height}", Modifier.weight(1f))
                Metric(
                    if (saved >=
                        0
                    ) {
                        "Saved"
                    } else {
                        "Larger by"
                    },
                    "${kotlin.math.abs(saved).roundToInt()}%",
                    "${output.elapsedMillis} ms",
                    Modifier.weight(1f),
                )
            }
            Text(
                "${output.info.mime.removePrefix("image/").uppercase(Locale.ROOT)} · EXIF removed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun Metric(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatBytes(bytes: Long): String =
    if (bytes >=
        1_000_000
    ) {
        String.format(Locale.ROOT, "%.2f MB", bytes / 1_000_000.0)
    } else {
        String.format(Locale.ROOT, "%.1f KB", bytes / 1_000.0)
    }

private fun stageLabel(step: Step?): String =
    when (step) {
        Step.Loading, Step.Decoding, null -> "Reading image…"
        Step.Resizing -> "Resizing image…"
        Step.Encoding -> "Finding an encoding that fits…"
        Step.Writing, Step.Done -> "Finishing image…"
    }
