# ADR 0004 — OrangePi Native Android Build Is Supported

Status: Accepted
Date: 2026-09-07

## Context

The primary development host is OrangePi ARM64. This machine has a working, pinned Android build chain that has already produced and validated Android APK artifacts using ARM64-compatible host tooling.

The generic Codex shell remains sandboxed and cannot see the host toolchain under `/home/orangepi/.local/...`. This is intentional isolation and must not be weakened merely for build convenience.

## Decision

- Treat OrangePi as both the primary source-development host and a supported Android APK build host.
- Use the pinned initial baseline: JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20, API 35, and the pinned ARM64 AAPT2 override.
- Keep ARM-specific toolchain adaptation isolated in build scripts and build-environment documentation; never leak host-specific assumptions into application architecture.
- Execute host builds through an audited, narrowly scoped build bridge/job runner rather than exposing the host home to arbitrary sandbox shell commands.
- Azure/CI may be used as independent verification builders but are not mandatory for each APK.
- Toolchain upgrades require explicit validation and rollback documentation.

## Consequences

- Android development and APK packaging can remain centered on the OrangePi.
- The build bridge becomes infrastructure, not application logic.
- Host-specific paths remain replaceable implementation details.
- Build reproducibility must record toolchain identity, host architecture and artifact hashes.
