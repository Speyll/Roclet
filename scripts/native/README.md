# Native dependency build (`scripts/native/`)

Builds the arm64-v8a Roc Toolkit native dependency and the roc-java JNI bridge
that the app consumes through the local `:roc-android` Gradle module, entirely
from pinned sources and patches.

This replaces the previous flow that depended on a prebuilt AAR under `.dev/`.

## What it produces (all gitignored)

```
scripts/native/
  src/    extracted + patched sources (roc-toolkit, openfec, roc-java)
  out/
    jniLibs/arm64-v8a/libroc.so
    jniLibs/arm64-v8a/libroc_jni.so
    jniLibs/arm64-v8a/libc++_shared.so
    include/roc/*.h
  logs/   scons / cmake logs
```

The `:roc-android` module compiles the roc-java Java API from
`scripts/native/src/roc-java/src/main/java` and packages the libraries from
`scripts/native/out/jniLibs`.

## Committed inputs (pinned, hash-verified)

| path | what |
|---|---|
| `pins.env` | every revision/hash pin |
| `vendor/` | upstream **source** archives (roc-toolkit, OpenFEC pristine, roc-java, libuv, speexdsp) |
| `patches/` | the R2 patches (Patch B; Patch A + fixup) |

No prebuilt binaries are committed or downloaded: everything is compiled from
the pinned source archives above.

## Usage

```
scripts/native/build-native.sh all        # sources -> build -> strip -> jni -> stage -> validate
scripts/native/build-native.sh clean      # remove src/, out/, logs/
```

Individual steps: `sources`, `build`, `strip`, `jni`, `stage`, `validate`.

The build is offline. It verifies every archive/patch hash, cross-checks the
Roc Toolkit content tree (`9ad74e48…`), and fails on any mismatch.

## Toolchain

Discovered from the environment, or overridden in a **gitignored**
`scripts/native/local.env` (see `local.env` in this workspace for an example
pointing at the project-local `.dev/` toolchain):

- Android NDK `21.1.6352462` (`ANDROID_NDK_HOME` / `NDK_DIR`, or under `ANDROID_HOME/ndk`)
- `scons` (4.x), `ragel` 6.10, GNU `make`, `cmake` + `ninja`

On F-Droid these come from the buildserver environment; see
`fdroid/com.roclet.mic.yml`.

## Acceptance checks (`validate`)

`libroc.so` must expose exactly 29 `roc_*` symbols, `SONAME=libroc.so`, the
expected `NEEDED` set, be AArch64, contain no LDPC/staircase symbols, and be
stripped. `libroc_jni.so` must import exactly 26 `roc_*` symbols, all resolved by
`libroc.so`.

## Reproducibility notes

- The OpenFEC dist tarball is generated deterministically from the patched tree
  (its throwaway `.git`, created for tree hashing, is excluded).
- `libroc.so` embeds absolute source paths in `.rodata`; the final binary is
  therefore sensitive to the checkout path. This does not affect behaviour.
- Roc Toolkit's `install` target has no dependency on the library, so `build`
  runs a compile step and then a separate install step.
