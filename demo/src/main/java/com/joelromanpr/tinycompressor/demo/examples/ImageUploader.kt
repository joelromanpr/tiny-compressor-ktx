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

import android.content.Context
import android.net.Uri
import com.joelromanpr.tinycompressor.CompressionResult
import com.joelromanpr.tinycompressor.Destination
import com.joelromanpr.tinycompressor.ImageCompressor
import com.joelromanpr.tinycompressor.Options
import com.joelromanpr.tinycompressor.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * An application-provided network client. Return only when the server accepts the upload and the
 * request body is closed. Consume [image]'s file before returning; the recipe deletes it afterward.
 * Use its actual MIME type. Propagate coroutine cancellation and send [idempotencyKey] to a backend
 * that deduplicates requests: a response can be lost after the server has already accepted a file.
 */
fun interface ImageUploader {
    suspend fun upload(
        image: CompressionResult,
        idempotencyKey: String,
    )
}

/** The network adapter should use this only for errors it knows are safe to retry. */
class TransientUploadException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Metadata that remains useful after the recipe deletes its temporary output file. */
data class UploadReceipt(
    val bytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
)

/** A foreground upload. It never takes ownership of, deletes, or replaces the picker input. */
suspend fun uploadPickedImage(
    context: Context,
    pickedUri: Uri,
    uploader: ImageUploader,
    idempotencyKey: String,
    onPrepared: (CompressionResult) -> Unit = {},
): UploadReceipt {
    val directory = File(context.cacheDir, "foreground-upload-${UUID.randomUUID()}")
    try {
        val image =
            ImageCompressor.compressWithResult(
                context,
                Source.Uri(pickedUri),
                Options.forUpload(maxBytes = 500_000).copy(destination = Destination.File(File(directory, "output"))),
            )
        onPrepared(image)
        uploader.upload(image, idempotencyKey)
        return UploadReceipt(image.outputBytes, image.width, image.height, image.mimeType)
    } finally {
        // A caller can be cancelled while a suspend compression hands back its result. Knowing the
        // destination in advance lets us remove that output even if the result was never delivered.
        withContext(NonCancellable + Dispatchers.IO) { directory.deleteRecursively() }
    }
}
