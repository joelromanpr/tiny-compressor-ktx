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

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Best-effort removal of a directory owned by an upload recipe. Concurrent cleanup may remove an
 * entry at any point. Symbolic links are deleted themselves; their targets are never traversed.
 * Returns false on an I/O or security failure, leaving remaining files available for later cleanup.
 * [beforeDelete] lets tests coordinate another remover during a real filesystem traversal.
 */
internal fun removeOwnedDirectory(
    directory: File,
    beforeDelete: (Path) -> Unit = {},
): Boolean {
    val root = directory.toPath()
    var complete = true

    fun removeEntry(path: Path) {
        beforeDelete(path)
        try {
            Files.deleteIfExists(path)
        } catch (_: NoSuchFileException) {
            // Another cleanup removed this entry or one of its parent directories.
        } catch (_: IOException) {
            complete = false
        } catch (_: SecurityException) {
            complete = false
        }
    }

    try {
        // The default walk does not enable FOLLOW_LINKS, including for a symbolic-link root.
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attributes: BasicFileAttributes,
                ): FileVisitResult {
                    removeEntry(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    failure: IOException,
                ): FileVisitResult {
                    if (failure !is NoSuchFileException) complete = false
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    directory: Path,
                    failure: IOException?,
                ): FileVisitResult {
                    if (failure != null && failure !is NoSuchFileException) complete = false
                    removeEntry(directory)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    } catch (_: NoSuchFileException) {
        // The root can disappear between starting the walk and opening it.
    } catch (_: IOException) {
        return false
    } catch (_: SecurityException) {
        return false
    }
    return try {
        complete && !Files.exists(root, LinkOption.NOFOLLOW_LINKS)
    } catch (_: SecurityException) {
        false
    }
}
