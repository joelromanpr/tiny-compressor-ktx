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
package com.joelromanpr.tinycompressor.demo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val darkColorScheme =
    darkColorScheme(
        primary = Teal80,
        secondary = Slate80,
        tertiary = Sand80,
        primaryContainer = Color(0xFF174C43),
        onPrimaryContainer = Color(0xFFB3ECE1),
        secondaryContainer = Color(0xFF344C44),
        onSecondaryContainer = Color(0xFFD2E8DF),
        outline = Color(0xFF84978F),
        surfaceVariant = Color(0xFF283A35),
        onSurfaceVariant = Color(0xFFB8C9C2),
    )

private val lightColorScheme =
    lightColorScheme(
        primary = Teal40,
        secondary = Slate40,
        tertiary = Sand40,
        primaryContainer = Color(0xFFD1E8E2),
        onPrimaryContainer = Color(0xFF06201C),
        secondaryContainer = Color(0xFFD5E7DF),
        onSecondaryContainer = Color(0xFF102F25),
        outline = Color(0xFF72867C),
        surfaceVariant = Color(0xFFE7EEEB),
        onSurfaceVariant = Color(0xFF45554F),
        background = Color(0xFFF8FAF8),
        surface = Color(0xFFF8FAF8),
    )

@Suppress("ktlint:standard:function-naming")
@Composable
fun DemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> darkColorScheme
            else -> lightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
