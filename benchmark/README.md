# Compression measurements

This opt-in Android module records compression results. It is excluded from the normal build and does not enforce speed or memory thresholds in CI.

## Run

Use an otherwise idle physical Android 11+ device for performance claims. An emulator is useful for exercising the harness, but its timings do not represent a phone. Keep the device, OS, power/thermal state, toolchain, and library revision the same when comparing changes.

```bash
adb devices
ANDROID_SERIAL=<device-serial> ./scripts/run_benchmarks.sh
```

The default is two warmups followed by five timed iterations for every input/engine pair. Override with `BENCHMARK_WARMUPS` (0–20) and `BENCHMARK_ITERATIONS` (1–100). Pass an output directory as the script's first argument. The default `benchmark/results/local/` directory is ignored by Git.

The script builds and installs two debug APKs for the benchmark package, runs instrumentation on the selected device, and exports `results.json`, the runner output, and a Markdown summary. It does not change the demo app. Uncommitted source changes are marked with `-dirty` in the report revision.

## What is compared

Every input is processed by three engines in a fixed order:

- `platform-fixed`: Android `ImageDecoder` with a software bitmap, sRGB, aspect-ratio-preserving bounds of 1280 × 1280, and a single platform encode at quality 80.
- `library-fixed`: the same bounds, requested format, and quality through Tiny Compressor KTX, with metadata copying disabled and no byte cap.
- `library-100kb`: the library with those options plus a strict 100,000-byte cap. The library may lower quality and dimensions to meet it.

The platform path is a small reference implementation, not another library or a complete upload pipeline. It has no byte-budget search, metadata copying, atomic replacement, progress, or coroutine dispatch. `library-100kb` is a different workload from either fixed engine: compare its dimensions and output bytes as well as time. PNG ignores the quality value. All paths normalize orientation during decode and write an output file. Inputs and output files are on app-private storage.

The corpus includes an actual high-resolution camera photograph, that photo requested as WebP, a generated text-heavy screenshot, translucent shapes, a very wide generated panorama, and the photo with a rotated EXIF orientation. The provenance is in [SOURCES.md](SOURCES.md). One real photo and synthetic cases are a small regression corpus, not a representative sample of everyone's images.

## Measurements and limits

`durationMs` contains every timed sample, including decode, resize, encode, file writing, and the library's dispatch/metadata checks. Fixture generation, hashing, quality evaluation, and the memory sampling run are outside those timings. Values are monotonic elapsed time. Median and nearest-rank P90 are calculated from the samples; five samples are too few for stable tail-latency claims. The order is fixed, the debug build is not profiled, filesystem caches are not cleared, and thermal/GC effects are not controlled. For comparative performance work, repeat whole runs with more samples and alternate engine order.

`outputBytes`, decoded dimensions, MIME type, and whether a requested cap was met come from the actual saved output. An impossible target fails the instrumentation rather than producing a success row. Raw input hashes make fixture drift visible.

Quality is RGB PSNR against an sRGB decode of the source resized to that output's dimensions, after both images are composited over white. Infinite PSNR is represented by `identicalRgbOverWhite=true` and a null dB value. Alpha mean absolute error is reported separately. PSNR is a pixel-error metric, not a perceptual quality score. Resizing the reference means the metric **does not penalize detail lost by reducing dimensions**. It can also reflect decoder/scaling differences. Inspect full-size crops and actual dimensions before drawing visual-quality conclusions, especially for transparency or orientation.

Memory is measured during a separate additional compression after timing. Android `Debug.MemoryInfo.totalPss` is sampled before/after and about every 25 ms while the call runs. The report labels the maximum `observedProcessPssMaxKiB`: it includes the whole process, runtime, caches, shared pages, and earlier allocations; it can miss short-lived peaks. It is not a per-operation allocation count, leak detector, or guaranteed peak bitmap memory. Sampling itself has overhead and is deliberately kept out of timed runs.

No benchmark results are included until a run has been saved with its device details and revision. Do not describe a library as faster, smaller, or visually equivalent based only on its API or these fixtures.

## Artifact and app footprint

```bash
./gradlew :lib:assembleRelease :demo:assembleRelease
python3 scripts/measure_footprint.py > footprint.json
```

This reports compressed AAR size, the classes JAR, uncompressed class bytes, packaged native libraries, and total demo APK bytes. The AAR excludes transitive dependencies; the APK includes the entire demo and its assets. Neither number alone is the library's incremental app cost. To measure that, build the same app without this library/dependencies and pass its APK as `--baseline-apk`; keep assets, app sources, shrinking, and build settings otherwise equivalent. APK byte differences are neither installed size nor Play download size.

For a controlled dependency-only comparison, run:

```bash
./scripts/measure_dependency_footprint.sh
```

This builds the opt-in `footprint` app's `baselineRelease` and `withCompressorRelease` flavors. Both use the same plain `android.app.Activity`, assets (none), application ID, SDK levels, and build settings. Only `withCompressor` declares the library dependency. Code and resource shrinking are disabled in both, so the APK delta includes the library and its transitive dependencies even though the sample Activity does not call them. This measures an unshrunk dependency addition to an otherwise empty app; it is not a prediction for an app using R8 or one that already depends on coroutines/ExifInterface. No device is needed. The JSON report includes the library version and Git revision, and defaults to `benchmark/results/local/dependency-footprint.json`.
