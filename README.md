# DeepSeek Harness Mobile

DeepSeek Harness Mobile is a personal Android host for running the official DeepSeek Harness locally on-device, with a Native Recovery Core that remains usable even when the Linux/Node/DSH runtime is unhealthy.

## Current release

**0.3.0-alpha.2 — manual-test build**

- Android target: fixed Android 11 self-hosting device profile.
- APK: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.2.apk`
- Update manifest: `release/update.json`
- The APK uses the project stable development signing certificate so later alpha builds can cover-install without silently changing identity.
- This alpha is intended for manual installation/feedback. Build, lint, signing, bundle-contract, and artifact-integrity checks pass; final device/runtime E2E promotion remains feedback-gated.

## Implemented surfaces

- **对话** — official DSH Web Client hosted at the local loopback DSH service, with mobile-only compatibility CSS for narrow settings/model screens.
- **项目** — Mobile Project registry layered above native DSH Workspace/Session semantics.
- **文件** — Native file manager/editor with create, rename, copy/move/paste, Trash and text editing.
- **终端** — one terminal surface with Auto/Linux Runtime/Android Local/ADB execution-domain model; Linux and Android Local are implemented, while in-app Wireless ADB transport remains a follow-up capability.
- **更多 / 恢复中心** — runtime health/control, recovery snapshots, portable backup, diagnostics, onboarding, and in-app update checks.

## Runtime architecture

The local runtime is rebuildable and separated from persistent user state:

- PRoot + Alpine userspace
- Node 24.x
- pnpm
- `@deepseek-ai/dsh` 0.1.2-rc.1
- A/B runtime slots with verification and rollback
- DSH Web binds only to `127.0.0.1:3080`
- persistent `DSH_HOME` lives outside A/B slots so settings, sessions and credentials do not disappear when a slot is rebuilt or switched

`root` inside PRoot is not Android UID 0 and does not bypass Android sandboxing or SELinux.

## DSH compatibility

The app does not replace DSH Chat or fork the DSH core. Native changes are kept outside the official Web Client wherever possible.

The bundled `@dsh-mobile/dsh-mobile-context` Cordis integration provides:

1. a process-stable system-prompt section whose text remains byte-stable across turns;
2. one `plugin/snapshot` bootstrap event for a fresh ordinary Session;
3. `plugin/notice` helpers for real runtime capability changes without rewriting the stable prompt prefix.

This keeps environment awareness explicit while preserving the longest stable prompt prefix for provider-side prompt/KV caching.

## UI

Native UI is being aligned to official DSH primitives instead of generic Material styling:

- Chinese-first labels for Native screens
- Light / Dark / Tokyo Night themes
- official DSH Fish/Whale launcher mark and available upstream icon paths
- compact DSH-like typography, spacing, fine borders and component geometry
- edge-to-edge content with a compact swipe-up navigation tray
- narrow-screen compatibility layer for the official DSH settings/models UI

## Recovery and secrets

Recovery is deliberately independent from the disposable runtime.

- Persistent Recovery Vault + manifest
- atomic checkpoints and portable ZIP exports
- per-file SHA-256/size manifests
- persistent DSH session/settings backup without plaintext secret files
- encrypted Secret Vault for DSH credentials
- Android Keystore for normal device unlock plus an independent Recovery Password path for recovery after uninstall
- runtime rebuild lock instead of blindly restoring an old rootfs

Plaintext `.credentials.yaml`, `.env`, `.pem` and `.key` files are excluded from ordinary portable archives.

## First-run bootstrap

The onboarding flow covers:

1. theme selection;
2. Recovery Vault discovery and recovery-password setup/unlock;
3. Android/OriginOS background-stability guidance;
4. local runtime installation/verification;
5. final readiness state.

A reinstall can rediscover existing Recovery assets rather than treating the device as a new environment.

## In-app updates

`AppUpdateManager` reads:

`https://raw.githubusercontent.com/SteveBrilien/DeepSeek-Harness-Mobile/main/release/update.json`

When a newer `versionCode` exists, it downloads the HTTPS APK, verifies its SHA-256, and hands the verified file to the Android system installer. Updates are not silently installed.

## Development and maintenance contract

Future agents should read `AGENTS.md` before changing architecture, storage formats, prompt/context injection, privilege behavior, DSH compatibility seams, or update/recovery mechanisms.

Primary documents:

- `docs/ARCHITECTURE.md`
- `docs/DEVELOPMENT_RULES.md`
- `docs/DSH_COMPATIBILITY.md`
- `docs/DATA_RECOVERY.md`
- `docs/PLUGIN_SYSTEM.md`
- `docs/UPDATE_RECOVERY.md`
- `docs/UI_BASELINE.md`
- `docs/ROADMAP.md`
- `docs/adr/`
