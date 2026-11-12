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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

public class CompressionResultTest {
    @Test
    public fun `savings handles known unknown empty and larger outputs`() {
        val result = CompressionResult(File("image.jpg"), 1000, 400, 40, 20, CompressFormat.JPEG, 80)
        assertEquals(60.0, requireNotNull(result.savingsPercent), 0.0001)
        assertEquals(-20.0, requireNotNull(result.copy(outputBytes = 1200).savingsPercent), 0.0001)
        assertNull(result.copy(inputBytes = null).savingsPercent)
        assertNull(result.copy(inputBytes = 0).savingsPercent)
    }

    @Test
    public fun `upload preset strips metadata without changing existing defaults`() {
        val upload = Options.forUpload(maxBytes = 500_000)
        assertFalse(upload.keepExif)
        assertEquals(500_000L, upload.maxBytes)
        assertEquals(1600, upload.maxWidth)
        assertEquals(1600, upload.maxHeight)
        assertEquals(82, upload.quality)
        assertTrue(Options().keepExif)
        assertNull(Options().maxBytes)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun `upload preset rejects a nonpositive byte limit`() {
        Options.forUpload(maxBytes = 0)
    }
}
