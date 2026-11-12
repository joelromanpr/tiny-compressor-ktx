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
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.joelromanpr.tinycompressor.Destination
import com.joelromanpr.tinycompressor.ImageCompressor
import com.joelromanpr.tinycompressor.Options
import com.joelromanpr.tinycompressor.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal const val MAX_BYTES_KEY = "maxBytes"
internal const val MAX_UPLOAD_ATTEMPTS = 3

internal fun uploadInputDirectory(
    context: Context,
    id: UUID,
): File = File(context.noBackupFilesDir, "pending-image-uploads/$id")

/** Created by [UploadWorkerFactory], so a real uploader is required even after process recreation. */
class UploadWorker(
    context: Context,
    parameters: WorkerParameters,
    private val uploader: ImageUploader,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val input = File(uploadInputDirectory(applicationContext, id), "source")
        val attemptDirectory = File(applicationContext.cacheDir, "worker-upload-$id-${UUID.randomUUID()}")
        try {
            val image =
                ImageCompressor.compressWithResult(
                    applicationContext,
                    Source.File(input),
                    Options
                        .forUpload(inputData.getLong(MAX_BYTES_KEY, 500_000))
                        .copy(destination = Destination.File(File(attemptDirectory, "output"))),
                )
            try {
                uploader.upload(image, id.toString())
            } catch (_: TransientUploadException) {
                return if (runAttemptCount + 1 < MAX_UPLOAD_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf("reason" to "upload_retries_exhausted"))
                }
            }
            return Result.success(workDataOf("outputBytes" to image.outputBytes, "mimeType" to image.mimeType))
        } catch (cancelled: CancellationException) {
            // A constraint change or system stop can reschedule this request. Preserve its input.
            throw cancelled
        } catch (_: Exception) {
            // Decode, size-limit, permission and unclassified server errors are not transient.
            return Result.failure(workDataOf("reason" to "prepare_or_upload_failed"))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { attemptDirectory.deleteRecursively() }
        }
        // UploadQueue removes durable input after WorkManager confirms a terminal state. Removing
        // it here would race a system stop between returning success and committing that success.
    }
}

class UploadWorkerFactory(
    private val uploader: ImageUploader,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == UploadWorker::class.java.name) UploadWorker(appContext, workerParameters, uploader) else null
}

fun uploadWorkConfiguration(uploader: ImageUploader): Configuration =
    Configuration.Builder().setWorkerFactory(UploadWorkerFactory(uploader)).build()
