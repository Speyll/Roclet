#!/usr/bin/env bash
# Project-local Android toolchain bootstrap for CachyOS/Arch x86_64.
# No sudo/pacman, no global environment changes, no PipeWire access.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/env.sh"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
for command_name in bash curl unzip tar sha256sum git realpath grep awk; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required host utility '$command_name' is missing. Install it yourself if desired; this script will not use pacman or sudo."
done
[[ "$(uname -m)" == x86_64 ]] || fail "this setup is pinned for x86_64 Linux; detected $(uname -m)."
[[ -x "$ADB" ]] || fail "expected the existing ADB at $ADB; this script will not download a duplicate."

mkdir -p "$DEV_DIR/downloads" "$DEV_DIR/staging" "$DEV_DIR/gradle-bootstrap" "$DEV_DIR/gradle-home" "$DEV_DIR/android-user-home" "$DEV_DIR/source" "$ANDROID_HOME/cmdline-tools"
if command -v df >/dev/null 2>&1; then
  free_kb="$(df -Pk "$PROJECT_ROOT" | awk 'NR==2 {print $4}')"
  if [[ "$free_kb" =~ ^[0-9]+$ ]] && (( free_kb < 3000000 )); then
    echo "WARNING: less than 3 GB free in the project filesystem. Downloads/build caches may need more space." >&2
    printf 'Continue anyway? [y/N] '
    IFS= read -r answer
    [[ "$answer" == y || "$answer" == Y || "$answer" == yes || "$answer" == YES ]] || exit 1
  fi
fi

fetch() {
  local url="$1" out="$2"
  if [[ -s "$out" ]]; then return; fi
  echo "Downloading $(basename -- "$out")"
  curl -fL --retry 2 --connect-timeout 20 -o "$out.part" "$url"
  mv -- "$out.part" "$out"
}
extract_verified() {
  local archive="$1" expected="$2"
  [[ "$expected" =~ ^[[:xdigit:]]{64}$ ]] || fail "invalid SHA-256 format for $(basename -- "$archive")."
  printf '%s  %s\n' "$expected" "$archive" | sha256sum -c - >/dev/null || fail "SHA-256 validation failed for $archive; not extracting it."
  echo "SHA-256 verified: $(basename -- "$archive")"
}
sha_from_sidecar() {
  local sidecar="$1" value
  value="$(grep -Eo '[[:xdigit:]]{64}' "$sidecar" | head -n 1 || true)"
  [[ "$value" =~ ^[[:xdigit:]]{64}$ ]] || fail "no valid 64-character SHA-256 found in official sidecar: $sidecar"
  printf '%s' "$value" | tr '[:upper:]' '[:lower:]'
}

# 1) Temurin JDK 21, verified against the checksum sidecar from its official release.
JDK_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz'
JDK_SHA_URL="${JDK_URL}.sha256.txt"
if [[ -x "$JAVA_HOME/bin/java" ]] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21\.'; then
  echo 'JDK 21 already installed; skipping.'
else
  [[ ! -e "$JAVA_HOME" ]] || fail "$JAVA_HOME exists but is not a verified working JDK 21. Inspect it manually; refusing to overwrite it."
  jdk_archive="$DEV_DIR/downloads/temurin-jdk-21.0.12.1+1-linux-x64.tar.gz"
  jdk_sidecar="$DEV_DIR/downloads/temurin-jdk-21.0.12.1+1-linux-x64.tar.gz.sha256.txt"
  fetch "$JDK_URL" "$jdk_archive"
  fetch "$JDK_SHA_URL" "$jdk_sidecar"
  jdk_sha="$(sha_from_sidecar "$jdk_sidecar")"
  extract_verified "$jdk_archive" "$jdk_sha"
  jdk_stage="$DEV_DIR/staging/jdk"
  [[ ! -e "$jdk_stage" ]] || fail "$jdk_stage already exists from an earlier interrupted run; inspect/remove it before retrying."
  mkdir -p "$jdk_stage"
  tar -xzf "$jdk_archive" --strip-components=1 -C "$jdk_stage"
  [[ -x "$jdk_stage/bin/java" ]] || fail 'JDK extraction did not produce bin/java.'
  "$jdk_stage/bin/java" -version 2>&1 | grep -q 'version "21\.' || fail 'staged JDK is not Java 21.'
  mv -- "$jdk_stage" "$JAVA_HOME"
  rm -f -- "$jdk_archive" "$jdk_sidecar"
fi

# Make the private JDK available only to this process and its children.
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# 2) Google Android command-line tools; SHA-256 is published on the official Android download page.
TOOLS_URL='https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip'
TOOLS_SHA='4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ -x "$SDKMANAGER" ]] && "$SDKMANAGER" --version >/dev/null 2>&1; then
  echo 'Android command-line tools already installed; skipping.'
else
  [[ ! -e "$ANDROID_HOME/cmdline-tools/latest" ]] || fail "$ANDROID_HOME/cmdline-tools/latest exists but sdkmanager failed validation. Inspect it manually; refusing to overwrite it."
  tools_archive="$DEV_DIR/downloads/commandlinetools-linux-15859902_latest.zip"
  fetch "$TOOLS_URL" "$tools_archive"
  extract_verified "$tools_archive" "$TOOLS_SHA"
  tools_stage="$(mktemp -d "$DEV_DIR/staging/android-tools.XXXXXX")"
  unzip -q "$tools_archive" -d "$tools_stage"
  [[ -x "$tools_stage/cmdline-tools/bin/sdkmanager" ]] || fail 'Android tools archive did not contain cmdline-tools/bin/sdkmanager.'
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv -- "$tools_stage/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rmdir "$tools_stage" 2>/dev/null || true
  rm -f -- "$tools_archive"
  "$SDKMANAGER" --version >/dev/null || fail 'sdkmanager did not run after extraction.'
fi
export ANDROID_HOME ANDROID_SDK_ROOT ANDROID_USER_HOME
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# 3) Accept licences only through an interactive terminal; do not auto-answer.
if [[ ! -d "$ANDROID_HOME/platforms/android-36" || ! -d "$ANDROID_HOME/build-tools/36.0.0" ]]; then
  [[ -t 0 && -t 1 ]] || fail 'SDK packages are missing but no interactive terminal is available for licence review. Run this script from a terminal.'
  echo 'Google Android SDK licence prompts follow. Read them and accept/reject interactively.'
  "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses
  echo 'Installing Android platform API 36 and Build Tools 36.0.0...'
  "$SDKMANAGER" --sdk_root="$ANDROID_HOME" 'platforms;android-36' 'build-tools;36.0.0'
else
  echo 'Android API 36 and Build Tools 36.0.0 already installed; skipping SDK package install.'
fi
[[ -d "$ANDROID_HOME/platforms/android-36" && -d "$ANDROID_HOME/build-tools/36.0.0" ]] || fail 'expected Android SDK packages are still missing after sdkmanager.'

# 4) Gradle 9.7.0. Download and verify official SHA sidecar; pin wrapper checksum too.
GRADLE_VERSION='9.7.0'
GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_SHA_URL="${GRADLE_URL}.sha256"
gradle_sidecar="$DEV_DIR/downloads/gradle-${GRADLE_VERSION}-bin.zip.sha256"
fetch "$GRADLE_SHA_URL" "$gradle_sidecar"
gradle_sha="$(sha_from_sidecar "$gradle_sidecar")"
WRAPPER_PROPS="$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties"
if [[ -x "$PROJECT_ROOT/gradlew" && -f "$WRAPPER_PROPS" ]]; then
  grep -q "gradle-${GRADLE_VERSION}-bin.zip" "$WRAPPER_PROPS" || fail "an existing Gradle wrapper is not pinned to ${GRADLE_VERSION}; refusing to overwrite it."
  if grep -q '^distributionSha256Sum=' "$WRAPPER_PROPS"; then
    current_sha="$(sed -n 's/^distributionSha256Sum=//p' "$WRAPPER_PROPS" | tail -n1)"
    [[ "$current_sha" == "$gradle_sha" ]] || fail 'existing wrapper checksum differs from the official Gradle checksum; inspect it manually.'
  else
    printf '\ndistributionSha256Sum=%s\n' "$gradle_sha" >> "$WRAPPER_PROPS"
  fi
  echo 'Existing Gradle wrapper version/checksum validated; skipping wrapper generation.'
else
  [[ ! -e "$PROJECT_ROOT/gradlew" && ! -e "$PROJECT_ROOT/gradle/wrapper" ]] || fail 'partial Gradle wrapper files exist. Back them up/inspect them before retrying.'
  gradle_archive="$DEV_DIR/downloads/gradle-${GRADLE_VERSION}-bin.zip"
  fetch "$GRADLE_URL" "$gradle_archive"
  extract_verified "$gradle_archive" "$gradle_sha"
  gradle_stage="$DEV_DIR/staging/gradle-bootstrap"
  [[ ! -e "$gradle_stage" ]] || fail "$gradle_stage already exists from an earlier interrupted run; inspect/remove it before retrying."
  mkdir -p "$gradle_stage"
  unzip -q "$gradle_archive" -d "$gradle_stage"
  GRADLE_BIN="$gradle_stage/gradle-${GRADLE_VERSION}/bin/gradle"
  [[ -x "$GRADLE_BIN" ]] || fail 'verified Gradle archive did not contain the expected executable.'
  "$GRADLE_BIN" --version >/dev/null || fail 'bootstrap Gradle failed to run.'
  wrapper_project="$DEV_DIR/staging/wrapper-project"
  [[ ! -e "$wrapper_project" ]] || fail "$wrapper_project exists from an earlier interrupted run; inspect/remove it before retrying."
  mkdir -p "$wrapper_project"
  printf 'rootProject.name = "wrapper-bootstrap"\n' > "$wrapper_project/settings.gradle.kts"
  printf '\n' > "$wrapper_project/build.gradle.kts"
  (cd "$wrapper_project" && "$GRADLE_BIN" --no-daemon wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin)
  [[ -x "$wrapper_project/gradlew" && -f "$wrapper_project/gradle/wrapper/gradle-wrapper.jar" && -f "$wrapper_project/gradle/wrapper/gradle-wrapper.properties" ]] || fail 'Gradle failed to create all wrapper files.'
  mkdir -p "$PROJECT_ROOT/gradle"
  cp -a "$wrapper_project/gradlew" "$PROJECT_ROOT/gradlew"
  cp -a "$wrapper_project/gradlew.bat" "$PROJECT_ROOT/gradlew.bat"
  cp -a "$wrapper_project/gradle/wrapper" "$PROJECT_ROOT/gradle/"
  chmod +x "$PROJECT_ROOT/gradlew"
  printf '\ndistributionSha256Sum=%s\n' "$gradle_sha" >> "$WRAPPER_PROPS"
  export GRADLE_USER_HOME
  GRADLE_USER_HOME="$DEV_DIR/gradle-home" "$PROJECT_ROOT/gradlew" --version >/dev/null || fail 'Gradle wrapper failed to download/verify its pinned distribution.'
  rm -rf -- "$gradle_stage" "$wrapper_project"
  rm -f -- "$gradle_archive"
fi
rm -f -- "$gradle_sidecar"

# 5) Inspect upstream implementation source only under .dev/source/. No audio commands are run.
for repo in roc-droid roc-java; do
  destination="$DEV_DIR/source/$repo"
  case "$repo" in
    roc-droid) url='https://github.com/roc-streaming/roc-droid.git' ;;
    roc-java)  url='https://github.com/roc-streaming/roc-java.git' ;;
  esac
  if [[ -d "$destination/.git" ]]; then
    echo "Upstream checkout already exists: $destination (not updating it)."
  elif [[ -e "$destination" ]]; then
    fail "$destination exists but is not a Git checkout; inspect it before retrying."
  else
    echo "Cloning official $repo source into .dev/source/$repo for read-only inspection..."
    git clone --depth 1 "$url" "$destination"
  fi
done

# Ensure the project-local cache directories exist and report results.
mkdir -p "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"
echo
echo 'Toolchain setup finished. No Android app source files were created.'
echo "JDK:             $JAVA_HOME"
echo "Android SDK:     $ANDROID_HOME"
echo "Gradle cache:    $GRADLE_USER_HOME"
echo "Upstream source: $DEV_DIR/source/"
echo 'Next: inspect .dev/source/roc-droid and roc-java build files for the actual ROC artifact/API before creating app dependencies.'
