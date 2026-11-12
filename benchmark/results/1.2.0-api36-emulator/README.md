# 1.2.0 release-candidate measurements

Recorded November 11, 2026 from clean public revision [`443a3ee28353f94f360291eed72964cbcc0e28d5`](https://github.com/joelromanpr/tiny-compressor-ktx/commit/443a3ee28353f94f360291eed72964cbcc0e28d5), with `VERSION_NAME=1.2.0`. Only report files were added after the runs. [Source hashes](source-hashes.json) retain the exact library, harness, assets, and build configuration used.

## Compression

[Summary table](summary.md) · [Raw JSON](results.json) · [Instrumentation output](instrumentation.txt)

The run used a Google `sdk_gphone64_arm64` emulator, Android API 36, arm64-v8a. Each of the 18 input/engine pairs had two warmups and five timed samples. All six capped workloads stayed within 100,000 bytes. The instrumentation test passed.

The library workloads call the existing three-argument `compress(context, source, options)` API, without `CompressionConstraints`. They therefore measure the legacy targeting strategy in 1.2.0; these results do not evaluate the new constrained search, batch concurrency, or upload networking.

These are emulator measurements, not physical-device performance claims. Host load, caches, and thermal state were not controlled. RGB PSNR is compared at each output's dimensions and does not penalize detail lost by downscaling. Process PSS is sampled in a separate run and is neither isolated per-operation memory nor a guaranteed peak. See the full [methodology](../../README.md) and [image provenance](../../SOURCES.md).

## Dependency footprint

[Raw footprint report](dependency-footprint.json)

| Measurement | Bytes |
| --- | ---: |
| Minimal baseline APK | 2,036,341 |
| Same app with the library | 3,267,433 |
| Unshrunk APK delta, including transitive dependencies | 1,231,092 |
| Library AAR, excluding transitive dependencies | 93,781 |

Both minimal-app release flavors use the same plain Activity source, assets (none), application ID, SDK levels, and build settings. Only `withCompressor` declares the library dependency. Code and resource shrinking are disabled. Existing apps may already share the dependencies; this is not a prediction of R8 cost, installed size, or Play download size. The AAR contains no packaged native libraries.

The footprint comparison used AGP 8.13.1, Kotlin 2.0.21, Gradle 8.13, compile/target SDK 36, min SDK 30 for the measurement app, and Java bytecode target 11. The core library's minimum is API 28. The host Java version is recorded in the source manifest. The [1.1.0 baseline](../1.1.0-api36-emulator/README.md) remains separate.
