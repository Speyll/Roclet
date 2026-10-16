#!/bin/sh
source .dev/scripts/env.sh
unset ANDROID_SDK_HOME

export JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT
export ANDROID_USER_HOME GRADLE_USER_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

./gradlew :app:assembleDebug
