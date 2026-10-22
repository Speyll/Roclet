# Third-party notices

This document records the third-party components actually distributed in a
Roclet release APK, source-archive-only files that are not part of the APK, the
license and notice obligations that apply to them, the exact provenance of each
license text, and the questions that remain open.

> **Status: NOT legal clearance.** This is an engineering inventory prepared for
> qualified review. It does not conclude that the combined distribution is
> license-compatible, and it does not change Roclet's own license (Apache-2.0).
> OpenFEC (CeCILL-C) compatibility is **unresolved**.

The machine-readable bundle that ships inside the app is
`app/src/main/res/raw/third_party_notices.txt` (kept from resource shrinking by
`app/src/main/res/raw/keep.xml`). It contains the full license texts reproduced
below for APK components. Source-archive-only files and the missing GPL text
are identified separately in this document.

## Components distributed in the APK

Confirmed by unpacking the built APK:

| Component | Where it ships | License | Basis |
|---|---|---|---|
| Roclet app code | `classes*.dex`, `res/` | Apache-2.0 | repo `LICENSE` |
| Roc Toolkit | `lib/arm64-v8a/libroc.so` | MPL-2.0 | `roc-toolkit/LICENSE` |
| OpenFEC | statically linked into `libroc.so` | **CeCILL-C** + BSD-2-Clause in `libroc.so`; archive-only objects noted below | per-file headers; `LICENCE_CeCILL-C_V1-en.txt` |
| roc-java Java API | `classes*.dex` (`org.rocstreaming.roctoolkit.*`) | MIT | `roc-java/LICENSE` |
| roc-java JNI bridge | `lib/arm64-v8a/libroc_jni.so` | MIT | `roc-java/LICENSE` |
| libuv | statically linked into `libroc.so` | MIT | `libuv/LICENSE` |
| SpeexDSP | statically linked into `libroc.so` | BSD-3-Clause | `speexdsp/COPYING` |
| libc++ | `lib/arm64-v8a/libc++_shared.so` | Apache-2.0 WITH LLVM-exception | NDK `llvm-libc++/NOTICE` |
| AndroidX `core-ktx`, `appcompat` | dex/resources | Apache-2.0 | Maven POM |
| Kotlin stdlib/runtime | dex | Apache-2.0 | Maven POM |

Build-time-only components (not distributed): Hedley (`3rdparty/hedley`, CC0-1.0,
header-only macros).

The prior Stage 2C inventory listed the same set; this pass confirms it against
the unpacked APK and distinguishes the objects compiled into `libopenfec.a`
from the subset linked into the final `libroc.so`.

## OpenFEC object breakdown (archive vs final link)

`libopenfec.a` contains exactly these 10 objects (verified with `llvm-ar t`).
The final column is based on file symbols in the unstripped `libroc.so`:

| Object | Source license | In final `libroc.so`? |
|---|---|---|
| `of_mem.c.o` | CeCILL-C | Yes |
| `of_statistics.c.o` | CeCILL-C | **No — archive only; dead-stripped** |
| `of_openfec_api.c.o` | CeCILL-C; embeds the upstream version and copyright strings | Yes |
| `of_rand.c.o` | Public domain (Robin Whittle, `rand31pmc`) | **No — archive only; dead-stripped** |
| `of_reed-solomon_gf_2_8.c.o` | BSD-2-Clause-style (Luigi Rizzo et al.) | Yes |
| `of_reed-solomon_gf_2_8_api.c.o` | CeCILL-C | Yes |
| `algebra_2_4.c.o` | BSD-2-Clause-style | Yes |
| `algebra_2_8.c.o` | BSD-2-Clause-style | Yes |
| `of_galois_field_code.c.o` | CeCILL-C | Yes |
| `of_reed-solomon_gf_2_m_api.c.o` | CeCILL-C | Yes |

The final `libroc.so` therefore contains 8 OpenFEC objects: five CeCILL-C
objects and three BSD-2-Clause-style objects. The public-domain `of_rand.c.o`
and the CeCILL-C `of_statistics.c.o` are not part of the final library.

The exact upstream `of_version_string` and `of_copyrights_string` from
`src/lib_common/of_openfec_api.c` are reproduced verbatim in the bundled
notices section 3d. The table above is not a substitute for that text.

No LDPC-Staircase objects are compiled or linked (R2 Patch A removed them). No
compiled OpenFEC object references CC-BY-SA or non-CeCILL-C copyleft. This is a
statement about the OpenFEC objects only; the final `libroc.so` also contains
MPL-2.0 Roc Toolkit code, which is outside that OpenFEC statement. The OpenFEC
source archive also carries `Licence_CeCILL_V2-en.txt`; no compiled OpenFEC
source references it, so it is not reproduced pending review.

### OpenFEC source archive only: CC-BY-SA files

The pinned pristine archive still contains
`src/lib_common/linear_binary_codes_utils/binary_matrix/of_hamming_weight.c`
and `.h` under CC-BY-SA 3.0. R2 Patch A deletes them from the patched build
tree, and they are absent from `libopenfec.a` and `libroc.so`.

The archive remains committed under `scripts/native/vendor/` and would be part
of a source distribution. The F-Droid recipe's `scanignore` entry for
`scripts/native/vendor` only hides the archive from the binary scanner; it does
not remove the archive from the source tree. The archive carries the CC-BY-SA
header and license URI but no separate full CC-BY-SA 3.0 text file. Whether the
source distribution requires additional notice text is a qualified-review
question; this document does not conclude either way.

### OpenFEC patch record

- `openfec-r2-remove-ldpc-ccbysa.patch` — first committed in Git commit
  `891ed9f7cb5a034fde5f6d5e59fe453b20b59490` by
  `Speyll <speyll@protonmail.com>` with Git author date
  `2026-10-19T05:02:05+01:00`.
- `openfec-r2-fixup-01-of_cb-and-exclude-ldpc-apps.patch` — first committed in
  Git commit `891ed9f7cb5a034fde5f6d5e59fe453b20b59490` by
  `Speyll <speyll@protonmail.com>` with Git author date
  `2026-10-19T05:02:05+01:00`.

This is the recorded Git history, not a claim that this is the date the changes
were originally authored. No author/date headers have been added to the patch
files; whether the CeCILL-C Article 5.2 notice must appear inside the modified
source is left for qualified review.

## Source-archive-only files (not included in the APK)

The pinned source archives contain the following files. They are not compiled
into the APK and are not linked into `libroc.so`. They are listed because the
archives are tracked and would be part of a source distribution. Nothing in
this section concludes that any source-distribution obligation is satisfied.

### GPL-2.0-or-later: OpenFEC `tools/descr_stats_v1.2/descr_stats.c`

Archive member: `openfec-1.4.2.7/tools/descr_stats_v1.2/descr_stats.c` in
`scripts/native/vendor/openfec-pristine_v1.4.2.7.tar.gz`. The file header says:

```text
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
```

The file is not among the 10 objects in `libopenfec.a`, is not linked into
`libroc.so`, and is not an APK component. The pinned archive contains no full
GNU GPL text, and no full GNU GPL text was found in the tracked repository
files or in either pinned archive. Whether a full GPL copy must be added to a
source distribution, and how the GPL-2.0-or-later file should be handled, is
unresolved and requires qualified review.

### GPL-2.0-or-later with libtool exception: SpeexDSP `ltmain.sh`

Archive member: `speexdsp-1.2.0/ltmain.sh` in
`scripts/native/vendor/speexdsp-1.2.0.tar.gz`. The header identifies
GPL-2.0-or-later and includes this exact exception:

```text
# As a special exception to the GNU General Public License,
# if you distribute this file as part of a program or library that
# is built using GNU Libtool, you may include this file under the
# same distribution terms that you use for the rest of that program.
```

`ltmain.sh` is an Autotools build script, not a compiled or linked component of
the APK. The SpeexDSP archive's `COPYING` is its BSD-3-Clause license, and the
archive contains no full GNU GPL text. Whether the exception applies to this
source distribution and whether a GPL copy is required are unresolved and
require qualified review.

## Provenance of the bundled texts

`app/src/main/res/raw/third_party_notices.txt` is assembled verbatim from the
**pinned, hash-verified** inputs committed under `scripts/native/`:

1. Roc Toolkit — `vendor/roc-toolkit_13da01d5.tar.gz` (`LICENSE`, `debian/copyright`)
2. roc-java — `vendor/roc-java_v0.2.1.tar.gz` (`LICENSE`)
3. OpenFEC — `vendor/openfec-pristine_v1.4.2.7.tar.gz` (`LICENCE_CeCILL-C_V1-en.txt`, per-file headers)
4. libuv — `vendor/libuv-v1.35.0.tar.gz` (`LICENSE`)
5. SpeexDSP — `vendor/speexdsp-1.2.0.tar.gz` (`COPYING`)
6. libc++ — Android NDK `21.1.6352462`, `sources/cxx-stl/llvm-libc++/NOTICE`

Each section of the bundle names its source, and the bundle header repeats this
list. The bundle is UTF-8; two legacy Latin-1 `à` characters in the upstream
CeCILL-C text were transcoded to UTF-8 for display. The OpenFEC embedded
strings are unchanged byte-for-byte.

Roclet source repository: `https://github.com/Speyll/Roclet`. The published
`0.1.0` release is tag `v0.1.0`, an annotated tag whose peeled commit is
`891ed9f7cb5a034fde5f6d5e59fe453b20b59490`. This public release reference does
not by itself establish that any particular APK was built by F-Droid. The
F-Droid recipe in this repository now pins the reviewed release commit
`cfe9e3a5ec6fadf130e28cdd5ed36d0d2f36dc33`; that metadata reference does not
itself establish that an F-Droid build or publication has occurred, nor that any
licensing obligation is satisfied.

## Obligations, and where they are met

- **MPL-2.0 (Roc Toolkit).** Requires license/notice text and making the Source
  Code Form of MPL-covered files available. The license text is bundled; the
  corresponding source (upstream pin `13da01d5…` + R2 Patch B) is committed
  under `scripts/native/` and should also be reachable from the public repo.
- **MIT (roc-java, libuv).** Copyright + permission notice reproduced in the
  bundle ("other materials provided with the distribution").
- **BSD-3 (SpeexDSP), BSD-2 (OpenFEC Rizzo).** Copyright + notice reproduced.
- **Apache-2.0 (app, AndroidX, Kotlin, and libc++ with LLVM exception).** App
  `LICENSE` covers the Apache-2.0 text; the LLVM exception text is bundled.
- **CeCILL-C (OpenFEC).** Full text bundled. Whether its copyleft/notice
  conditions are satisfied by a dynamically loaded `libroc.so` built from a mix
  of CeCILL-C and MPL-2.0 sources is **unresolved** — see below.
- **GPL-2.0-or-later source-archive-only files.** `descr_stats.c` and
  `ltmain.sh` are not compiled into the APK. Whether a full GPL copy and
  additional source-distribution steps are required is **unresolved** — see
  the source-archive-only section above.

## Notices entry point in the app

The settings panel includes a **Third-party notices** button. It opens a
scrollable, selectable dialog that reads `R.raw.third_party_notices` directly;
`keep.xml` keeps that resource during resource shrinking. This makes the
bundled text reachable from the app UI without adding a network dependency.

## Unresolved questions (require qualified legal review)

1. **CeCILL-C ↔ MPL-2.0/Apache-2.0 combination.** No compatibility is asserted.
2. **CeCILL-C notice mechanism.** Whether the in-APK text bundle and the
   settings-panel notices dialog are sufficient, or whether an additional
   prominent notice / source offer is required.
3. **`Licence_CeCILL_V2-en.txt`** present upstream with no compiled-file
   reference; applicability unknown.
4. **MPL-2.0 and CeCILL-C source-offer wording.** The app bundle now names the
   public Roclet repository and the published `v0.1.0` commit; final wording for
   a future build depends on the exact release commit/tag recorded in the
   F-Droid metadata.
5. **libc++ NOTICE scope.** The NDK `llvm-libc++/NOTICE` covers the LLVM Project
   and bundled third-party code; the bundle reproduces it in full.
6. **CC-BY-SA 3.0 source archive.** The pristine archive contains
   `of_hamming_weight.c/.h`, which are not compiled. Whether the source
   distribution's header/URI is sufficient, or additional CC-BY-SA text is
   required, needs qualified review.
7. **Article 5.2 modification notice.** The Git author/date is recorded in the
   documentation; whether an in-source notice is required has not been decided.
8. **GPL-2.0-or-later `descr_stats.c` source distribution.** The file is in the
   pristine archive but not the APK. Whether a full GPL copy and additional
   source-distribution steps are required is unresolved.
9. **GPL-2.0-or-later `ltmain.sh` with libtool exception.** Whether the
   exception covers this source distribution and whether a full GPL copy is
   required is unresolved.

## Files added by this pass

- `app/src/main/res/raw/third_party_notices.txt` — the bundled notices.
- `app/src/main/res/raw/keep.xml` — keeps the bundle through resource shrinking.
- `app/src/main/res/layout/dialog_third_party_notices.xml` — scrollable dialog layout.
- `MainActivity.kt` / `activity_main.xml` — settings-panel entry point.
- `THIRD-PARTY-NOTICES.md` — this document.
