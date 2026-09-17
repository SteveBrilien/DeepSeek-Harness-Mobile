#!/usr/bin/env bash
# PHONE ONLY: optional, separate from the SSH rescue channel. Never restart rescue to change ADB ports.
# Usage: phone-adb-reverse.sh CONNECT_PORT [ADB_HOST] [PAIR_PORT]
set -euo pipefail
if (( $# < 1 || $# > 3 )); then echo "Usage: $0 CONNECT_PORT [ADB_HOST=127.0.0.1] [PAIR_PORT]" >&2; exit 2; fi
ADB_PORT=$1
ADB_HOST=${2:-127.0.0.1}
PAIR_PORT=${3:-}
PI_SSH_ALIAS=${PI_SSH_ALIAS:-orange}
port_ok() { [[ $1 =~ ^[0-9]{1,5}$ ]] && (( 10#$1 >= 1024 && 10#$1 <= 65535 )); }
port_ok "$ADB_PORT" && { [[ -z $PAIR_PORT ]] || port_ok "$PAIR_PORT"; } || { echo 'Bad ADB port.' >&2; exit 2; }
[[ $ADB_HOST =~ ^[a-zA-Z0-9._-]+$ && $PI_SSH_ALIAS =~ ^[a-zA-Z0-9._-]+$ ]] || { echo 'Invalid host/alias.' >&2; exit 2; }
if ! timeout 3 bash -c 'exec 3<>/dev/tcp/$1/$2' _ "$ADB_HOST" "$ADB_PORT" 2>/dev/null; then
  echo "ADB endpoint $ADB_HOST:$ADB_PORT is unreachable from this phone shell. Confirm the CURRENT Wireless debugging connection port and address." >&2
  exit 3
fi
forwards=(-R "127.0.0.1:25555:${ADB_HOST}:${ADB_PORT}")
if [[ -n $PAIR_PORT ]]; then
  if ! timeout 3 bash -c 'exec 3<>/dev/tcp/$1/$2' _ "$ADB_HOST" "$PAIR_PORT" 2>/dev/null; then echo 'Pairing endpoint is unreachable.' >&2; exit 3; fi
  forwards+=(-R "127.0.0.1:25554:${ADB_HOST}:${PAIR_PORT}")
fi
echo 'Opening OPTIONAL localhost-only ADB forwards on Orange Pi. Rescue SSH runs independently.'
echo 'ADB port identity must be checked after connection; do not expose these ports publicly.'
exec ssh -N -T -o BatchMode=yes -o StrictHostKeyChecking=yes \
  -o ExitOnForwardFailure=yes -o ServerAliveInterval=20 -o ServerAliveCountMax=3 \
  -o ControlMaster=no -o ControlPath=none "${forwards[@]}" "$PI_SSH_ALIAS"
