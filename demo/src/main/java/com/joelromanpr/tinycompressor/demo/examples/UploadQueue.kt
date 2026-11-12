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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Application-scoped owner of durable inputs. Construct once at application startup with a scope
 * that outlives individual screens. WorkManager must be configured with [UploadWorkerFactory].
 */
class UploadQueue(
    context: Context,
    private val applicationScope: CoroutineScope,
    private val workManager: WorkManager = WorkManager.getInstance(context),
) {
    private val context = context.applicationContext

    init {
        applicationScope.launch(Dispatchers.IO) { reconcile() }
    }

    /** Copies a picker grant into app-owned files BEFORE scheduling persistent work. */
    suspend fun enqueue(
        pickedUri: Uri,
        maxBytes: Long = 500_000,
    ): UUID {
        require(maxBytes > 0)
        val id = UUID.randomUUID()
        val directory = uploadInputDirectory(context, id)
        var handedOver = false
        try {
            withContext(Dispatchers.IO) {
                if (!directory.mkdirs()) throw IOException("Could not prepare upload storage")
                context.contentResolver.openInputStream(pickedUri)?.use { source ->
                    File(directory, "source").outputStream().use { destination ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            destination.write(buffer, 0, count)
                        }
                    }
                } ?: throw IOException("Could not read selected image")
            }
            val request =
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setId(id)
                    .setInputData(workDataOf(MAX_BYTES_KEY to maxBytes))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            // Resolve the enqueue transaction before giving up ownership, even if the caller's
            // screen is cancelled during that transaction. Blocking future waits stay off main.
            withContext(NonCancellable + Dispatchers.IO) {
                workManager.enqueue(request).result.get()
                handedOver = true
                observeTerminalState(id)
            }
            return id
        } catch (cancelled: CancellationException) {
            if (handedOver) withContext(NonCancellable) { cancel(id) }
            throw cancelled
        } finally {
            if (!handedOver) withContext(NonCancellable + Dispatchers.IO) { directory.deleteRecursively() }
        }
    }

    /** Explicit user cancellation: WorkManager will no longer retry this request. */
    suspend fun cancel(id: UUID) {
        withContext(Dispatchers.IO) {
            workManager.cancelWorkById(id).result.get()
            val info = workManager.getWorkInfoByIdFlow(id).first()
            if (info?.state?.isFinished == true) uploadInputDirectory(context, id).deleteRecursively()
        }
    }

    private fun observeTerminalState(id: UUID) {
        applicationScope.launch(Dispatchers.IO) {
            workManager.getWorkInfoByIdFlow(id).filterNotNull().first { it.state.isFinished }
            uploadInputDirectory(context, id).deleteRecursively()
        }
    }

    private suspend fun reconcile() {
        val root = File(context.noBackupFilesDir, "pending-image-uploads")
        root.listFiles()?.filter { it.isDirectory }?.forEach { directory ->
            val id = runCatching { UUID.fromString(directory.name) }.getOrNull() ?: return@forEach
            val info = workManager.getWorkInfoByIdFlow(id).first()
            when {
                info?.state?.isFinished == true -> directory.deleteRecursively()
                info != null -> observeTerminalState(id)
                // A crash before enqueue can strand a copy. Leave fresh copies alone because an
                // enqueue operation in this process may still be preparing one of them.
                System.currentTimeMillis() - directory.lastModified() > TimeUnit.DAYS.toMillis(1) -> directory.deleteRecursively()
            }
        }
    }
}
