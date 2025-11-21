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

import android.os.Build
import androidx.annotation.RequiresApi
import android.graphics.ColorSpace as AndroidColorSpace

public enum class ColorSpace {
    SRGB,
    DISPLAY_P3,
    ;

    @RequiresApi(Build.VERSION_CODES.O)
    internal fun toAndroid(): AndroidColorSpace {
        val named =
            when (this) {
                SRGB -> AndroidColorSpace.Named.SRGB
                DISPLAY_P3 -> AndroidColorSpace.Named.DISPLAY_P3
            }
        return AndroidColorSpace.get(named)
    }
}
