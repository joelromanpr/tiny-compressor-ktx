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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal fun batchCompressionFlow(
    sources: List<Source>,
    destination: Destination,
    concurrency: Int,
    compress: suspend (Source) -> CompressionResult,
): Flow<BatchProgress> {
    require(concurrency > 0) { "concurrency must be greater than zero" }
    val inputs = sources.toList()
    require(inputs.size <= 1 || destination !is Destination.File) {
        "A batch with multiple inputs cannot share a Destination.File"
    }
    return flow {
        if (inputs.isEmpty()) return@flow
        coroutineScope {
            val nextIndex = AtomicInteger()
            val workerCount = minOf(concurrency, inputs.size)
            val remainingWorkers = AtomicInteger(workerCount)
            val pendingCacheFiles = ConcurrentHashMap.newKeySet<File>()
            val results = Channel<BatchItemResult>(Channel.RENDEZVOUS)
            // Starting on IO also avoids discarding a committed file during a return dispatch from
            // compressWithResult's IO context before the worker can register it for cancellation cleanup.
            val workers =
                List(workerCount) {
                    launch(Dispatchers.IO) {
                        try {
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val index = nextIndex.getAndIncrement()
                                if (index >= inputs.size) break
                                val item =
                                    try {
                                        val result = compress(inputs[index])
                                        if (destination is Destination.Cache) pendingCacheFiles.add(result.file)
                                        BatchItemResult.Success(index, result)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        BatchItemResult.Failure(index, failure)
                                    }
                                results.send(item)
                            }
                        } finally {
                            if (remainingWorkers.decrementAndGet() == 0) results.close()
                        }
                    }
                }
            try {
                var completed = 0
                for (item in results) {
                    currentCoroutineContext().ensureActive()
                    // Ownership passes when the item is offered to the collector, including take(1).
                    if (item is BatchItemResult.Success) pendingCacheFiles.remove(item.result.file)
                    emit(BatchProgress(++completed, inputs.size, item))
                }
            } finally {
                withContext(NonCancellable) {
                    workers.forEach { it.cancel() }
                    workers.joinAll()
                    results.cancel()
                    pendingCacheFiles.forEach { it.delete() }
                }
            }
        }
    }
}

internal suspend fun collectBatchResults(
    progress: Flow<BatchProgress>,
    destination: Destination,
): List<BatchItemResult> {
    val completed = mutableListOf<BatchItemResult>()
    var returned = false
    try {
        progress.collect { completed.add(it.item) }
        currentCoroutineContext().ensureActive()
        return completed.sortedBy { it.index }.also { returned = true }
    } finally {
        if (!returned && destination is Destination.Cache) {
            completed.filterIsInstance<BatchItemResult.Success>().forEach { it.result.file.delete() }
        }
    }
}
