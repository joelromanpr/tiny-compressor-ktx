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

import android.graphics.Bitmap
import android.os.Build

public enum class CompressFormat {
    JPEG,
    PNG,
    WEBP,
    ;

    public fun isLossy(): Boolean =
        when (this) {
            JPEG -> true
            WEBP -> true
            PNG -> false
        }

    public fun defaultExtension(): String =
        when (this) {
            JPEG -> ".jpg"
            PNG -> ".png"
            WEBP -> ".webp"
        }

    public fun resolveFor(mimeHint: String?): CompressFormat {
        if (this != WEBP && (mimeHint?.contains("png") == true)) {
            return PNG
        }
        return this
    }

    internal fun toAndroid(options: Options): Bitmap.CompressFormat =
        when (this) {
            JPEG -> Bitmap.CompressFormat.JPEG
            PNG -> Bitmap.CompressFormat.PNG
            WEBP -> {
                if (Build.VERSION.SDK_INT >= 30 && options.quality >= 100) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
}
