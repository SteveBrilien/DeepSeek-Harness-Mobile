# ADR 0005 — Pin targetSdk 28 for the fixed Android 11 self-hosting runtime

Status: Accepted

## Context

The application is designed for a fixed Android 11 device and must execute locally installed runtime components such as PRoot, Node.js and DSH from app-private writable storage. Android 10 introduced restrictions for apps targeting API 29+ that prevent executing files from the writable app home. Raising targetSdk without redesigning the runtime launcher would therefore break a core product capability.

## Decision

Keep `compileSdk` current enough for modern APIs, keep `minSdk` at the supported floor, and intentionally pin `targetSdk = 28` while the runtime depends on executable files stored in app-private writable storage. Android lint rule `ExpiredTargetSdkVersion` is disabled only for this intentional architecture decision; all other lint checks remain enabled.

## Consequences

This build is intended for controlled self-distribution rather than Google Play publication. A future targetSdk upgrade requires first moving executable native launchers to an Android-compliant executable location or replacing the current runtime mechanism, then validating PRoot/Node/DSH execution on the target device. No Agent may raise targetSdk merely to silence tooling or store-policy warnings.
