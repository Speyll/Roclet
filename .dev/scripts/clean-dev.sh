#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
DEV_DIR="$PROJECT_ROOT/.dev"

[[ -d "$PROJECT_ROOT/.git" ]] || { echo "ERROR: project root is not a Git repository: $PROJECT_ROOT" >&2; exit 1; }
[[ "$PROJECT_ROOT" != "/" ]] || { echo 'ERROR: refusing to operate at /.' >&2; exit 1; }
[[ ! -L "$PROJECT_ROOT/.dev" ]] || { echo 'ERROR: refusing to remove .dev because it is a symlink.' >&2; exit 1; }
[[ -d "$DEV_DIR" ]] || { echo "Nothing to clean: $DEV_DIR does not exist."; exit 0; }
RESOLVED_DEV="$(realpath -e -- "$DEV_DIR")"
EXPECTED_DEV="$(realpath -m -- "$PROJECT_ROOT/.dev")"
[[ "$RESOLVED_DEV" == "$EXPECTED_DEV" && "$RESOLVED_DEV" == "$PROJECT_ROOT/.dev" ]] || {
  echo "ERROR: safety check failed; resolved path '$RESOLVED_DEV' differs from '$EXPECTED_DEV'." >&2
  exit 1
}
printf 'This removes only this project-local directory and all downloaded toolchain/cache files:\n  %s\n\n' "$DEV_DIR"
du -sh -- "$DEV_DIR" 2>/dev/null || true
printf "Type 'yes' to permanently delete .dev/: "
IFS= read -r confirmation
[[ "$confirmation" == yes ]] || { echo 'Aborted.'; exit 1; }
rm -rf -- "$DEV_DIR"
echo 'Removed .dev/. Source code, .git/, Gradle wrapper files, /usr/bin/adb, ADB keys, and PipeWire configuration were not targeted.'
