# Tiny Compressor KTX

[![Maven Central](https://img.shields.io/maven-central/v/io.github.joelromanpr/tiny-compressor-ktx.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.joelromanpr%22%20AND%20a:%22tiny-compressor-ktx%22)

A tiny, modern image compression library for Android. Kotlin-first, coroutine/Flow-friendly, and Compose-ready with a small but powerful API.

## Features
- **Modern, concise API**: `suspend` functions and an optional `Flow` for progress.
- **Small footprint**: No heavyweight image pipelines; lightweight EXIF support.
- **Sensible defaults**: Safe max dimensions, quality, and color space.
- **EXIF preservation**: Copies common EXIF tags for JPEG outputs.
- **Broad format support**: JPEG, PNG, WEBP (lossy and lossless when applicable).
- **Efficient decoding**: Uses `ImageDecoder` on API 28+ for predictable memory use.
- **Compose-ready**: Easy to wire progress into your UI.
- **Flexible I/O**:
    - **Inputs**: `File`, `Uri`, `ByteArray`.
    - **Outputs**: `File` or `ByteArray`.

## Installation
Make sure you have `mavenCentral()` in your root `settings.gradle.kts` repositories block:
```kotlin
dependencyResolutionManagement {
    repositories {
        // ...
        mavenCentral()
    }
}
```
Then, add the dependency to your module's `build.gradle.kts`:
```kotlin
dependencies {
    implementation("io.github.joelromanpr:tiny-compressor-ktx:1.0.0")
}
```

## Usage & Recipes

### 1. Basic Compression (Fire-and-Forget)
For simple cases where you don't need progress updates, use the `suspend` functions.

**Compress a `File` to another `File`:**
```kotlin
suspend fun compressPhoto(context: Context, inputFile: File): File {
    return ImageCompressor.compress(
        context = context,
        source = Source.File(inputFile),
        options = Options(
            maxWidth = 1600,
            maxHeight = 1600,
            format = CompressFormat.JPEG,
            quality = 82
        )
    )
}
```

**Compress a content `Uri` to a `ByteArray`:**
```kotlin
suspend fun compressFromUriToBytes(context: Context, uri: Uri): ByteArray {
    return ImageCompressor.compressToByteArray(
        context = context,
        source = Source.Uri(uri),
        options = Options(
            maxWidth = 1280,
            maxHeight = 1280,
            format = CompressFormat.WEBP,
            quality = 85
        )
    )
}
```

### 2. Observing Progress with Flow
For UI integration, use `compressAsFlow` to receive `Progress` updates.

This example shows a `ViewModel` that launches compression and exposes the state to the UI.

**ViewModel:**
```kotlin
data class CompressionState(
    val inProgress: Boolean = false,
    val percent: Int = 0,
    val outputFile: File? = null,
    val error: Throwable? = null
)

class PhotoCompressViewModel(
    private val appContext: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(CompressionState())
    val state = _state.asStateFlow()

    private var job: Job? = null

    fun compress(uri: Uri) {
        job?.cancel()
        job = viewModelScope.launch {
            ImageCompressor.compressAsFlow(
                context = appContext,
                source = Source.Uri(uri)
            )
            .onStart { _state.value = CompressionState(inProgress = true) }
            .onCompletion { if (it != null) _state.value = _state.value.copy(inProgress = false, error = it) }
            .collect { p ->
                val isDone = p.step.isDone()
                _state.value = _state.value.copy(
                    inProgress = !isDone,
                    percent = p.percent,
                    outputFile = if (isDone) p.file else _state.value.outputFile
                )
            }
        }
    }
}
```

**Jetpack Compose Screen:**
```kotlin
@Composable
fun PhotoCompressScreen(vm: PhotoCompressViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.compress(uri)
    }

    Column {
        Button(
            onClick = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            enabled = !state.inProgress
        ) {
            Text("Pick & Compress Photo")
        }

        if (state.inProgress) {
            LinearProgressIndicator(progress = state.percent / 100f)
            Text("Compressing… ${state.percent}%")
        }

        state.outputFile?.let { file ->
            Text("Output: ${file.absolutePath} (${file.length() / 1024} KB)")
        }

        state.error?.let {
            Text("Error: ${it.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}
```

### 3. Background Compression with WorkManager
For long-running or batch jobs, `WorkManager` is the best choice.

```kotlin
class CompressWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val uriStr = inputData.getString("sourceUri") ?: return Result.failure()
        val uri = Uri.parse(uriStr)

        return try {
            val outFile = ImageCompressor.compress(
                context = applicationContext,
                source = Source.Uri(uri)
            )
            Result.success(workDataOf("outPath" to outFile.absolutePath))
        } catch (t: Throwable) {
            Result.retry() // or Result.failure()
        }
    }
}

fun enqueueCompression(workManager: WorkManager, sourceUri: Uri) {
    val request = OneTimeWorkRequestBuilder<CompressWorker>()
        .setInputData(workDataOf("sourceUri" to sourceUri.toString()))
        .setConstraints(Constraints(requiresStorageNotLow = true))
        .build()
    workManager.enqueue(request)
}
```

## API Reference
```kotlin
// Main entry points
public object ImageCompressor {
    public suspend fun compress(context: Context, source: Source, options: Options = Options()): File
    public suspend fun compressToByteArray(context: Context, source: Source, options: Options = Options()): ByteArray
    public fun compressAsFlow(context: Context, source: Source, options: Options = Options()): Flow<Progress>
}

// Configuration & State
public data class Options(
    public val maxWidth: Int = 1280,
    public val maxHeight: Int = 1280,
    public val format: CompressFormat = CompressFormat.JPEG,
    public val quality: Int = 80,
    public val maxBytes: Long? = null,
    public val keepExif: Boolean = true,
    public val colorSpace: ColorSpace = ColorSpace.SRGB,
    public val destination: Destination = Destination.Cache()
)

public data class Progress(
    public val step: Step,
    public val percent: Int,
    public val file: File? = null
)

public enum class Step {
    Loading, Decoding, Resizing, Encoding, Writing, Done;
    public fun isDone(): Boolean
}

// Inputs and Destination
public sealed class Source {
    public data class File(public val file: java.io.File) : Source()
    public data class Uri(public val uri: android.net.Uri) : Source()
    public data class Bytes(public val bytes: ByteArray) : Source()
}

public sealed class Destination {
    public data class File(public val file: java.io.File) : Destination()
    public data class Cache(public val subdir: String = "images") : Destination()
}
```

## Best Practices
- **Permissions**: This library only handles compression. Your app is responsible for requesting storage permissions or using Storage Access Framework (e.g., Photo Picker) to get a readable `Uri`.
- **Threading**: All heavy work is dispatched to `Dispatchers.IO` internally. You can safely call these functions from the main thread.
- **Lifecycle**: Keep compression work out of Composable functions. Trigger compression from a `ViewModel` or a `LaunchedEffect` tied to a specific state.
- **Size Constraints**: To meet a specific file size (e.g., for uploads), set `maxBytes`. The library will first reduce quality (for lossy formats) and then downscale the image iteratively to meet the constraint.
- **Transparency**: Prefer `PNG` or `WEBP` for images with an alpha channel. `JPEG` does not support transparency.

## License
Licensed under the MIT License. See the root `LICENSE` file for details.
