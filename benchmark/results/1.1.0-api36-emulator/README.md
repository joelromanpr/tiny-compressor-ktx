# 1.1.0 baseline measurements

Recorded November 11, 2026. This is an **Android API 36 arm64 emulator run** used to validate the harness and retain a pre-change baseline. Its timings are not physical-device performance claims or a prediction for every app.

- [Compression results](summary.md), [raw JSON](results.json), and [instrumentation output](instrumentation.txt): clean source revision `cf7b53fa3dd4341135673e91aef3ed898ade3f0e`, library `1.1.0`; two warmups and five measured samples for each of 18 input/engine pairs. All six capped workloads met the 100,000-byte limit.
- [Dependency footprint](dependency-footprint.json): clean revision `94b01827126bc628856e4600a9b4f121e362f62f`, with the same library `1.1.0` source. The additional commit added the footprint harness; it did not change compression. The minimal unshrunk release APKs measured 2,036,341 bytes without the library and 3,227,453 bytes with it: a 1,191,112-byte delta including transitive dependencies. The AAR itself measured 47,096 bytes, excluding those dependencies.

The footprint comparison used AGP 8.13.1, Kotlin 2.0.21, compile/target SDK 36, min SDK 30, and Java bytecode target 11. Both flavors use the same source and have code/resource shrinking disabled. Existing apps may already include these dependencies, and R8 can remove unused code. This is not an estimate of Play download size or installed size.

See the [methodology](../../README.md) and [image provenance](../../SOURCES.md) before comparing these values. In particular, RGB PSNR is evaluated at each output's dimensions; process PSS includes the runtime and previous allocations. Future releases need their own recorded run.

The measured revision IDs above were recorded before this PR was rebased. The library implementation matches the published repository at [`9be0f61`](https://github.com/joelromanpr/tiny-compressor-ktx/commit/9be0f61). [Source hashes](source-hashes.json) retain the exact harness, configuration, and library inputs used for each measurement. Current main may contain newer behavior; use a new run when evaluating it.
