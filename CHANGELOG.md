# Changelog

## 1.2.1 — 2026-11-12

- Make upload-recipe cleanup safe when cancellation, completion observers, and reconciliation remove the same directory concurrently.
- Add regression coverage for concurrent removal and already-removed paths.

## 1.2.0 — 2026-11-11

- Support Android 9 (API 28) and newer, using the older platform WebP encoder before API 30.
- Add `compressWithResult` for actual output dimensions, format, encoder quality, and byte counts. URI input sizes and savings can be unknown.
- Add `Options.forUpload(maxBytes)` to apply an upload limit without retaining source EXIF.
- Add optional `CompressionConstraints` overloads for minimum encoder quality and dimensions. Existing calls keep their targeting behavior and signatures.
- Add batch file compression with bounded concurrency, per-item failures, completion progress, and cancellation cleanup.
- Replace the sample screen with an interactive size/format comparison and a credited NASA photograph.
- Add reproducible compression and dependency-footprint measurements, plus compiled foreground and WorkManager upload examples.
- Run library and upload-recipe device tests on API 28, 30, and 36. Releases include a debug-signed demo APK and checksum after Maven publishing succeeds.

**Compatibility:** Existing APIs and `Options()` defaults are unchanged. The legacy default still retains selected JPEG EXIF, including GPS. Use `Options.forUpload(...)` or `keepExif = false` when those details are unnecessary. The new APIs are additive.

## 1.1.0 — 2026-09-25

- Stage output before replacing a destination file, so failed encoding leaves an existing file intact. Concurrent cache outputs get unique names.
- Make `compressToByteArray` independent of `Options.destination`. Byte output avoids a disk round trip when JPEG EXIF retention is off.
- Detect the input format from decoded content. PNG inputs retain PNG output unless WebP is requested, and generated cache extensions match their actual format. Explicit file destinations with a known, mismatched image extension are rejected.
- Keep rotated JPEG pixels and EXIF orientation consistent.
- Make `maxBytes` a strict limit on final output, including retained EXIF. An impossible target now throws `IOException` instead of returning an oversized image. Adaptive downscaling preserves aspect ratio.
- Refresh the demo, documentation, and pull request checks. Maven Central publication is gated by a matching annotated version tag after validation.

**Privacy:** `keepExif` remains `true` by default for compatibility. JPEG output may retain GPS coordinates. Set `keepExif = false` when that metadata is unnecessary.

## 1.0.0

- Initial Android release with file, content URI, and byte-array inputs; file and byte-array outputs; Flow progress; JPEG, PNG, and WebP encoding.
