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
DSH_VERSION="0.1.2-rc.1"
PNPM_VERSION="12.3.4"
NPM_REGISTRY=${DSHM_E2E_NPM_REGISTRY:-https://registry.npmmirror.com}
ALPINE_BASE=${DSHM_E2E_ALPINE_BASE:-https://mirrors.ustc.edu.cn/alpine/v3.24}

command -v bwrap >/dev/null
command -v tar >/dev/null
command -v sha256sum >/dev/null

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

echo '[e2e] pnpm'
inside "NPM_CONFIG_REGISTRY='$NPM_REGISTRY' npm install -g pnpm@$PNPM_VERSION"

echo '[e2e] DSH dependency tree'
inside "rm -rf /opt/dsh/node_modules /opt/dsh/package-lock.json /opt/dsh/pnpm-lock.yaml; printf '%s\\n' '{\"private\":true}' > /opt/dsh/package.json; NPM_CONFIG_REGISTRY='$NPM_REGISTRY' NPM_CONFIG_AUDIT=false NPM_CONFIG_FUND=false NPM_CONFIG_FETCH_RETRIES=3 NPM_CONFIG_FETCH_TIMEOUT=120000 NPM_CONFIG_FETCH_RETRY_MINTIMEOUT=3000 NPM_CONFIG_FETCH_RETRY_MAXTIMEOUT=20000 npm install --omit=dev --include=optional --no-audit --no-fund @deepseek-ai/dsh@$DSH_VERSION"

echo '[e2e] native build dependencies + node-pty rebuild'
inside 'apk add --no-cache --virtual .dsh-build-deps build-base linux-headers'
inside 'npm_config_build_from_source=true npm rebuild node-pty'

echo '[e2e] native module + PTY behavior'
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

echo '[e2e] remove compiler-only packages and verify native modules still load'
inside 'apk del .dsh-build-deps'
inside 'node -e "require(\"koffi\"); const p=require(\"node-pty\"); if(typeof p.spawn!==\"function\") process.exit(2); console.log(\"native-modules-ok\")"'

echo '[e2e] Alpine/musl-safe DSH launcher'
inside 'mkdir -p /usr/local/bin; printf "%s\n" "#!/bin/sh" "exec node --expose-internals /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js \"\$@\"" > /usr/local/bin/dsh; chmod 0755 /usr/local/bin/dsh; /usr/local/bin/dsh --version'

echo '[e2e] mobile context plugin integration'
mkdir -p "$TMP_ROOT/dsh-home/mobile-plugins/dsh-mobile-context"
cp -a "$ROOT_DIR/core/runtime-android/src/main/assets/runtime/dsh-mobile-context/." "$TMP_ROOT/dsh-home/mobile-plugins/dsh-mobile-context/"
inside '/usr/local/bin/dsh plugin --profile web add file:/dsh-home/mobile-plugins/dsh-mobile-context'

echo '[e2e] DSH web token exchange + authenticated frontend'
inside 'set -e; /usr/local/bin/dsh web --host 127.0.0.1 --port 13080 --no-open >/tmp/dsh-web-e2e.log 2>&1 & pid=$!; trap "kill $pid 2>/dev/null || true" EXIT; url=""; i=0; while [ $i -lt 120 ]; do url=$(sed -n "s#^dsh web: \(http://127.0.0.1:13080/?token=[^ ]*\).*#\1#p" /tmp/dsh-web-e2e.log | tail -1); [ -n "$url" ] && break; if ! kill -0 $pid 2>/dev/null; then cat /tmp/dsh-web-e2e.log >&2; exit 2; fi; i=$((i+1)); sleep 0.25; done; [ -n "$url" ] || { cat /tmp/dsh-web-e2e.log >&2; exit 3; }; unauth=$(curl -sS -o /tmp/unauth.txt -w "%{http_code}" http://127.0.0.1:13080/); [ "$unauth" = 401 ]; grep -q "dsh web authentication required" /tmp/unauth.txt; code=$(curl -sS -L -c /tmp/dsh-cookies -o /tmp/dsh-index.html -w "%{http_code}" "$url"); [ "$code" = 200 ]; grep -Eq "__DSH_BOOT__|<html" /tmp/dsh-index.html; clean=$(curl -sS -b /tmp/dsh-cookies -o /dev/null -w "%{http_code}" http://127.0.0.1:13080/); [ "$clean" = 200 ]; echo web-auth-e2e-ok'

actual=$(inside '/usr/local/bin/dsh --version' | tail -n 1 | tr -d '\r')
[[ "$actual" == "$DSH_VERSION" ]] || { echo "Expected DSH $DSH_VERSION, got $actual" >&2; exit 2; }
inside 'node --version; npm --version; pnpm --version'

echo "runtime-alpine-e2e: PASS dsh=$actual"
