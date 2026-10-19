#!/usr/bin/env bash
#
# Build the Roclet native dependency (arm64-v8a) from pinned sources + the R2
# patches, and stage the artifacts the Android app consumes:
#
#   scripts/native/out/jniLibs/arm64-v8a/libroc.so
#   scripts/native/out/jniLibs/arm64-v8a/libroc_jni.so
#   scripts/native/out/jniLibs/arm64-v8a/libc++_shared.so
#   scripts/native/out/include/roc/*.h              (Roc public headers)
#
# Everything is generated under scripts/native/{src,out,logs}/ which are
# gitignored. The pinned upstream *source* archives and the R2 patches are
# committed under scripts/native/{vendor,patches}/ so a fresh checkout builds
# offline. No prebuilt binaries are committed or downloaded.
#
# Toolchain: discovered from the environment, or overridden via
# scripts/native/local.env (gitignored). See README.md.
#
# Usage: scripts/native/build-native.sh [all|sources|build|strip|jni|validate|clean]
#
set -euo pipefail

NATIVE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$NATIVE_DIR/../.." && pwd -P)"
# shellcheck source=pins.env
. "$NATIVE_DIR/pins.env"
# Optional per-workspace toolchain overrides (gitignored).
[ -f "$NATIVE_DIR/local.env" ] && . "$NATIVE_DIR/local.env"

WORK_DIR="$NATIVE_DIR/src"
OUT_DIR="$NATIVE_DIR/out"
LOG_DIR="$NATIVE_DIR/logs"
VENDOR_DIR="$NATIVE_DIR/vendor"
PATCH_DIR="$NATIVE_DIR/patches"

ROC_DIR="$WORK_DIR/roc-toolkit"
OPENFEC_DIR="$WORK_DIR/$OPENFEC_TOP"
ROC_JAVA_DIR="$WORK_DIR/roc-java"
ROC_DIST_DIR="$ROC_DIR/3rdparty/_distfiles"

NDK_VERSION="${NDK_VERSION:-21.1.6352462}"
CMAKE_BIN_DIR="${CMAKE_BIN_DIR:-}"
ROC_HOST="${ROC_HOST:-aarch64-linux-android29}"
ROC_BUILD="${ROC_BUILD:-x86_64-pc-linux-gnu}"
ROC_ABI="${ROC_ABI:-arm64-v8a}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-21}"
EXPECTED_ROC_EXPORTS="${EXPECTED_ROC_EXPORTS:-29}"
EXPECTED_JNI_IMPORTS="${EXPECTED_JNI_IMPORTS:-26}"

log()  { printf '==> %s\n' "$*" >&2; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
sha256_of() { sha256sum "$1" | awk '{print $1}'; }

check_sha256() {
    [ -f "$1" ] || die "missing required file: $1"
    local got; got="$(sha256_of "$1")"
    [ "$got" = "$2" ] || die "$3 sha256 mismatch: got $got want $2"
    log "$3 sha256 OK ($got)"
}

# ---------------------------------------------------------------------------
# toolchain discovery
# ---------------------------------------------------------------------------
resolve_toolchain() {
    if [ -z "${NDK_DIR:-}" ]; then
        for base in "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}" \
                    "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk"; do
            [ -n "$base" ] && [ -d "$base/ndk/$NDK_VERSION" ] && { NDK_DIR="$base/ndk/$NDK_VERSION"; break; }
            [ -n "$base" ] && [ -d "$base" ] && [ -f "$base/source.properties" ] && \
                [ "$(sed -n 's/^Pkg.Revision *= *//p' "$base/source.properties")" = "$NDK_VERSION" ] && \
                { NDK_DIR="$base"; break; }
        done
    fi
    [ -n "${NDK_DIR:-}" ] && [ -d "$NDK_DIR" ] || die "Android NDK $NDK_VERSION not found (set ANDROID_NDK_HOME or NDK_DIR)"
    NDK_BIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin"

    SCONS="${SCONS:-$(command -v scons || true)}"
    RAGEL="${RAGEL:-$(command -v ragel || true)}"
    MAKE="${MAKE:-$(command -v make || true)}"
    CMAKE="${CMAKE:-$(command -v cmake || true)}"
    if [ -z "${CMAKE:-}" ] && [ -n "${CMAKE_BIN_DIR:-}" ]; then CMAKE="$CMAKE_BIN_DIR/cmake"; fi

    for t in "$NDK_BIN/aarch64-linux-android29-clang" "$NDK_BIN/llvm-strip" \
             "$NDK_BIN/llvm-readelf" "$NDK_BIN/llvm-nm" "$SCONS" "$RAGEL" "$MAKE" "$CMAKE"; do
        [ -n "$t" ] && [ -x "$t" ] || die "required tool not found: ${t:-<unset>}"
    done
    log "NDK:   $NDK_DIR"
    log "scons: $SCONS ($("$SCONS" --version 2>/dev/null | sed -n '2p'))"
    log "ragel: $RAGEL ($("$RAGEL" --version 2>/dev/null))"
    log "cmake: $CMAKE ($("$CMAKE" --version 2>/dev/null | sed -n 1p))"
}

# ---------------------------------------------------------------------------
# sources: extract pinned archives, verify, apply patches
# ---------------------------------------------------------------------------
git_init_commit() {
    local dir="$1" msg="$2"
    git -C "$dir" init -q
    git -C "$dir" config user.email "native-build@local"
    git -C "$dir" config user.name "native-build"
    git -C "$dir" add -A
    git -C "$dir" commit -qm "$msg"
}

do_sources() {
    log "verifying pinned patch + archive hashes"
    check_sha256 "$PATCH_DIR/$PATCH_B_FILE"     "$PATCH_B_SHA"     "Patch B"
    check_sha256 "$PATCH_DIR/$PATCH_A_FILE"     "$PATCH_A_SHA"     "Patch A"
    check_sha256 "$PATCH_DIR/$PATCH_FIXUP_FILE" "$PATCH_FIXUP_SHA" "OpenFEC fixup"
    check_sha256 "$VENDOR_DIR/$ROC_TOOLKIT_ARCHIVE" "$ROC_TOOLKIT_ARCHIVE_SHA" "roc-toolkit archive"
    check_sha256 "$VENDOR_DIR/$OPENFEC_ARCHIVE"     "$OPENFEC_ARCHIVE_SHA"     "OpenFEC archive"
    check_sha256 "$VENDOR_DIR/$ROC_JAVA_ARCHIVE"    "$ROC_JAVA_ARCHIVE_SHA"    "roc-java archive"
    check_sha256 "$VENDOR_DIR/$LIBUV_ARCHIVE"       "$LIBUV_ARCHIVE_SHA"       "libuv archive"
    check_sha256 "$VENDOR_DIR/$SPEEXDSP_ARCHIVE"    "$SPEEXDSP_ARCHIVE_SHA"    "speexdsp archive"

    log "resetting native source tree"
    rm -rf "$WORK_DIR"; mkdir -p "$WORK_DIR"

    tar xzf "$VENDOR_DIR/$ROC_TOOLKIT_ARCHIVE" -C "$WORK_DIR"
    [ -d "$WORK_DIR/$ROC_TOOLKIT_TOP" ] || die "unexpected roc-toolkit archive layout"
    mv "$WORK_DIR/$ROC_TOOLKIT_TOP" "$ROC_DIR"
    git_init_commit "$ROC_DIR" "baseline: roc-toolkit $ROC_TOOLKIT_REV"
    local base_tree; base_tree="$(git -C "$ROC_DIR" write-tree)"
    log "roc-toolkit baseline content tree: $base_tree"
    [ "$base_tree" = "$ROC_TOOLKIT_TREE" ] || die \
        "roc-toolkit content tree mismatch: got $base_tree want $ROC_TOOLKIT_TREE"

    git -C "$ROC_DIR" apply --check "$PATCH_DIR/$PATCH_B_FILE"
    git -C "$ROC_DIR" apply "$PATCH_DIR/$PATCH_B_FILE"
    if grep -q 'FEC_LDPC_Staircase' "$ROC_DIR/src/internal_modules/roc_fec/codec_map.cpp"; then
        die "Patch B postcondition failed: FEC_LDPC_Staircase still present"
    fi

    tar xzf "$VENDOR_DIR/$OPENFEC_ARCHIVE" -C "$WORK_DIR"
    [ -d "$OPENFEC_DIR" ] || die "expected $OPENFEC_DIR after extract"
    git_init_commit "$OPENFEC_DIR" "baseline: openfec $OPENFEC_TAG"
    git -C "$OPENFEC_DIR" apply --check "$PATCH_DIR/$PATCH_A_FILE"
    git -C "$OPENFEC_DIR" apply "$PATCH_DIR/$PATCH_A_FILE"
    git -C "$OPENFEC_DIR" apply --check "$PATCH_DIR/$PATCH_FIXUP_FILE"
    git -C "$OPENFEC_DIR" apply "$PATCH_DIR/$PATCH_FIXUP_FILE"
    [ -d "$OPENFEC_DIR/src/lib_stable/ldpc_staircase" ] && die "Patch A postcondition failed" || true
    log "openfec patched content tree: $(git -C "$OPENFEC_DIR" add -A && git -C "$OPENFEC_DIR" write-tree)"

    tar xzf "$VENDOR_DIR/$ROC_JAVA_ARCHIVE" -C "$WORK_DIR"
    [ -d "$ROC_JAVA_DIR/src/main/java/org/rocstreaming/roctoolkit" ] || die "unexpected roc-java archive layout"
    log "sources ready"
}

# ---------------------------------------------------------------------------
# build: generate the patched openfec distfile, then scons build + install
# ---------------------------------------------------------------------------
seed_openfec_dist() {
    local out="$ROC_DIST_DIR/$OPENFEC_DIST_NAME"
    mkdir -p "$ROC_DIST_DIR"
    # Deterministic tar; exclude the throwaway .git created for tree hashing.
    ( cd "$WORK_DIR" && GZIP=-n tar --sort=name --mtime='@0' --owner=0 --group=0 \
        --numeric-owner --exclude="$OPENFEC_TOP/.git" -czf "$out" "$OPENFEC_TOP" )
    tar tzf "$out" | sed -n '1p' | grep -q "^$OPENFEC_TOP/" || die "bad openfec dist tarball top dir"
    tar tzf "$out" | grep -q "^$OPENFEC_TOP/\.git/" && die "openfec dist tarball contains .git" || true
    cp -f "$VENDOR_DIR/$LIBUV_ARCHIVE"    "$ROC_DIST_DIR/$LIBUV_ARCHIVE"
    cp -f "$VENDOR_DIR/$SPEEXDSP_ARCHIVE" "$ROC_DIST_DIR/$SPEEXDSP_ARCHIVE"
    log "openfec dist tarball: $(sha256_of "$out")"
}

do_build() {
    [ -d "$ROC_DIR" ] || die "run 'sources' first"
    mkdir -p "$OUT_DIR" "$LOG_DIR"
    resolve_toolchain
    seed_openfec_dist

    export PATH="$NDK_BIN:$(dirname "$CMAKE"):$(dirname "$SCONS"):$(dirname "$RAGEL"):$(dirname "$MAKE"):${CMAKE_BIN_DIR:+$CMAKE_BIN_DIR:}$PATH"

    local common=(
        -Q
        --build="$ROC_BUILD"
        --host="$ROC_HOST"
        --compiler=clang
        --incdir="$OUT_DIR/include"
        --libdir="$OUT_DIR/lib"
        --mandir="$OUT_DIR/man"
        --disable-soversion
        --disable-tools
        --disable-pulseaudio
        --disable-sox
        --disable-openssl
        --build-3rdparty=libuv,openfec,speexdsp
    )

    # Roc Toolkit's 'install' alias has no dependency on the library, so a build
    # step must run first; then 'install' copies the built artifacts.
    log "building libroc.so ($ROC_HOST); log: $LOG_DIR/scons-build.log"
    ( cd "$ROC_DIR" && "$SCONS" "${common[@]}" ) >"$LOG_DIR/scons-build.log" 2>&1 || {
        tail -40 "$LOG_DIR/scons-build.log" >&2; die "scons build failed"; }
    log "installing libroc.so; log: $LOG_DIR/scons-install.log"
    ( cd "$ROC_DIR" && "$SCONS" "${common[@]}" install ) >"$LOG_DIR/scons-install.log" 2>&1 || {
        tail -40 "$LOG_DIR/scons-install.log" >&2; die "scons install failed"; }

    [ -f "$OUT_DIR/lib/libroc.so" ] || die "build produced no libroc.so"
    grep -q 'github.com/roc-streaming/openfec/archive' "$LOG_DIR/scons-build.log" \
        && die "openfec was downloaded instead of using the vendored tarball" || true
    log "libroc.so (unstripped): $(sha256_of "$OUT_DIR/lib/libroc.so")"
}

# ---------------------------------------------------------------------------
# strip: reconcile from first principles, select release strip mode
# ---------------------------------------------------------------------------
do_strip() {
    resolve_toolchain
    local src="$OUT_DIR/lib/libroc.so"
    [ -f "$src" ] || die "run 'build' first"
    mkdir -p "$OUT_DIR/strip-trials"
    local trials="$OUT_DIR/strip-trials"
    local modes=(none --strip-all --strip-unneeded --strip-debug) m name
    for m in "${modes[@]}"; do
        if [ "$m" = none ]; then name=none; else name="${m#--strip-}"; fi
        local dst="$trials/libroc-$name.so"
        cp -f "$src" "$dst"
        [ "$m" = none ] || "$NDK_BIN/llvm-strip" "$m" "$dst"
        printf '  %-16s %s  symtab=%s\n' "$m" "$(sha256_of "$dst")" \
            "$(if "$NDK_BIN/llvm-readelf" -S "$dst" | grep -q '\.symtab'; then echo yes; else echo no; fi)" >&2
    done
    local selected="" cand
    for cand in all unneeded; do
        local f="$trials/libroc-$cand.so" ex sym son need
        ex="$("$NDK_BIN/llvm-nm" -D --defined-only "$f" | awk '{print $3}' | grep -c '^roc_')"
        sym="$(if "$NDK_BIN/llvm-readelf" -S "$f" | grep -q '\.symtab'; then echo yes; else echo no; fi)"
        son="$("$NDK_BIN/llvm-readelf" -d "$f" | grep -c 'SONAME.*libroc.so')"
        need="$("$NDK_BIN/llvm-readelf" -d "$f" | grep -c 'NEEDED')"
        if [ "$ex" -eq "$EXPECTED_ROC_EXPORTS" ] && [ "$sym" = no ] && [ "$son" -ge 1 ] && [ "$need" -ge 1 ]; then
            selected="$cand"; break
        fi
    done
    [ -n "$selected" ] || die "no strip mode preserved acceptance invariants"
    cp -f "$trials/libroc-$selected.so" "$OUT_DIR/libroc.so"
    log "selected strip mode: --strip-$selected -> $OUT_DIR/libroc.so ($(sha256_of "$OUT_DIR/libroc.so"))"
}

# ---------------------------------------------------------------------------
# jni: build libroc_jni.so from the pinned roc-java tree + staged libroc
# ---------------------------------------------------------------------------
do_jni() {
    resolve_toolchain
    [ -f "$OUT_DIR/libroc.so" ] || die "run 'strip' first"
    [ -d "$ROC_JAVA_DIR/roc_jni" ] || die "run 'sources' first"

    local stage="$WORK_DIR/roc-stage" jni_build="$WORK_DIR/jni-$ROC_ABI"
    rm -rf "$stage" "$jni_build"; mkdir -p "$stage/include/$ROC_ABI" "$stage/lib/$ROC_ABI" "$jni_build"
    cp -a "$OUT_DIR/include/roc" "$stage/include/$ROC_ABI/"
    cp -f "$OUT_DIR/libroc.so" "$stage/lib/$ROC_ABI/libroc.so"
    export PATH="$NDK_BIN:$(dirname "$CMAKE"):$PATH"
    ( cd "$jni_build" && "$CMAKE" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ROC_ABI" -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared \
        -DROC_BASE_DIR="$stage" "$ROC_JAVA_DIR/roc_jni" ) >"$LOG_DIR/cmake-jni.log" 2>&1 \
        || { tail -40 "$LOG_DIR/cmake-jni.log" >&2; die "roc_jni cmake configure failed"; }
    ( cd "$jni_build" && "$CMAKE" --build . ) >"$LOG_DIR/cmake-jni-build.log" 2>&1 \
        || { tail -40 "$LOG_DIR/cmake-jni-build.log" >&2; die "roc_jni build failed"; }

    local out; out="$(find "$jni_build" -name 'libroc_jni.so' | head -1)"
    [ -n "$out" ] || die "no libroc_jni.so produced"
    "$NDK_BIN/llvm-strip" --strip-all "$out"
    cp -f "$out" "$OUT_DIR/libroc_jni.so"
    log "libroc_jni.so: $(sha256_of "$OUT_DIR/libroc_jni.so")"
}

# ---------------------------------------------------------------------------
# stage: assemble the jniLibs the app consumes
# ---------------------------------------------------------------------------
do_stage() {
    resolve_toolchain
    [ -f "$OUT_DIR/libroc.so" ] || die "run 'strip' first"
    [ -f "$OUT_DIR/libroc_jni.so" ] || die "run 'jni' first"
    mkdir -p "$OUT_DIR/jniLibs/$ROC_ABI"
    cp -f "$OUT_DIR/libroc.so" "$OUT_DIR/libroc_jni.so" "$OUT_DIR/jniLibs/$ROC_ABI/"
    local libcxx="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
    [ -f "$libcxx" ] || die "libc++_shared.so not found in NDK"
    cp -f "$libcxx" "$OUT_DIR/jniLibs/$ROC_ABI/libc++_shared.so"
    "$NDK_BIN/llvm-strip" --strip-all "$OUT_DIR/jniLibs/$ROC_ABI/libc++_shared.so"
    log "staged jniLibs/$ROC_ABI:"
    ls -la "$OUT_DIR/jniLibs/$ROC_ABI" >&2
}

# ---------------------------------------------------------------------------
# validate: acceptance checks against the staged artifacts
# ---------------------------------------------------------------------------
do_validate() {
    resolve_toolchain
    local lib="$OUT_DIR/jniLibs/$ROC_ABI/libroc.so"
    local jni="$OUT_DIR/jniLibs/$ROC_ABI/libroc_jni.so"
    [ -f "$lib" ] || die "run 'stage' first"
    local nm="$NDK_BIN/llvm-nm" rf="$NDK_BIN/llvm-readelf" rc=0
    p() { printf '%s\n' "$*" >&2; }

    local n; n="$("$nm" -D --defined-only "$lib" | awk '{print $3}' | grep -c '^roc_')"
    [ "$n" -eq "$EXPECTED_ROC_EXPORTS" ] && p "PASS exports: $n" || { p "FAIL exports: $n"; rc=1; }

    if [ -f "$jni" ]; then
        local imports; imports="$("$nm" -D --undefined-only "$jni" | awk '{print $2}' | grep '^roc_' | sort -u)"
        local ni; ni="$(printf '%s\n' "$imports" | grep -c '^roc_')"
        [ "$ni" -eq "$EXPECTED_JNI_IMPORTS" ] && p "PASS jni imports: $ni" || { p "FAIL jni imports: $ni"; rc=1; }
        local missing; missing="$(comm -23 <(printf '%s\n' "$imports") \
            <("$nm" -D --defined-only "$lib" | awk '{print $3}' | grep '^roc_' | sort))"
        [ -z "$missing" ] && p "PASS jni imports resolved" || { p "FAIL unresolved jni imports"; rc=1; }
    fi

    local soname needed
    soname="$("$rf" -d "$lib" | sed -n 's/.*SONAME.*\[\(.*\)\].*/\1/p' | head -1)"
    needed="$("$rf" -d "$lib" | sed -n 's/.*NEEDED.*\[\(.*\)\].*/\1/p' | sort | paste -sd, -)"
    [ "$soname" = "libroc.so" ] && p "PASS SONAME=$soname" || { p "FAIL SONAME=$soname"; rc=1; }
    local exp="libandroid.so,libc++_shared.so,libc.so,libdl.so,liblog.so,libm.so"
    [ "$needed" = "$exp" ] && p "PASS NEEDED=$needed" || { p "FAIL NEEDED=$needed"; rc=1; }

    local machine; machine="$("$rf" -h "$lib" | awk -F: '/Machine/{gsub(/^ +/,"",$2);print $2}')"
    [ "$machine" = "AArch64" ] && p "PASS ABI=AArch64" || { p "FAIL ABI=$machine"; rc=1; }
    [ "$("$nm" -D "$lib" | grep -ci 'ldpc\|staircase')" -eq 0 ] && p "PASS no LDPC symbols" || { p "FAIL LDPC symbols"; rc=1; }
    "$rf" -S "$lib" | grep -q '\.symtab' && { p "FAIL libroc not stripped"; rc=1; } || p "PASS libroc stripped"

    [ "$rc" -eq 0 ] && p "RESULT: PASS" || p "RESULT: FAIL"
    return "$rc"
}

do_clean() {
    log "removing generated native build tree"
    rm -rf "$WORK_DIR" "$OUT_DIR" "$LOG_DIR"
}

cmd="${1:-all}"
case "$cmd" in
    sources)  do_sources ;;
    build)    do_build ;;
    strip)    do_strip ;;
    jni)      do_jni ;;
    stage)    do_stage ;;
    validate) do_validate ;;
    clean)    do_clean ;;
    all)
        do_sources
        do_build
        do_strip
        do_jni
        do_stage
        do_validate
        log "native build complete; staged under $OUT_DIR/jniLibs/$ROC_ABI"
        ;;
    *)
        sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 1 ;;
esac
