# ADR 0006 — Cache-stable DSH Mobile environment context

## Status

Accepted.

## Decision

DSH Mobile integrates environment awareness through the official DSH `systemPrompt.section(...)` Cordis extension point. It does not rewrite the DSH core prompt and does not prepend ad-hoc text in the WebView/client submission path.

The context is split into three layers:

1. **Global static Mobile section** — registered once when the DSH process boots. Its rendered text is immutable for that process and contains only stable facts and rules: Android-host semantics, PRoot-not-root clarification, execution-domain meanings, persistent `DSH_HOME`, Project-vs-Workspace semantics, least-privilege routing, and the instruction to query dynamic capabilities rather than assume them.
2. **Session bootstrap snapshot** — created once for a newly established DSH Session. It may contain the Primary Project, Attached Projects, stable IDs/resolved paths, access mode, and an initial capability snapshot. It is append-only history and is never rewritten in place.
3. **Dynamic environment deltas** — meaningful changes such as ADB `unavailable -> available`, authorization changes, project attachment changes, or runtime degradation are appended as bounded environment-update events or queried on demand through capability tools. They do not mutate the static Mobile system section.

## Prompt-cache semantics

"Injected once" means that the plugin/section registration and the process-stable text are established once. The model request may still include the complete system prompt on every turn, as normal DSH/model request construction requires.

The important invariant is that the Mobile system section renders to the **same exact text across turns** while the DSH process is alive. This preserves the longest stable request prefix and therefore maximizes compatibility with provider-side prompt/KV caching.

The static section MUST NOT include volatile values such as:

- current time;
- CPU/memory/battery values;
- ADB online/offline polling state;
- current network address;
- current foreground Activity;
- transient permissions/approvals;
- the complete list of all known Mobile Projects.

Stable boot facts such as Android version, device model, and pinned DSH version may be included because they do not change during the process lifetime.

DSH's own `@deepseek-ai/dsh-web-app` uses the same design: its Web-surface prompt section is process-stable and explicitly documents that this avoids invalidating the cache across turns.

## Execution domains

The canonical domains are:

- `LINUX_RUNTIME` — PRoot Linux userspace for normal development. Simulated root is not Android UID 0.
- `ANDROID_LOCAL` — App-UID native recovery shell. Lowest privilege and independent of the Linux/DSH runtime.
- `ADB_SHELL` — Android shell-UID capability when ADB is paired and connected. Availability is dynamic and must be queried.
- `ANDROID_ROOT` — optional future provider; unavailable unless explicitly detected and authorized.

A shared `MobileEnvironmentContextProvider` / Capability Registry is the source of truth for DSH context, terminal auto-routing, permission gates, diagnostics, and UI status.

## Why not rewrite the prompt every turn?

Per-turn prompt mutation would make volatile state easy to expose but would shorten or invalidate stable cache prefixes, increase token churn, make request inspection noisy, and create ambiguity about which environment state applied to historical turns. Append-only deltas and on-demand capability queries preserve history and cache stability.

## Implementation

The bundled local Cordis package is shipped in the Android runtime assets as `@dsh-mobile/dsh-mobile-context`. Runtime installation registers it into the official DSH `web` profile with `dsh plugin --profile web add file:...`.

Before starting `dsh web`, the Android runtime writes a small process-stable `/dsh-home/mobile/context.json` snapshot without timestamps or volatile capability state. The Cordis plugin reads that file once during `apply()` and closes over the rendered string used by its `systemPrompt.section` callback.

The plugin lives under persistent `DSH_HOME`, not an A/B Linux slot, so runtime replacement does not remove the integration.

## Compatibility

For every DSH upgrade, verify:

1. `systemPrompt.section` and `getSectionOrder("WEB_SURFACE")` still exist or migrate to the documented successor API;
2. the local bundle composes into `web --dump-config` without overriding unrelated rows;
3. two consecutive requests produce byte-identical Mobile system-section text when no process restart occurs;
4. an ADB availability change does not change that section;
5. request inspection reports no system-prompt change solely from dynamic capability changes.
