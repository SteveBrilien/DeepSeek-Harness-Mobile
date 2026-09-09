# ADR 0007 — One terminal surface, multiple execution domains

## Status

Accepted.

## Decision

DSH Mobile exposes one Terminal screen rather than three duplicated terminal pages. The user-visible mode selector is:

- `AUTO` — default. Route to the least-privileged domain that can correctly satisfy the command.
- `LINUX_RUNTIME` — managed PRoot Linux userspace for normal development.
- `ANDROID_LOCAL` — App-UID `/system/bin/sh` recovery shell, independent of DSH/Node/PRoot.
- `ADB_SHELL` — Android shell-UID backend when the embedded/remote Wireless ADB provider is actually paired and connected.

`ADB_SHELL` must never be represented as Android root. `LINUX_RUNTIME` root is PRoot-simulated root and must never be represented as Android UID 0.

The three backends are capabilities, not three separate products. Android Local adds effectively no runtime payload because it reuses the system shell; ADB adds only the selected protocol/client provider and pairing state; the Linux Runtime remains the dominant storage consumer.

## Auto-routing rules

The default router prefers Linux Runtime for normal development commands when a healthy runtime exists. Commands whose semantics require Android shell privileges (`pm`, `am`, `dumpsys`, `settings`, `input`, `cmd`, `svc`, and future allowlisted equivalents) route to ADB rather than silently running under the lower-privilege App UID. Native Android/recovery commands such as `getprop` may use Android Local.

If a required domain is unavailable, the terminal reports that domain as unavailable and explains the prerequisite. It must not silently downgrade a privileged command to a different identity.

The UI always exposes the selected mode and the actual execution domain for each recorded command.

## Relationship to Mobile Environment Context

ADR 0006 is the canonical prompt/context contract. DSH is told the stable semantic meaning of Linux Runtime, Android Local, ADB Shell and optional Android Root through the cache-stable Mobile system section. Dynamic availability and authorization are not encoded into that stable prefix; they are queried from the shared capability source or supplied as bounded environment updates.

The same execution-domain semantics must be consumed by:

- the Terminal auto-router;
- DSH Mobile context injection;
- permission gates;
- diagnostics/Recovery Center;
- future embedded Wireless ADB provider;
- Agent/tool routing.

## Safety

Automatic routing is a convenience layer, not an authorization bypass. Capability availability and authorization remain separate facts. A provider may be available while an operation still requires explicit confirmation. Root-capable providers, if added later, require an explicit provider and separate authorization path.

## Current implementation boundary

The current implementation executes Linux Runtime and Android Local commands. The ADB terminal domain is deliberately exposed as unavailable until the embedded Wireless ADB provider is implemented and paired. This keeps the UI/API contract stable without pretending that App-UID shell and shell-UID ADB are equivalent.
