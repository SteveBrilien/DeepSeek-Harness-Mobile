#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
bundle_dir="$project_root/.tools/arm64-android"
deb_dir="$bundle_dir/debs"
root_dir="$bundle_dir/root"
bin_dir="$bundle_dir/bin"
android_bin_dir="$root_dir/usr/lib/android-sdk/build-tools/debian"
lib_path="$root_dir/usr/lib/aarch64-linux-gnu/android:$root_dir/usr/lib/aarch64-linux-gnu:$root_dir/usr/lib"

tools=(aapt aapt2 aidl zipalign dexdump split-select)

packages=(
  aapt
  aidl
  zipalign
  dexdump
  split-select
  android-libaapt
  android-libandroidfw
  android-libbase
  android-liblog
  android-libutils
  android-libziparchive
  android-libcutils
  android-libbacktrace
  android-libunwind
  android-libart
  android-libnativebridge
  android-libnativeloader
  libprotobuf-lite23
  libzopfli1
  p7zip-full
)

smoke() {
  for tool in "${tools[@]}"; do
    [[ -x "$android_bin_dir/$tool" ]] || return 1
  done
  LD_LIBRARY_PATH="$lib_path" "$android_bin_dir/aapt" version >/dev/null 2>&1
  LD_LIBRARY_PATH="$lib_path" "$android_bin_dir/aapt2" version >/dev/null 2>&1
  LD_LIBRARY_PATH="$lib_path" "$android_bin_dir/dexdump" >/dev/null 2>&1 || [[ $? -ne 127 ]]
  LD_LIBRARY_PATH="$lib_path" "$android_bin_dir/split-select" --help >/dev/null 2>&1
}

if ! smoke; then
  command -v apt >/dev/null 2>&1 || {
    echo "[arm64-tools] apt is required to bootstrap Android ARM64 build tools" >&2
    exit 71
  }
  command -v dpkg-deb >/dev/null 2>&1 || {
    echo "[arm64-tools] dpkg-deb is required to bootstrap Android ARM64 build tools" >&2
    exit 71
  }

  mkdir -p "$deb_dir"
  rm -rf "$root_dir"
  mkdir -p "$root_dir"

  echo "[arm64-tools] downloading ARM64 Android build-tool packages"
  (
    cd "$deb_dir"
    apt download "${packages[@]}"
  )

  echo "[arm64-tools] extracting tool bundle"
  shopt -s nullglob
  debs=("$deb_dir"/*.deb)
  ((${#debs[@]} > 0)) || {
    echo "[arm64-tools] no .deb files were downloaded" >&2
    exit 72
  }
  for deb in "${debs[@]}"; do
    dpkg-deb -x "$deb" "$root_dir"
  done
  shopt -u nullglob

  smoke || {
    echo "[arm64-tools] ARM64 tool smoke test failed after extraction" >&2
    for tool in "${tools[@]}"; do
      target="$android_bin_dir/$tool"
      [[ -e "$target" ]] || continue
      echo "--- $tool" >&2
      LD_LIBRARY_PATH="$lib_path" ldd "$target" >&2 || true
    done
    exit 73
  }
fi

mkdir -p "$bin_dir"
for tool in "${tools[@]}"; do
  cat > "$bin_dir/$tool" <<EOF
#!/usr/bin/env bash
set -euo pipefail
bundle_dir=\$(cd "\$(dirname "\${BASH_SOURCE[0]}")/.." && pwd)
root_dir="\$bundle_dir/root"
export LD_LIBRARY_PATH="\$root_dir/usr/lib/aarch64-linux-gnu/android:\$root_dir/usr/lib/aarch64-linux-gnu:\$root_dir/usr/lib\${LD_LIBRARY_PATH:+:\$LD_LIBRARY_PATH}"
exec "\$root_dir/usr/lib/android-sdk/build-tools/debian/$tool" "\$@"
EOF
  chmod 0755 "$bin_dir/$tool"
done

# Ubuntu's native ARM64 aapt2 is older than the AGP 8.9.x protocol.  AGP uses
# --source-path only to preserve the logical source location in diagnostics; it
# does not alter the resource payload.  Strip exactly this metadata-only option
# while keeping every other argument byte-for-byte equivalent.  This is scoped
# to aapt2 and intentionally fails if --source-path has no value.
cat > "$bin_dir/aapt2" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
bundle_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
root_dir="$bundle_dir/root"
export LD_LIBRARY_PATH="$root_dir/usr/lib/aarch64-linux-gnu/android:$root_dir/usr/lib/aarch64-linux-gnu:$root_dir/usr/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
args=()
while (($#)); do
  if [[ "$1" == "--source-path" ]]; then
    (($# >= 2)) || { echo "aapt2 wrapper: --source-path requires a value" >&2; exit 64; }
    shift 2
    continue
  fi
  args+=("$1")
  shift
done
exec "$root_dir/usr/lib/android-sdk/build-tools/debian/aapt2" "${args[@]}"
EOF
chmod 0755 "$bin_dir/aapt2"

printf '%s\n' "$bin_dir"
