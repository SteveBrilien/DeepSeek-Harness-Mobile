#!/usr/bin/env bash
set -euo pipefail

NODE_VERSION="24.18.1"
NODE_DIST="node-v${NODE_VERSION}-linux-arm64"
NODE_SHA256="7201e3a09dc825bac57867c81913e2b8f0ef87d04cb9082af4cda82f6ff3d88c"
TOOLS_DIR="${PWD}/.mcp/tools"
NODE_DIR="${TOOLS_DIR}/${NODE_DIST}"
NODE_BIN="${NODE_DIR}/bin/node"
ARCHIVE="${PWD}/.mcp/tmp/${NODE_DIST}.tar.xz"
URL="https://nodejs.org/dist/v${NODE_VERSION}/${NODE_DIST}.tar.xz"

if [[ "$(uname -m)" != "aarch64" ]]; then
  echo "mobile-context-contract: unsupported host architecture $(uname -m); expected aarch64" >&2
  exit 86
fi

if [[ ! -x "${NODE_BIN}" ]]; then
  mkdir -p "${TOOLS_DIR}" "${PWD}/.mcp/tmp"
  echo "[node] bootstrapping Node ${NODE_VERSION} for contract test"
  curl --fail --location --retry 3 --connect-timeout 15 --output "${ARCHIVE}.part" "${URL}"
  printf '%s  %s\n' "${NODE_SHA256}" "${ARCHIVE}.part" | sha256sum -c -
  mv "${ARCHIVE}.part" "${ARCHIVE}"
  rm -rf "${NODE_DIR}.tmp"
  mkdir -p "${NODE_DIR}.tmp"
  tar -xJf "${ARCHIVE}" -C "${NODE_DIR}.tmp" --strip-components=1
  rm -rf "${NODE_DIR}"
  mv "${NODE_DIR}.tmp" "${NODE_DIR}"
fi

actual="$(${NODE_BIN} --version)"
if [[ "${actual}" != "v${NODE_VERSION}" ]]; then
  echo "mobile-context-contract: unexpected Node version ${actual}; expected v${NODE_VERSION}" >&2
  exit 87
fi

echo "[node] ${actual}"
exec "${NODE_BIN}" scripts/test-mobile-context.mjs
