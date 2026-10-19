# Third-party notices

This document records the third-party components actually distributed in a
Roclet release APK, the license and notice obligations that apply to them, the
exact provenance of each license text, and the questions that remain open.

> **Status: NOT legal clearance.** This is an engineering inventory prepared for
> qualified review. It does not conclude that the combined distribution is
> license-compatible, and it does not change Roclet's own license (Apache-2.0).
> OpenFEC (CeCILL-C) compatibility is **unresolved**.

The machine-readable bundle that ships inside the app is
`app/src/main/res/raw/third_party_notices.txt` (kept from resource shrinking by
`app/src/main/res/raw/keep.xml`). It contains the full license texts reproduced
below.

## Components distributed in the APK

Confirmed by unpacking the built APK:

| Component | Where it ships | License | Basis |
|---|---|---|---|
| Roclet app code | `classes*.dex`, `res/` | Apache-2.0 | repo `LICENSE` |
| Roc Toolkit | `lib/arm64-v8a/libroc.so` | MPL-2.0 | `roc-toolkit/LICENSE` |
| OpenFEC | statically linked into `libroc.so` | **CeCILL-C** + BSD-2-Clause + public domain | per-file headers; `LICENCE_CeCILL-C_V1-en.txt` |
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
the unpacked APK and adds the exact per-file OpenFEC breakdown that is compiled
into `libroc.so`.

## OpenFEC file-level breakdown (compiled objects)

`libopenfec.a` contains exactly these objects (verified with `llvm-ar t`):

| Object | Source license |
|---|---|
| `of_mem.c.o`, `of_statistics.c.o` | CeCILL-C |
| `of_openfec_api.c.o` | CeCILL-C (embeds the INRIA / R. Neal / L. Rizzo credit string) |
| `of_rand.c.o` | public domain (Robin Whittle, `rand31pmc`) |
| `of_reed-solomon_gf_2_8.c.o` | BSD-style (Luigi Rizzo et al.) |
| `of_reed-solomon_gf_2_8_api.c.o` | CeCILL-C |
| `algebra_2_4.c.o`, `algebra_2_8.c.o`, `of_galois_field_code.c.o`, `of_reed-solomon_gf_2_m_api.c.o` | BSD-style / CeCILL-C as noted |

No LDPC-Staircase objects are present (R2 Patch A removed them), and no compiled
file references CC-BY-SA or non-CeCILL-C copyleft. The OpenFEC source archive
also carries `Licence_CeCILL_V2-en.txt`; no compiled source references it, so it
is not reproduced pending review.

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
list.

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

## Unresolved questions (require qualified legal review)

1. **CeCILL-C ↔ MPL-2.0/Apache-2.0 combination.** No compatibility is asserted.
2. **CeCILL-C notice mechanism.** Whether an in-APK text bundle is sufficient,
   or whether an additional prominent notice / source offer is required.
3. **`Licence_CeCILL_V2-en.txt`** present upstream with no compiled-file
   reference; applicability unknown.
4. **MPL-2.0 source offer wording.** The bundle points at the repo patch set;
   final wording depends on the public repository URL and release tag.
5. **libc++ NOTICE scope.** The NDK `llvm-libc++/NOTICE` covers the LLVM Project
   and bundled third-party code; the bundle reproduces it in full.

## Files added by this pass

- `app/src/main/res/raw/third_party_notices.txt` — the bundled notices.
- `app/src/main/res/raw/keep.xml` — keeps the bundle through resource shrinking.
- `THIRD-PARTY-NOTICES.md` — this document.
