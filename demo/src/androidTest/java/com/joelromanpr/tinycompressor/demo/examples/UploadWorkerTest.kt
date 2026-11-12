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
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class UploadWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun successfulUploadConsumesActualResultAndReleasesAttemptOutput() =
        runBlocking {
            var calls = 0
            val worker =
                worker(
                    ImageUploader { image, key ->
                        calls++
                        assertTrue(image.file.isFile)
                        assertEquals(image.file.length(), image.outputBytes)
                        assertEquals("image/jpeg", image.mimeType)
                        assertTrue(key.isNotBlank())
                    },
                )
            val input = prepareInput(worker)
            try {
                assertTrue(worker.doWork() is ListenableWorker.Result.Success)
                assertEquals(1, calls)
                // Only terminal WorkInfo confirmation may release input; doWork returning is not that.
                assertTrue(input.exists())
                assertNoAttemptOutput(worker)
            } finally {
                input.parentFile?.deleteRecursively()
            }
        }

    @Test
    fun onlyExplicitTransientNetworkErrorsRetryAndAttemptsAreBounded() =
        runBlocking {
            val temporaryFailure = ImageUploader { _, _ -> throw TransientUploadException("Temporary test failure") }
            val first = worker(temporaryFailure)
            val final = worker(temporaryFailure, attempt = MAX_UPLOAD_ATTEMPTS - 1)
            val firstInput = prepareInput(first)
            val finalInput = prepareInput(final)
            try {
                assertTrue(first.doWork() is ListenableWorker.Result.Retry)
                assertTrue(final.doWork() is ListenableWorker.Result.Failure)
                assertTrue(firstInput.exists())
                assertNoAttemptOutput(first)
                assertNoAttemptOutput(final)
            } finally {
                firstInput.parentFile?.deleteRecursively()
                finalInput.parentFile?.deleteRecursively()
            }
        }

    @Test
    fun invalidImagesAndUnclassifiedUploadFailuresDoNotRetry() =
        runBlocking {
            var calls = 0
            val invalid = worker(ImageUploader { _, _ -> calls++ })
            val invalidInput = File(uploadInputDirectory(context, invalid.id), "source")
            invalidInput.parentFile?.mkdirs()
            invalidInput.writeText("Not an image")
            val rejected = worker(ImageUploader { _, _ -> throw IOException("Permanent test rejection") })
            val rejectedInput = prepareInput(rejected)
            try {
                assertTrue(invalid.doWork() is ListenableWorker.Result.Failure)
                assertEquals(0, calls)
                assertTrue(rejected.doWork() is ListenableWorker.Result.Failure)
                assertNoAttemptOutput(invalid)
                assertNoAttemptOutput(rejected)
            } finally {
                invalidInput.parentFile?.deleteRecursively()
                rejectedInput.parentFile?.deleteRecursively()
            }
        }

    @Test
    fun cancellationPropagatesAndKeepsInputForARescheduledRequest() =
        runBlocking {
            val uploadStarted = CompletableDeferred<Unit>()
            val worker =
                worker(
                    ImageUploader { _, _ ->
                        uploadStarted.complete(Unit)
                        awaitCancellation()
                    },
                )
            val input = prepareInput(worker)
            try {
                val job = launch { worker.doWork() }
                withTimeout(10_000) { uploadStarted.await() }
                job.cancelAndJoin()
                assertTrue(job.isCancelled)
                assertTrue(input.exists())
                assertNoAttemptOutput(worker)
            } finally {
                input.parentFile?.deleteRecursively()
            }
        }

    private fun worker(
        uploader: ImageUploader,
        attempt: Int = 0,
    ): UploadWorker =
        TestListenableWorkerBuilder<UploadWorker>(context)
            .setWorkerFactory(UploadWorkerFactory(uploader))
            .setRunAttemptCount(attempt)
            .build()

    private fun prepareInput(worker: UploadWorker): File {
        val file = File(uploadInputDirectory(context, worker.id), "source")
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(60, 40, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.rgb(20, 110, 90))
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun assertNoAttemptOutput(worker: UploadWorker) {
        assertFalse(
            context.cacheDir
                .listFiles()
                .orEmpty()
                .any { it.name.startsWith("worker-upload-${worker.id}-") },
        )
    }
}
