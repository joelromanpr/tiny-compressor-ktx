# Upload recipes

These Kotlin examples are compiled with the demo. They use `compressWithResult` and `Options.forUpload(maxBytes = 500_000)`, which removes source EXIF, including GPS. Use the result's actual `mimeType`: PNG inputs can remain PNG. The core library has no networking or WorkManager dependency. Both recipes also require the shared [OwnedDirectoryCleanup.kt](../demo/src/main/java/com/joelromanpr/tinycompressor/demo/examples/OwnedDirectoryCleanup.kt) helper; copy it with the foreground, queue, or worker examples.

## Foreground: Photo Picker → compression → upload

Start with [PhotoUploadRecipe.kt](../demo/src/main/java/com/joelromanpr/tinycompressor/demo/examples/PhotoUploadRecipe.kt) and [ImageUploader.kt](../demo/src/main/java/com/joelromanpr/tinycompressor/demo/examples/ImageUploader.kt).

```kotlin
// In an app screen, pass your real authenticated networking implementation.
PhotoUploadRecipe(uploader = imageUploader)
```

The screen opens Android's Photo Picker, compresses the selected URI, and sends the resulting file through your injected `ImageUploader`. It shows preparation, upload, success, cancellation, and failure states. Choosing another image cancels and joins the earlier request. A transient failure offers a retry using the same idempotency key. Leaving the composition cancels its work; this recipe does not survive process death.

Implement `ImageUploader.upload` with your HTTP client. Consume `image.file` and close the request body before returning, send `image.mimeType`, and return only after the backend accepts the upload. Make network calls suspending and cancellation-aware. The helper owns the output and deletes it on success, failure, and cancellation; it never deletes the original picker input. To keep a local copy, explicitly move or copy it to storage you own before returning from your uploader.

Map only known transient conditions to `TransientUploadException`: for example, connectivity loss, a timeout, or a retryable backend response. Authentication, validation, permission and unsupported-image errors require an app decision. Do not wrap `CancellationException` as a network failure. Your server should honor the supplied idempotency key, because a response may be lost after it has accepted the upload.

## Persistent work: keep a durable input first

The complete implementation is [UploadQueue.kt](../demo/src/main/java/com/joelromanpr/tinycompressor/demo/examples/UploadQueue.kt) and [UploadWorker.kt](../demo/src/main/java/com/joelromanpr/tinycompressor/demo/examples/UploadWorker.kt).

The demo compiles against `androidx.work:work-runtime-ktx:2.11.2`, verified against the [official AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/work#2.11.2). Add WorkManager to your app only if you need persistent work.

1. Provide a real `ImageUploader` through `UploadWorkerFactory`. It must be reconstructible after process death; an Activity or a closure holding a signed-in screen is insufficient.
2. Configure your Application before obtaining WorkManager. Implement `Configuration.Provider` and return `uploadWorkConfiguration(imageUploader)` from `workManagerConfiguration`. If you already use a worker factory or Hilt, include this worker in that existing DI setup instead of replacing your other factories.
3. Remove **only** WorkManager's default initializer in your app manifest, as described in [Android's custom configuration guide](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration):

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

Declare `xmlns:tools="http://schemas.android.com/tools"` on the manifest. This recipe is compiled in the demo but is not wired to its Application, because this repository has no upload service or authenticated network client to inject.

4. Construct one `UploadQueue(applicationContext, applicationScope)` at application startup. Use an application-lifetime `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, not a screen's scope. Startup reconciliation restores cleanup observers for pending work.
5. From a coroutine, call:

```kotlin
val requestId = uploadQueue.enqueue(pickedUri, maxBytes = 500_000)
// Store requestId with your app's upload record and observe WorkInfo for status.
// For an explicit user cancellation:
uploadQueue.cancel(requestId)
```

`enqueue` copies the granted URI to private `noBackupFilesDir` before scheduling. It sends only identifiers and settings through WorkManager's small `Data` payload; it never sends image bytes there. Inputs are durable until a confirmed terminal work state and are excluded from automatic backup. The request waits for a connected network and uses exponential backoff. Supply normal network permissions and your backend's authentication in the consuming app.

The worker compresses to its own attempt directory and invokes the real injected uploader. It retries only `TransientUploadException`, up to three attempts. Invalid images, impossible compression limits, permission errors and unclassified upload failures return failure. An uploader must avoid requesting a retry for a permanent HTTP rejection.

## File ownership and cancellation

| File | Owner and cleanup |
| --- | --- |
| Original picker URI | The app/user owns it; recipes never delete it. |
| Foreground output | The helper deletes its directory in `finally`, including on cancellation. |
| Durable queued input | `UploadQueue` deletes it after WorkManager confirms `SUCCEEDED`, `FAILED`, or `CANCELLED`. Startup reconciliation repeats this check after process recreation. |
| Worker attempt output | The worker deletes it in `finally`; a retry starts with a fresh output. |

A worker can stop because constraints changed or its execution window ended, and WorkManager may run it again. Its cancellation therefore propagates while preserving the durable input. Deleting that input inside `doWork` before returning success would race WorkManager accepting the result. Use `UploadQueue.cancel` for an explicit cancellation so even work cancelled before it starts gets cleaned up. A cancellation cannot undo an upload the server has already accepted. Cleanup is idempotent when observers, cancellation, and startup reconciliation run together. Symbolic links are removed without traversing their targets. I/O and security failures make cleanup best-effort; durable inputs left behind are checked again at the next startup.

An abrupt process kill cannot run Kotlin `finally`. On the next app startup, queue reconciliation deletes inputs with terminal WorkInfo, resumes observers for unfinished requests, and removes copies older than one day that have no WorkManager record (for example, a crash during preparation). Fresh unregistered copies are left alone to avoid racing an enqueue. Attempt outputs live in app cache and can be evicted by Android; do not treat cache as durable storage. These examples assume ordinary small image uploads within WorkManager's execution window; longer jobs need an app-specific foreground strategy.

## Check the examples

```bash
./gradlew :demo:assembleDebug :demo:assembleDebugAndroidTest
# With an Android device or emulator connected:
./gradlew :demo:connectedDebugAndroidTest
```

[UploadWorkerTest.kt](../demo/src/androidTest/java/com/joelromanpr/tinycompressor/demo/examples/UploadWorkerTest.kt) uses explicit test doubles to verify real image preparation, bounded transient retries, permanent failures, cancellation propagation and attempt-file cleanup. The companion `UploadQueueTest` runs a real WorkManager test scheduler to check confirmed-success cleanup and cancellation before a worker starts. The cleanup regression tests coordinate several removers on a real nested filesystem tree, force deletion during an active traversal, and check missing roots, symbolic links, and failure handling. The production examples contain no fake successful uploader.

See Android's guidance on [worker testing](https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl), [stopping and cancelling work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work), and [persisting Photo Picker access](https://developer.android.com/training/data-storage/shared/photo-picker#persist-media-file-access).
