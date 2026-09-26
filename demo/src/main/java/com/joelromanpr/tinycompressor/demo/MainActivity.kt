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

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.joelromanpr.tinycompressor.ImageCompressor
import com.joelromanpr.tinycompressor.Options
import com.joelromanpr.tinycompressor.Progress
import com.joelromanpr.tinycompressor.Source
import com.joelromanpr.tinycompressor.demo.ui.theme.DemoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CompressionScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun CompressionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val compressionJob = remember { mutableStateOf<Job?>(null) }

    var originalUri by remember { mutableStateOf<Uri?>(null) }
    var originalSize by remember { mutableStateOf<Long?>(null) }
    var compressedFile by remember { mutableStateOf<File?>(null) }
    var compressionProgress by remember { mutableStateOf<Progress?>(null) }
    var isCompressing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                compressionJob.value?.cancel()
                originalUri = uri
                originalSize = null
                compressedFile = null
                compressionProgress = null
                errorMessage = null
                isCompressing = true

                compressionJob.value =
                    coroutineScope.launch {
                        try {
                            originalSize =
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                                            descriptor.length.takeIf { it >= 0 }
                                        }
                                    }.getOrNull()
                                }

                            ImageCompressor
                                .compressAsFlow(
                                    context = context,
                                    source = Source.Uri(uri),
                                    options = Options(keepExif = false),
                                ).collect { progress ->
                                    compressionProgress = progress
                                    if (progress.step.isDone()) {
                                        compressedFile = progress.file
                                        isCompressing = false
                                    }
                                }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            isCompressing = false
                            errorMessage = error.message ?: "Could not compress this image."
                        }
                    }
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tiny Compressor KTX", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Pick an image to compare the original and compressed output.")
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { imagePicker.launch("image/*") }) {
            Text(if (isCompressing) "Pick another image" else "Pick an image")
        }

        compressionProgress?.let { progress ->
            Spacer(modifier = Modifier.height(12.dp))
            Text("${progress.step}: ${progress.percent}%")
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Original", style = MaterialTheme.typography.titleMedium)
                Text(originalSize?.let(::formatSize) ?: "Size unavailable")
                originalUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Original image",
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Compressed", style = MaterialTheme.typography.titleMedium)
                Text(compressedFile?.length()?.let(::formatSize) ?: "No output yet")
                compressedFile?.let { file ->
                    Image(
                        painter = rememberAsyncImagePainter(file),
                        contentDescription = "Compressed image",
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "This demo leaves source EXIF out of the output. Compressed files are kept in the app cache.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatSize(bytes: Long): String = "${bytes / 1024} KiB"
