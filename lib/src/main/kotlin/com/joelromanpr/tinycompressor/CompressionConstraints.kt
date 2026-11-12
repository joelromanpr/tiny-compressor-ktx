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

/**
 * Minimum acceptable output properties when meeting a byte limit.
 *
 * Compression fails rather than reducing the image below these limits. Images are never enlarged.
 * [minQuality] applies to lossy formats and must not exceed [Options.quality]. PNG ignores quality.
 */
public data class CompressionConstraints(
    public val minQuality: Int = 0,
    public val minWidth: Int = 1,
    public val minHeight: Int = 1,
) {
    init {
        require(minQuality in 0..100) { "minQuality must be between zero and 100" }
        require(minWidth > 0) { "minWidth must be greater than zero" }
        require(minHeight > 0) { "minHeight must be greater than zero" }
    }
}
