# DeepSeek Harness Mobile — Project Handoff

Last updated: 2026-09-09
Current app release: `0.3.0-alpha.3` (`versionCode = 4`)
Current documented Git HEAD before this handoff update: `f9c3d12ebce873aefd34cfb7d9bd0660189bb814`
Primary branch: `main`
Remote: `ssh://git@ssh.github.com:443/SteveBrilien/DeepSeek-Harness-Mobile.git`

## 1. Product goal

DeepSeek Harness Mobile is an Android host for running the official DeepSeek Harness locally on a fixed Android 11 / OriginOS device profile. The Native Recovery Core must remain usable even when the Linux userspace, Node, DSH runtime, plugins, or network installation path is unhealthy.

The product intentionally separates:

- disposable/rebuildable Runtime state;
- persistent DSH/user state;
- Native Recovery and diagnostics;
- Android-local execution capability;
- official DSH Web Client compatibility.

Do not collapse these layers into one mutable installation directory.

## 2. Current architecture baseline

### Native app

- Kotlin + Jetpack Compose.
- Native surfaces: onboarding, Projects, Files, Terminal, More/Recovery, runtime controls and diagnostics.
- Official DSH Web Client remains the Chat surface; avoid cosmetic forks of DSH Chat.

### Local Runtime

- PRoot + Alpine Linux userspace.
- Node 24.x.
- pnpm.
- `@deepseek-ai/dsh` pinned by `RuntimePins`.
- A/B Runtime slots with verify/switch/rollback semantics.
- DSH Web service binds to `127.0.0.1:3080`.
- Persistent DSH state is outside disposable A/B slots.

### Mobile context integration

Bundled `@dsh-mobile/dsh-mobile-context` currently follows the cache-stable contract:

1. one byte-stable process-level system prompt section;
2. one `plugin/snapshot` bootstrap for a fresh ordinary Session;
3. `plugin/notice` for actual environment/capability changes;
4. no per-turn rewriting of the stable prompt prefix.

This behavior is covered by `mobile_context_contract`.

### Terminal execution domains

One Terminal UI exposes distinct execution domains rather than pretending every command runs with the same privileges:

- `Auto`;
- Linux Runtime;
- Android Local / app-owned recovery shell;
- ADB, when a future in-app transport/provider is available.

Never silently downgrade a privileged command into a weaker execution domain.

## 3. 0.3.0-alpha.3 implementation state

The following changes are already implemented and validated on the OrangePi development host:

### First-run UI / branding

- launcher icon safe-area/cropping corrected;
- DeepSeek Harness fish branding integrated from the public Harness source asset/path rather than a hand-drawn approximation;
- onboarding information hierarchy simplified;
- content can scroll while the main action area remains visually stable;
- page transitions and button press feedback added.

### Runtime installation UX

- progress percentage;
- live scrolling installation log;
- elapsed time;
- estimated remaining time when enough telemetry exists;
- foreground notification synchronized with install state;
- source/mirror state shown to the user.

### Mirror selection

Runtime installation supports multiple download candidates instead of one hard-coded endpoint. Current design includes official and common China-accessible mirrors for Alpine plus npm/npmmirror selection.

Expected behavior:

1. probe candidate availability/latency;
2. automatically prefer the best current source;
3. allow manual source selection;
4. retry/fallback safely if a selected mirror fails;
5. never trust a downloaded rootfs without integrity verification.

### Reuse existing resources before downloading

Installation now checks reusable material before network download:

- an already installed usable Runtime;
- app-local cached rootfs;
- Recovery Vault cached rootfs;
- compatible existing DSH Runtime version metadata.

Reusable archives must pass expected SHA-256 validation before reuse.

If an existing Runtime is older than the recommended Runtime/DSH version, the UI should offer a centered update choice rather than automatically destroying the existing environment. The update path remains non-destructive by using the inactive A/B slot first.

### Crash containment

A previous Runtime-install failure path could allow an exception to escape the foreground-service worker and terminate the whole app process. The installation/service boundary now catches and reports failures as recoverable install errors with logs instead of allowing the app process to crash from that path.

This fix still requires device-side reproduction/regression verification because no ADB device was connected during the alpha.3 release build.

## 4. Release artifact

Current manual-test package:

- file: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.3.apk`
- SHA-256: `a191a6988820a831b893e0338a736155ddbd2dc29988f09a59748eb8125054cb`
- update manifest: `release/update.json`

The current update manifest advertises:

- `versionCode`: 4
- `versionName`: `0.3.0-alpha.3`
- download endpoint: `https://dsh.wmy-cloud.cn/dsh-mobile-download/DeepSeek-Harness-Mobile-0.3.0-alpha.3.apk`

The project uses a stable development signing identity for alpha cover-install testing. Do not replace the signing identity casually; doing so breaks seamless upgrade/cover-install behavior.

## 5. Validation status

Verified on the development host for alpha.3:

- `android_debug` succeeded;
- `android_lint` succeeded;
- stable signing certificate verification succeeded;
- release SHA-256 generated and recorded;
- XML/resource parse checks passed;
- project path contamination check reports no known contamination.

Not yet completed for alpha.3:

- physical-device install E2E after this exact release;
- reproduction and regression validation of the previously observed Runtime-install app crash;
- full Runtime download/install/start/health loop on the target OriginOS device;
- real-world mirror selection under the target phone's network;
- UI inspection for onboarding/installer layout and animation on the target display.

These are release-gating manual-test items, not host-build blockers.

## 6. Target-device validation sequence

When the phone is available over ADB, prefer the existing controlled TaskProfiles instead of ad-hoc host shell commands.

Recommended order:

1. `android_device_smoke` or controlled replace-install;
2. `android_device_cold_start_probe`;
3. `android_device_runtime_probe`;
4. reproduce first-run Runtime installation from a clean/known state;
5. `android_runtime_install_watch` while installation is running;
6. inspect fatal/ANR/runtime logs if anything fails;
7. verify Runtime health at loopback and DSH Chat startup;
8. verify reinstall/resource-reuse path;
9. verify older-Runtime update-choice dialog and A/B upgrade path;
10. verify onboarding page layout, animation, notification telemetry and icon appearance.

Do not delete user data just to create a clean test unless a backup/recovery path has already been verified.

## 7. Important files and owners

- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/OnboardingScreen.kt`
  - first-run flow, hierarchy, Runtime installation presentation.
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/Branding.kt`
  - native brand presentation.
- `app/src/main/kotlin/com/stevebrilien/dshmobile/runtime/RuntimeForegroundService.kt`
  - foreground Runtime install/control service and exception containment.
- `app/src/main/kotlin/com/stevebrilien/dshmobile/runtime/RuntimeInstallTelemetry.kt`
  - progress/log/elapsed/ETA state used by UI and notification.
- `core/runtime-android/.../NativeRuntimeInstaller.kt`
  - rootfs acquisition, mirror use, cache reuse, install orchestration.
- `core/runtime-android/.../RuntimePins.kt`
  - pinned Runtime/DSH versions and integrity metadata.
- `core/runtime-android/.../AndroidRuntimeManager.kt`
  - A/B Runtime lifecycle owner.
- `core/runtime-android/.../RuntimeControlPlane.kt`
  - Runtime-facing orchestration/control contract.
- `core/runtime-android/src/main/assets/runtime/dsh-mobile-context/`
  - bundled DSH mobile-context integration.
- `release/`
  - manually promoted release APK, checksum and update manifest.

## 8. Documentation map

Before architectural work, read:

- `AGENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPMENT_RULES.md`
- `docs/DATA_RECOVERY.md`
- `docs/DSH_COMPATIBILITY.md`
- `docs/PLUGIN_SYSTEM.md`
- `docs/UPDATE_RECOVERY.md`
- `docs/UI_BASELINE.md`
- `docs/ROADMAP.md`
- `docs/NEXT_ACTIONS.md`
- `CHANGELOG.md`
- relevant ADRs under `docs/adr/`

Architecture-changing decisions require a new ADR. Functional changes should update `CHANGELOG.md` and this handoff when they materially change current state or next-step assumptions.

## 9. Development infrastructure

Registered MCP project: `deepseek-harness-mobile`.

Known-good environment at handoff time:

- OrangePi host: aarch64;
- MCP: `2.1.4-orange.1`;
- project branch: `main`;
- repository-scoped ED25519 GitHub Deploy Key authentication is configured under ignored `.mcp/ssh/` state; the private key must never be committed or copied into ordinary logs/docs;
- SSH host verification is pinned to GitHub's published Ed25519 host key and the repository deploy-key authentication/push path was verified on 2026-09-09;
- network available in controlled project tasks;
- JDK 17 HostCapability valid;
- Android SDK HostCapability valid;
- AAPT2 HostCapability valid;
- ADB HostCapability valid, but target device may not currently be connected.

Build through configured TaskProfiles where possible:

- `android_debug`;
- `android_lint`;
- `android_signing_verify`;
- `mobile_context_contract`;
- device probes when ADB is online.

Avoid reintroducing host/canonical path contamination by mixing uncontrolled host build state with Bubblewrap project caches.

## 10. Security and recovery invariants

Do not regress these constraints:

- secrets must not be written to logs or ordinary plaintext backup bundles;
- Recovery Vault must remain logically separate from disposable Runtime slots;
- downloaded Runtime assets require integrity verification;
- A/B upgrade must preserve a rollback path until the new slot is verified;
- DSH Web remains loopback-only by default;
- PRoot `root` is not Android root;
- user-selected files/directories must never be silently deleted;
- update installation must remain user-visible and Android-system-mediated unless a separately reviewed privileged design is adopted.

## 11. Current top priorities

See `docs/NEXT_ACTIONS.md` for the active queue. The immediate priority is device E2E of alpha.3, especially the Runtime-install crash regression and resource-reuse/update-choice flows. After that, stabilize the first-run UX from real screenshots before expanding into new feature work.
