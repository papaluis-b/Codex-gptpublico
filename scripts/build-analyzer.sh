#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ -x .tooling/jdk/bin/java && -d .tooling/android-sdk ]]; then
    source scripts/cloud-env.sh
fi
./gradlew :measurement-core:test :dsp:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :generator-kit:testDebugUnitTest :generator:testDebugUnitTest --no-daemon --console=plain
