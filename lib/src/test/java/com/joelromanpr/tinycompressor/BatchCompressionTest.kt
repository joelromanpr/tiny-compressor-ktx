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
package com.joelromanpr.tinycompressor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

public class BatchCompressionTest {
    @get:Rule
    public val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    public fun `bounded workers emit completion order and return input order despite item failure`(): Unit =
        runBlocking {
            withTimeout(10_000) {
                val started = Channel<Int>(Channel.UNLIMITED)
                val emitted = Channel<BatchProgress>(Channel.UNLIMITED)
                val release = List(5) { CompletableDeferred<Unit>() }
                val active = AtomicInteger()
                val maximumActive = AtomicInteger()
                val events = mutableListOf<BatchProgress>()
                val progress =
                    batchCompressionFlow(sources(5), Destination.Cache(), concurrency = 2) { source ->
                        val index = (source as Source.Bytes).bytes.single().toInt()
                        val count = active.incrementAndGet()
                        maximumActive.updateAndGet { maxOf(it, count) }
                        try {
                            started.send(index)
                            release[index].await()
                            if (index == 3) throw IOException("Unreadable image")
                            result(index)
                        } finally {
                            active.decrementAndGet()
                        }
                    }.onEach {
                        events.add(it)
                        emitted.send(it)
                    }
                val batch = async { collectBatchResults(progress, Destination.Cache()) }

                assertEquals(setOf(0, 1), setOf(started.receive(), started.receive()))
                assertTrue(started.tryReceive().isFailure)
                release[1].complete(Unit)
                assertEquals(1, emitted.receive().item.index)
                assertEquals(2, started.receive())
                release[0].complete(Unit)
                assertEquals(0, emitted.receive().item.index)
                assertEquals(3, started.receive())
                release[2].complete(Unit)
                release[3].complete(Unit)
                assertEquals(4, started.receive())
                release[4].complete(Unit)

                val results = batch.await()
                assertEquals((0..4).toList(), results.map { it.index })
                assertTrue((results[3] as BatchItemResult.Failure).error is IOException)
                assertTrue(results.filterIsInstance<BatchItemResult.Success>().all { it.result.file.exists() })
                assertEquals((1..5).toList(), events.map { it.completed })
                assertTrue(events.all { it.total == 5 })
                assertEquals(2, maximumActive.get())
                assertEquals(0, active.get())
            }
        }

    @Test
    public fun `cancelled flow stops workers and removes only unreceived cache files`(): Unit =
        runBlocking {
            withTimeout(10_000) {
                val created = Channel<File>(Channel.UNLIMITED)
                val offered = CompletableDeferred<File>()
                val blocked = CompletableDeferred<Unit>()
                val active = AtomicInteger()
                val progress =
                    batchCompressionFlow(sources(3), Destination.Cache(), concurrency = 2) { source ->
                        active.incrementAndGet()
                        try {
                            val index = (source as Source.Bytes).bytes.single().toInt()
                            if (index == 2) {
                                blocked.complete(Unit)
                                awaitCancellation()
                            }
                            result(index).also { created.send(it.file) }
                        } finally {
                            active.decrementAndGet()
                        }
                    }
                val collection =
                    launch {
                        progress.collect {
                            offered.complete((it.item as BatchItemResult.Success).result.file)
                            awaitCancellation()
                        }
                    }
                val receivedFile = offered.await()
                val files = listOf(created.receive(), created.receive())
                blocked.await()
                assertEquals(1, active.get())
                collection.cancelAndJoin()

                assertTrue(receivedFile.exists())
                assertTrue(files.filter { it != receivedFile }.none { it.exists() })
                assertEquals(0, active.get())
            }
        }

    @Test
    public fun `taking one result preserves its file and cleans pending outputs`(): Unit =
        runBlocking {
            withTimeout(10_000) {
                val created = Channel<File>(Channel.UNLIMITED)
                val item =
                    batchCompressionFlow(sources(2), Destination.Cache(), concurrency = 1) { source ->
                        result((source as Source.Bytes).bytes.single().toInt()).also { created.send(it.file) }
                    }.take(1).single().item as BatchItemResult.Success

                assertTrue(item.result.file.exists())
                while (true) {
                    val file = created.tryReceive().getOrNull() ?: break
                    if (file != item.result.file) assertFalse(file.exists())
                }
            }
        }

    @Test
    public fun `cancelled list collection cleans collected cache files but retains explicit files`(): Unit =
        runBlocking {
            withTimeout(10_000) {
                for (cache in listOf(true, false)) {
                    val output = result(if (cache) 0 else 1)
                    val destination = if (cache) Destination.Cache() else Destination.File(output.file)
                    val collected = CompletableDeferred<Unit>()
                    val collection =
                        launch {
                            collectBatchResults(
                                flow {
                                    emit(BatchProgress(1, 2, BatchItemResult.Success(0, output)))
                                    collected.complete(Unit)
                                    awaitCancellation()
                                },
                                destination,
                            )
                        }
                    collected.await()
                    collection.cancelAndJoin()
                    assertEquals(!cache, output.file.exists())
                }
            }
        }

    @Test
    public fun `fatal errors abort instead of becoming item failures`(): Unit =
        runBlocking {
            val failure =
                runCatching {
                    batchCompressionFlow(sources(1), Destination.Cache(), concurrency = 1) {
                        throw AssertionError("Fatal encoder failure")
                    }.toList()
                }.exceptionOrNull()
            assertTrue(failure is AssertionError)
        }

    @Test
    public fun `empty batches do not start compression`(): Unit =
        runBlocking {
            val results =
                batchCompressionFlow(emptyList(), Destination.Cache(), concurrency = 1) {
                    error("An empty batch must not start a worker")
                }.toList()
            assertTrue(results.isEmpty())
        }

    @Test(expected = IllegalArgumentException::class)
    public fun `multiple inputs cannot share an explicit destination`() {
        batchCompressionFlow(sources(2), Destination.File(temporaryFolder.newFile()), concurrency = 1) { result(0) }
    }

    @Test(expected = IllegalArgumentException::class)
    public fun `nonpositive concurrency is rejected`() {
        batchCompressionFlow(sources(1), Destination.Cache(), concurrency = 0) { result(0) }
    }

    private fun sources(count: Int): List<Source> = List(count) { Source.Bytes(byteArrayOf(it.toByte())) }

    private fun result(index: Int): CompressionResult {
        val file = temporaryFolder.newFile("item-$index.jpg")
        file.writeBytes(byteArrayOf(1))
        return CompressionResult(file, 100, 1, 10, 10, CompressFormat.JPEG, 80)
    }
}
