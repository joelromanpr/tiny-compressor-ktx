# Roadmap

Tiny Compressor KTX focuses on dependable Android image preparation with a small core API. New work should solve a concrete app problem and include an example or reproducible image when practical. The items below are contribution ideas, not release commitments. Open an issue before taking on a larger change.

| Contribution | Useful outcome | Suggested scope |
| --- | --- | --- |
| Assert pixels for mirrored EXIF orientations | Tests check left/right pixel positions as well as dimensions and the normalized orientation tag. | One asymmetric image generator and instrumented regression case; suitable first contribution. |
| Cover semi-transparent WebP pixels | Lossy and lossless WebP preserve intermediate alpha values as well as fully transparent pixels. | One small fixture or generator with source/license and decoded-alpha assertions. |
| Report a benchmark from a physical device | Maintainers can compare real device behavior with emulator measurements. | Record device/API, settings, raw repeated timings, output size and benchmark revision. |
| Add a real HTTP-client adapter recipe | Developers can connect the existing uploader contract to their network stack. | One optional app example with response classification, cancellation, MIME and idempotency handling; no network dependency in core. |
| Check the demo with large text and TalkBack | Controls and before/after comparison remain understandable and operable. | Reproduce one accessibility issue, fix it, and include real device evidence. |

For bugs, include the library version, Android version, input type, options, expected result and actual output. See [CONTRIBUTING.md](CONTRIBUTING.md) and the [compiled upload recipes](docs/uploads.md). Broad platform ports and new codecs need a separate design discussion before implementation.
