#!/usr/bin/env bash
# Source from a project helper script. Do not source this into your normal shell unless intended.
_DEV_ENV_SOURCE="${BASH_SOURCE[0]}"
_DEV_SCRIPTS_DIR="$(cd -- "$(dirname -- "$_DEV_ENV_SOURCE")" && pwd -P)"
PROJECT_ROOT="$(cd -- "$_DEV_SCRIPTS_DIR/../.." && pwd -P)"
DEV_DIR="$PROJECT_ROOT/.dev"
JAVA_HOME="$DEV_DIR/jdk"
ANDROID_HOME="$DEV_DIR/android-sdk"
ANDROID_SDK_ROOT="$ANDROID_HOME"
GRADLE_USER_HOME="$DEV_DIR/gradle-home"
ANDROID_USER_HOME="$DEV_DIR/android-user-home"
ADB="/usr/bin/adb"
export PROJECT_ROOT DEV_DIR JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT GRADLE_USER_HOME ANDROID_USER_HOME ADB
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
