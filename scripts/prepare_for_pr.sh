#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
  cat <<'HELP'
Usage: scripts/prepare_for_pr.sh [--fix] [-- <extra Gradle arguments>]

Checks formatting, library unit tests, and the demo debug build.
Pass --fix to apply Spotless formatting before running those checks.

Examples:
  scripts/prepare_for_pr.sh
  scripts/prepare_for_pr.sh --fix
  scripts/prepare_for_pr.sh -- --stacktrace
HELP
}

fix=false
gradle_args=()
while (($#)); do
  case "$1" in
    --fix)
      fix=true
      shift
      ;;
    --)
      shift
      gradle_args=("$@")
      break
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

if "$fix"; then
  ./gradlew --no-daemon spotlessApply "${gradle_args[@]}"
fi

./gradlew --no-daemon spotlessCheck :lib:testDebugUnitTest :demo:assembleDebug "${gradle_args[@]}"
