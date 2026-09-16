# DeepSeek Harness Mobile — Project Handoff

Last updated: 2026-09-16
Current app release: `0.4.0-preview.2` (`versionCode = 22`)
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

### Alpha.12 deterministic startup and offline profile migration

- Runtime startup is now one observable six-stage pipeline: `verify-start-prerequisites` -> `write-mobile-context` -> `ensure-mobile-plugins` -> `spawn-dsh-web` -> `wait-web-ready` -> `web-ready`. Foreground-service telemetry is the UI status source rather than a separate three-minute Web-port guess loop.
- Every `ACTION_START` first rebuilds startup state from the on-disk Runtime inventory. Stale install/start telemetry cannot make an absent Runtime look installed or keep a failed startup spinning. Unexpected service-level startup exceptions are persisted as current-attempt failures.
- APK-owned Mobile Context and mobile-UI plugins are reconciled entirely on the Android host. Existing Web profiles are preserved and patched offline/atomically; normal startup no longer enters PRoot to invoke `dsh plugin add`/pnpm for these bundled plugins. Managed plugin trees use staging/backup/rollback activation, and version markers commit only after the final profile contract verifies.
- Robolectric/API-30 tests now cover the service/telemetry path and old-profile migration. The ARM64 Runtime E2E additionally proves an old Web profile can be reconciled without changing the pnpm lockfile and still start the real token-authenticated DSH Web service.

### Alpha.13 native DSH Web baseline and persistent Home WebView

- The Home `WebView` is now composed for the lifetime of the native app shell and only toggled visible/invisible when switching bottom tabs. Workspace/Terminal/Settings no longer destroy Home, so returning to Home preserves the same DSH SPA instance and its in-memory state instead of calling `loadUrl()` again.
- `dsh-client-ui-mobile` is no longer loaded into the active DSH Web profile. Its previous narrow-screen stylesheet intentionally hid native sidebar/header utility/tab elements, which also removed the official settings entry. Alpha.13 keeps the package only as a dormant APK-owned asset for future UI work.
- Existing alpha.12 profiles migrate offline: the coordinator removes only the managed mobile-UI dependency/bundle/profile tree, keeps `@dsh-mobile/dsh-mobile-context` active, preserves unrelated/user JSON fields and does not require a startup pnpm transaction. The profile-mode marker is now `native-dsh-web-v1`, forcing the one-time alpha.12 -> alpha.13 reconciliation.
- UI adaptation is deliberately deferred until the official DSH Web baseline is validated on OriginOS. Future mobile work must not hide or replace DSH-owned settings/header/sidebar controls by default.

### Alpha.14 current-process DSH Web authentication handshake

- Alpha.13 target-device evidence proves the Runtime and DSH Web server can start normally: a direct browser request to `127.0.0.1:3080` receives DSH's expected `authentication required` response. The blank Home was therefore an Android host/WebView authentication handoff failure, not a missing Runtime or missing frontend.
- The device log exposed an ordering race: on some starts Android recorded `web-ready` from the loopback probe before the current DSH process had printed its `dsh web: http://127.0.0.1:3080/?token=...` URL. The previous parser searched the cumulative log and could consequently hand WebView a token owned by an older DSH process. DSH correctly rejects that stale token with HTTP 401.
- Alpha.14 scopes token extraction to the newest `=== DSH start ... ===` section. Startup reaches `web-ready` only when an acceptable loopback response **and** the current attempt's launch-token URL are both present. A transitional raw-loopback 404 is no longer treated as readiness; 2xx/3xx and the expected unauthenticated-root 401 remain valid endpoint-ready statuses.
- `ChatScreen` additionally observes main-frame local HTTP 401 and turns it into a visible retryable authentication error instead of leaving a silent black WebView. The persistent Home WebView and native official DSH Web baseline from Alpha.13 are otherwise unchanged.
- Regression coverage includes the exact stale-token ordering pattern from the target-device log: an old token followed by a new start marker and `web-ready` must yield no launch URL until the new process prints its own token.

## 4. Release artifact

Current manual-test package:

- file: `release/DeepSeek-Harness-Mobile-0.4.0-preview.2.apk`
- SHA-256: `7c9b3d44cc8bc386910cd822fa972f7372a1553f5dfca3a45f263c075a583516`
- size: `88,305,836` bytes
- source-freeze commit: `4f11518e5ff2e4e45fb1d71877f2dc772a86730a`
- update manifest: `release/update.json`

The current update manifest advertises:

- `versionCode`: 22
- `versionName`: `0.4.0-preview.2`
- download endpoint: `https://raw.githubusercontent.com/SteveBrilien/DeepSeek-Harness-Mobile/main/release/DeepSeek-Harness-Mobile-0.4.0-preview.2.apk`

Preview.2 is a feedback/manual-test candidate, not a stable release. It supersedes the non-publishable `0.4.0-preview.1` diagnostic baseline. The project uses the same stable development signing identity as earlier cover-install builds; do not replace that identity casually because doing so breaks seamless cover-install behavior.

## 5. Validation status

Verified on the Orange Pi ARM64 development host for Preview.2:

- `android_unit_test`: PASS for Recovery, Runtime Android and App/Robolectric coverage;
- `android_lint`: PASS (`289 actionable tasks`, `18 executed`, `271 up-to-date`);
- `mobile_context_contract`: PASS with Mobile Context `0.2.2` and DSH `0.1.5-rc.2`;
- `runtime_alpine_e2e`: PASS for embedded/offline and online fallback installation, authenticated DSH Web, bundled and source-built `node-pty`, real PTY behavior, compiler cleanup and old-profile mobile-UI reconciliation;
- fresh native MCP Chromium presentation test: PASS at exactly `360x708` with non-persistent browser state. Token authentication, first-run notice, 280 px sidebar, 32 px open-sidebar dismissal band, mobile Settings, Tokyo Night, workspace selection and active Composer all behave normally;
- DSH font-size stepper: PASS at behavior level. ARIA exposes `Increase font size` / `Decrease font size`, and a real click changed conversation font size `14 -> 15 px`; it was then restored to `14 px`;
- attachment pipeline: PASS in DSH Web. A real hidden `input[type=file]` accepted a 24-byte TXT test file, progressed from `Uploading…` to `TXT 24B`, and re-enabled Send;
- Console after authenticated interaction had no new application errors; the only captured 401 came from the deliberate pre-auth bare-root request used to obtain the token flow;
- clean-commit `android_debug`: PASS with task reproducibility reporting `dirty=false` and source commit `4f11518e5ff2e4e45fb1d71877f2dc772a86730a`;
- stable signing certificate verification: PASS; certificate SHA-256 remains `08:5C:7B:7D:EA:58:2F:F9:29:5B:25:0F:88:D0:E9:0E:94:7B:D2:93:AC:72:7A:82:40:A7:47:C8:C9:B2:49:07`;
- release APK is byte-identical to the clean-commit build artifact; all entries in `release/SHA256SUMS` verify successfully.

The remaining acceptance work is intentionally physical-device-only: confirm the Android system file chooser actually opens and returns its authorized `content://` URI into DSH, confirm the OriginOS soft keyboard raises the Composer rather than covering it, and confirm immersive three-button/system navigation hides and transiently reappears as intended. These are not claimed by Chromium.

## 6. Target-device validation sequence

Preview.2 must be **cover-installed** over the current app. Do not clear app data, delete `persistent/dsh-home`, or remove Recovery Vault state merely to make the test clean.

Recommended order on the Vivo/OriginOS Android 11 target:

1. cover-install `0.4.0-preview.2` and confirm existing Runtime/projects/sessions remain present;
2. cold-start Home and record the new Runtime startup timing (`this / last / average`) until DSH Web becomes ready;
3. open/close the DSH sidebar and right-side surfaces, confirming the edge handles do not cover native DSH content;
4. open DSH Settings, change font size once, verify the conversation content changes, then restore the preferred value;
5. tap **Add attachment**, confirm Android's system document chooser appears, choose a small file and confirm its attachment card reaches a ready size/type state;
6. focus the Composer and type several lines with the soft keyboard visible; confirm the active input remains above the keyboard and readable;
7. verify the system navigation bar is hidden during normal use and can be revealed transiently by the expected edge gesture without leaving a permanent bottom inset;
8. open Native Settings -> DSH Config and verify `settings.yaml` can be read, edited and saved; avoid putting secrets into bug-report screenshots;
9. switch Light/Dark/System/Tokyo Night and verify no full-width blue tap-highlight blocks return;
10. if anything regresses, copy the timestamped Runtime/WebView diagnostics before changing app data.

When ADB is available, the controlled `android_device_*` TaskProfiles remain preferred over ad-hoc host shell commands.

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
- MCP: `2.1.12-orange.1`;
- project branch: `main`;
- repository-scoped ED25519 GitHub Deploy Key authentication is configured under ignored `.mcp/ssh/` state; the private key must never be committed or copied into ordinary logs/docs;
- SSH host verification is pinned to GitHub's published Ed25519 host key and the repository deploy-key authentication/push path was verified on 2026-09-09;
- network available in controlled project tasks;
- JDK 17 HostCapability valid;
- Android SDK HostCapability valid;
- AAPT2 HostCapability valid;
- ADB HostCapability valid, but target device may not currently be connected.

MCP 2.1.12 exposes the `browser_*` tools directly to this ChatGPT connector. Alpha.16 release smoke used native `browser_start_session`, navigation, snapshot, click, screenshot, console and network-request tools against a real localhost DSH rc.2 process; the previous connector/schema exposure gap is closed.

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

See `docs/NEXT_ACTIONS.md` for the active queue. The immediate priority is physical Vivo/OriginOS cover-install acceptance of Preview.2, specifically the three behaviors Chromium cannot prove: Android document chooser handoff, IME/Composer geometry, and immersive system-navigation behavior. Do not reopen the older root-collapse/UI-composition investigation unless target evidence demonstrates a regression; the fresh 360x708 exact-composition Chromium gate and current Runtime E2E are green.

## 12. 2026-09-13 Alpha.15 Runtime-generation unification

- The shipped DSH generation is now centrally pinned in `config/dsh-runtime.properties`; Android BuildConfig, RuntimePins, seed build and ARM64 E2E consume the same DSH/pnpm/Mobile Context versions and seed hashes.
- Target DSH is `0.1.5-rc.2`; Mobile Context is `0.2.2` and depends on `@deepseek-ai/dsh-llm 0.1.5-rc.2`. Old rc.1 DSH/profile seed archives are removed from APK assets so releases cannot silently carry two Runtime generations.
- The target still has no usable Linux ARM64 `node-pty` prebuild. Preserve the SHA-pinned Node 24 ABI 137 ARM64/musl `pty.node` fast path and source-build fallback.
- Upgrading an installed older Runtime creates and verifies a Recovery Vault checkpoint before staging the inactive A/B slot. Existing persistent Web profiles preserve user fields/plugins; only the APK-owned Mobile Context package and its recursively resolved dependency graph are refreshed from the verified rc.2 profile seed.
- DSH Session persistence changed across the upgrade range. Do not treat A/B rootfs rollback alone as a complete user-data rollback; the pre-upgrade Recovery Vault checkpoint is part of the upgrade invariant.
- Runtime logs remain cumulative/rotating but every new startup/process line carries a readable local offset timestamp. UI log views still redact DSH Web token query values.
- The embedded WebView now has a separate token-redacted diagnostic log (`files/runtime/logs/dsh-webview.log`) containing provider/version, user agent, page navigation/finish, HTTP/network failures, JavaScript console output, renderer death and a DOM/crypto health probe. The Settings/Recovery screen can copy/open the current DSH token URL explicitly without persisting it in display logs.
- Official DSH Web remains the UI baseline. The dormant `dsh-client-ui-mobile` asset must not be re-enabled globally; phone-browser evidence shows upstream narrow-screen layout problems, but those are handled after render reliability with targeted non-destructive adaptation.


## 13. 2026-09-14 Alpha.16 PRoot-safe Recovery checkpoint

- Alpha.15 target-device evidence isolated the rc.1 -> rc.2 upgrade failure to the pre-upgrade Recovery Vault ZIP, before the inactive Runtime slot was staged or activated. The failing entry was a PRoot `link2symlink` `.l2s.*` backing object inside pnpm's CAS store.
- Recovery backup ZIP names now come from logical relative paths. Canonical paths remain only for containment/security checks, so multiple guest logical files no longer collapse onto one `.l2s.*` archive name.
- Rebuildable `dsh-home/.local/share/pnpm/store/**` and `.l2s.*` implementation files are excluded, and the pnpm store subtree is pruned during traversal instead of scanned file-by-file.
- Backup entry names have an explicit registry: an identical duplicate source is skipped, while a real logical archive-path collision fails with a diagnostic before `ZipOutputStream` emits an invalid archive.
- Checkpoints write a partial ZIP, verify the completed archive, then atomically publish it; failed partials are removed and stale partials are bounded.
- New Recovery unit tests cover link2symlink logical-path preservation, pnpm/L2S exclusion and collision detection. `android_unit_test` now includes `:core:recovery:testDebugUnitTest`.
- Native MCP Browser smoke against DSH `0.1.5-rc.2` at `390x844` verifies auth/onboarding/sidebar/Settings/workspace interaction and records screenshot, console and network evidence. The upstream Settings layout remains narrow/squeezed and is intentionally deferred to the P1 mobile-adapter phase.

## 14. 2026-09-16 Preview.2 mobile presentation checkpoint

The release freeze introduced for `0.4.0-preview.1` has been satisfied for host-side presentation and Runtime gates. The active APK-owned `dsh-client-ui-mobile` package is now `0.4.0-dshm.1`; it keeps official DSH semantics while applying bounded narrow-screen adaptation rather than replacing DSH-owned surfaces.

The exact candidate now passes Unit, Debug Build, Lint, Mobile Context, ARM64 Runtime E2E, stable signing, fresh `360x708` Chromium interaction and DSH file-upload behavior. The measured mobile presentation confirms the sidebar uses a 280 px panel with only a 32 px dismissal strip, Settings occupies a bounded 344x692 dialog at this viewport, font-size controls have correct accessible semantics and actually change state, Tokyo Night remains selectable, and the Composer/attachment card fit without the previous broad tap-highlight blocks.

Android-specific host integration is implemented but remains device-gated: `WebChromeClient` bridges DSH file inputs to a user-authorized system chooser, `adjustResize`/IME insets keep native chrome out of the keyboard path, and immersive system bars are requested without weakening file/network security. Physical OriginOS evidence is required before promoting beyond Preview.2.
