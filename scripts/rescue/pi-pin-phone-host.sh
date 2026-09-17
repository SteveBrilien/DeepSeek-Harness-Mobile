#!/usr/bin/env bash
# Orange Pi only. Verify out-of-band PHONE sshd host fingerprint; never trust ssh-keyscan blindly.
set -euo pipefail
if [[ $# -ne 1 || ! $1 =~ ^SHA256:[a-zA-Z0-9+/]{43}$ ]]; then
  echo 'Usage: pi-pin-phone-host.sh SHA256:<fingerprint read directly from PHONE sshd host key>' >&2
  exit 2
fi
expected=$1
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
keydir="$root/.private/ssh"
port=${PI_RESCUE_PORT:-22023}
[[ $port =~ ^[0-9]{1,5}$ ]] && (( 10#$port >= 1024 && 10#$port <= 65535 )) || exit 2
umask 077
mkdir -p "$keydir"
tmp=$(mktemp "$keydir/.known-hosts.XXXXXXXX")
trap 'rm -f "$tmp"' EXIT
ssh-keyscan -T 5 -p "$port" -t ed25519 127.0.0.1 2>/dev/null > "$tmp" || { echo "Rescue tunnel/phone SSH host unavailable; nothing was trusted." >&2; exit 3; }
[[ $(grep -c '^\[127\.0\.0\.1\]:' "$tmp") -eq 1 ]] || { echo 'Expected one ED25519 host key via rescue tunnel; tunnel not ready or unexpected host key.' >&2; exit 3; }
actual=$(ssh-keygen -lf "$tmp" -E sha256 | awk '{print $2}')
[[ "$actual" == "$expected" ]] || { echo "PHONE HOST KEY MISMATCH: observed $actual; expected $expected. Refusing to trust." >&2; exit 4; }
if [[ -e "$keydir/known_hosts" ]]; then
  cmp -s "$tmp" "$keydir/known_hosts" || { echo 'Previously pinned host key differs. Stop and investigate; do not overwrite automatically.' >&2; exit 5; }
  echo 'Phone host key already pinned and verified.'
else
  chmod 600 "$tmp"
  mv -n "$tmp" "$keydir/known_hosts"
  echo 'Phone host key pinned after fingerprint verification.'
fi
