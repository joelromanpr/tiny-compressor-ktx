#!/usr/bin/env bash
set -euo pipefail

# Always run from repo root regardless of where this script is invoked
cd "$(dirname "$0")/.."

print_intro() {
  cat <<'EOF'
Prepare for PR: this script formats code with Spotless.

Examples:
  - Entire project (default):
      scripts/spotless.sh
  - Entire project (check mode):
      scripts/spotless.sh --check
  - Specific module (apply):
      scripts/spotless.sh :app
      scripts/spotless.sh app
  - Specific module (check mode):
      scripts/spotless.sh :app --check
  - Pass extra Gradle flags:
      scripts/spotless.sh -- --stacktrace --continue

EOF
}

usage() {
  print_intro
  echo "Usage:"
  echo "  scripts/spotless.sh [<modulePath>] [--check] [-y|--yes] [-- <extra gradle args>]"
  echo
  echo "Notes:"
  echo "  - <modulePath> can be ':app' or 'app' (nested modules like ':feature:core' are supported)."
  echo "  - Default action is 'apply' across the entire project."
  echo "  - Use --check for verification without modifying files."
  exit 0
}

normalize_module_path() {
  local m="${1:-}"
  if [[ -z "${m}" ]]; then
    echo ""
    return
  fi
  if [[ "${m}" == :* ]]; then
    echo "${m}"
  else
    echo ":${m}"
  fi
}

# Parse args
module=""
mode="apply"      # apply | check
auto_yes="false"
declare -a extra_args=()

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
fi

# Split args at '--' to forward the rest to Gradle
forward=false
for arg in "$@"; do
  if [[ "${forward}" == "true" ]]; then
    extra_args+=("$arg")
    continue
  fi
  case "${arg}" in
    --)
      forward=true
      ;;
    --check)
      mode="check"
      ;;
    -y|--yes)
      auto_yes="true"
      ;;
    -*)
      # Unknown flags before '--' -> show usage
      usage
      ;;
    *)
      # First non-flag is module path (optional)
      if [[ -z "${module}" ]]; then
        module="${arg}"
      else
        usage
      fi
      ;;
  esac
done

module="$(normalize_module_path "${module}")"
task="spotlessApply"
[[ "${mode}" == "check" ]] && task="spotlessCheck"

print_intro

# Build the command safely using an array
cmd=(./gradlew --no-daemon)
if [[ -n "${module}" ]]; then
  cmd+=("${module}:${task}")
else
  cmd+=("${task}")
fi
# Append extra args only if present (avoids unbound issues under set -u)
if [[ ${#extra_args[@]:-0} -gt 0 ]]; then
  cmd+=("${extra_args[@]}")
fi

# Show what will run
printf "About to run: "
printf "%q " "${cmd[@]}"
printf "\n\n"

# Auto-confirm in CI or with -y/--yes
if [[ "${auto_yes}" != "true" && -z "${CI:-}" ]]; then
  read -r -p "Proceed? [Y/n]: " reply
  reply="${reply:-Y}"
  case "${reply}" in
    [Yy]*) ;;
    *) echo "Aborted."; exit 0 ;;
  esac
fi

# Execute
"${cmd[@]}"

echo
echo "Done. Spotless ${mode} completed."
