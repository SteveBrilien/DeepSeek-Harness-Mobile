# Changelog

All notable architectural and product changes should be recorded here.

## 2026-09-18 — DEVELOPMENT VERSION: 0.5.0-preview.1-dev (code 27; not released)

Advanced the **App versionName and Android versionCode** from `0.4.0-preview.6-dev`/26 to `0.5.0-preview.1-dev`/27 for the next development line. This version bump alone adds no attachment, drawer, page-animation or startup-performance feature. The product owner narrowed the active UI work to (1) image upload and preview including authorized recent photos, (2) inline three-source actions, (3) left drawer swipe open/close, (4) page transitions, and (5) startup performance. All other UI features are owner-confirmed for this scope, not independently certified as fully implemented. Termux SSH rescue is no longer a user-required release gate; standard non-destructive update safeguards and existing SSH isolation still apply.

No new release APK or OTA manifest is published by changing Gradle metadata. Historical `release/` files and previous release notes remain immutable. See `docs/handoff/2026-09-18-mobile-ui-implementation-checklist.md` V1.3.

## 2026-09-16 — CANDIDATE: Preview.3 mobile motion and startup visibility

Replaced Preview.2 sidebar dead-strip/display toggling with full-width transform-only drawer, integrated top-left panel button and native header collapse; added bounded rightbar, modal and tab transitions with reduced-motion support. Added IME/bottom chrome transition and OriginOS immersive-sticky re-hide. Instrumented startup stages and added owned-generation warm-reuse fast path; corrected a token lookup regression by writing a new DSH start marker only on the cold path and added regression coverage. Bumped candidate app to `0.4.0-preview.3` (code 23), mobile UI bundle to `0.4.1-dshm.1`. Browser 360x708, Unit and Alpine E2E pass; physical Vivo keyboard/nav/startup timing validation remains required. See `docs/handoff/2026-09-16-ui-motion-and-startup-preview3.md`.

## [0.1.0-dev] - 2026-09-07

### Added
- Initial OrangePi-hosted project scaffold.
- Repository-wide `AGENTS.md` maintenance contract.
- Architecture baseline for Native Recovery Core, local DSH runtime, privilege providers, persistent data and remote runtimes.
- DSH compatibility policy preserving official Web Client and Workspace/Session semantics.
- Mobile Project layer with many-to-many Project↔Session model and one Primary Project per Session.
- `@Project` reference design using stable IDs.
- Native Files/Text Editor/Recovery Terminal requirements.
- Data durability, Trash, non-destructive recovery and OrangePi mirror design.
- Plugin compatibility model with optional Android Mobile Extensions and Safe Mode.
- A/B runtime update and self-repair policy.
- Development rules and phased roadmap.

### Decisions
- Local Wireless ADB selected as preferred non-root enhanced privilege path.
- Shizuku retained as optional compatibility fallback.
- Root retained as optional highest-privilege provider.
- DSH session event log remains authoritative for chat/session content.
- Chat UI remains official DSH Web Client except documented mobile compatibility fixes.

### Known infrastructure issue
- Codex MCP Dev file operations are active on `/home/orangepi/workspace`, but `run_shell` is currently failing; Android toolchain bootstrap/build verification is therefore pending.

## 2026-09-09T06:51:01.149663Z — UPDATE: Unify terminal execution domains and activate cache-stable Mobile Context v2

Implemented one Terminal surface with Auto/Linux/Android/ADB modes. Linux Runtime commands now execute through the managed PRoot A/B runtime; Android Local continues to use the independent App-UID recovery shell; ADB remains an explicit unavailable provider until Wireless ADB transport is integrated and privileged commands are never silently downgraded. Added AndroidMobileEnvironmentContextProvider as the shared capability source for Linux/Android Local/ADB/Root availability and authorization. Upgraded @dsh-mobile/dsh-mobile-context to v0.2.0/mobile-context-v2, preserving a byte-stable process-level systemPrompt.section while documenting automatic least-privilege routing. Added a durable mobile_context_contract TaskProfile and contract test; test passed. Added ADR 0007. Android Debug build succeeded after the changes.

Files:
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/TerminalScreen.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/RuntimeShell.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidRuntimeManager.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/RuntimeControlPlane.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidMobileEnvironmentContextProvider.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/MobileContextSnapshotWriter.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/NativeRuntimeInstaller.kt`
- `core/runtime-android/src/main/assets/runtime/dsh-mobile-context/lib/index.js`
- `core/runtime-android/src/main/assets/runtime/dsh-mobile-context/package.json`
- `scripts/test-mobile-context.mjs`
- `docs/adr/0007-single-terminal-multiple-execution-domains.md`

## 2026-09-09T07:17:22.801293Z — RELEASE: Prepare 0.3.0-alpha.2 manual-test release

Finalized the 0.3.0-alpha.2 manual-test release candidate. Mobile Context Bundle is 0.2.1 and now uses a byte-stable process systemPrompt section, one plugin/snapshot bootstrap for fresh ordinary Sessions, and plugin/notice helpers for runtime capability changes. Contract mock passes. Android Debug and Android Lint both pass. Release APK was copied into release/ with SHA-256 e35ebb812adbc4f894b8d86300b53ee88859625d1d126314527403d7e6c78f89 and update.json points to the GitHub raw HTTPS location. This alpha is feedback-gated and does not claim completed device/runtime E2E validation.

Files:
- `core/runtime-android/src/main/assets/runtime/dsh-mobile-context/lib/index.js`
- `core/runtime-android/src/main/assets/runtime/dsh-mobile-context/package.json`
- `scripts/test-mobile-context.mjs`
- `app/build.gradle.kts`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.2.apk`
- `release/update.json`
- `release/SHA256SUMS`
- `README.md`
- `docs/DSH_COMPATIBILITY.md`
- `docs/adr/0006-cache-stable-mobile-environment-context.md`
- `docs/adr/0007-single-terminal-multiple-execution-domains.md`

## 2026-09-09T09:10:15.553635Z — UPDATE: Polish first-run UX and harden Runtime installation

Refined onboarding hierarchy and bottom actions, corrected launcher icon safe-area cropping, added official DeepSeek Harness fish branding, animated page/action feedback, Runtime install progress/log/elapsed/ETA telemetry with notification sync, multi-mirror Alpine/npm probing and automatic fallback, reusable local/Recovery-Vault rootfs cache discovery, existing Runtime version inventory and non-destructive A/B update prompt, and foreground-service exception containment so install failures are surfaced instead of crashing the app. Version bumped to 0.3.0-alpha.3 / code 4. Validation on the development host: android_debug succeeded, android_lint succeeded, stable signing certificate verification succeeded, git diff check and new XML parse checks passed. Device E2E is still pending because no ADB device is currently connected.

Files:
- `app/build.gradle.kts`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/runtime/RuntimeForegroundService.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/runtime/RuntimeInstallTelemetry.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/AppShell.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/Branding.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/DshComponents.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/OnboardingScreen.kt`
- `app/src/main/res/drawable/ic_deepseek_fish_mark.xml`
- `app/src/main/res/drawable/ic_dsh_fish_foreground.xml`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidRuntimeManager.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/NativeRuntimeInstaller.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/RuntimeControlPlane.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/RuntimeInstallModels.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/RuntimePins.kt`

## 2026-09-09T09:14:17.645305Z — RELEASE: Package 0.3.0-alpha.3 manual-test release

Packaged the clean-commit Android debug artifact from commit 95cc9839a4427168e9b8b9f45a6d32b2d0d9d7f2 as DeepSeek-Harness-Mobile-0.3.0-alpha.3.apk. The clean build succeeded, Android lint succeeded, stable signing certificate verification succeeded, release SHA-256 is a191a6988820a831b893e0338a736155ddbd2dc29988f09a59748eb8125054cb, and release/update.json now advertises versionCode 4 / versionName 0.3.0-alpha.3. This remains a manual-test release because no ADB device is currently connected for final device/runtime E2E validation.

Files:
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.3.apk`
- `release/SHA256SUMS`
- `release/update.json`
- `README.md`

## 2026-09-09T09:20:31.294706Z — DOCS: Add current project handoff and refresh active next-actions

Added docs/HANDOFF.md as the canonical continuation handoff for 0.3.0-alpha.3, replacing stale assumptions with current architecture, release artifact, Runtime installer/mirror/cache/crash-containment state, validation status, target-device E2E sequence, security invariants, important file ownership, and current infrastructure. Rewrote docs/NEXT_ACTIONS.md around target OriginOS alpha.3 validation, resource reuse/non-destructive A/B update tests, installer resilience, and release promotion. Updated README to direct future agents to HANDOFF and NEXT_ACTIONS first.

Files:
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`
- `README.md`
- `CHANGELOG.md`

## 2026-09-09T11:11:43.091059Z — DOCS: Record verified GitHub deploy-key push path

Updated docs/HANDOFF.md to reflect the repository-scoped ED25519 GitHub Deploy Key, SSH-over-443 remote, strict host verification using GitHub's published Ed25519 host key, and successful authenticated push verification. Private key remains only in ignored .mcp/ssh state and must never be committed or logged.

Files:
- `docs/HANDOFF.md`
- `.mcp/ssh/github_deploy_ed25519`
- `.mcp/ssh/known_hosts`

## 2026-09-09T12:09:26.181198Z — SECURITY: Harden release validation path and record unsafe legacy download root

Revalidated alpha.3 after GitHub/deploy-key cutover: android_debug, android_lint and android_signing_verify all succeed. Fixed the Mobile Context contract runner so it no longer depends on the OrangePi distro's obsolete Node 12; it now bootstraps pinned Node 24.18.1 into ignored .mcp/tools state after SHA-256 verification and the contract passes. The former dsh.wmy-cloud.cn/dsh-mobile-download alpha.3 URL returned 404; inspection of the directory index showed the Python SimpleHTTP document root is the Azure user's home directory and publicly exposes private-directory names including .ssh/. To avoid publishing through that unsafe root, release/update.json now points alpha.3 to the already-tracked GitHub raw APK while Azure static hosting is remediated. No private directory contents were read.

Files:
- `scripts/run-mobile-context-contract.sh`
- `release/update.json`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`
- `CHANGELOG.md`

## 2026-09-09T16:19:45.485996Z — RELEASE: Prepare 0.3.0-alpha.4 manual-test release

Promoted the Runtime/terminal/UI hardening work to 0.3.0-alpha.4 (versionCode 5). The clean Alpine ARM64 Runtime E2E passes with Node 24.18.1, DSH 0.1.2-rc.1, koffi, source-rebuilt node-pty, a real PTY shell command, Mobile Context integration, and authenticated DSH Web token exchange. Runtime downloads now support resumable partial files, retry/fallback and stalled-install detection; DSH uses an Alpine/musl-safe launcher and tokenized initial WebView URL with log redaction. UI was simplified toward the DeepSeek Harness visual language, launcher icon safe area was reduced, and Terminal was made terminal-first with recent/pinned command history behind a history surface. Final alpha.4 android_debug, android_lint, android_signing_verify and mobile_context_contract all passed on the development host. APK SHA-256: 043c9ecc64784f09da1782607c8b8e25df7a1db5f55e637e16dccfb25c95f109. Physical OriginOS device validation remains intentionally manual and is not claimed complete.

Files:
- `app/build.gradle.kts`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/AppShell.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/Branding.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/ChatScreen.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/DshIcons.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/OnboardingScreen.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/TerminalScreen.kt`
- `app/src/main/res/drawable/ic_dsh_fish_foreground.xml`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidRuntimeManager.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/NativeRuntimeInstaller.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/RuntimeControlPlane.kt`
- `scripts/test-runtime-alpine-e2e.sh`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.4.apk`
- `release/SHA256SUMS`
- `release/update.json`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`

## 2026-09-09T17:02:20.364279Z — RELEASE: Prepare 0.3.0-alpha.5 after OriginOS node-pty install failure

Device feedback from alpha.4 showed Runtime installation still failed at 87% while validating node-pty: the Android PRoot source rebuild path could complete without leaving a loadable pty.node. Alpha.5 now bundles the verified Alpine 3.24 / arm64 / musl Node-24 ABI 137 pty.node (79 KB, SHA-256 3e9cb29670c2cac1f7d54302099af8b0f998b9acc79891666b3136db575f18c3) and installs it directly after DSH npm install. The previous source-build path remains only as an ABI-mismatch fallback. Runtime logs now retain up to 250 lines, are text-selectable, and expose a one-tap copy action for device bug reports. Validation: clean runtime_alpine_e2e passed both bundled fast path and source fallback, real PTY, Mobile Context and authenticated DSH Web; mobile_context_contract passed; final alpha.5 android_debug and android_lint passed; stable signing certificate verified. Physical OriginOS alpha.5 regression remains manual.

Files:
- `app/build.gradle.kts`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/runtime/RuntimeInstallTelemetry.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/OnboardingScreen.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/NativeRuntimeInstaller.kt`
- `core/runtime-android/src/main/assets/runtime/native-modules/node24-arm64-musl/pty.node`
- `scripts/test-runtime-alpine-e2e.sh`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.5.apk`
- `release/SHA256SUMS`
- `release/update.json`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`

## 2026-09-10T03:01:10.048872Z — RELEASE: 0.3.0-alpha.6: DSH startup recovery, immersive installer, swipe onboarding, exact upstream wordmark

Promoted the manual-test build to versionCode 7. Runtime installation now preserves a verified slot when DSH Web startup is delayed, exposes retry-start without reinstall, extends loopback readiness polling to 180 seconds, and appends DSH startup diagnostics to the copyable install log. The Runtime installer uses a fixed immersive viewport with an independently scrollable log, and onboarding uses a synchronized five-page horizontal pager. DeepSeek Harness branding no longer uses Android text/font reconstruction: whale, deepseek lettering, HARNESS badge and HARNESS glyph outlines are mechanically synchronized from the official upstream BrandWordmark.tsx 182x24 artwork, blob a9df992179c7cc8f0792142f2dde66dbbb3b5464; all 10 primary paths and 7 inverted glyph paths were checked for exact equality. Final android_debug, android_lint, stable signing verification, XML/resource validation, release manifest SHA validation and mobile_context_contract pass. Runtime Alpine ARM64 E2E had already passed on the same alpha.6 runtime code before the branding-only final change.

Files:
- `app/build.gradle.kts`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/runtime/RuntimeForegroundService.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/runtime/RuntimeInstallTelemetry.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/Branding.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/OnboardingScreen.kt`
- `app/src/main/res/drawable/ic_deepseek_harness_wordmark_primary.xml`
- `app/src/main/res/drawable/ic_deepseek_harness_wordmark_inverted.xml`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidRuntimeManager.kt`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`
- `docs/adr/0005-target-sdk-28-for-local-runtime-exec.md`
- `release/update.json`
- `release/SHA256SUMS`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.6.apk`

## 2026-09-10T07:57:49.817579Z — FIX: 0.3.0-alpha.7: fix Android localhost startup gate and make Runtime reuse fast

Target-device alpha.6 evidence showed the verified Runtime and DSH process were healthy enough to emit `dsh web: http://127.0.0.1:3080/?token=...`, while app readiness polling stayed false for 180 seconds. The root cause is the Android network-security boundary: cleartext was explicitly allowed for `localhost`, but alpha.6 probed numeric `127.0.0.1`; the probe converted the resulting exception into `false`. Alpha.7 probes `http://localhost:3080/`, normalizes the token launch URL and any local WebView redirect to localhost, keeps DSH bound to 127.0.0.1, allows both loopback spellings in the local-only network config, and records the last probe result in any timeout diagnostic. Exact-match active Runtime is now reused directly instead of staging another slot, so the user's already verified alpha.6 Runtime should skip Alpine extraction, apk packages, pnpm and DSH reinstall. A persistent Recovery-Vault npm cache accelerates genuine reinstall/update. Alpine speed probes use bounded normal GET instead of a tiny Range request, fixing false 403 results observed for TUNA/Aliyun; host checks returned HTTP 200 for both with the new request shape. Version promoted to 0.3.0-alpha.7 / versionCode 8. Android debug build, lint, stable signing, Mobile Context contract, and clean Alpine ARM64 Runtime E2E all pass; target OriginOS verification remains manual.

Files:
- `app/build.gradle.kts`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/ChatScreen.kt`
- `app/src/main/res/xml/network_security_config.xml`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidRuntimeManager.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/NativeRuntimeInstaller.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/RuntimeControlPlane.kt`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`
- `release/update.json`
- `release/SHA256SUMS`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.7.apk`

## 2026-09-10T16:59:26.098242Z — RELEASE: 0.3.0-alpha.8: accelerate Runtime install, harden loopback startup and encrypted SSH recovery

Promoted the manual-test candidate to versionCode 9. The normal fresh-install path now uses SHA-pinned embedded ARM64/musl seeds for DSH 0.1.2-rc.1 + pnpm 12.3.4 and the validated DSH Web/Mobile Context profile, eliminating the target-device 523-package DSH npm download from the happy path while retaining online npm as fallback. DSH startup now probes literal 127.0.0.1:3080 with a raw IPv4 HTTP socket before URLConnection fallbacks, preserves the literal token URL, restarts stale app-owned processes, and emits Android-side plus Runtime-side probe diagnostics on timeout. Valid active Runtime slots are reused rather than reinstalled. Recovery checkpoints exclude .ssh and rebuildable caches; SSH identity now has separate AES-GCM encrypted backup/restore. Terminal hides the unfinished ADB mode and AUTO routes implemented Linux/Android domains only. Narrow-screen onboarding actions wrap, adaptive launcher resources were restored, and the official DeepSeek Harness wordmark remains exact upstream vector geometry. Validation: runtime_alpine_e2e PASS; mobile_context_contract PASS; android_debug PASS; android_lint PASS; android_signing_verify PASS; strict seed/tracked sensitive-path audit PASS. APK SHA-256 75f4ed5f3320bfe11354f17d0b5d9ecc6830f6befc315091a108ccc6215cfa7e, size 80,364,450 bytes. Physical OriginOS validation remains manual because no ADB device is connected.

Files:
- `app/build.gradle.kts`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/runtime/RuntimeForegroundService.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/ChatScreen.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/OnboardingScreen.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/TerminalScreen.kt`
- `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `core/recovery/src/main/kotlin/com/stevebrilien/dshmobile/core/recovery/RecoveryBackupManager.kt`
- `core/recovery/src/main/kotlin/com/stevebrilien/dshmobile/core/recovery/SecretVaultManager.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidRuntimeManager.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/NativeRuntimeInstaller.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/RuntimeControlPlane.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/RuntimePins.kt`
- `core/runtime-android/src/main/assets/runtime/seeds/dsh-0.1.2-rc.1-node24-arm64-musl.tgz`
- `core/runtime-android/src/main/assets/runtime/seeds/web-profile-0.1.2-rc.1-mobile-context-0.2.1.tgz`
- `scripts/build-embedded-dsh-seed.sh`
- `scripts/test-runtime-alpine-e2e.sh`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.8.apk`
- `release/SHA256SUMS`
- `release/update.json`

## 2026-09-10T18:41:58.399943Z — RELEASE: 0.3.0-alpha.9: restore approved mobile DSH shell and Cordis-native responsive UI

Promote the approved mobile UI baseline to alpha.9/versionCode 10. Home hosts DSH Web at full usable phone width with wide-viewport/overview scaling disabled; the previous Android-side DOM/CSS compatibility injection is removed. The app vendors dsh-client-ui-mobile 0.1.9 under MIT and installs it through DSH/Cordis alongside Mobile Context, with the embedded Web profile regenerated and Runtime E2E verifying the mobile plugin and authenticated Web client. The native shell is restored to Home / Workspace / Terminal / Settings; Workspace contains Projects/Files, Settings is grouped into finite sections/subpages, and theme controls wrap on narrow screens. Opening the app with a reusable Runtime proactively starts DSH. Embedded DSH/pnpm seeds, A/B Runtime recovery and encrypted recovery hardening from alpha.8 remain. Final alpha.9 android_debug, android_lint, stable signing verification and mobile_context_contract all pass; the Runtime Alpine ARM64 E2E also passes with the vendored mobile UI plugin and authenticated Web client. Physical OriginOS inspection remains manual.

Files:
- `app/build.gradle.kts`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/AppShell.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/ChatScreen.kt`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/SettingsScreen.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/NativeRuntimeInstaller.kt`
- `core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile/`
- `core/runtime-android/src/main/assets/runtime/seeds/dsh-0.1.2-rc.1-node24-arm64-musl.tgz`
- `core/runtime-android/src/main/assets/runtime/seeds/web-profile-0.1.2-rc.1-mobile-context-0.2.1.tgz`
- `THIRD_PARTY_NOTICES.md`
- `docs/UI_BASELINE.md`
- `docs/adr/0008-dsh-cordis-mobile-layout.md`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.9.apk`
- `release/SHA256SUMS`
- `release/update.json`

## 2026-09-10T19:23:11.876145Z — RELEASE: 0.3.0-alpha.10: fix OriginOS embedded-seed hardlinks and DSH startup rc127 diagnostics

Target-device alpha.9 logs identified two concrete failures. Embedded DSH extraction called Android Os.link for tar hard-link entries and OriginOS returned EACCES, so the installer fell back to the slow 523-package npm path. Alpha.10 materializes every tar hard-link entry as an ordinary file copy with archived mode/timestamp, keeping the verified alpha.9 seed bytes and online fallback. Startup then failed with Runtime command failed (127) before dsh-web.log existed; alpha.10 creates the log before preflight, replaces redundant full runtime command verification with host-side start prerequisites, skips PRoot/plugin migration when mobile plugin markers are already current, launches the pinned DSH Node entrypoint directly, and records startup stages plus exit code. Runtime ARM64 E2E passes with 2 DSH-seed and 181 Web-profile hardlinks materialized, direct-Node DSH Web auth, online/source-build fallbacks, PTY and Mobile Context. Final android_debug, android_lint, stable signing and mobile_context_contract pass. Physical OriginOS verification remains manual because no ADB device is connected. APK SHA-256 ffc7e1f772feda2a1ee99c4b5fac5f039eb240dfe0ea9457734a7a700da15a8c; size 86,641,657 bytes.

Files:
- `app/build.gradle.kts`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/NativeRuntimeInstaller.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidRuntimeManager.kt`
- `scripts/test-runtime-alpine-e2e.sh`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.10.apk`
- `release/SHA256SUMS`
- `release/update.json`

## 2026-09-11T01:00:46.840035Z — RELEASE: 0.3.0-alpha.11: fix false Runtime shell missing on OriginOS

Alpha.10 target-device logs showed the startup preflight repeatedly failing with `Runtime shell is missing` even though slot A had completed installation and verification. Root cause: Alpine `/bin/sh` is an absolute guest symlink (`/bin/sh -> /bin/busybox`), while Android-side `File.isFile()` follows that link in the Android host namespace instead of the PRoot guest rootfs and therefore reports a false negative. Alpha.11 changes Runtime prerequisite, inventory and health rootfs-node checks to no-follow `Os.lstat()` semantics. The ARM64 Runtime E2E now explicitly covers the host-vs-guest absolute-symlink case and still passes embedded seed hardlink materialization, native modules, PTY, Mobile Context/mobile UI and authenticated DSH Web. DSH retry telemetry also resets stale install elapsed/source fields so a retry no longer displays the original npm mirror and multi-hour install duration. Final android_debug, android_lint, stable signing, mobile_context_contract and runtime_alpine_e2e all pass. Existing slot A should be reused: cover-install alpha.11 and retry startup without clearing app data or reinstalling Runtime. Physical OriginOS startup remains manual because no ADB device is connected. APK SHA-256 ab2588ffdae8b2762f219c6788173e017bc37d541719ece247481b2fec38b2a1; size 86,641,657 bytes.

Files:
- `app/build.gradle.kts`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/runtime/RuntimeInstallTelemetry.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidRuntimeManager.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/NativeRuntimeInstaller.kt`
- `scripts/test-runtime-alpine-e2e.sh`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.11.apk`
- `release/SHA256SUMS`
- `release/update.json`

## 2026-09-12T16:18:33.581147Z — RELEASE: Package 0.3.0-alpha.12 deterministic Runtime startup release

Rebuilt Runtime startup as a six-stage observable pipeline; moved APK-owned mobile plugin migration to offline/atomic Android-host reconciliation; reset stale startup telemetry and propagate service failures directly to Chat. Validation passed: mobile_context_contract, Robolectric/API 30 android_unit_test, ARM64 runtime_alpine_e2e including old-profile offline reconciliation and real authenticated DSH Web startup, android_lint, clean-commit android_debug, and stable signing verification. APK SHA-256 f7c96a3f8381992e0b8a1984f5c7b6396b5196d9b0cea9e8c7254245c0357701, size 86,641,657 bytes. No physical Android device was connected, so OriginOS cover-install remains manual.

Files:
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.12.apk`
- `release/SHA256SUMS`
- `release/update.json`
- `docs/INSTALLATION_RUNTIME_RESEARCH.md`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`


## 2026-09-12 — RELEASE: 0.3.0-alpha.13 native DSH Web baseline

- Keep the Home WebView alive across native bottom-tab navigation, eliminating the Home reconstruction/reload loop.
- Disable the active `dsh-client-ui-mobile` override that hid official DSH sidebar/header/settings controls; retain its files only as a dormant APK-owned asset for later UI redesign.
- Migrate existing alpha.12 Web profiles offline while preserving user profile fields and active Mobile Context integration.
- Validation: Robolectric unit tests, mobile-context contract, ARM64 Runtime E2E, Android lint, clean-commit Android build and stable signing verification PASS.
- Release: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.13.apk`, SHA-256 `1c59410aea85f67929a207cbd57bbe725a24e74bd858bf0e10acc616116ac743`, size `86,641,657` bytes. OriginOS visual/interaction regression remains a manual device check.


## 2026-09-12 — RELEASE: 0.3.0-alpha.14 bind DSH Web readiness to the current auth token

- Target-device Alpha.13 evidence showed DSH Web itself was alive while Home remained blank. The failure was the token handoff: readiness could be reported before the current process printed its launch token, while cumulative-log parsing could reuse an older process token.
- Token discovery is now scoped to the newest DSH start marker; startup is ready only after both an acceptable loopback HTTP response and the current process token exist. Transitional HTTP 404 no longer counts as ready.
- WebView main-frame local HTTP 401 now becomes an explicit retryable authentication error instead of a silent blank surface.
- Validation: Android/Robolectric unit tests including the stale-token race, Mobile Context contract, ARM64 Runtime E2E with real token-to-cookie authentication, Android lint, clean-commit Android build and stable signing verification PASS.
- Release: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.14.apk`, SHA-256 `0b551ca46db034eeeed082e2547cc80945c6729be6d03ddff3a138a3c73c366b`, size `86,658,045` bytes. OriginOS Alpha.14 visual/auth validation remains manual because no ADB device is attached.

## 2026-09-13T16:55:39.002509Z — RELEASE: Prepare 0.3.0-alpha.15 with DSH 0.1.5-rc.2 and WebView diagnostics

Promoted the Alpha.15 manual-test candidate from clean source commit 1ba59cb9c7791a549396b65424ad698f1ae0b2fa. DSH/runtime/profile versions are unified on 0.1.5-rc.2 with Mobile Context 0.2.2; existing rc.1 installations use a Recovery-Vault-checkpointed A/B upgrade path that replaces only APK-managed dependencies. Added readable runtime timestamps, token-redacted WebView provider/navigation/console/renderer/DOM diagnostics, and explicit copy/open-current-DSH-URL actions. Validation passed Android/Robolectric unit tests, Mobile Context contract, ARM64 Alpine Runtime E2E, Android lint, a 390x844 MCP Chromium UI smoke covering onboarding/sidebar/Settings/workspace interactions with zero console/page/request errors, clean-commit Android build, byte-identical release packaging, and stable signing. Release APK SHA-256 is b8b5e56568320d9489be3ce294a340ef25c161c0e6d998c7dcdc7e64bef38d88, size 88,108,265 bytes. Physical OriginOS WebView rendering remains manual validation.

Files:
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.15.apk`
- `release/SHA256SUMS`
- `release/update.json`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`
- `config/dsh-runtime.properties`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/DshWebViewDiagnostics.kt`
- `core/runtime-android/src/main/assets/runtime/seeds/dsh-0.1.5-rc.2-node24-arm64-musl.tgz`
- `core/runtime-android/src/main/assets/runtime/seeds/web-profile-0.1.5-rc.2-mobile-context-0.2.2.tgz`

## 2026-09-14T04:04:52.152163Z — RELEASE: Prepare 0.3.0-alpha.16 with PRoot-safe Recovery checkpoints

Alpha.16 fixes the target-device rc.1 -> rc.2 upgrade checkpoint failure caused by canonicalizing PRoot link2symlink logical files onto the same .l2s backing path. Recovery ZIP entries now preserve logical relative paths, pnpm CAS/.l2s implementation data is pruned, real archive-path collisions are diagnosed, partial ZIPs are verified before atomic publication, and failed/stale partials are cleaned. Recovery regression tests are part of android_unit_test. Validation is green for Recovery/Runtime/App unit tests, Mobile Context contract, Android lint, ARM64 Alpine Runtime E2E with DSH 0.1.5-rc.2, native MCP browser smoke at 390x844, clean source commit build 2f31d54c06ec055530182fa4fd588990623e8e99 and stable signing. Physical OriginOS cover-install remains manual; upstream rc.2 Settings content is still visually squeezed on a 390px viewport and stays in P1 UI adaptation.

Files:
- `core/recovery/src/main/kotlin/com/stevebrilien/dshmobile/core/recovery/RecoveryBackupManager.kt`
- `core/recovery/src/test/kotlin/com/stevebrilien/dshmobile/core/recovery/RecoveryBackupManagerTest.kt`
- `app/build.gradle.kts`
- `release/DeepSeek-Harness-Mobile-0.3.0-alpha.16.apk`
- `release/update.json`
- `release/SHA256SUMS`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`

## 2026-09-14T10:03:35.901415Z — DOCS: Freeze releases and document WebView rendering/debug test failures

Added a debug baseline for 0.4.0-preview.1. It separates the Android WebView root-collapse failure from the old broad mobile-layout regressions, records the stale 13080 Chromium session/profile mismatch, identifies that the current Alpine E2E stops at HTTP/profile contract rather than rendered presentation, catalogs Runtime/WebView/log/navigation architecture debt, and defines a release gate plus the research backlog for the next conversation. No new APK should be published until fresh Chromium, WebView-equivalent, and true-device presentation gates pass for the exact candidate.

Files:
- `docs/debug/2026-09-14-webview-rendering-debug.md`

## 2026-09-14T10:40:19.751806Z — DOCS: Complete Android WebView presentation technical-route research

Research-only pass. Documented pinned DSH/Cordis client-plugin lifecycle, DSH boot/combo revision semantics, Android System WebView 151 viewport evidence, an origin-scoped WebMessageListener presentation handshake, CDP + Espresso-Web observability, and a real-WebView Orange Pi strategy centered on conditional ARM64 Cuttlefish userdebug plus mandatory physical Vivo OEM acceptance. Selected DSH compat-plugin ownership with measured geometry; rejected Chromium-as-WebView, post-load Android DOM patching, semantic-version-only generations, and immediately=true as a standalone fix. No product repair implementation was performed.

Files:
- `docs/research/2026-09-14-webview-presentation-technical-route.md`


## 2026-09-16 — RELEASE: 0.4.0-preview.2 mobile interaction and Settings repair candidate

- Promote the post-diagnostic candidate to `versionCode 22` / `0.4.0-preview.2`; `0.4.0-preview.1` remains the frozen rendering-debug baseline and is not republished.
- Restore Android WebView file upload by bridging DSH `<input type=file>` through a bounded native chooser while keeping `file://` disabled and accepting only user-authorized content URIs.
- Make the chat host IME-aware and immersive: composer content can resize above the soft keyboard, system navigation is hidden by default, and native chrome yields to transient system bars.
- Simplify native Settings into task-oriented DSH Config / Runtime / Backup & Restore / App Update / Advanced Maintenance surfaces, including an atomic in-app `settings.yaml` editor with size/NUL safeguards.
- Upgrade the APK-owned mobile UI package to `0.4.0-dshm.1`: reduce the open-sidebar dismissal band to 32 px, replace overlapping hamburger affordances with a bounded edge handle, suppress the handle while drawers/settings are open, remove mobile blue tap-highlight blocks, and repair DSH font-size stepper semantics/layout.
- Add Runtime startup timing telemetry and tighten Web readiness polling from 1000 ms to 250 ms without changing the overall startup timeout.
- Validation for the exact runtime/UI code before this metadata-only version bump: Android Unit PASS, Android Debug PASS, Android Lint PASS, Mobile Context Contract PASS, ARM64 Alpine Runtime E2E PASS with authenticated DSH Web and old-profile reconciliation, fresh 360×708 Chromium behavior PASS for sidebar/settings/font-size/Tokyo Night, and a real DSH attachment upload probe PASS. Physical Vivo/OriginOS checks remain manual for Android system file chooser, IME raise behavior and immersive navigation gestures.


## 2026-09-16 — RELEASE: Package 0.4.0-preview.2 manual-test APK

Packaged the exact clean-source Preview.2 candidate from commit `4f11518e5ff2e4e45fb1d71877f2dc772a86730a`. Clean-commit `android_debug` reports `dirty=false`; stable signing is unchanged; the release APK is byte-identical to the build artifact. `release/DeepSeek-Harness-Mobile-0.4.0-preview.2.apk` is `88,305,836` bytes with SHA-256 `7c9b3d44cc8bc386910cd822fa972f7372a1553f5dfca3a45f263c075a583516`, and the full release hash manifest verifies. Host-side Unit/Lint/Mobile Context/Runtime E2E plus fresh 360x708 Chromium and real DSH file-upload behavior are green. Physical Vivo/OriginOS acceptance remains manual for system chooser handoff, IME/Composer geometry and immersive navigation behavior.

Files:
- `release/DeepSeek-Harness-Mobile-0.4.0-preview.2.apk`
- `release/SHA256SUMS`
- `release/update.json`
- `docs/HANDOFF.md`
- `docs/NEXT_ACTIONS.md`

## 2026-09-16T07:41:39.678385Z — DOCS: Preview.3 candidate verification checkpoint; physical OriginOS gate still pending

2026-09-16 continuation: android_debug task-android_debug-914d2d073d31430c9426 PASS (dirty-workspace candidate, not clean-release build); app-debug.apk 88,305,836 bytes, SHA-256 25bbb780d38f6a61d615250958c495db0155302e0d91a1d2af9c41b832880d84, versionName 0.4.0-preview.3/versionCode 23. android_unit_test task-android_unit_test-465e1ffa01a046d9ae29 PASS; android_lint task-android_lint-869ef91986ff43e9aa96 PASS (289 tasks, exit 0); android_signing_verify task-android_signing_verify-35b3eb11466645d0a616 PASS, stable certificate. Pinned Node24 JavaScript syntax, mobile-ui-policy and git diff --check PASS. Authenticated 360x708 Chromium inspection: sidebar fills 359/360px, native collapse, Settings open/close and session search 275px after animation, console messages empty. Earlier runtime_alpine_e2e task-runtime_alpine_e2e-f55f77ce2ca84b4d8084 PASS, but subsequent reruns failed independently on npm 'Exit handler never called!' and concurrent test temporary-directory deletion; do not report all reruns green. adb_devices found no connected device, so cold/warm startup 26s comparison, physical IME bounce, OriginOS nav recovery and chooser remain unverified. No clean-source promotion, Git push, public update.json change or publication performed.

Files:
- `docs/handoff/2026-09-16-ui-motion-and-startup-preview3.md`
- `app/build.gradle.kts`
- `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/AppShell.kt`
- `core/runtime-android/src/main/kotlin/com/stevebrilien/dshmobile/core/runtimeandroid/AndroidRuntimeManager.kt`
