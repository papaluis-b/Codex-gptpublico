#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ -x .tooling/jdk/bin/java && -d .tooling/android-sdk ]]; then
    source scripts/cloud-env.sh
fi
./gradlew :dsp:test :generator-kit:testDebugUnitTest :generator-kit:lintDebug :generator-kit:lintRelease :generator-kit:assembleRelease :dsp:publishReleasePublicationToKitRepository :generator-kit:publishReleasePublicationToKitRepository :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :generator:testDebugUnitTest :generator:lintDebug :generator:assembleDebug --no-daemon --console=plain
python3 scripts/package-generator-kit.py
