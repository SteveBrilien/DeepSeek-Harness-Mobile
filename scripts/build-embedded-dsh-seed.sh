#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
E2E_ROOT="$ROOT_DIR/.mcp/tmp/runtime-alpine-e2e-clean"
PTY="$ROOT_DIR/core/runtime-android/src/main/assets/runtime/native-modules/node24-arm64-musl/pty.node"
SEED_DIR="$ROOT_DIR/core/runtime-android/src/main/assets/runtime/seeds"
OUT="$SEED_DIR/dsh-0.1.2-rc.1-node24-arm64-musl.tgz"
PROFILE_OUT="$SEED_DIR/web-profile-0.1.2-rc.1-mobile-context-0.2.1.tgz"
EXPECTED_DSH=0.1.2-rc.1
EXPECTED_PNPM=12.3.4
EXPECTED_PLUGIN=0.2.1
EXPECTED_PTY_SHA=3e9cb29670c2cac1f7d54302099af8b0f998b9acc79891666b3136db575f18c3
[[ $(uname -m) == aarch64 ]] || { echo 'embedded seed build requires aarch64' >&2; exit 77; }
[[ -f "$E2E_ROOT/opt/dsh/node_modules/@deepseek-ai/dsh/package.json" ]] || { echo 'run scripts/test-runtime-alpine-e2e.sh first' >&2; exit 2; }
[[ -d "$E2E_ROOT/usr/local/lib/node_modules/pnpm" ]] || { echo 'pnpm tree missing from E2E root' >&2; exit 2; }
[[ -f "$E2E_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-mobile-context/package.json" ]] || { echo 'web profile seed missing from E2E root' >&2; exit 2; }
actual=$(python3 - "$E2E_ROOT/opt/dsh/node_modules/@deepseek-ai/dsh/package.json" <<'PY'
import json,sys
print(json.load(open(sys.argv[1]))['version'])
PY
)
[[ "$actual" == "$EXPECTED_DSH" ]] || { echo "expected DSH $EXPECTED_DSH, got $actual" >&2; exit 2; }
pnpm_actual=$(node -e "const p=require('$E2E_ROOT/usr/local/lib/node_modules/pnpm/package.json'); console.log(p.version)")
[[ "$pnpm_actual" == "$EXPECTED_PNPM" ]] || { echo "expected pnpm $EXPECTED_PNPM, got $pnpm_actual" >&2; exit 2; }
plugin_actual=$(python3 - "$E2E_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-mobile-context/package.json" <<'PY'
import json,sys
print(json.load(open(sys.argv[1]))['version'])
PY
)
[[ "$plugin_actual" == "$EXPECTED_PLUGIN" ]] || { echo "expected mobile context $EXPECTED_PLUGIN, got $plugin_actual" >&2; exit 2; }
printf '%s  %s\n' "$EXPECTED_PTY_SHA" "$PTY" | sha256sum -c -
mkdir -p "$E2E_ROOT/opt/dsh/node_modules/node-pty/build/Release" "$SEED_DIR"
cp "$PTY" "$E2E_ROOT/opt/dsh/node_modules/node-pty/build/Release/pty.node"
rm -f "$OUT.tmp" "$PROFILE_OUT.tmp"
# Bundle DSH and pnpm together so the normal first-run path does not need npm at all.
tar -C "$E2E_ROOT" -czf "$OUT.tmp" \
  opt/dsh \
  usr/local/lib/node_modules/pnpm \
  usr/local/bin/pnpm \
  usr/local/bin/pnpx
# The web profile contains only framework/plugin dependencies and no user credentials.
tar -C "$E2E_ROOT/dsh-home" -czf "$PROFILE_OUT.tmp" profiles/web
mv "$OUT.tmp" "$OUT"
mv "$PROFILE_OUT.tmp" "$PROFILE_OUT"
echo "seed=$OUT"
ls -lh "$OUT"
sha256sum "$OUT"
echo "profile_seed=$PROFILE_OUT"
ls -lh "$PROFILE_OUT"
sha256sum "$PROFILE_OUT"
