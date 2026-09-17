#!/usr/bin/env bash
set -euo pipefail

# Reproduce the phone's Alpine/aarch64 userspace install on the Orange Pi without
# touching any installed app/runtime. This is intentionally a clean install so
# missing prebuild/native-module regressions cannot be hidden by old caches.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TMP_ROOT="$ROOT_DIR/.mcp/tmp/runtime-alpine-e2e-clean"
ARCHIVE="$ROOT_DIR/.mcp/tmp/alpine-minirootfs-3.24.1-aarch64.tar.gz"
ARCHIVE_URL="https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.1-aarch64.tar.gz"
ARCHIVE_SHA="f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259"
PIN_FILE="$ROOT_DIR/config/dsh-runtime.properties"
prop() { awk -F= -v key="$1" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PIN_FILE"; }
DSH_VERSION=$(prop dshVersion)
PNPM_VERSION=$(prop pnpmVersion)
NPM_REGISTRY=${DSHM_E2E_NPM_REGISTRY:-https://registry.npmmirror.com}
ALPINE_BASE=${DSHM_E2E_ALPINE_BASE:-https://mirrors.ustc.edu.cn/alpine/v3.24}
BUNDLED_PTY="$ROOT_DIR/core/runtime-android/src/main/assets/runtime/native-modules/node24-arm64-musl/pty.node"
BUNDLED_PTY_SHA="3e9cb29670c2cac1f7d54302099af8b0f998b9acc79891666b3136db575f18c3"
DSH_SEED="$ROOT_DIR/core/runtime-android/src/main/assets/$(prop dshSeedAsset)"
DSH_SEED_SHA=$(prop dshSeedSha256)
PROFILE_SEED="$ROOT_DIR/core/runtime-android/src/main/assets/$(prop webProfileSeedAsset)"
PROFILE_SEED_SHA=$(prop webProfileSeedSha256)
MOBILE_CONTEXT_VERSION=$(prop mobileContextVersion)
WEBVIEW_COMPAT_VERSION="0.1.2"
MOBILE_UI_VERSION="0.4.2-dshm.2"
TOKYO_THEME_VERSION="0.2.2-dshm.1"

command -v bwrap >/dev/null
command -v tar >/dev/null
command -v sha256sum >/dev/null
command -v node >/dev/null

echo '[e2e] WebView compat measured viewport policy'
node "$ROOT_DIR/scripts/test-webview-compat-policy.mjs"

if [[ $(uname -m) != aarch64 ]]; then
  echo "runtime-alpine-e2e: SKIP: requires an aarch64 host" >&2
  exit 77
fi

mkdir -p "$(dirname "$ARCHIVE")"
if [[ ! -f "$ARCHIVE" ]] || ! printf '%s  %s\n' "$ARCHIVE_SHA" "$ARCHIVE" | sha256sum -c - >/dev/null 2>&1; then
  rm -f "$ARCHIVE.part"
  curl -fL --retry 3 --retry-all-errors --connect-timeout 15 --max-time 300 \
    -o "$ARCHIVE.part" "$ARCHIVE_URL"
  printf '%s  %s\n' "$ARCHIVE_SHA" "$ARCHIVE.part" | sha256sum -c -
  mv "$ARCHIVE.part" "$ARCHIVE"
fi
printf '%s  %s\n' "$ARCHIVE_SHA" "$ARCHIVE" | sha256sum -c -

rm -rf "$TMP_ROOT"
mkdir -p "$TMP_ROOT"
tar -xzf "$ARCHIVE" -C "$TMP_ROOT"
printf '%s\n' \
  "$ALPINE_BASE/main" \
  "$ALPINE_BASE/community" \
  > "$TMP_ROOT/etc/apk/repositories"
printf '%s\n' 'nameserver 1.1.1.1' 'nameserver 8.8.8.8' > "$TMP_ROOT/etc/resolv.conf"
mkdir -p "$TMP_ROOT/opt/dsh" "$TMP_ROOT/dsh-home" "$TMP_ROOT/workspace"

echo '[e2e] rootfs absolute-symlink namespace regression'
test -L "$TMP_ROOT/bin/sh"
printf 'rootfs-bin-sh=%s\n' "$(readlink "$TMP_ROOT/bin/sh")"
# A rootfs absolute symlink belongs to the guest namespace. A host-side follow check
# can therefore report it missing even though the PRoot guest resolves it correctly.
probe="$ROOT_DIR/.mcp/tmp/rootfs-nofollow-probe"
rm -rf "$probe"
mkdir -p "$probe/bin"
printf probe > "$probe/bin/dshm-rootfs-target"
ln -s /bin/dshm-rootfs-target "$probe/bin/sh"
test -L "$probe/bin/sh"
if test -e "$probe/bin/sh"; then
  echo 'synthetic rootfs symlink unexpectedly resolved in host namespace' >&2
  exit 2
fi
rm -rf "$probe"

materialize_seed_hardlinks() {
  python3 - "$1" "$2" <<'PYHARD'
import os, pathlib, stat, sys, tarfile
archive, root = sys.argv[1], pathlib.Path(sys.argv[2])
count = 0
with tarfile.open(archive, "r:gz") as tf:
    for member in tf:
        if not member.islnk():
            continue
        destination = root / member.name.removeprefix("./")
        target = root / member.linkname.removeprefix("./")
        data = target.read_bytes()
        destination.unlink(missing_ok=True)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        os.chmod(destination, member.mode & 0o7777)
        count += 1
print(f"materialized-hardlinks={count} archive={pathlib.Path(archive).name}")
PYHARD
}

inside() {
  bwrap --unshare-all --share-net --die-with-parent \
    --bind "$TMP_ROOT" / \
    --dev /dev \
    --proc /proc \
    --tmpfs /tmp \
    --chdir /opt/dsh \
    /usr/bin/env -i \
      HOME=/dsh-home USER=root LOGNAME=root LANG=C.UTF-8 TERM=xterm-256color \
      PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
      DSH_HOME=/dsh-home \
      /bin/sh -lc "$1"
}

echo '[e2e] base packages'
inside 'apk update && apk add --no-cache bash ca-certificates curl git openssh-client python3 nodejs npm'

echo '[e2e] embedded DSH + pnpm fast path (no registry)'
printf '%s  %s\n' "$DSH_SEED_SHA" "$DSH_SEED" | sha256sum -c -
rm -rf "$TMP_ROOT/opt/dsh" "$TMP_ROOT/usr/local/lib/node_modules/pnpm" "$TMP_ROOT/usr/local/bin/pnpm" "$TMP_ROOT/usr/local/bin/pnpx"
tar -xzf "$DSH_SEED" -C "$TMP_ROOT"
materialize_seed_hardlinks "$DSH_SEED" "$TMP_ROOT"
inside 'node -e "require(\"koffi\"); const p=require(\"node-pty\"); if(typeof p.spawn!==\"function\") process.exit(2); console.log(\"seed-native-ok\")"'
seed_version=$(inside 'node --expose-internals /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js --version' | tail -n 1 | tr -d '\r')
[[ "$seed_version" == "$DSH_VERSION" ]] || { echo "Embedded seed expected DSH $DSH_VERSION, got $seed_version" >&2; exit 2; }
seed_pnpm=$(inside 'pnpm --version' | tail -n 1 | tr -d '\r')
[[ "$seed_pnpm" == "$PNPM_VERSION" ]] || { echo "Embedded seed expected pnpm $PNPM_VERSION, got $seed_pnpm" >&2; exit 2; }

echo '[e2e] embedded web profile fast path (no registry)'
printf '%s  %s\n' "$PROFILE_SEED_SHA" "$PROFILE_SEED" | sha256sum -c -
rm -rf "$TMP_ROOT/dsh-home/profiles/web"
mkdir -p "$TMP_ROOT/dsh-home"
tar -xzf "$PROFILE_SEED" -C "$TMP_ROOT/dsh-home"
materialize_seed_hardlinks "$PROFILE_SEED" "$TMP_ROOT/dsh-home"
profile_version=$(python3 - "$TMP_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-mobile-context/package.json" <<'PY2'
import json,sys
print(json.load(open(sys.argv[1]))['version'])
PY2
)
[[ "$profile_version" == "$MOBILE_CONTEXT_VERSION" ]] || { echo "Embedded profile expected mobile context $MOBILE_CONTEXT_VERSION, got $profile_version" >&2; exit 2; }
mkdir -p "$TMP_ROOT/dsh-home/mobile-plugins/dsh-mobile-context"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-mobile-context/." "$TMP_ROOT/dsh-home/mobile-plugins/dsh-mobile-context/"
mkdir -p "$TMP_ROOT/dsh-home/mobile-plugins/dsh-webview-compat"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-webview-compat/." "$TMP_ROOT/dsh-home/mobile-plugins/dsh-webview-compat/"
mkdir -p "$TMP_ROOT/dsh-home/mobile-plugins/dsh-client-ui-mobile"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile/." "$TMP_ROOT/dsh-home/mobile-plugins/dsh-client-ui-mobile/"
mkdir -p "$TMP_ROOT/dsh-home/mobile-plugins/dsh-plugin-tokyo-night"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-plugin-tokyo-night/." "$TMP_ROOT/dsh-home/mobile-plugins/dsh-plugin-tokyo-night/"
python3 - "$TMP_ROOT/dsh-home/profiles/web/package.json" <<'PY3'
import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
profile = json.loads(p.read_text())
deps = profile.setdefault('dependencies', {})
deps['@dsh-mobile/dsh-webview-compat'] = 'file:/dsh-home/mobile-plugins/dsh-webview-compat'
deps['dsh-client-ui-mobile'] = 'file:/dsh-home/mobile-plugins/dsh-client-ui-mobile'
deps['dsh-plugin-tokyo-night'] = 'file:/dsh-home/mobile-plugins/dsh-plugin-tokyo-night'
bundles = profile.setdefault('dsh', {}).setdefault('profile', {}).setdefault('bundles', [])
for name in ['@dsh-mobile/dsh-webview-compat', 'dsh-client-ui-mobile', 'dsh-plugin-tokyo-night']:
    if name not in bundles:
        bundles.append(name)
p.write_text(json.dumps(profile, indent=2) + '\n')
assert deps['@dsh-mobile/dsh-mobile-context'] == 'file:/dsh-home/mobile-plugins/dsh-mobile-context'
assert deps['@dsh-mobile/dsh-webview-compat'] == 'file:/dsh-home/mobile-plugins/dsh-webview-compat'
assert deps['dsh-client-ui-mobile'] == 'file:/dsh-home/mobile-plugins/dsh-client-ui-mobile'
assert deps['dsh-plugin-tokyo-night'] == 'file:/dsh-home/mobile-plugins/dsh-plugin-tokyo-night'
assert '@dsh-mobile/dsh-mobile-context' in profile['dsh']['profile']['bundles']
assert '@dsh-mobile/dsh-webview-compat' in profile['dsh']['profile']['bundles']
assert 'dsh-client-ui-mobile' in profile['dsh']['profile']['bundles']
assert 'dsh-plugin-tokyo-night' in profile['dsh']['profile']['bundles']
print('embedded-mobile-ui-profile-offline-contract-ok')
PY3
mkdir -p "$TMP_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-webview-compat/." "$TMP_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat/"
mkdir -p "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile/." "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile/"
mkdir -p "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-plugin-tokyo-night/." "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/"
inside 'test -f /dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat/lib/client.js'
inside 'test -f /dsh-home/profiles/web/node_modules/dsh-client-ui-mobile/lib/client.js'
inside 'test -f /dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/lib/client.js'
python3 - "$TMP_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat/package.json" <<'PYCOMPAT'
import json, sys
assert json.load(open(sys.argv[1]))['version'] == '0.1.2'
print('webview-compat-active-ok')
PYCOMPAT
grep -q 'webview-compat: viewport root contract' "$TMP_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat/lib/client.js"
grep -q 'measured-layout-px' "$TMP_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat/lib/client.js"
grep -q 'verticalViewportPatchedDeclarations' "$TMP_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat/lib/client.js"
inside 'test -f /dsh-home/mobile-plugins/dsh-client-ui-mobile/package.json'
python3 - "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile/package.json" "$TMP_ROOT/dsh-home/mobile-plugins/dsh-client-ui-mobile/package.json" <<'PYUI'
import json, sys
for path in sys.argv[1:]:
    assert json.load(open(path))['version'] == '0.4.2-dshm.2'
print('active-mobile-ui-asset-ok')
PYUI
grep -q 'data-dshm-shell' "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile/lib/client.js"
grep -q 'data-dshm-settings-panel' "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile/lib/client.js"
! grep -Eq '\[class[$*^]?=' "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile/lib/client.js"
python3 - "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/package.json" "$TMP_ROOT/dsh-home/mobile-plugins/dsh-plugin-tokyo-night/package.json" <<'PYTOKYO'
import json, sys
for path in sys.argv[1:]:
    assert json.load(open(path))['version'] == '0.2.2-dshm.1'
print('active-tokyo-theme-asset-ok')
PYTOKYO
grep -q 'colorScheme: "dark"' "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/lib/client.js"
grep -q 'settingsScope.bind({ namespace: "ui-theme" })' "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/lib/client.js"
grep -q 'status === "loading"' "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/lib/client.js"
! grep -q 'setTimeout(' "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/lib/client.js"
! grep -q 'colorScheme: "tokyo"' "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/lib/client.js"
! grep -q 'new MutationObserver' "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/lib/client.js"

echo '[e2e] Tokyo Night settings-adoption restore policy'
mkdir -p "$TMP_ROOT/opt/dsh-mobile-tests"
cp "$ROOT_DIR/scripts/test-tokyo-night-restore-policy.mjs" "$TMP_ROOT/opt/dsh-mobile-tests/test-tokyo-night-restore-policy.mjs"
inside 'node /opt/dsh-mobile-tests/test-tokyo-night-restore-policy.mjs /dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/lib/client.js'

echo '[e2e] embedded fast-path DSH web token exchange'
inside 'set -e; : >/tmp/dsh-web-seed.log; /usr/bin/node --expose-internals /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js web --host 127.0.0.1 --port 13081 --no-open >/tmp/dsh-web-seed.log 2>&1 & pid=$!; trap "kill $pid 2>/dev/null || true" EXIT; url=""; i=0; while [ $i -lt 120 ]; do url=$(sed -n "s#^dsh web: \(http://127.0.0.1:13081/?token=[^ ]*\).*#\1#p" /tmp/dsh-web-seed.log | tail -1); [ -n "$url" ] && break; if ! kill -0 $pid 2>/dev/null; then cat /tmp/dsh-web-seed.log >&2; exit 2; fi; i=$((i+1)); sleep 0.25; done; [ -n "$url" ] || { cat /tmp/dsh-web-seed.log >&2; exit 3; }; code=$(curl -sS -L -c /tmp/dsh-seed-cookies -o /tmp/dsh-seed-index.html -w "%{http_code}" "$url"); [ "$code" = 200 ]; grep -Eq "__DSH_BOOT__|<html" /tmp/dsh-seed-index.html; echo embedded-web-auth-ok'

echo '[e2e] reset for online fallback coverage'
rm -rf "$TMP_ROOT/opt/dsh" "$TMP_ROOT/usr/local/lib/node_modules/pnpm" "$TMP_ROOT/usr/local/bin/pnpm" "$TMP_ROOT/usr/local/bin/pnpx" "$TMP_ROOT/dsh-home/profiles/web"
mkdir -p "$TMP_ROOT/opt/dsh"

echo '[e2e] online pnpm fallback'
inside "NPM_CONFIG_REGISTRY='$NPM_REGISTRY' npm install -g pnpm@$PNPM_VERSION"

echo '[e2e] online DSH fallback dependency tree'
inside "rm -rf /opt/dsh/node_modules /opt/dsh/package-lock.json /opt/dsh/pnpm-lock.yaml; printf '%s\\n' '{\"private\":true}' > /opt/dsh/package.json; NPM_CONFIG_REGISTRY='$NPM_REGISTRY' NPM_CONFIG_AUDIT=false NPM_CONFIG_FUND=false NPM_CONFIG_FETCH_RETRIES=3 NPM_CONFIG_FETCH_TIMEOUT=120000 NPM_CONFIG_FETCH_RETRY_MINTIMEOUT=3000 NPM_CONFIG_FETCH_RETRY_MAXTIMEOUT=20000 npm install --omit=dev --include=optional --no-audit --no-fund @deepseek-ai/dsh@$DSH_VERSION"

echo '[e2e] bundled node-pty fast path'
printf '%s  %s\n' "$BUNDLED_PTY_SHA" "$BUNDLED_PTY" | sha256sum -c -
abi=$(inside 'node -p "process.versions.modules"' | tail -n 1 | tr -d '\r')
[[ "$abi" == 137 ]] || { echo "Expected Node module ABI 137, got $abi" >&2; exit 2; }
rm -rf "$TMP_ROOT/opt/dsh/node_modules/node-pty/build/Release"
mkdir -p "$TMP_ROOT/opt/dsh/node_modules/node-pty/build/Release"
cp "$BUNDLED_PTY" "$TMP_ROOT/opt/dsh/node_modules/node-pty/build/Release/pty.node"
inside 'node -e "require(\"koffi\"); const p=require(\"node-pty\"); if(typeof p.spawn!==\"function\") process.exit(2); console.log(\"bundled-native-modules-ok\")"'

echo '[e2e] bundled node-pty PTY behavior'
inside 'node - <<'"'"'NODE'"'"'
require("koffi");
const pty = require("node-pty");
if (typeof pty.spawn !== "function") throw new Error("node-pty spawn missing");
const child = pty.spawn("/bin/sh", ["-lc", "printf pty-ok"], {
  name: "xterm", cols: 80, rows: 24, cwd: "/tmp", env: { PATH: "/usr/bin:/bin" },
});
let output = "";
const timer = setTimeout(() => { child.kill(); process.exit(3); }, 5000);
child.onData(data => { output += data; });
child.onExit(event => {
  clearTimeout(timer);
  if (event.exitCode !== 0 || !output.includes("pty-ok")) process.exit(2);
  console.log("pty-ok");
});
NODE'
inside 'node -e "import(\"@deepseek-ai/dsh-subprocess-local\").then(m=>{ if(!m.LocalSubprocessRuntime) process.exit(2); console.log(\"subprocess-import-ok\") })"'

echo '[e2e] source-build fallback path'
rm -f "$TMP_ROOT/opt/dsh/node_modules/node-pty/build/Release/pty.node"
inside 'apk add --no-cache --virtual .dsh-build-deps build-base linux-headers nodejs-dev'
inside 'rm -rf /dsh-home/.cache/node-gyp; cd /opt/dsh/node_modules/node-pty && node /usr/local/lib/node_modules/pnpm/dist/node_modules/node-gyp/bin/node-gyp.js rebuild --nodedir=/usr'
inside 'test -f /opt/dsh/node_modules/node-pty/build/Release/pty.node'
inside 'node -e "const p=require(\"node-pty\"); if(typeof p.spawn!==\"function\") process.exit(2); console.log(\"source-rebuild-ok\")"'

echo '[e2e] remove compiler-only packages and verify native modules still load'
inside 'apk del .dsh-build-deps'
inside 'node -e "require(\"koffi\"); const p=require(\"node-pty\"); if(typeof p.spawn!==\"function\") process.exit(2); console.log(\"native-modules-ok\")"'

echo '[e2e] Alpine/musl-safe DSH launcher'
inside 'mkdir -p /usr/local/bin; printf "%s\n" "#!/bin/sh" "exec node --expose-internals /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js \"\$@\"" > /usr/local/bin/dsh; chmod 0755 /usr/local/bin/dsh; /usr/local/bin/dsh --version'

echo '[e2e] old Web profile offline migration back to native DSH Web'
mkdir -p "$TMP_ROOT/dsh-home/mobile-plugins/dsh-mobile-context"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-mobile-context/." "$TMP_ROOT/dsh-home/mobile-plugins/dsh-mobile-context/"
mkdir -p "$TMP_ROOT/dsh-home/mobile-plugins/dsh-webview-compat"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-webview-compat/." "$TMP_ROOT/dsh-home/mobile-plugins/dsh-webview-compat/"
mkdir -p "$TMP_ROOT/dsh-home/mobile-plugins/dsh-client-ui-mobile"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile/." "$TMP_ROOT/dsh-home/mobile-plugins/dsh-client-ui-mobile/"
mkdir -p "$TMP_ROOT/dsh-home/mobile-plugins/dsh-plugin-tokyo-night"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-plugin-tokyo-night/." "$TMP_ROOT/dsh-home/mobile-plugins/dsh-plugin-tokyo-night/"
inside '/usr/local/bin/dsh plugin --profile web add file:/dsh-home/mobile-plugins/dsh-mobile-context'
lock_before=$(sha256sum "$TMP_ROOT/dsh-home/profiles/web/pnpm-lock.yaml" | awk '{print $1}')
mkdir -p "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile/." "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile/"
python3 - "$TMP_ROOT/dsh-home/profiles/web/package.json" <<'PYOLD'
import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
data = json.loads(p.read_text())
data['userCustom'] = {'preserved': True}
deps = data.setdefault('dependencies', {})
deps['dsh-client-ui-mobile'] = 'file:/dsh-home/mobile-plugins/dsh-client-ui-mobile'
deps['dsh-plugin-tokyo-night'] = 'file:/dsh-home/mobile-plugins/dsh-plugin-tokyo-night'
profile = data.setdefault('dsh', {}).setdefault('profile', {})
bundles = profile.setdefault('bundles', [])
if 'dsh-client-ui-mobile' not in bundles:
    bundles.append('dsh-client-ui-mobile')
if 'dsh-plugin-tokyo-night' not in bundles:
    bundles.append('dsh-plugin-tokyo-night')
p.write_text(json.dumps(data, indent=2) + '\n')
PYOLD
python3 - "$TMP_ROOT/dsh-home/profiles/web/package.json" <<'PYNATIVE'
import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
data = json.loads(p.read_text())
deps = data.setdefault('dependencies', {})
deps['@dsh-mobile/dsh-webview-compat'] = 'file:/dsh-home/mobile-plugins/dsh-webview-compat'
deps['dsh-client-ui-mobile'] = 'file:/dsh-home/mobile-plugins/dsh-client-ui-mobile'
deps['dsh-plugin-tokyo-night'] = 'file:/dsh-home/mobile-plugins/dsh-plugin-tokyo-night'
profile = data.setdefault('dsh', {}).setdefault('profile', {})
bundles = profile.setdefault('bundles', [])
for name in ['@dsh-mobile/dsh-webview-compat', 'dsh-client-ui-mobile', 'dsh-plugin-tokyo-night']:
    if name not in bundles:
        bundles.append(name)
p.write_text(json.dumps(data, indent=2) + '\n')
PYNATIVE
mkdir -p "$TMP_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-webview-compat/." "$TMP_ROOT/dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat/"
rm -rf "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile"
mkdir -p "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile/." "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-client-ui-mobile/"
rm -rf "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night"
mkdir -p "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-plugin-tokyo-night/." "$TMP_ROOT/dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/"
lock_after=$(sha256sum "$TMP_ROOT/dsh-home/profiles/web/pnpm-lock.yaml" | awk '{print $1}')
[[ "$lock_after" == "$lock_before" ]] || { echo 'Offline native-profile reconciliation unexpectedly modified pnpm lockfile' >&2; exit 2; }
python3 - "$TMP_ROOT/dsh-home/profiles/web/package.json" <<'PYCHECK'
import json, sys
data=json.load(open(sys.argv[1]))
assert data['userCustom']['preserved'] is True
assert data['dependencies']['@dsh-mobile/dsh-mobile-context'] == 'file:/dsh-home/mobile-plugins/dsh-mobile-context'
assert data['dependencies']['@dsh-mobile/dsh-webview-compat'] == 'file:/dsh-home/mobile-plugins/dsh-webview-compat'
assert data['dependencies']['dsh-client-ui-mobile'] == 'file:/dsh-home/mobile-plugins/dsh-client-ui-mobile'
assert data['dependencies']['dsh-plugin-tokyo-night'] == 'file:/dsh-home/mobile-plugins/dsh-plugin-tokyo-night'
assert '@dsh-mobile/dsh-mobile-context' in data['dsh']['profile']['bundles']
assert '@dsh-mobile/dsh-webview-compat' in data['dsh']['profile']['bundles']
assert 'dsh-client-ui-mobile' in data['dsh']['profile']['bundles']
assert 'dsh-plugin-tokyo-night' in data['dsh']['profile']['bundles']
print('old-profile-mobile-ui-reconcile-contract-ok')
PYCHECK
inside 'test -f /dsh-home/profiles/web/node_modules/@dsh-mobile/dsh-webview-compat/lib/client.js'
inside 'test -f /dsh-home/profiles/web/node_modules/dsh-client-ui-mobile/lib/client.js'
inside 'test -f /dsh-home/profiles/web/node_modules/dsh-plugin-tokyo-night/lib/client.js'
inside 'test -f /dsh-home/mobile-plugins/dsh-client-ui-mobile/package.json'
inside 'test -f /dsh-home/mobile-plugins/dsh-plugin-tokyo-night/package.json'
python3 - "$TMP_ROOT/dsh-home/mobile-plugins/dsh-client-ui-mobile/package.json" <<'PYUI'
import json, sys
assert json.load(open(sys.argv[1]))['version'] == '0.4.2-dshm.2'
print('active-mobile-ui-asset-ok')
PYUI
python3 - "$TMP_ROOT/dsh-home/mobile-plugins/dsh-plugin-tokyo-night/package.json" <<'PYTOKYO2'
import json, sys
assert json.load(open(sys.argv[1]))['version'] == '0.2.2-dshm.1'
print('active-tokyo-theme-asset-ok')
PYTOKYO2

echo '[e2e] DSH web token exchange + authenticated frontend'
inside 'set -e; : >/tmp/dsh-web-e2e.log; /usr/bin/node --expose-internals /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js web --host 127.0.0.1 --port 13082 --no-open >/tmp/dsh-web-e2e.log 2>&1 & pid=$!; trap "kill $pid 2>/dev/null || true" EXIT; url=""; i=0; while [ $i -lt 120 ]; do url=$(sed -n "s#^dsh web: \(http://127.0.0.1:13082/?token=[^ ]*\).*#\1#p" /tmp/dsh-web-e2e.log | tail -1); [ -n "$url" ] && break; if ! kill -0 $pid 2>/dev/null; then cat /tmp/dsh-web-e2e.log >&2; exit 2; fi; i=$((i+1)); sleep 0.25; done; [ -n "$url" ] || { cat /tmp/dsh-web-e2e.log >&2; exit 3; }; unauth=$(curl -sS -o /tmp/unauth.txt -w "%{http_code}" http://127.0.0.1:13082/); [ "$unauth" = 401 ]; grep -q "dsh web authentication required" /tmp/unauth.txt; code=$(curl -sS -L -c /tmp/dsh-cookies -o /tmp/dsh-index.html -w "%{http_code}" "$url"); [ "$code" = 200 ]; grep -Eq "__DSH_BOOT__|<html" /tmp/dsh-index.html; clean=$(curl -sS -b /tmp/dsh-cookies -o /dev/null -w "%{http_code}" http://127.0.0.1:13082/); [ "$clean" = 200 ]; echo web-auth-e2e-ok'

actual=$(inside '/usr/local/bin/dsh --version' | tail -n 1 | tr -d '\r')
[[ "$actual" == "$DSH_VERSION" ]] || { echo "Expected DSH $DSH_VERSION, got $actual" >&2; exit 2; }
inside 'node --version; npm --version; pnpm --version'

echo "runtime-alpine-e2e: PASS dsh=$actual"
