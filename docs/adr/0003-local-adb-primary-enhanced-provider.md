# ADR 0003 — Embedded Local ADB as Preferred Non-Root Enhanced Provider

Status: Accepted
Date: 2026-09-07

## Context

Shizuku is capable but introduces a separately installed/started app dependency. The personal-use Mobile app should minimize external setup while retaining shell-UID Android capabilities.

## Decision

- Implement a Local Wireless ADB provider inside the app as the preferred non-root enhanced path.
- Reuse mature/AOSP-compatible ADB protocol implementations rather than reimplementing cryptography/protocol from scratch.
- Keep Shizuku as an optional fallback compatibility provider.
- Keep Root as a separate optional UID-0 provider.
- Expose capabilities through `PrivilegeGateway`; callers must not bind to transport details.

## Consequences

- First-time Android Wireless Debugging pairing is still required where the OS requires it.
- Reconnect behavior must handle reboot, network change and OEM differences.
- ADB-shell capability remains different from Root and must never be presented as UID 0.
