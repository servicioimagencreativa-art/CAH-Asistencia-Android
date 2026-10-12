#!/bin/sh
set -eu
command -v gradle >/dev/null 2>&1 || { echo 'Necesitas Gradle 8.9 y Android SDK 35, o abre el proyecto en Android Studio.'; exit 1; }
gradle --no-daemon assembleDebug
echo 'APK: app/build/outputs/apk/debug/app-debug.apk'