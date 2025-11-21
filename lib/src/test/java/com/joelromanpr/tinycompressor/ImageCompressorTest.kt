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

import com.joelromanpr.tinycompressor.ImageCompressor.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class ImageCompressorTest {
    // region computeTargetSize

    @Test
    public fun `computeTargetSize scales down landscape image correctly`() {
        // Landscape to smaller landscape
        val target =
            ImageCompressor.computeTargetSize(
                srcWidth = 2000,
                srcHeight = 1000,
                maxWidth = 500,
                maxHeight = 500,
            )
        assertEquals(Size(500, 250), target)
    }

    @Test
    public fun `computeTargetSize scales down portrait image correctly`() {
        // Portrait to smaller portrait
        val target =
            ImageCompressor.computeTargetSize(
                srcWidth = 1000,
                srcHeight = 2000,
                maxWidth = 500,
                maxHeight = 500,
            )
        assertEquals(Size(250, 500), target)
    }

    @Test
    public fun `computeTargetSize does not scale up`() {
        val target =
            ImageCompressor.computeTargetSize(
                srcWidth = 200,
                srcHeight = 100,
                maxWidth = 500,
                maxHeight = 500,
            )
        assertEquals(Size(200, 100), target)
    }

    @Test
    public fun `computeTargetSize handles one dimension larger`() {
        val target =
            ImageCompressor.computeTargetSize(
                srcWidth = 600,
                srcHeight = 400,
                maxWidth = 500,
                maxHeight = 500,
            )
        // 400 * (500/600) = 333.33 -> 333
        assertEquals(Size(500, 333), target)
    }

    @Test
    public fun `computeTargetSize scales down with one dimension matching max`() {
        val target =
            ImageCompressor.computeTargetSize(
                srcWidth = 1280,
                srcHeight = 1000,
                maxWidth = 1280,
                maxHeight = 800,
            )
        // Ratio = 800/1000 = 0.8. Width = 1280 * 0.8 = 1024. Height = 1000 * 0.8 = 800.
        assertEquals(Size(1024, 800), target)
    }

    @Test
    public fun `computeTargetSize handles zero or negative source dimensions`() {
        // Zero
        val targetZero =
            ImageCompressor.computeTargetSize(
                srcWidth = 0,
                srcHeight = 0,
                maxWidth = 500,
                maxHeight = 500,
            )
        assertEquals(Size(500, 500), targetZero)

        // Negative
        val targetNegative =
            ImageCompressor.computeTargetSize(
                srcWidth = -100,
                srcHeight = -100,
                maxWidth = 500,
                maxHeight = 500,
            )
        assertEquals(Size(500, 500), targetNegative)
    }

    // endregion

    // region computeSampleSize

    @Test
    public fun `computeSampleSize calculates correct sample size`() {
        // Scale by 4
        assertEquals(
            4,
            ImageCompressor.computeSampleSize(srcW = 4000, srcH = 2000, targetW = 1000, targetH = 500),
        )
        // Scale by 2
        assertEquals(
            2,
            ImageCompressor.computeSampleSize(srcW = 2000, srcH = 1000, targetW = 1000, targetH = 500),
        )
        // Scale by 1 (no downsampling needed)
        assertEquals(
            1,
            ImageCompressor.computeSampleSize(srcW = 1000, srcH = 500, targetW = 1000, targetH = 500),
        )
    }

    @Test
    public fun `computeSampleSize returns 1 for smaller source`() {
        assertEquals(
            1,
            ImageCompressor.computeSampleSize(srcW = 500, srcH = 250, targetW = 1000, targetH = 500),
        )
    }

    @Test
    public fun `computeSampleSize handles imperfect division`() {
        // (1920/2=960) >= 640 && (1080/2=540) >= 360 -> sample=2
        // (960/2=480) < 640 -> stop
        assertEquals(
            2,
            ImageCompressor.computeSampleSize(srcW = 1920, srcH = 1080, targetW = 640, targetH = 360),
        )
    }

    @Test
    public fun `computeSampleSize handles zero or negative source dimensions`() {
        assertEquals(1, ImageCompressor.computeSampleSize(srcW = 0, srcH = 0, targetW = 100, targetH = 100))
        assertEquals(1, ImageCompressor.computeSampleSize(srcW = -1, srcH = 100, targetW = 100, targetH = 100))
    }

    @Test
    public fun `computeSampleSize handles zero or negative target dimensions`() {
        assertEquals(1, ImageCompressor.computeSampleSize(srcW = 1000, srcH = 1000, targetW = 0, targetH = 0))
        assertEquals(1, ImageCompressor.computeSampleSize(srcW = 1000, srcH = 1000, targetW = -1, targetH = 100))
    }

    // endregion

    // region guessMimeFromName

    @Test
    public fun `guessMimeFromName handles standard extensions`() {
        assertEquals("image/jpeg", ImageCompressor.guessMimeFromName("image.jpg"))
        assertEquals("image/jpeg", ImageCompressor.guessMimeFromName("image.jpeg"))
        assertEquals("image/png", ImageCompressor.guessMimeFromName("image.png"))
        assertEquals("image/webp", ImageCompressor.guessMimeFromName("image.webp"))
    }

    @Test
    public fun `guessMimeFromName is case-insensitive`() {
        assertEquals("image/jpeg", ImageCompressor.guessMimeFromName("IMAGE.JPG"))
        assertEquals("image/jpeg", ImageCompressor.guessMimeFromName("ImAgE.jPeG"))
        assertEquals("image/png", ImageCompressor.guessMimeFromName("photo.PNG"))
    }

    @Test
    public fun `guessMimeFromName handles unknown extensions`() {
        assertNull(ImageCompressor.guessMimeFromName("document.pdf"))
        assertNull(ImageCompressor.guessMimeFromName("archive.zip"))
        assertNull(ImageCompressor.guessMimeFromName("image.gif"))
    }

    @Test
    public fun `guessMimeFromName handles filenames with no extension`() {
        assertNull(ImageCompressor.guessMimeFromName("myimage"))
        assertNull(ImageCompressor.guessMimeFromName("another-image"))
    }

    @Test
    public fun `guessMimeFromName handles filenames with multiple dots`() {
        assertEquals("image/jpeg", ImageCompressor.guessMimeFromName("archive.tar.jpg"))
        assertNull(ImageCompressor.guessMimeFromName("archive.jpg.tar"))
    }

    @Test
    public fun `guessMimeFromName handles filenames with leading dot`() {
        assertEquals("image/jpeg", ImageCompressor.guessMimeFromName(".image.jpg"))
        assertEquals("image/jpeg", ImageCompressor.guessMimeFromName(".jpg"))
    }

    // endregion
}
