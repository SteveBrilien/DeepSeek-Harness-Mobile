#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
java_home=${JAVA_HOME:-/home/orangepi/.local/opt/openjdk-17}
source_android_sdk=${DSHM_SOURCE_ANDROID_SDK:-${ANDROID_SDK_ROOT:-/home/orangepi/.local/share/android-sdk}}
gradle_version=8.11.1
gradle_sha256=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
build_tools_version=35.0.0
tools_dir="$project_root/.tools"
gradle_dir="$tools_dir/gradle-$gradle_version"
gradle_bin="$gradle_dir/bin/gradle"
gradle_zip="$tools_dir/gradle-$gradle_version-bin.zip"
trust_store="$tools_dir/java-cacerts"
kotlin_daemon_run_files="$tools_dir/kotlin-daemon"
overlay_sdk="$tools_dir/android-sdk-overlay"
arm64_bundle="$tools_dir/arm64-android"
arm64_bin="$arm64_bundle/bin"
official_aapt2="$tools_dir/official-aapt2-bin/aapt2"

export JAVA_HOME="$java_home"
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-$tools_dir/gradle-user-home}
export PATH="$java_home/bin:$PATH"
export KOTLIN_DAEMON_RUN_FILES_PATH="$kotlin_daemon_run_files"

for required in \
  "$java_home/bin/java" \
  "$source_android_sdk/platforms/android-35/android.jar" \
  "$source_android_sdk/build-tools/$build_tools_version/source.properties"; do
  [[ -e "$required" ]] || { echo "[build] missing required component: $required" >&2; exit 69; }
done

mkdir -p "$tools_dir" "$GRADLE_USER_HOME" "$kotlin_daemon_run_files"

/bin/bash "$project_root/scripts/bootstrap-android-arm64-tools.sh" >/dev/null
/bin/bash "$project_root/scripts/bootstrap-official-aapt2.sh" >/dev/null
[[ -x "$official_aapt2" ]] || { echo "[build] official aapt2 bootstrap did not produce $official_aapt2" >&2; exit 88; }

rm -rf "$overlay_sdk/build-tools/$build_tools_version"
mkdir -p "$overlay_sdk/build-tools"
for component in platforms platform-tools cmdline-tools licenses; do
  if [[ -e "$source_android_sdk/$component" ]]; then
    ln -sfn "$source_android_sdk/$component" "$overlay_sdk/$component"
  fi
done
cp -a "$source_android_sdk/build-tools/$build_tools_version" "$overlay_sdk/build-tools/$build_tools_version"
for tool in aapt aidl zipalign dexdump split-select; do
  rm -f "$overlay_sdk/build-tools/$build_tools_version/$tool"
  ln -s "$arm64_bin/$tool" "$overlay_sdk/build-tools/$build_tools_version/$tool"
done
rm -f "$overlay_sdk/build-tools/$build_tools_version/aapt2"
ln -s "$official_aapt2" "$overlay_sdk/build-tools/$build_tools_version/aapt2"

export ANDROID_HOME="$overlay_sdk"
export ANDROID_SDK_ROOT="$overlay_sdk"

if command -v trust >/dev/null 2>&1; then
  trust extract \
    --filter=ca-anchors \
    --purpose=server-auth \
    --format=java-cacerts \
    --overwrite \
    "$trust_store" >/dev/null || true
  if [[ -f "$trust_store" ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=$trust_store -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=JKS"
  fi
fi

if [[ ! -x "$gradle_bin" ]]; then
  tmp_zip="$gradle_zip.part"
  rm -f "$tmp_zip"
  echo "[build] bootstrapping Gradle $gradle_version with curl"
  curl --fail --location --retry 3 --retry-delay 2 \
    --output "$tmp_zip" \
    "https://services.gradle.org/distributions/gradle-$gradle_version-bin.zip"
  printf '%s  %s\n' "$gradle_sha256" "$tmp_zip" | sha256sum --check --status || {
    echo "[build] Gradle distribution SHA-256 verification failed" >&2
    rm -f "$tmp_zip"
    exit 70
  }
  mv "$tmp_zip" "$gradle_zip"
  rm -rf "$gradle_dir"
  unzip -q "$gradle_zip" -d "$tools_dir"
fi

printf 'sdk.dir=%s\n' "$overlay_sdk" > "$project_root/local.properties"

if [[ $# -eq 0 ]]; then
  set -- :app:assembleDebug
fi

cd "$project_root"

gradle_network_args=()
if [[ ${DSHM_GRADLE_OFFLINE:-1} != 0 ]]; then
  gradle_network_args+=(--offline)
else
  proxy_port=${DSHM_MAVEN_PROXY_PORT:-$((18000 + ($$ % 1000)))}
  proxy_log="$tools_dir/maven-proxy.log"
  proxy_cache="$tools_dir/maven-proxy-cache"
  python3 "$project_root/scripts/maven_proxy.py" \
    --host 127.0.0.1 \
    --port "$proxy_port" \
    --cache "$proxy_cache" \
    >"$proxy_log" 2>&1 &
  proxy_pid=$!
  cleanup_proxy() {
    kill "$proxy_pid" >/dev/null 2>&1 || true
    wait "$proxy_pid" >/dev/null 2>&1 || true
  }
  trap cleanup_proxy EXIT

  for _ in $(seq 1 50); do
    if curl --fail --silent "http://127.0.0.1:$proxy_port/__health" >/dev/null 2>&1; then
      break
    fi
    sleep 0.1
  done
  curl --fail --silent "http://127.0.0.1:$proxy_port/__health" >/dev/null || {
    echo "[build] local Maven proxy failed to start; see $proxy_log" >&2
    exit 74
  }
  export DSHM_MAVEN_PROXY_URL="http://127.0.0.1:$proxy_port"
  echo "[build] local Maven proxy: $DSHM_MAVEN_PROXY_URL"
  gradle_network_args+=("-DDSHM_MAVEN_PROXY_URL=$DSHM_MAVEN_PROXY_URL")
fi

"$gradle_bin" \
  --no-daemon \
  --max-workers=3 \
  --console=plain \
  "${gradle_network_args[@]}" \
  "-Dkotlin.daemon.options=runFilesPath=$kotlin_daemon_run_files" \
  "-Pandroid.aapt2FromMavenOverride=$official_aapt2" \
  "$@"
