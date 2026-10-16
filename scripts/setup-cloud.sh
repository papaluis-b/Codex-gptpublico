#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
python3 scripts/bootstrap.py
source scripts/cloud-env.sh
python3 - <<'PY'
import os
import subprocess
import urllib.parse

proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
command = ['sdkmanager', '--sdk_root=' + os.environ['ANDROID_HOME'], '--licenses']
if proxy.hostname:
    command += ['--proxy=http', '--proxy_host=' + proxy.hostname, '--proxy_port=' + str(proxy.port or 80)]
# Installing this development SDK includes accepting its published package licenses.
subprocess.run(command, input=('y\n' * 150).encode(), check=True)
PY
./gradlew :dsp:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
apksigner verify app/build/outputs/apk/debug/app-debug.apk
