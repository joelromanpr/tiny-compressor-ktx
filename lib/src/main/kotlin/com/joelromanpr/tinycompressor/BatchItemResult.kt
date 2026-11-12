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

/** Outcome for one input, identified by its zero-based position in the batch. */
public sealed class BatchItemResult {
    public abstract val index: Int

    public data class Success(
        override val index: Int,
        public val result: CompressionResult,
    ) : BatchItemResult()

    /** A recoverable item failure. Coroutine cancellation and fatal errors are never wrapped. */
    public data class Failure(
        override val index: Int,
        public val error: Exception,
    ) : BatchItemResult()
}
