# Size targets with quality limits

`maxBytes` is a ceiling on the complete output, including any copied EXIF. A successful call returns an image within that ceiling; an impossible target throws `IOException`.

Use `CompressionConstraints` when the output also needs minimum dimensions or an encoder quality floor:

```kotlin
val result = ImageCompressor.compressWithResult(
    context,
    Source.Uri(pickedImage),
    options = Options.forUpload(maxBytes = 300_000),
    constraints = CompressionConstraints(
        minQuality = 65,
        minWidth = 640,
        minHeight = 480,
    ),
)
```

The minimum dimensions apply to the oriented output. Choose them for the expected image shape: a portrait photo may need a different width/height floor from a landscape photo. Small inputs are never enlarged to meet a floor. A floor above the decoded image dimensions fails.

For JPEG and lossy WebP, `Options.quality` is the encoder quality ceiling. `minQuality` must be between 0 and that ceiling. These are codec parameters, not percentages of visual quality. PNG ignores both quality values. WebP at quality 100 is lossless on API 29+ and lossy on API 28; a lower selected quality uses lossy encoding.

The constrained path probes quality within those bounds before reducing dimensions. It uses a bounded search with at most 32 dimension adjustments, checks the final byte count after metadata, and reports the selected quality and actual dimensions in `CompressionResult`. Codec sizes are not perfectly monotonic, so this is not a guarantee of the highest possible visual quality. Metadata copying can add work to every probe; the upload preset omits that metadata.

Calls without `CompressionConstraints` keep the existing targeting strategy. The constraints overloads are also available for file, byte-array, and progress-flow output.

Handle an unsatisfiable target alongside other image input/output failures. An explicit destination is replaced only after the complete output passes validation; failure leaves an existing destination intact. Let coroutine cancellation propagate.
