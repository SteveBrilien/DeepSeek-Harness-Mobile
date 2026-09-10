# ADR 0005 — Pin targetSdk 28 for the fixed Android 11 self-hosting runtime

Status: Accepted, with a planned modernization path

## Context

The application is designed for a fixed Android 11 device and must execute locally installed runtime components such as PRoot, Node.js and DSH from app-private writable storage. Android 10 introduced restrictions for apps targeting API 29+ that prevent executing files from the writable app home. Raising targetSdk without redesigning the runtime launcher would therefore break a core product capability.

The installer warning shown by current Android/Google package-install UI is caused by this deliberately old `targetSdk`, not by an old build toolchain. The app already builds against a current Android SDK and current AndroidX/Compose libraries. `compileSdk` and `targetSdk` serve different purposes: modernizing framework/API compilation does not by itself make writable app-private executables legal on newer target levels.

## Decision

Keep `compileSdk` current enough for modern APIs, keep `minSdk` at the supported floor, and intentionally pin `targetSdk = 28` while the runtime depends on executable files stored in app-private writable storage. Android lint rule `ExpiredTargetSdkVersion` is disabled only for this intentional architecture decision; all other lint checks remain enabled.

Do not raise `targetSdk` merely to remove the installer warning. A build that installs without that warning but can no longer start PRoot/Node/DSH is a regression.

## Modernization path

Eliminating the warning remains a desired platform task. The safe sequence is:

1. move native bootstrap executables out of writable app-home execution and into Android-approved packaged native-library/bootstrap locations;
2. prove that the packaged bootstrap can still enter the Alpine/PRoot environment and execute the guest Node/DSH stack;
3. create a separate modern-target test build first, rather than changing the stable package in place;
4. validate install, cold start, Runtime install/reuse, PTY, DSH Web, process restart, backup/restore and cover-upgrade on the fixed Android 11 / OriginOS device;
5. only then promote the modern target to the normal build and remove the `ExpiredTargetSdkVersion` exception.

The intended end state is a current target SDK while retaining Android 11 support. `minSdk` can remain low enough for Android 11; target-SDK modernization does not require dropping Android 11.

## Consequences

This build is intended for controlled self-distribution rather than Google Play publication while the self-hosted runtime architecture still requires the legacy target. A future targetSdk upgrade requires first moving executable native launchers to an Android-compliant executable location or replacing the current runtime mechanism, then validating PRoot/Node/DSH execution on the target device. No Agent may raise targetSdk merely to silence tooling or store-policy warnings.
