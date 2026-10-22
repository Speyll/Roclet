# Native dependency build

## Purpose

`scripts/native/build-native.sh` builds the arm64-v8a native dependency that the
`:roc-android` Gradle module packages for the app, entirely offline from pinned
source archives and patches. It produces:

- `libroc.so` — Roc Toolkit built with the patched OpenFEC, libuv, and SpeexDSP.
- `libroc_jni.so` — the roc-java JNI bridge, built against the staged `libroc.so`.
- `libc++_shared.so` — the NDK C++ runtime.

The `:roc-android` module compiles the roc-java Java API from the extracted
roc-java tree and packages these libraries. No prebuilt binaries are committed or
downloaded.

## Pinned dependencies and patches

All revisions and archive/patch hashes live in `pins.env`; the build verifies
every input against them before use.

Upstream source archives committed under `vendor/`:

| Archive | Source | Revision | License |
|---|---|---|---|
| `roc-toolkit_13da01d5.tar.gz` | roc-toolkit | `13da01d5a82b5c61e34aaa11865134d7371810f4` | MPL-2.0 |
| `openfec-pristine_v1.4.2.7.tar.gz` | OpenFEC | `v1.4.2.7` (`9e83faa4e3b02fb9f8565ad809fa555b68d3b770`) | CeCILL-C |
| `roc-java_v0.2.1.tar.gz` | roc-java | `v0.2.1` (`8df737b7667415ddb34f2f725d5fcace5ff62b5c`) | MIT |
| `libuv-v1.35.0.tar.gz` | libuv | v1.35.0 | MIT |
| `speexdsp-1.2.0.tar.gz` | SpeexDSP | 1.2.0 | BSD-3-Clause |

Patches under `patches/`, applied to the extracted sources:

| Patch | Target | Purpose |
|---|---|---|
| `roc-toolkit-r2-remove-ldpc.patch` (Patch B) | roc-toolkit | Removes the LDPC-Staircase FEC codec entry. |
| `openfec-r2-remove-ldpc-ccbysa.patch` (Patch A) | OpenFEC | Removes the LDPC-Staircase sources (the only CC-BY-SA-licensed files). |
| `openfec-r2-fixup-01-of_cb-and-exclude-ldpc-apps.patch` | OpenFEC | Adds the missing `#include "of_cb.h"` and excludes the LDPC example/test targets from the build. |
| `openfec-r2-modification-notices.patch` | OpenFEC | Adds CeCILL-C 5.2 modification notices to the five surviving modified files. |

The recorded date of Roclet's OpenFEC project modifications is 2026-10-19, as
recorded by commit `891ed9f7cb5a034fde5f6d5e59fe453b20b59490`, which introduced
the two original patches. This is the project's recorded modification date; it
does not independently establish when each underlying edit was first made. The
modifications were made by Speyll <speyll@protonmail.com>. Patch A removes 38
files; the five surviving modified files receive in-file notices from the
notices patch. The pinned archive plus the patches reconstruct the complete
modified OpenFEC source.

The extracted roc-toolkit tree is checked against the `ROC_TOOLKIT_TREE` content
hash (`9ad74e48a3437a70c7d1782661e8691f2b913be8`) before Patch B is applied.

## Prerequisites

- Android NDK `21.1.6352462`.
- `scons` (4.x), `ragel` 6.10, GNU `make`, and `cmake` + `ninja`.
- A POSIX shell; the build runs on an x86_64 host targeting
  `aarch64-linux-android29`.

The toolchain is discovered from the environment:

- NDK via `NDK_DIR`, or `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT` / `ANDROID_HOME` /
  `ANDROID_SDK_ROOT` / `~/Android/Sdk`.
- `scons`, `ragel`, `make`, `cmake` from `PATH`, or overridden with the `SCONS`,
  `RAGEL`, `MAKE`, `CMAKE` environment variables.

Host-specific overrides can be placed in a gitignored `scripts/native/local.env`,
which the script sources when present. In CI/F-Droid the toolchain comes from the
buildserver environment (see `fdroid/com.roclet.mic.yml`).

## Build commands

```sh
scripts/native/build-native.sh all        # sources -> build -> strip -> jni -> stage -> validate
scripts/native/build-native.sh clean      # remove src/, out/, logs/
```

Individual steps: `sources`, `build`, `strip`, `jni`, `stage`, `validate`.

The build is offline. It verifies every archive and patch hash, cross-checks the
roc-toolkit content tree, and fails on any mismatch.

### Validation (`validate`)

`libroc.so` must expose exactly 29 `roc_*` symbols, have `SONAME=libroc.so`, the
`NEEDED` set `libandroid.so,libc++_shared.so,libc.so,libdl.so,liblog.so,libm.so`,
be AArch64, contain no LDPC/staircase symbols, and be stripped. `libroc_jni.so`
must import exactly 26 `roc_*` symbols, all resolved by `libroc.so`.

## Output locations and generated files

Generated under `scripts/native/` (all gitignored):

```
src/                      extracted + patched sources
  roc-toolkit/
  openfec-1.4.2.7/
  roc-java/
out/
  lib/libroc.so           installed (unstripped) Roc Toolkit library
  include/roc/*.h         Roc public headers
  libroc.so               stripped library selected by 'strip'
  libroc_jni.so           roc-java JNI bridge
  strip-trials/           strip-mode candidates compared by 'strip'
  jniLibs/arm64-v8a/      staged libroc.so, libroc_jni.so, libc++_shared.so
logs/                     scons and cmake logs
```

`:roc-android` consumes `out/jniLibs/arm64-v8a/` and
`src/roc-java/src/main/java`.

## Reproducibility notes and known limitations

- `build` generates the OpenFEC dist tarball expected by roc-toolkit's
  `build-3rdparty` step deterministically from the patched tree (excluding the
  throwaway `.git` used for tree hashing), and copies the vendored libuv/SpeexDSP
  archives into roc-toolkit's `3rdparty/_distfiles/`. The build fails if OpenFEC
  would be downloaded instead of using the vendored tarball.
- `strip` selects the first of `--strip-all` / `--strip-unneeded` that preserves
  the `validate` invariants.
- `jni` configures the roc-java JNI bridge with `ANDROID_PLATFORM=android-26` and
  `ANDROID_STL=c++_shared`.
- Roc Toolkit's `install` target has no dependency on the library, so `build`
  runs a compile step and then a separate `install` step.
- `libroc.so` embeds absolute source paths in `.rodata`, so the binary bytes
  depend on the checkout path. This does not affect behaviour.

## Licenses and attribution

The third-party license and notice texts are bundled into the app as the
`@raw/third_party_notices` resource (kept from resource shrinking by
`app/src/main/res/raw/keep.xml`): Roc Toolkit (MPL-2.0), OpenFEC (CeCILL-C),
roc-java and libuv (MIT), SpeexDSP (BSD-3-Clause). Roc Toolkit's MPL-2.0
corresponding source is the upstream pin `13da01d5…` plus Patch B, committed
here. OpenFEC's modification notices are applied by the notices patch; the
full CeCILL-C text and original attribution remain in the bundled notices.
The OpenFEC CeCILL-C compatibility question remains unresolved.
