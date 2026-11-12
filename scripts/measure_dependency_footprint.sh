#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_file="${1:-$repo_root/benchmark/results/local/dependency-footprint.json}"
cd "$repo_root"
./gradlew -PincludeFootprint=true :lib:assembleRelease \
    :footprint:assembleBaselineRelease :footprint:assembleWithCompressorRelease \
    :footprint:spotlessCheck --console=plain
mkdir -p "$(dirname "$output_file")"
python3 scripts/measure_footprint.py --dependency-only \
    --apk footprint/build/outputs/apk/withCompressor/release/footprint-withCompressor-release-unsigned.apk \
    --baseline-apk footprint/build/outputs/apk/baseline/release/footprint-baseline-release-unsigned.apk \
    > "$output_file"
printf 'Saved %s\n' "$output_file"
