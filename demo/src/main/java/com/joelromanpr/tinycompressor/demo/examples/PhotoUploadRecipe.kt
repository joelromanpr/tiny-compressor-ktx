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
package com.joelromanpr.tinycompressor.demo.examples

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

private data class PickedUpload(
    val uri: Uri,
    val key: String = UUID.randomUUID().toString(),
)

/**
 * Drop into an app screen with its real [uploader]. This foreground recipe deliberately follows
 * composition lifetime; use [UploadQueue] when the operation must survive leaving the screen.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun PhotoUploadRecipe(
    uploader: ImageUploader,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<PickedUpload?>(null) }
    var busy by remember { mutableStateOf(false) }
    var retryAvailable by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Choose a photo to upload.") }

    fun start(request: PickedUpload) {
        val current = ++generation
        val previous = job
        previous?.cancel()
        selected = request
        busy = true
        retryAvailable = false
        message = "Preparing photo…"
        job =
            scope.launch {
                try {
                    previous?.join()
                    val receipt =
                        uploadPickedImage(context, request.uri, uploader, request.key) {
                            if (current == generation) message = "Uploading photo…"
                        }
                    if (current ==
                        generation
                    ) {
                        message = "Uploaded ${receipt.bytes} bytes · ${receipt.width} × ${receipt.height} · ${receipt.mimeType}"
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: TransientUploadException) {
                    if (current == generation) {
                        message = "Upload interrupted. Try again when your connection is ready."
                        retryAvailable = true
                    }
                } catch (_: Exception) {
                    if (current == generation) message = "Could not upload this photo. Choose another or check your account."
                } finally {
                    if (current == generation) busy = false
                }
            }
    }

    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) start(PickedUpload(uri))
        }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Upload a photo", style = MaterialTheme.typography.titleLarge)
        Text(message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) { Text("Choose photo") }
            if (busy) {
                OutlinedButton(onClick = {
                    generation++
                    job?.cancel()
                    busy = false
                    retryAvailable = false
                    message = "Upload cancelled."
                }) { Text("Cancel") }
            } else if (retryAvailable) {
                OutlinedButton(onClick = { selected?.let(::start) }) { Text("Retry upload") }
            }
        }
    }
}
