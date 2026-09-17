#!/usr/bin/env bash
# Orange Pi only: connect to PHONE over an already verified localhost reverse SSH path.
set -euo pipefail
[[ $# -ge 1 ]] || { echo 'Usage: pi-phone-ssh.sh PHONE_SSH_USERNAME [--check|--shell]' >&2; exit 2; }
username=$1
mode=${2:---check}
[[ $username =~ ^[a-zA-Z_][a-zA-Z0-9_.-]*$ ]] || { echo 'Invalid phone username.' >&2; exit 2; }
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
keydir="$root/.private/ssh"
port=${PI_RESCUE_PORT:-22023}
[[ $port =~ ^[0-9]{1,5}$ ]] && (( 10#$port >= 1024 && 10#$port <= 65535 )) || exit 2
[[ -s "$keydir/id_ed25519_dshmobile_device" && -s "$keydir/known_hosts" ]] || {
  echo 'Rescue identity or out-of-band-verified phone host key is missing. Refusing insecure SSH fallback.' >&2; exit 3;
}
options=( -F /dev/null -p "$port" -i "$keydir/id_ed25519_dshmobile_device"
  -o UserKnownHostsFile="$keydir/known_hosts" -o GlobalKnownHostsFile=/dev/null
  -o StrictHostKeyChecking=yes -o BatchMode=yes -o IdentitiesOnly=yes
  -o PreferredAuthentications=publickey -o PasswordAuthentication=no
  -o KbdInteractiveAuthentication=no -o ConnectTimeout=6
  -o ServerAliveInterval=20 -o ServerAliveCountMax=3 )
case "$mode" in
  --check) exec ssh "${options[@]}" -T "$username@127.0.0.1" 'printf "RESCUE_SSH_OK\n"; id -u' ;;
  --shell) exec ssh "${options[@]}" -t "$username@127.0.0.1" ;;
  *) echo 'Use --check or --shell.' >&2; exit 2 ;;
esac
