#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
tools_dir="$project_root/.tools"
download_dir="$tools_dir/downloads"
qemu_root="$tools_dir/qemu"
x86_root="$tools_dir/x86-runtime"
payload_dir="$tools_dir/aapt2-official"
bin_dir="$tools_dir/official-aapt2-bin"

# Must track the AGP line used by the project.  Keep the payload checksum pinned
# so a bootstrap never executes an unverified build tool.
aapt2_version=${DSHM_AAPT2_VERSION:-8.9.2-12782657}
aapt2_jar_sha256=${DSHM_AAPT2_SHA256:-ffb7b7d419c7341dd013f2f87bfb0148f8bff0a2a47c222f54935b3b8f233605}
aapt2_jar="$download_dir/aapt2-${aapt2_version}-linux.jar"
aapt2_url="https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/${aapt2_version}/aapt2-${aapt2_version}-linux.jar"
wrapper="$bin_dir/aapt2"

mkdir -p "$download_dir" "$qemu_root" "$x86_root" "$payload_dir" "$bin_dir"

# qemu-user-static is an ARM64 executable capable of running the official
# x86_64 aapt2. apt verifies the package against the configured signed index.
qemu_bin=$(find "$qemu_root" -type f -name qemu-x86_64-static -print -quit 2>/dev/null || true)
if [[ -z "$qemu_bin" || ! -x "$qemu_bin" ]]; then
  command -v apt-get >/dev/null 2>&1 || { echo "[official-aapt2] apt-get is required" >&2; exit 81; }
  command -v dpkg-deb >/dev/null 2>&1 || { echo "[official-aapt2] dpkg-deb is required" >&2; exit 81; }
  qemu_deb="$download_dir/qemu-user-static-arm64.deb"
  rm -f "$qemu_deb"
  (
    cd "$download_dir"
    rm -f qemu-user-static_*_arm64.deb
    apt-get download qemu-user-static >/dev/null
    mv qemu-user-static_*_arm64.deb "$(basename "$qemu_deb")"
  )
  rm -rf "$qemu_root"
  mkdir -p "$qemu_root"
  dpkg-deb -x "$qemu_deb" "$qemu_root"
  qemu_bin=$(find "$qemu_root" -type f -name qemu-x86_64-static -print -quit)
  [[ -n "$qemu_bin" && -x "$qemu_bin" ]] || { echo "[official-aapt2] qemu-x86_64-static missing after extraction" >&2; exit 82; }
fi

# Fetch an amd64 glibc + libgcc runtime without modifying the host's dpkg
# architecture list. Package filenames and hashes come from Debian's signed
# package index; the downloaded .deb payloads are verified against SHA256.
if [[ ! -e "$x86_root/lib/x86_64-linux-gnu/libc.so.6" || ! -e "$x86_root/lib/x86_64-linux-gnu/libgcc_s.so.1" ]]; then
  command -v xz >/dev/null 2>&1 || { echo "[official-aapt2] xz is required" >&2; exit 83; }
  packages_xz="$download_dir/debian-bookworm-amd64-Packages.xz"
  packages_txt="$download_dir/debian-bookworm-amd64-Packages"
  if [[ ! -s "$packages_xz" ]]; then
    curl -fL --retry 8 --retry-all-errors --connect-timeout 20 --max-time 300 \
      -o "$packages_xz" 'https://deb.debian.org/debian/dists/bookworm/main/binary-amd64/Packages.xz'
  fi
  xz -dc "$packages_xz" > "$packages_txt"

  package_meta() {
    python3 - "$packages_txt" "$1" <<'PY'
import sys
index, wanted = sys.argv[1:]
with open(index, encoding="utf-8", errors="replace") as f:
    text = f.read()
for stanza in text.split("\n\n"):
    fields = {}
    for line in stanza.splitlines():
        if ": " in line:
            k, v = line.split(": ", 1)
            fields[k] = v
    if fields.get("Package") == wanted:
        print(fields["Filename"])
        print(fields["SHA256"])
        raise SystemExit(0)
raise SystemExit(f"package not found: {wanted}")
PY
  }

  rm -rf "$x86_root"
  mkdir -p "$x86_root"
  for pkg in libc6 libgcc-s1; do
    mapfile -t meta < <(package_meta "$pkg")
    file=${meta[0]}
    sha=${meta[1]}
    deb="$download_dir/${pkg}-amd64.deb"
    if [[ ! -s "$deb" ]] || ! printf '%s  %s\n' "$sha" "$deb" | sha256sum --check --status; then
      curl -fL --retry 8 --retry-all-errors --connect-timeout 20 --max-time 300 \
        -o "$deb" "https://deb.debian.org/debian/$file"
    fi
    printf '%s  %s\n' "$sha" "$deb" | sha256sum --check --status || {
      echo "[official-aapt2] SHA256 mismatch for $pkg" >&2
      exit 84
    }
    dpkg-deb -x "$deb" "$x86_root"
  done

  # Debian's /lib64 loader symlink is absolute. Make it relative so QEMU -L
  # cannot escape the workspace-local runtime root.
  mkdir -p "$x86_root/lib64"
  rm -f "$x86_root/lib64/ld-linux-x86-64.so.2"
  ln -s ../lib/x86_64-linux-gnu/ld-linux-x86-64.so.2 "$x86_root/lib64/ld-linux-x86-64.so.2"
fi

if [[ ! -s "$aapt2_jar" ]] || ! printf '%s  %s\n' "$aapt2_jar_sha256" "$aapt2_jar" | sha256sum --check --status; then
  tmp="$aapt2_jar.part"
  rm -f "$tmp"
  echo "[official-aapt2] downloading aapt2 $aapt2_version"
  curl -fL --retry 8 --retry-all-errors --connect-timeout 20 --max-time 300 -o "$tmp" "$aapt2_url"
  printf '%s  %s\n' "$aapt2_jar_sha256" "$tmp" | sha256sum --check --status || {
    echo "[official-aapt2] aapt2 archive SHA256 mismatch" >&2
    rm -f "$tmp"
    exit 85
  }
  mv "$tmp" "$aapt2_jar"
fi

rm -rf "$payload_dir"
mkdir -p "$payload_dir"
unzip -q "$aapt2_jar" -d "$payload_dir"
chmod 0755 "$payload_dir/aapt2" "$qemu_bin"

cat > "$wrapper" <<EOF
#!/usr/bin/env bash
set -euo pipefail
project_root=\$(cd "\$(dirname "\${BASH_SOURCE[0]}")/../.." && pwd)
qemu=\$(find "\$project_root/.tools/qemu" -type f -name qemu-x86_64-static -print -quit)
exec "\$qemu" -L "\$project_root/.tools/x86-runtime" "\$project_root/.tools/aapt2-official/aapt2" "\$@"
EOF
chmod 0755 "$wrapper"

version_output=$("$wrapper" version 2>&1)
[[ "$version_output" == *"12782657"* ]] || {
  echo "[official-aapt2] unexpected version: $version_output" >&2
  exit 86
}
daemon_output=$(printf 'quit\n\n' | timeout 10 "$wrapper" daemon 2>&1)
[[ "$daemon_output" == *"Ready"* && "$daemon_output" == *"Exiting daemon"* ]] || {
  echo "[official-aapt2] daemon smoke failed: $daemon_output" >&2
  exit 87
}

printf '%s\n' "$wrapper"
