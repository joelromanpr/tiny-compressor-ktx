#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
serial="${ANDROID_SERIAL:-}"
output_dir="${1:-$repo_root/benchmark/results/local}"
iterations="${BENCHMARK_ITERATIONS:-5}"
warmups="${BENCHMARK_WARMUPS:-2}"

if [[ -z "$serial" ]]; then
    printf 'Set ANDROID_SERIAL to the device to measure (see adb devices).\n' >&2
    exit 1
fi
if [[ ! "$iterations" =~ ^[0-9]+$ || "$iterations" -lt 1 || "$iterations" -gt 100 || ! "$warmups" =~ ^[0-9]+$ || "$warmups" -gt 20 ]]; then
    printf 'BENCHMARK_ITERATIONS must be 1..100; BENCHMARK_WARMUPS must be 0..20.\n' >&2
    exit 1
fi

cd "$repo_root"
revision="$(git rev-parse HEAD)"
if [[ -n "$(git status --porcelain)" ]]; then
    revision="$revision-dirty"
fi
export ANDROID_SERIAL="$serial"
./gradlew -PincludeBenchmarks=true -PbenchmarkRevision="$revision" \
    :benchmark:assembleDebug :benchmark:assembleDebugAndroidTest --console=plain
adb -s "$serial" install -r benchmark/build/outputs/apk/debug/benchmark-debug.apk
adb -s "$serial" install -r benchmark/build/outputs/apk/androidTest/debug/benchmark-debug-androidTest.apk

mkdir -p "$output_dir"
adb -s "$serial" shell am force-stop com.joelromanpr.tinycompressor.benchmark
adb -s "$serial" shell am instrument -w -r \
    -e class com.joelromanpr.tinycompressor.benchmark.CompressionBenchmark \
    -e iterations "$iterations" -e warmups "$warmups" \
    com.joelromanpr.tinycompressor.benchmark.test/androidx.test.runner.AndroidJUnitRunner \
    | tee "$output_dir/instrumentation.txt"
if ! grep -q 'OK (1 test)' "$output_dir/instrumentation.txt"; then
    printf 'Benchmark instrumentation did not pass; results were not exported.\n' >&2
    exit 1
fi
adb -s "$serial" exec-out run-as com.joelromanpr.tinycompressor.benchmark \
    cat files/benchmark/results.json > "$output_dir/results.json"
python3 "$repo_root/scripts/summarize_benchmarks.py" "$output_dir/results.json" \
    > "$output_dir/summary.md"
printf 'Saved %s\n' "$output_dir/results.json" "$output_dir/summary.md"
