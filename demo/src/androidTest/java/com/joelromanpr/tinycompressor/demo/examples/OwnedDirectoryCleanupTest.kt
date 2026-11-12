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
package com.joelromanpr.tinycompressor.demo.examples

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class OwnedDirectoryCleanupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun severalCoordinatedCallersCanRemoveTheSameNestedTree() {
        repeat(12) {
            val root = createNestedTree()
            val callers = 8
            val executor = Executors.newFixedThreadPool(callers)
            val ready = CountDownLatch(callers)
            val start = CountDownLatch(1)
            try {
                val results =
                    (0 until callers).map {
                        executor.submit<Boolean> {
                            ready.countDown()
                            assertTrue("Cleanup callers did not start together", start.await(10, TimeUnit.SECONDS))
                            removeOwnedDirectory(root)
                        }
                    }
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
                results.forEach { assertTrue("Concurrent cleanup failed", it.get(10, TimeUnit.SECONDS)) }
                assertFalse(Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS))
            } finally {
                start.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
                removeOwnedDirectory(root)
            }
        }
    }

    @Test
    fun anEntryCanDisappearWhileAnotherCleanupAlreadyTraversesItsParent() {
        val root = createNestedTree()
        val executor = Executors.newSingleThreadExecutor()
        val insideTraversal = CountDownLatch(1)
        val resumeTraversal = CountDownLatch(1)
        val pauseOnce = AtomicBoolean()
        try {
            val first =
                executor.submit<Boolean> {
                    removeOwnedDirectory(root) {
                        if (pauseOnce.compareAndSet(false, true)) {
                            insideTraversal.countDown()
                            assertTrue("Second cleanup never released the first", resumeTraversal.await(10, TimeUnit.SECONDS))
                        }
                    }
                }
            assertTrue("First cleanup never entered the tree", insideTraversal.await(10, TimeUnit.SECONDS))
            try {
                // The first walker has an open directory and an entry selected for deletion.
                // Remove the entire real tree before allowing that first walker to continue.
                assertTrue(removeOwnedDirectory(root))
            } finally {
                resumeTraversal.countDown()
            }
            assertTrue(first.get(10, TimeUnit.SECONDS))
            assertFalse(root.exists())
        } finally {
            resumeTraversal.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
            removeOwnedDirectory(root)
        }
    }

    @Test
    fun aMissingRootCanBeRemovedRepeatedly() {
        val missing = File(context.cacheDir, "missing-cleanup-${UUID.randomUUID()}")
        assertTrue(removeOwnedDirectory(missing))
        assertTrue(removeOwnedDirectory(missing))
    }

    @Test
    fun symbolicLinksAreRemovedWithoutTouchingTheirExternalTargets() {
        val root = createNestedTree()
        val external = Files.createTempDirectory(context.cacheDir.toPath(), "cleanup-external-").toFile()
        val marker = File(external, "keep.txt").apply { writeText("Must survive owned-tree cleanup") }
        val rootLink = File(context.cacheDir, "cleanup-root-link-${UUID.randomUUID()}").toPath()
        try {
            Files.createSymbolicLink(File(root, "external-link").toPath(), external.toPath())
            Files.createSymbolicLink(File(root, "dangling-link").toPath(), File(external, "missing").toPath())
            Files.createSymbolicLink(rootLink, external.toPath())
            assertTrue(removeOwnedDirectory(root))
            assertTrue(removeOwnedDirectory(rootLink.toFile()))
            assertFalse(Files.exists(rootLink, LinkOption.NOFOLLOW_LINKS))
            assertEquals("Must survive owned-tree cleanup", marker.readText())
        } finally {
            removeOwnedDirectory(root)
            removeOwnedDirectory(rootLink.toFile())
            removeOwnedDirectory(external)
        }
    }

    @Test
    fun cleanupFailuresAreBestEffortAndCanBeRetried() {
        val root = createNestedTree()
        try {
            assertFalse(removeOwnedDirectory(root) { throw IOException("Injected inaccessible entry") })
            assertTrue(root.exists())
            assertFalse(removeOwnedDirectory(root) { throw SecurityException("Injected denied entry") })
            assertTrue(root.exists())
            assertTrue(removeOwnedDirectory(root))
        } finally {
            removeOwnedDirectory(root)
        }
    }

    @Test
    fun fatalErrorsAreNotSwallowedByBestEffortCleanup() {
        val root = createNestedTree()
        val failure = AssertionError("Fatal test failure")
        try {
            val thrown = assertThrows(AssertionError::class.java) { removeOwnedDirectory(root) { throw failure } }
            assertTrue(thrown === failure)
        } finally {
            removeOwnedDirectory(root)
        }
    }

    private fun createNestedTree(): File {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "owned-cleanup-").toFile()
        repeat(8) { branch ->
            val directory = File(root, "branch-$branch/nested/deep").apply { assertTrue(mkdirs()) }
            repeat(8) { entry -> File(directory, "image-$entry.bin").writeBytes(ByteArray(64) { entry.toByte() }) }
        }
        File(root, "source").writeText("Owned source file")
        return root
    }
}
