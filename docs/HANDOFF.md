# DeepSeek Harness Mobile — Project Handoff

Last updated: 2026-09-10
Current app release: `0.3.0-alpha.11` (`versionCode = 12`)
Base Git HEAD before alpha.8 changes: `f4f0344fed75186115d9e1e4b0ff3e6a7f6073c9`
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

## 3. 0.3.0-alpha.8 implementation state

The following changes are already implemented and validated on the OrangePi development host:

### First-run UI / branding

- launcher icon safe-area/cropping corrected;
- DeepSeek Harness branding is sourced directly from the official upstream `BrandWordmark.tsx` vector artwork rather than recreated with Android text/fonts;
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

### Runtime/DSH reliability added in alpha.4 / alpha.5

- Runtime downloads support resumable partial files, retry and source fallback instead of hanging indefinitely.
- DSH installation uses the Alpine/musl-safe dependency path. After alpha.4 device testing showed that Android PRoot could report a successful `node-pty` rebuild yet still leave no loadable `pty.node`, alpha.5 bundles the verified 79 KB Alpine/musl arm64 Node-24 ABI 137 module and installs it directly; source rebuild remains only as an ABI-mismatch fallback.
- DSH is launched through a wrapper that enables the Node internals required by its loader on Alpine/musl.
- Runtime readiness no longer depends on a nonexistent `/healthz` endpoint.
- DSH Web startup token is carried into the first WebView navigation, then normal authenticated cookie flow takes over.
- Runtime logs redact token-bearing launch URLs.
- `runtime_alpine_e2e` validates Node 24.18.1, DSH 0.1.2-rc.1, native modules, a real PTY command, Mobile Context integration, and the 401 -> token -> authenticated 200 Web flow.
- Terminal UI is terminal-first; recent and explicitly pinned commands live behind a history surface instead of explanatory chrome.
- Runtime installation logs are selectable and provide one-tap copy; telemetry retains up to the latest 250 lines for device-side bug reports.
- Alpha.5 target-device evidence confirms the bundled Node-24 ABI 137 `node-pty` fast path works through Runtime verification; the remaining 99% failure was DSH Web startup readiness detection, not Runtime installation.
- Alpha.6 separates `Runtime installed` from `DSH started`: a startup failure preserves the verified A/B slot and exposes `重试启动` instead of forcing another install.
- DSH Web readiness now waits up to 180 seconds and treats a reachable loopback HTTP endpoint (2xx/3xx/401) as process readiness; token/cookie authentication remains a separate WebView step.
- DSH startup log tail is appended to the same copyable install diagnostics on startup failure.
- Onboarding is a five-page horizontal pager; button navigation and left/right swipe use the same page state.
- Runtime progress uses a fixed immersive viewport: header/actions remain fixed, only the log body scrolls, and the log auto-follows only while the user is already near its end.
- Compact onboarding branding uses the exact official Harness `BrandWordmark.tsx` outline geometry (182:24 at 24dp). The whale, `deepseek` lettering, HARNESS badge shape and HARNESS glyphs are mechanically synchronized from upstream blob `a9df992179c7cc8f0792142f2dde66dbbb3b5464`; no Android `Text`, font approximation, or locally redrawn wordmark is permitted.
- Alpha.7 fixes the target-device 99% startup failure: Android network security permitted cleartext loopback for `localhost`, while alpha.6 readiness probing used `http://127.0.0.1:3080/`. The resulting cleartext-policy exception was swallowed by the boolean probe and looked like a 180-second server timeout even though DSH had already emitted its token URL. Readiness probing and the token launch URL now canonicalize to `localhost`; DSH itself remains safely bound to `127.0.0.1`.
- Exact-match Runtime reuse is now a first-class fast path. If the active slot verifies and already contains the pinned DSH version, install requests skip extraction, apk package installation, pnpm and the 523-package DSH install, then proceed directly to DSH startup.
- npm package tarballs are cached under `Recovery/Runtime/npm-cache` so a genuine reinstall/update can reuse public package data across app updates/uninstalls when the Recovery Vault survives. Credentials are not stored in this cache.
- Alpine mirror probes no longer send a tiny HTTP Range request, because TUNA/Aliyun can reject those probes with 403 while serving normal GETs. The app now performs a normal GET, reads only a bounded prefix, and closes the response.

### Alpha.8 hardening and install acceleration

- Normal first-run no longer downloads the 523-package DSH tree from npm. The APK contains SHA-pinned ARM64/musl seeds for DSH `0.1.2-rc.1` + pnpm `12.3.4` and the prevalidated DSH Web/Mobile Context profile. Online npm installation remains a fallback only.
- Embedded seed SHA-256 values are pinned in `RuntimePins` and verified before extraction. The seed archive contains no user `.ssh`, `.credentials.yaml`, `.env`, or secret files.
- The Runtime E2E covers the embedded fast path, the online fallback, bundled `pty.node`, real PTY execution, source rebuild fallback, Mobile Context integration, and authenticated DSH Web.
- Startup readiness now probes literal `127.0.0.1:3080` with a raw IPv4 socket before URLConnection fallbacks. This avoids false negatives caused by OEM/VPN handling of `localhost`; tokenized DSH URLs remain on the literal loopback address. Failure diagnostics include both Android-side and Runtime-side probes.
- A stale app-owned DSH process is stopped before a new start attempt. Startup failure preserves a verified Runtime so `重试启动` does not reinstall Alpine/DSH. Invalid active Runtime verification falls back to rebuilding the inactive A/B slot.
- Foreground-service restart recovery resumes an interrupted install only when install telemetry says the Runtime was not yet prepared; otherwise it attempts DSH start.
- Recovery checkpoints exclude Runtime/npm caches and all `.ssh` content. SSH identity now has a separate AES-GCM encrypted Secret Vault backup and restore path; an empty local `.ssh` cannot overwrite a valid encrypted backup.
- Terminal exposes only implemented execution choices: `自动`, `Linux`, `Android`. The unfinished ADB terminal mode is intentionally hidden until a real transport/provider exists; AUTO no longer misroutes Android commands into a placeholder ADB path.
- Narrow-screen onboarding actions use a wrapping layout. Android 11 uses restored adaptive-icon resources with the smaller official whale foreground safe area.
- Official DeepSeek Harness wordmark paths remain the exact upstream vector geometry; no Android text/font reconstruction is allowed.
- `targetSdk` intentionally remains 28 while `compileSdk` is 35 because the current self-hosted PRoot/Node architecture executes files from app-private writable storage. Target-SDK modernization requires first moving executable bootstrap components to an Android-compliant packaged executable location.
- Remote DSH-version discovery (for example newer 0.1.5 candidates) is still future work; updates must install into the inactive slot and pass compatibility checks before switching.

### Alpha.9 mobile shell and DSH responsive UI

- The approved native shell is restored as `首页 / 工作区 / 终端 / 设置`; the old floating/drag navigation tray is removed. `工作区` contains `项目 / 文件`, and Settings is a grouped index with advanced Runtime/recovery material on a deeper page.
- Home proactively starts DSH when a reusable installed Runtime is present. Missing Runtime and DSH startup timeout are separate states; a Web startup problem does not require reinstalling a verified Runtime.
- The DSH WebView fills the usable phone content area and disables wide-viewport/overview scaling that could shrink the desktop-first page into a small centered surface. Zoom is disabled and text zoom remains 100%.
- Android-side DOM/CSS compatibility injection has been removed. Mobile DSH layout is provided by vendored `dsh-client-ui-mobile 0.1.9`, installed as a normal Cordis/DSH Web plugin alongside Mobile Context. The vendored plugin is recorded in `THIRD_PARTY_NOTICES.md` under its MIT license.
- The embedded Web profile was regenerated with the mobile UI plugin. Runtime ARM64 E2E verifies its installed version and completes authenticated DSH Web startup.
- Appearance controls use a wrapping layout on narrow screens. The exact upstream DeepSeek Harness wordmark geometry remains unchanged.
- Embedded DSH/pnpm fast-install seeds, A/B Runtime recovery, raw loopback startup diagnostics, encrypted credential/SSH recovery and alpha.8 security hardening remain intact.

### Alpha.10 OriginOS Runtime extraction and startup hardening

- Target-device alpha.9 logs identified the embedded fast-path failure precisely: Android/OriginOS denied `link(2)` with `EACCES` while restoring tar hard-link entries. The DSH seed contains 2 hard links and the Web-profile seed contains 181; all targets are regular in-archive files. The Android extractor now materializes hard-link entries as ordinary file copies with the archived mode/timestamp instead of calling `Os.link`, preserving the same verified seed bytes while avoiding the OEM kernel restriction.
- Because the alpha.9 embedded seed failed, the installer correctly fell back to npm and downloaded 523 DSH packages. Alpha.10 keeps that fallback for corruption/compatibility failures, but the normal target-device path should remain on the bundled seed and avoid the multi-minute registry install.
- Alpha.9 then failed during the startup preflight with `Runtime command failed (127)` before `dsh-web.log` had useful output. Alpha.10 creates the startup log before preflight and records explicit `[startup]` stages. Cold start uses a host-side manifest/file prerequisite check rather than rerunning the full DSH command verification that already gates slot staging/activation.
- Persistent Mobile Context/mobile-UI markers are checked before entering PRoot; an already-current Web profile no longer rewrites the launcher or runs a redundant plugin migration command on every app start.
- The long-lived Web process launches the pinned DSH Node entrypoint directly: `/usr/bin/node --expose-internals /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js web ...`. The shell launcher remains available for interactive/plugin commands. Process-exit diagnostics now include the exit code.
- The Runtime E2E explicitly materializes all seed hard links as ordinary files and uses the exact direct-Node production Web launch path. This passes embedded DSH/native loading, the mobile Web profile, authenticated Web startup, online fallback, source-built `node-pty`, cleanup and final version checks.

### Alpha.11 rootfs symlink-aware startup preflight

- Target-device alpha.10 logs isolated the next startup failure to `[startup] verify-start-prerequisites`: `Runtime shell is missing`. The Runtime itself was not missing. Alpine stores `/bin/sh` as an absolute guest symlink (`/bin/sh -> /bin/busybox`). Android-side `File.isFile()` follows that link in the Android host namespace, where the guest target is not present, producing a false negative before PRoot ever starts.
- Runtime prerequisite, inventory and health checks now use `Os.lstat()` semantics for rootfs nodes. This verifies that the rootfs entry exists without following a guest-absolute symlink into Android's host filesystem. Actual executable/runtime behavior remains verified inside PRoot by the install/activation E2E path.
- `runtime_alpine_e2e` now records the real Alpine `/bin/sh -> /bin/busybox` link and contains a synthetic host-vs-guest absolute-symlink regression probe, preventing a return to host-following existence checks.
- Runtime retry telemetry starts a fresh timer and clears stale download/source fields, so a DSH-only retry no longer displays the original multi-hour install elapsed time or npm mirror.
- Existing alpha.10/alpha.9 slot A should be reused. This fix intentionally requires only an APK cover-install followed by `重试启动` or cold launch; no Runtime reinstall and no app-data clear are required.

## 4. Release artifact

Current manual-test package:

- file: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.11.apk`
- SHA-256: `ab2588ffdae8b2762f219c6788173e017bc37d541719ece247481b2fec38b2a1`
- size: `86,641,657` bytes
- update manifest: `release/update.json`

The current update manifest advertises:

- `versionCode`: 12
- `versionName`: `0.3.0-alpha.11`
- download endpoint: `https://raw.githubusercontent.com/SteveBrilien/DeepSeek-Harness-Mobile/main/release/DeepSeek-Harness-Mobile-0.3.0-alpha.11.apk`

The project uses a stable development signing identity for alpha cover-install testing. Do not replace the signing identity casually; doing so breaks seamless upgrade/cover-install behavior.

## 5. Validation status

Verified on the development host for alpha.11:

- `runtime_alpine_e2e` succeeded, including embedded DSH/pnpm fast path, embedded Web profile, `dsh-client-ui-mobile 0.1.9`, online fallback, bundled `pty.node`, real PTY execution, source-build fallback, Mobile Context and authenticated DSH Web;
- `mobile_context_contract` succeeded;
- final `android_debug` succeeded on versionCode 12 after the rootfs symlink/preflight fix;
- final `android_lint` succeeded on the alpha.11 candidate; retained vector-path performance warnings do not alter the exact upstream brand geometry;
- final stable signing verification succeeded against the alpha.11 candidate APK;
- release SHA-256 generated and recorded;
- project path contamination check reports no known contamination.

Physical-device validation remains manual because no ADB device is connected. The immediate target test is to cover-install alpha.11 and retry the already prepared slot A. The `Runtime shell is missing` false negative should be gone without reinstalling Runtime; the next startup stages and DSH Web result should then be visible in the copyable log.

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
  - renders the exact upstream BrandWordmark vector geometry; never substitute Android text/font reconstruction.
- `app/src/main/res/drawable/ic_deepseek_harness_wordmark_primary.xml` / `ic_deepseek_harness_wordmark_inverted.xml`
  - mechanically converted layers from official `BrandWordmark.tsx` blob `a9df992179c7cc8f0792142f2dde66dbbb3b5464`; preserve source geometry exactly.
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
- MCP: `2.1.6-orange.1`;
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

`mobile_context_contract` is executed with a project-local pinned Node 24.18.1 bootstrap (`scripts/run-mobile-context-contract.sh`) because the OrangePi host distro still exposes Node 12 by default. The bootstrap verifies the official Node tarball against a pinned SHA-256 and caches it under ignored `.mcp/tools/` state before running the contract test.

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

See `docs/NEXT_ACTIONS.md` for the active queue. The immediate priority is target-device verification of alpha.7, especially direct DSH startup from the already prepared alpha.6 Runtime without reinstall and confirmation that localhost loopback probing reaches DSH Web. After that, stabilize the first-run UX from real screenshots before expanding into new feature work.
