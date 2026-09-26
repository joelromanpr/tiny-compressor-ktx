# Changelog

## 1.1.0 — unreleased

- Stage output before replacing a destination file, so failed encoding leaves an existing file intact. Concurrent cache outputs get unique names.
- Make `compressToByteArray` independent of `Options.destination`. Byte output avoids a disk round trip when JPEG EXIF retention is off.
- Detect the input format from decoded content. PNG inputs retain PNG output unless WebP is requested, and generated cache extensions match their actual format. Explicit file destinations with a known, mismatched image extension are rejected.
- Keep rotated JPEG pixels and EXIF orientation consistent.
- Make `maxBytes` a strict limit on final output, including retained EXIF. An impossible target now throws `IOException` instead of returning an oversized image. Adaptive downscaling preserves aspect ratio.
- Refresh the demo, documentation, and pull request checks. Maven Central publication is gated by a matching annotated version tag after validation.

**Privacy:** `keepExif` remains `true` by default for compatibility. JPEG output may retain GPS coordinates. Set `keepExif = false` when that metadata is unnecessary.

## 1.0.0

- Initial Android release with file, content URI, and byte-array inputs; file and byte-array outputs; Flow progress; JPEG, PNG, and WebP encoding.
