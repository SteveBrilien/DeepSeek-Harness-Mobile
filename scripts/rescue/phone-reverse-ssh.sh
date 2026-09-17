#!/usr/bin/env bash
# Run in the PHONE's independent Termux/Ubuntu environment, never inside DSH Mobile.
# Establish a persistent phone->Orange Pi reverse SSH rescue path. No APK commands.
set -euo pipefail

PI_SSH_ALIAS=${PI_SSH_ALIAS:-orange}
PHONE_SSH_PORT=${PHONE_SSH_PORT:-8022}
PI_RESCUE_PORT=${PI_RESCUE_PORT:-22023}
MODE=${1:-run}

port_ok() { [[ $1 =~ ^[0-9]{1,5}$ ]] && (( 10#$1 >= 1024 && 10#$1 <= 65535 )); }
port_ok "$PHONE_SSH_PORT" && port_ok "$PI_RESCUE_PORT" || { echo 'Port must be 1024..65535.' >&2; exit 2; }
[[ $PI_SSH_ALIAS =~ ^[a-zA-Z0-9._-]+$ ]] || { echo 'Invalid SSH alias.' >&2; exit 2; }
command -v ssh >/dev/null || { echo 'OpenSSH client is required on the phone.' >&2; exit 2; }
# Reject a bare `orange` name. On some mobile DNS networks an unconfigured
# hostname can resolve to an unrelated public IP. SSH host keys alone are not
# evidence that this is the intended Orange Pi.
resolved_host=$(ssh -G "$PI_SSH_ALIAS" 2>/dev/null | awk '$1 == "hostname" && !done { print $2; done=1 }')
if [[ -z $resolved_host || ( $PI_SSH_ALIAS == orange && $resolved_host == orange ) ]]; then
  echo 'The orange SSH alias is unconfigured. Verify and configure the trusted LAN or cloud route first; refusing remote connection.' >&2
  exit 4
fi

if ! timeout 3 bash -c 'exec 3<>/dev/tcp/127.0.0.1/$1' _ "$PHONE_SSH_PORT" 2>/dev/null; then
    echo "No independent phone sshd responding at 127.0.0.1:$PHONE_SSH_PORT. Start it before this tunnel." >&2
    exit 3
fi

ssh_options=(
  -N -T -o BatchMode=yes -o StrictHostKeyChecking=yes
  -o ExitOnForwardFailure=yes -o ServerAliveInterval=20 -o ServerAliveCountMax=3
  -o TCPKeepAlive=yes -o ControlMaster=no -o ControlPath=none
  -R "127.0.0.1:${PI_RESCUE_PORT}:127.0.0.1:${PHONE_SSH_PORT}"
)
case "$MODE" in
  once)
    exec ssh "${ssh_options[@]}" "$PI_SSH_ALIAS"
    ;;
  run)
    echo "Starting independent rescue tunnel (Orange Pi localhost:${PI_RESCUE_PORT} -> phone localhost:${PHONE_SSH_PORT})."
    echo 'Keep this command in a separate phone tmux session; Ctrl-C or closing the session stops it.'
    trap 'exit 0' INT TERM
    failures=0
    while :; do
      started=$SECONDS
      ssh "${ssh_options[@]}" "$PI_SSH_ALIAS" && rc=0 || rc=$?
      echo "Rescue SSH disconnected (status=$rc)." >&2
      if (( SECONDS - started >= 120 )); then failures=0; else ((failures+=1)); fi
      delay=$(( 5 * failures ))
      (( delay > 60 )) && delay=60
      echo "Reconnecting in $delay seconds; no DSH process or existing tunnel will be restarted." >&2
      sleep "$delay"
    done
    ;;
  *) echo "Usage: $0 [once|run]" >&2; exit 2 ;;
esac
