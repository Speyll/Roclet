# Self-contained Android ROC microphone app: development setup plan

**Reviewed:** 2026-10-15  
**Status:** Proposal only — do not install, download, build, or change the system until the user approves.

## Goal and hard boundaries

Build a small Kotlin Android app that sends microphone audio over Wi-Fi using the official ROC Android library to the user's **already-working, user-managed PipeWire ROC receiver** on CachyOS. The app must be testable on the user's Galaxy S23 and designed for continued microphone capture while the screen is locked, within Android's supported permission and foreground-service rules.

The development toolchain should be disposable and located inside this project, principally under `.dev/`. No Docker or Podman.

### Absolute no-touch rules for the host audio system

- Do not install, remove, update, configure, reload, restart, or otherwise manage PipeWire, WirePlumber, `pipewire-roc`, ROC receiver modules, or ROC toolkit packages on the host.
- Do not run `pw-cli load-module`, `pw-cli unload-module`, `systemctl` against PipeWire/WirePlumber, `roc-recv`, or commands intended to reconfigure or claim the user's receiver ports.
- Do not create, edit, remove, or copy configuration under `/etc/pipewire/`, `~/.config/pipewire/`, WirePlumber directories, or any other host audio configuration location.
- Treat the existing receiver as a working external dependency. The user will test it using their current configuration.
- Do not use `sudo` or automatically install packages through `pacman`.
- Do not change shell startup files, global `PATH`, or persistent global environment variables.
- Do not use containers or background services.
- Do not download anything or make any change until the user explicitly approves the setup phase.

## 1. Host facts already confirmed

| Component | Known state | Plan |
|---|---|---|
| OS / architecture | CachyOS, x86_64 | Use Linux x64 tools. |
| ADB | `/usr/bin/adb` exists | Reuse it; do not download a duplicate. |
| ROC receiver CLI | `/usr/bin/roc-recv` exists | Do not run it as part of setup or tests. |
| PipeWire control CLI | `/usr/bin/pw-cli` exists | Do not invoke it. |
| PipeWire ROC receiver | Already configured and works with Roc Droid | Leave it entirely untouched. |
| Java/JDK | `java` and `javac` not on `PATH` | Download a private JDK into `.dev/`. |
| Android SDK command-line tools | `sdkmanager` not on `PATH` | Download to `.dev/android-sdk/`. |
| Gradle | Not on `PATH` | Bootstrap a Gradle wrapper using a verified temporary Gradle distribution under `.dev/`. |

Before any download, check that the host already has `bash`, `curl`, `unzip`, `tar`, `sha256sum`, `git`, and `realpath`. If any are missing, stop and tell the user what is missing. **Do not install host packages automatically.**

## 2. Project-local directory layout

Create these paths only after the plan is approved:

```text
roc-mic/
├── .git/
├── .gitignore
├── .dev/
│   ├── downloads/                 # Temporary archives and checksum files
│   ├── staging/                   # Temporary extraction/validation area
│   ├── jdk/                       # Private Temurin JDK
│   ├── android-sdk/
│   │   ├── cmdline-tools/latest/  # sdkmanager and related tools
│   │   ├── platforms/android-36/
│   │   └── build-tools/36.0.0/
│   ├── gradle-bootstrap/          # Temporary Gradle used to generate wrapper
│   ├── gradle-home/               # GRADLE_USER_HOME: wrapper, dependency caches
│   ├── android-user-home/         # SDK tool user state, local to project
│   ├── source/                    # Temporary official upstream source checkout(s)
│   ├── logs/
│   ├── scripts/
│   │   ├── setup-dev.sh
│   │   ├── env.sh
│   │   ├── build.sh
│   │   ├── install-apk.sh
│   │   └── clean-dev.sh
│   └── setup_plan.md
├── gradlew
├── gradle/wrapper/                # Gradle wrapper files
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/...
```

`gradle/wrapper/` and the wrapper scripts belong to the project because they are the small launcher that pins Gradle. They can remain in the Git repository. Large SDKs, downloaded tools, Gradle caches, and temporary source checkouts are under `.dev/` and ignored by Git.

Use absolute paths derived from the project root inside scripts. Do not assume the user's working directory is the repository root.

## 3. Version choices to pin for the first build

Use this conservative, version-pinned toolchain for the first app build; do not silently upgrade components independently:

| Component | Planned choice | Reason / official reference |
|---|---|---|
| JDK | Eclipse Temurin JDK 21.0.12.1+1, Linux x64 HotSpot | Maintained JDK 21 release. Official release list: https://adoptium.net/temurin/releases/?arch=x64&os=linux&package=jdk&version=21 |
| Android command-line tools | Revision `15859902` | Official Google download page publishes the Linux archive and SHA-256: https://developer.android.com/studio/index.html |
| Android Gradle Plugin | `9.4.0` | Official notes state it supports Android API 37, Gradle 9.6.0 minimum, Build Tools 36.0.0, and JDK 17 minimum: https://developer.android.com/build/releases/agp-9-4-0-release-notes |
| Gradle bootstrap/wrapper | `9.7.0` binary distribution | Stable official distribution: https://services.gradle.org/distributions/gradle-9.7.0-bin.zip; official SHA sidecar: https://services.gradle.org/distributions/gradle-9.7.0-bin.zip.sha256 |
| Kotlin | AGP 9.4's built-in Kotlin support; **do not also apply `org.jetbrains.kotlin.android`** | AGP 9+ enables built-in Kotlin; official migration guide: https://developer.android.com/build/migrate-to-built-in-kotlin |
| Compile SDK | API 36 for the first stable target build | Keeps the initial build on the Android 16 SDK. Revisit API 37 only if the official SDK package is stable/available and it is useful for the app. |
| Target SDK | API 36 | Modern target suitable for initial local sideload/testing; test on the actual Galaxy S23 and verify current microphone foreground-service requirements. |
| Build Tools | `36.0.0` | Default version listed for AGP 9.4.0. |
| Min SDK | `29` | Required by the official roc-java Android runtime. |
| ROC Android library | Resolve the exact release by inspecting official `roc-droid`/`roc-java` Gradle files and Maven Central before writing dependency declarations | Do not guess the version or manually download a standalone AAR. |

**Compatibility note:** AGP 9.x has built-in Kotlin support. Do not copy older templates that apply `org.jetbrains.kotlin.android` as well; that can cause duplicate Kotlin configuration. If upstream ROC examples need a separate Kotlin plugin for a specific module, verify the current official migration and compatibility instructions before adding it.

API choice distinction: `compileSdk` controls which Android APIs the source can compile against; `targetSdk` opts the app into target-specific platform behavior; `minSdk` sets the oldest OS version that can install it. The initial values are a deliberate development choice, not a claim that API 36 is the newest API in October 2026.

## 4. Download sources and integrity validation

### JDK

- Official archive: `https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz`
- Download its matching official `.sha256.txt` sidecar from the same release URL (append `.sha256.txt` to the archive URL).
- Save both under `.dev/downloads/`.
- Parse the first checksum token; require exactly 64 hexadecimal characters; verify the downloaded archive with `sha256sum -c` before extracting. If the sidecar cannot be downloaded or parsed, or the checksum doesn't match, abort without extraction.
- Extract into `.dev/staging/jdk/`, verify `.dev/staging/jdk/bin/java -version` reports Java 21, then promote it to `.dev/jdk/`. Do not overwrite a pre-existing valid JDK blindly; skip if already valid, otherwise stop and ask before replacing.

### Android command-line tools

- Official archive: `https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip`
- SHA-256 from Google's official Android developer download page: `4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583`.
- Verify the archive against this pinned hash before extraction. Abort if verification fails.
- Extract to a fresh directory under `.dev/staging/` (not into `cmdline-tools/latest/`). Google’s ZIP contains a top-level `cmdline-tools/` directory. Validate that the staged `cmdline-tools/bin/sdkmanager` exists and is executable, then move the **contents** into `.dev/android-sdk/cmdline-tools/latest/` so the final path is exactly `.dev/android-sdk/cmdline-tools/latest/bin/sdkmanager`.
- Validate with `sdkmanager --version`. Do not use `mv cmdline-tools/* cmdline-tools/latest/`; it can try to move `latest` into itself.
- If an existing command-line tools install is valid, skip it. For a broken existing install, stage and validate a replacement first, retain a backup under `.dev/staging/` until the new one passes validation, and only then replace it.

### Gradle

- Bootstrap archive: `https://services.gradle.org/distributions/gradle-9.7.0-bin.zip`.
- Download its official `.sha256` sidecar from `https://services.gradle.org/distributions/gradle-9.7.0-bin.zip.sha256`.
- Parse and validate the checksum, then verify the archive before extracting. If the sidecar or validation fails, abort.
- Extract only into `.dev/gradle-bootstrap/gradle-9.7.0/`. Use this verified Gradle executable once to generate wrapper files pinned to `9.7.0` with the `bin` distribution type.
- Set `GRADLE_USER_HOME=.dev/gradle-home` before invoking the wrapper. Configure `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` matching the official SHA-256 sidecar so the wrapper verifies its own distribution archive. Verify `./gradlew --version` reports Gradle `9.7.0`; this invocation caches the wrapper distribution inside `.dev/gradle-home/`.
- Only after wrapper verification succeeds, remove the temporary bootstrap extraction and archive. Keep the wrapper files at the project root and the downloaded wrapper distribution/cache under `.dev/gradle-home/`.

Do not continue after failed downloads, missing checksum files, invalid archive layouts, or failed validation. Use `curl -fL`/`curl -fsSL` so HTTP errors fail, and use staging plus atomic promotion where possible. Never extract unverified archives.

## 5. SDK packages and interactive licences

After JDK and command-line tools validation:

1. Set environment variables in the setup process only:
   - `JAVA_HOME="$PROJECT_ROOT/.dev/jdk"`
   - `ANDROID_HOME="$PROJECT_ROOT/.dev/android-sdk"`
   - `ANDROID_SDK_ROOT="$PROJECT_ROOT/.dev/android-sdk"`
   - `GRADLE_USER_HOME="$PROJECT_ROOT/.dev/gradle-home"`
   - `ANDROID_USER_HOME="$PROJECT_ROOT/.dev/android-user-home"`
   - prepend the private JDK and command-line tools `bin` directories to `PATH` only in that process.
2. Run `sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses` interactively. Show each prompt; the user must decide whether to accept the licences. Do not pipe `yes`, auto-answer prompts, or continue if required licences are declined.
3. After acceptance, run `sdkmanager --sdk_root="$ANDROID_SDK_ROOT" "platforms;android-36" "build-tools;36.0.0"`.
4. Check that the exact expected package directories and key executables exist. Do not install `platform-tools` because `/usr/bin/adb` is already available and should be reused.
5. Write `.dev/scripts/env.sh` as a script that sets project-local variables for a child process. Prefer `build.sh` and `install-apk.sh` to invoke a child shell with these variables rather than asking the user to source a file that permanently changes their current interactive shell.

SDK components are downloaded by Google's `sdkmanager` over HTTPS. If `sdkmanager` reports package or licence errors, stop and report them; do not switch to unverified mirror downloads.

## 6. Investigate and select the ROC implementation (no host audio changes)

After the user approves source inspection:

1. Check out `https://github.com/roc-streaming/roc-droid` and, if needed, `https://github.com/roc-streaming/roc-java` **only under `.dev/source/`**. Do not clone to another directory.
2. Read the repositories' current build files, APIs, licence files, and setup/build instructions. Compare adapting the smallest useful parts of upstream Roc Droid against writing a new minimal Kotlin UI/service using the published library.
3. Verify the exact published Maven coordinate and version using the upstream Gradle build files and Maven Central metadata before writing the dependency. The expected coordinate family is `org.roc-streaming.roctoolkit:roc-android:<verified-version>`; if official metadata disagrees, stop and explain the discrepancy rather than guessing.
4. Let Gradle resolve the AAR and native libraries normally; keep its dependency cache in `.dev/gradle-home/caches/`. Do not download an arbitrary AAR manually or add a native toolchain/NDK unless the verified dependency demonstrably requires one.
5. Preserve the source project's applicable licence notices and any licence obligations for code reused from Roc Droid.

No inspection or build step may execute `pw-cli`, launch `roc-recv`, load/unload ROC modules, or modify the user's PipeWire setup. Use the existing receiver only when the user manually chooses to perform an end-to-end audio test.

## 7. App design constraints

Before implementing, decide whether adapting upstream code is more reliable and smaller than a fresh app. In either case:

- Kotlin for app-facing Android lifecycle and UI; avoid Rust, C/C++, NDK, Compose or other frameworks unless a concrete benefit is demonstrated.
- Minimal UI: destination address and ROC ports/profile, start/stop control, permission status, connection/stream status and clear errors.
- Microphone capture in a foreground service with correct microphone foreground-service declarations/permissions and runtime permission flow for supported Android versions.
- Start microphone capture from a visible/eligible state, respecting modern Android restrictions; do not promise that a language choice can bypass OS background rules.
- Design for screen lock, graceful stop, errors, reconnection, audio format, packetization, latency and Wi-Fi changes. Do not add wake locks or Wi-Fi locks by default without showing their necessity and expected impact on battery life.
- Do not assume ROC API names, audio format defaults, port values or FEC profile names. Derive them from upstream examples/docs and the user's existing receiver setup.
- Add tests for non-hardware logic, verify debug APK builds, and document that end-to-end microphone/ROC behavior requires a real phone and the user's existing receiver.

## 8. Scripts to create after approval

- `.dev/scripts/setup-dev.sh`: idempotent bootstrap; verifies existing pieces and skips already-valid components; validates downloads before extraction; resumes safely after interruption; never installs host packages or touches PipeWire.
- `.dev/scripts/env.sh`: resolves absolute project path and defines project-local environment variables.
- `.dev/scripts/build.sh`: runs `./gradlew assembleDebug` with the private JDK, SDK, SDK user home and Gradle home.
- `.dev/scripts/install-apk.sh`: checks `/usr/bin/adb`, confirms a device is connected, then installs the debug APK. It must not change ADB keys or use `sudo`.
- `.dev/scripts/clean-dev.sh`: clearly lists `.dev/` size/contents, asks the user to type `yes`, and removes only this repository's `.dev/` directory. It must refuse to run if the resolved target isn't exactly `<project-root>/.dev`, if the path is `/`, the project root is ambiguous, or the target is a symlink. It must not use `sudo` and must not delete source files, `.git/`, Gradle wrapper files, system tools, `$HOME/.android`, or any audio configuration.

The project `.gitignore` should include `.dev/`, `local.properties`, `.gradle/`, `.kotlin/`, `build/`, `app/build/`, IDE state and local signing material. Do not create or overwrite an existing `.gitignore` without inspecting and preserving its other entries.

`adb` may use existing Android user state such as `$HOME/.android/adbkey`; reuse it and do not regenerate, copy, or delete it. This is a pre-existing host concern and is explicitly outside cleanup scope.

## 9. Build and test workflow

After the user approves initialization and the source is implemented:

```bash
# From the project root
./.dev/scripts/setup-dev.sh
./.dev/scripts/build.sh
./.dev/scripts/install-apk.sh
```

The resulting debug APK should be under `app/build/outputs/apk/debug/`. The user manually opens the app, grants microphone permission, sets the receiver address/ports to match their already-running PipeWire ROC source, starts streaming and verifies audio through their current configuration. The agent must not invoke or modify that configuration.

## 10. Disk space and cleanup

Reserve roughly **2–3 GB of free disk space** for the first setup/build, including the private JDK, SDK tools/platform/build tools, Gradle bootstrap and cache, and Maven dependencies. Actual usage depends on downloaded artifacts and Gradle caches; measure it after setup rather than promising an exact fixed size. The temporary Gradle bootstrap archive/extraction should be removed after the wrapper distribution is verified and cached.

To remove the disposable toolchain and dependency caches, run `.dev/scripts/clean-dev.sh` and confirm the project-specific deletion. To remove absolutely everything associated with the project, including source and Git history, the user can subsequently delete the project directory manually. Neither procedure should remove or change anything outside the project directory. Existing `/usr/bin/adb`, any existing ADB authorization state, and the user's pre-existing PipeWire/ROC configuration remain untouched.

## 11. Official references to verify during implementation

- Android command-line tools and official SHA-256: https://developer.android.com/studio/index.html
- AGP 9.4.0 compatibility table: https://developer.android.com/build/releases/agp-9-4-0-release-notes
- AGP built-in Kotlin guide: https://developer.android.com/build/migrate-to-built-in-kotlin
- Temurin JDK 21 releases: https://adoptium.net/temurin/releases/?arch=x64&os=linux&package=jdk&version=21
- Gradle 9.7.0 archive and SHA sidecar: https://services.gradle.org/distributions/gradle-9.7.0-bin.zip and https://services.gradle.org/distributions/gradle-9.7.0-bin.zip.sha256
- Official Roc Droid repository: https://github.com/roc-streaming/roc-droid
- Official ROC Java bindings: https://github.com/roc-streaming/roc-java

## Approval gate

**Do not implement application files, download tools, execute installers, accept licences, or run builds until the user has explicitly approved this plan.** Once approved, make changes incrementally, show diffs and commands first, and stop before any unanticipated operation outside the repository.
