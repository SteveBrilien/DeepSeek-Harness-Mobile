# Changelog

All notable architectural and product changes should be recorded here.

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
