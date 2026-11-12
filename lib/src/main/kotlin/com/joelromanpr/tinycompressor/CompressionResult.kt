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

import java.io.File

/**
 * A completed file and the properties of the image actually written to it.
 *
 * [inputBytes] is captured before writing, and is null when the source provider does not report
 * a size. [outputBytes] includes retained metadata. [appliedQuality] is null for PNG, whose encoder
 * ignores quality. Sizes describe the completed operation; later changes to [file] are not tracked.
 */
public data class CompressionResult(
    public val file: File,
    public val inputBytes: Long?,
    public val outputBytes: Long,
    public val width: Int,
    public val height: Int,
    public val format: CompressFormat,
    public val appliedQuality: Int?,
) {
    /** MIME type of the encoded output, which may differ from the requested format. */
    public val mimeType: String
        get() =
            when (format) {
                CompressFormat.JPEG -> "image/jpeg"
                CompressFormat.PNG -> "image/png"
                CompressFormat.WEBP -> "image/webp"
            }

    /** Percentage of input bytes saved, or null for an unknown or empty input. May be negative. */
    public val savingsPercent: Double?
        get() = inputBytes?.takeIf { it > 0 }?.let { 100.0 * (1.0 - outputBytes.toDouble() / it) }
}
