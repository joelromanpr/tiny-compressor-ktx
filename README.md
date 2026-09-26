# Tiny Compressor KTX

Android image compression with a small Kotlin API. Resize and encode images from a `File`, content `Uri`, or `ByteArray`; receive a file, bytes, or progress events from a `Flow`.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.joelromanpr/tiny-compressor-ktx.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.joelromanpr/tiny-compressor-ktx)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

- **Android 11+ (`minSdk 30`)**
- **Kotlin coroutines:** suspending calls for file and byte output; a `Flow` for coarse progress
- **JPEG, PNG, and WebP:** bounded dimensions, configurable quality, and an optional strict byte limit
- **Private by choice:** retain a supported subset of JPEG EXIF or opt out for uploads

## Install

Add Maven Central to your app's repositories (alongside Google's Android repository):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Then add the dependency:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.joelromanpr:tiny-compressor-ktx:1.0.0")
}
```

**Release status:** `1.0.0` is the current Maven Central release. This README describes the `main` branch, where `1.1.0` is being prepared. The strict `maxBytes` guarantee, safe destination replacement, and corrected PNG/EXIF handling below require `1.1.0`; they are not claims about `1.0.0`. Clone this repository and run the demo to try the unreleased source. We will update the dependency snippet after `1.1.0` is verified on [Maven Central](https://central.sonatype.com/artifact/io.github.joelromanpr/tiny-compressor-ktx).

## Compress an image

Call from a coroutine. Android's Photo Picker gives your app a readable content `Uri` without requesting broad media access.

```kotlin
import android.content.Context
import android.net.Uri
import com.joelromanpr.tinycompressor.ImageCompressor
import com.joelromanpr.tinycompressor.Options
import com.joelromanpr.tinycompressor.Source
import java.io.File

suspend fun prepareUpload(context: Context, pickedImage: Uri): File =
    ImageCompressor.compress(
        context = context,
        source = Source.Uri(pickedImage),
        options = Options(
            maxWidth = 1600,
            maxHeight = 1600,
            quality = 82,
            keepExif = false, // Do not copy camera details or GPS coordinates.
        ),
    )
```

The returned file is in the app's cache by default. Upload it or move it to durable app storage before the cache is cleared. You can write to a specific app-owned file with `Options(destination = Destination.File(file))`.

## Choose the result type

| Call | Result | When to use it |
| --- | --- | --- |
| `ImageCompressor.compress(context, source, options)` | `File` | Save, share, or upload a file. |
| `ImageCompressor.compressToByteArray(context, source, options)` | `ByteArray` | An API needs bytes; the entire output fits comfortably in memory. `destination` is ignored. |
| `ImageCompressor.compressAsFlow(context, source, options)` | `Flow<Progress>` | Show approximate stage and percent updates. The final `Step.Done` event contains the file. |

For example, to receive bytes without copying source EXIF:

```kotlin
val bytes = ImageCompressor.compressToByteArray(
    context,
    Source.Uri(pickedImage),
    Options(keepExif = false),
)
```

To observe progress, collect the flow in a lifecycle-aware coroutine:

```kotlin
ImageCompressor.compressAsFlow(context, Source.Uri(pickedImage))
    .collect { progress ->
        if (progress.step.isDone()) {
            val outputFile = requireNotNull(progress.file)
            // Use outputFile.
        } else {
            // Show progress.step and progress.percent (an estimate).
        }
    }
```

A second compression should cancel the first job if its result is no longer needed. The [demo app](demo/src/main/java/com/joelromanpr/tinycompressor/demo/MainActivity.kt) shows a picker, progress, error handling, and before/after previews.

## Options and behavior

| Option | Default | Notes |
| --- | --- | --- |
| `maxWidth`, `maxHeight` | `1280` each | Preserve aspect ratio and avoid enlarging small images. |
| `format` | `CompressFormat.JPEG` | PNG input stays PNG unless WebP is requested. See formats below. |
| `quality` | `80` | Used for lossy JPEG/WebP. PNG encoding ignores it. |
| `maxBytes` | `null` | When set, adaptive encoding must fit the final output within the limit or throws `IOException`. |
| `keepExif` | `true` | For JPEG output, copies a supported subset of source EXIF, including GPS if present. Use `false` for privacy-sensitive uploads. |
| `colorSpace` | `ColorSpace.SRGB` | `ColorSpace.DISPLAY_P3` is also available. |
| `destination` | `Destination.Cache("default")` | File calls write under `context.cacheDir/tinycompressor/default/` unless you provide `Destination.File`. |

### Formats and transparency

- **JPEG** is usually a good choice for photos. It cannot preserve transparency.
- **PNG** preserves transparency. Its `quality` setting does not change its lossless encoding.
- **WebP** supports transparency. At quality `100`, Android 11+ uses lossless WebP when no byte limit forces a lower quality. With `maxBytes`, adaptive encoding may switch to lossy WebP.

The decoder identifies the input format from its content for `Source.File`, `Source.Uri`, and `Source.Bytes`. PNG input stays PNG when JPEG or PNG is requested; request WebP to convert it. For other transparent inputs, choose PNG or WebP explicitly. The cache filename extension matches the actual output format. For `Destination.File`, use an extension that matches the resolved format. A known image extension that does not match throws `IllegalArgumentException` before writing; an extensionless path is allowed.

### EXIF, limits, and errors

`keepExif = true` copies selected tags for JPEG output, not every metadata field. This includes camera details and GPS latitude/longitude when present. The image pixels are oriented during decode; the output orientation is normalized. Set `keepExif = false` before sharing or uploading images when source metadata is unnecessary.

`maxBytes` may reduce quality and dimensions. The limit applies to the final file, including copied EXIF. A target too small to satisfy causes an `IOException`; handle it like other input or output failures. Invalid or unreadable images and inaccessible destinations also fail with an exception. Coroutine cancellation propagates; do not convert it into a retry or a generic failure.

### Background jobs

A picker `Uri` may not remain readable for a job that runs much later. For durable work, persist an eligible Photo Picker URI grant or copy the input to app-owned storage first. Write long-lived output to `Destination.File` instead of relying on the app cache. With WorkManager, retry only transient failures and let cancellation propagate. See Android's guidance on [persisting picker access](https://developer.android.com/training/data-storage/shared/photo-picker#persist-media-file-access) and [long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

## Run the demo and contribute

Open this repository in Android Studio and run the `demo` configuration on Android 11 or newer. To check a change locally:

```bash
./scripts/prepare_for_pr.sh
```

See [CHANGELOG.md](CHANGELOG.md) for release notes and [CONTRIBUTING.md](CONTRIBUTING.md) for the contribution and release process.

## License

[MIT](LICENSE)
