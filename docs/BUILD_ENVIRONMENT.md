# Build Environment

## Primary host

The primary development and Android build host is an OrangePi ARM64 machine using the workspace `/home/orangepi/workspace`.

This machine has a previously validated ARM64 Android build chain. The project therefore treats OrangePi-native APK builds as supported, provided the pinned toolchain and build checks below continue to pass.

## Validated toolchain baseline

Initial builds use the following pinned baseline:

- architecture: aarch64;
- JDK 17: `/home/orangepi/.local/opt/openjdk-17`;
- Android SDK: `/home/orangepi/.local/share/android-sdk`;
- Android platform: API 35;
- Gradle: 8.11.1 at `/home/orangepi/.local/opt/gradle-8.11.1`;
- Android Gradle Plugin: 8.9.2;
- Kotlin: 2.1.20;
- Compose BOM: 2025.05.01;
- ARM64 Android helper tools: `/home/orangepi/.local/opt/android-arm64-tools`;
- ARM64 AAPT2 override: `/home/orangepi/.local/bin/aapt2`.

The project ships `scripts/build-orangepi.sh` to inject these host paths and `android.aapt2FromMavenOverride` in one auditable place.

## Toolchain policy

The first milestone prioritizes a known-good ARM64 build chain over chasing the newest Android tool versions.

Changes to AGP, Gradle, Kotlin, SDK platform, build-tools, JDK, AAPT2 or ARM helper libraries must be treated as explicit toolchain migrations. A migration is accepted only after:

1. a clean debug APK build succeeds;
2. signing/package validation succeeds where applicable;
3. the generated APK installs and starts on the target phone;
4. the previous pinned baseline remains documented as a rollback option until the new baseline is proven stable.

Do not upgrade the entire Android toolchain merely because newer versions exist.

## Sandbox vs host build environment

Codex MCP Dev `run_shell` executes in a bubblewrap workspace and intentionally cannot access `/home/orangepi/.local/...`.

Therefore:

- `run_shell` is used for source edits, Git, workspace-visible tests, static checks and build-script validation;
- the Android compiler/toolchain remains outside the generic shell sandbox;
- APK compilation must use an audited host build bridge/job runner or an equivalent narrowly scoped mechanism;
- do not weaken sandbox isolation or expose the complete host home directory merely to make Android builds convenient.

The build bridge should expose fixed actions such as debug build, clean debug build, unit tests and lint rather than arbitrary privileged shell execution.

## Host build bridge requirements

The controlled build executor must provide:

- fixed project root;
- fixed/pinned toolchain paths;
- bounded runtime and memory usage;
- plain persistent logs;
- cancellation and stale-job handling;
- artifact existence checks;
- artifact size and SHA-256 reporting;
- no access to secrets except explicitly required release-signing material;
- no arbitrary host command execution through user-supplied Gradle task strings unless separately audited.

## Reproducibility

Every distributable build should record:

- Git commit;
- build host architecture;
- JDK / Gradle / AGP / Kotlin versions;
- compileSdk / targetSdk;
- AAPT2 override identity and SHA-256 when practical;
- runtime bundle versions;
- signing key identity/fingerprint, never private key material;
- artifact SHA-256.

## Secondary builders

Azure x86_64 or CI may be used as independent verification/release builders. They are useful for reproducibility checks but are not mandatory for every development APK.
