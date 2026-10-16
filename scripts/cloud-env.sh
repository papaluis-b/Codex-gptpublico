#!/usr/bin/env bash
# Source from a shell: source scripts/cloud-env.sh
SONDA_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${SONDA_TOOLS:-$SONDA_ROOT/.tooling}/jdk"
export ANDROID_HOME="${SONDA_TOOLS:-$SONDA_ROOT/.tooling}/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="${SONDA_TOOLS:-$SONDA_ROOT/.tooling}/android-user"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
SONDA_JAVA_USER_DIR="${SONDA_TOOLS:-$SONDA_ROOT/.tooling}/java-user"
export GRADLE_USER_HOME="${SONDA_GRADLE_HOME:-$SONDA_ROOT/.tooling/gradle-home}"
mkdir -p "$ANDROID_USER_HOME" "$SONDA_JAVA_USER_DIR/.android" "$GRADLE_USER_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/19.0/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$PATH"
# Some older Android metrics/debug-keystore helpers only honor the JVM home property.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Duser.home=$SONDA_JAVA_USER_DIR"
# JVM tools do not read HTTPS_PROXY themselves. Use the inherited managed proxy.
if [[ -n "${HTTPS_PROXY:-}" ]]; then
    SONDA_PROXY_FLAGS="$(python3 - <<'PY'
import os, urllib.parse
p = urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
if p.username or p.password:
    raise SystemExit('Use a managed proxy without embedding credentials in JVM arguments')
print(f'-Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port or 80} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port or 80} -Dhttp.nonProxyHosts=localhost|127.*|[::1]')
PY
)"
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} $SONDA_PROXY_FLAGS"
    # Use the machine's managed CA bundle, including its session proxy CA.
    if [[ -f /etc/ssl/certs/java/cacerts ]]; then
        export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
    fi
fi
