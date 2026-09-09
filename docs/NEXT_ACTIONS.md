# Immediate Next Actions

## Infrastructure status

Completed / verified:

1. Codex MCP Dev `run_shell` works on the OrangePi aarch64 workspace.
2. A working ARM64 Android build strategy and toolchain baseline have been identified and pinned for this repository.
3. DeepSeek Harness Mobile uses JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20, API 35 and an ARM64 AAPT2 override for its initial build baseline.
4. Gradle Wrapper files and `scripts/build-orangepi.sh` are present.
5. Manifest parsing and build-script syntax checks pass.

Open infrastructure items:

1. Restore or implement an audited host Android build bridge for this repository. Normal `run_shell` intentionally cannot access `/home/orangepi/.local/...` because of bubblewrap isolation.
2. Execute the first `:app:assembleDebug` through the host build bridge and fix any compile failures.
3. Attach/push the local directory to `https://github.com/SteveBrilien/DeepSeek-Harness-Mobile`. Do not bypass TLS verification to work around connector or sandbox certificate issues.
4. Add Azure/CI later as an independent verification builder, not as a prerequisite for each APK.

## First executable milestone — V0.2 Native App Shell

- bottom navigation: Chat / Projects / Files / Terminal / More;
- structured logger;
- Recovery Center screen;
- native file browser MVP;
- native text editor MVP;
- Recovery Terminal MVP that does not depend on DSH;
- persistent Project registry skeleton;
- runtime health placeholder.

Acceptance test: disable or remove the DSH runtime directory and confirm the app still opens Files, Editor, Recovery and diagnostics.

## Second executable milestone — V0.3 Local DSH Runtime

- choose and pin the Android arm64 Linux-userspace mechanism;
- package rootfs/runtime manifest;
- A/B slot manager;
- Node + DSH installation;
- process supervision and rotating logs;
- health endpoints;
- host the official DSH Web Client in Chat;
- provide mobile compatibility CSS/JS as version-gated plugins or compatibility layers rather than rewriting the DSH client;
- implement in-app update/install through isolated updater components.

## Third milestone — V0.4 Project Context

- Project registry and import-existing-folder;
- Project↔Session relation metadata;
- one Primary Project + Attached Projects;
- stable `@Project` resolver;
- DSH project-context plugin;
- Files/Terminal/Chat cross-navigation.

## Decisions intentionally deferred until usage/testing

- exact Local ADB implementation library;
- physical layout of the persistent vault and active session database mirror;
- plugin marketplace UX;
- remote runtime transport protocol;
- exact background task/notification policy for each OEM;
- final theme/gesture/tablet polish.
