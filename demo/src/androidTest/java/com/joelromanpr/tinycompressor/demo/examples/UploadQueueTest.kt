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
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class UploadQueueTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun successfulQueuedUploadCleansDurableCopyAfterConfirmedCompletion() =
        runBlocking {
            val calls = AtomicInteger()
            val manager =
                initialize(
                    ImageUploader { image, _ ->
                        assertTrue(image.file.exists())
                        calls.incrementAndGet()
                    },
                )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val source = createImage()
            val queue = UploadQueue(context, scope, manager)
            var id: UUID? = null
            try {
                id = queue.enqueue(Uri.fromFile(source))
                assertTrue(File(uploadInputDirectory(context, id), "source").exists())
                WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(id)
                val finished =
                    withTimeout(10_000) {
                        manager.getWorkInfoByIdFlow(id).filterNotNull().first { it.state.isFinished }
                    }
                assertEquals(WorkInfo.State.SUCCEEDED, finished.state)
                withTimeout(10_000) { while (uploadInputDirectory(context, id).exists()) delay(10) }
                assertEquals(1, calls.get())
                assertTrue(source.exists())
            } finally {
                id?.let {
                    manager.cancelWorkById(it).result.get()
                    removeOwnedDirectory(uploadInputDirectory(context, it))
                }
                scope.cancel()
                source.delete()
            }
        }

    @Test
    fun explicitCancellationCleansAnInputEvenBeforeTheWorkerStarts() =
        runBlocking {
            val calls = AtomicInteger()
            val manager = initialize(ImageUploader { _, _ -> calls.incrementAndGet() })
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val source = createImage()
            val queue = UploadQueue(context, scope, manager)
            var id: UUID? = null
            try {
                id = queue.enqueue(Uri.fromFile(source))
                assertTrue(File(uploadInputDirectory(context, id), "source").exists())
                queue.cancel(id)
                assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoByIdFlow(id).first()?.state)
                assertFalse(uploadInputDirectory(context, id).exists())
                assertEquals(0, calls.get())
                assertTrue(source.exists())
            } finally {
                id?.let {
                    manager.cancelWorkById(it).result.get()
                    removeOwnedDirectory(uploadInputDirectory(context, it))
                }
                scope.cancel()
                source.delete()
            }
        }

    private fun initialize(uploader: ImageUploader): WorkManager {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(UploadWorkerFactory(uploader))
                .build(),
        )
        return WorkManager.getInstance(context)
    }

    private fun createImage(): File {
        val file = File.createTempFile("recipe-test-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(60, 40, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.rgb(20, 110, 90))
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
