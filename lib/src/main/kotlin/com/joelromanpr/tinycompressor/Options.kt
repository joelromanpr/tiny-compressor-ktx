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
 * Public options for compression.
 *
 * [keepExif] retains selected JPEG metadata, including GPS coordinates when present. It defaults
 * to true for compatibility; set it to false before uploading images when location and device
 * metadata are not needed. [maxBytes] limits the complete encoded result, including copied EXIF.
 * [quality] is clamped to 0..100; PNG encoding does not use the quality setting.
 */
public data class Options(
    public val maxWidth: Int = 1280,
    public val maxHeight: Int = 1280,
    public val format: CompressFormat = CompressFormat.JPEG,
    public val quality: Int = 80,
    public val maxBytes: Long? = null,
    public val keepExif: Boolean = true,
    public val colorSpace: ColorSpace = ColorSpace.SRGB,
    public val destination: Destination = Destination.Cache(subdir = "default"),
)
