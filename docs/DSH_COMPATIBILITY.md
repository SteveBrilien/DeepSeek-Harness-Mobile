# DSH Compatibility Contract V0.1

## Goal

DeepSeek Harness Mobile should behave as a host for official DSH rather than a divergent reimplementation.

## Chat UI

- Use official DSH Web Client as the primary Chat surface.
- Preserve original cards, thinking/tool call rendering, session semantics and interaction patterns.
- Only patch mobile-specific issues: viewport, safe-area, keyboard occlusion, touch targets, overflow, orientation and narrow-screen bugs.
- Prefer extension through DSH/Cordis plugins and slots.

## Workspace / Session

- Keep native DSH workspace semantics intact.
- Each native DSH Session has one primary `cwd`/Workspace.
- Mobile `Project` is a separate metadata abstraction above DSH.
- Multiple Mobile Sessions may reference one Project; one Session may reference multiple Projects.
- Exactly one Primary Project maps to the native Session `cwd`; Attached Projects are contextual/mounted references only.

## Mentions

Extend mention UX without replacing native references:

- `@file` — native DSH behavior.
- `@session` — native DSH behavior.
- `@project` — Mobile extension resolved by stable project ID.

A project mention injects bounded metadata/path/capabilities and lets the agent inspect files on demand. It must not dump the entire repository into the prompt.

## Prompt context

Use the official DSH `systemPrompt.section(...)` extension point rather than modifying DSH core or rewriting prompts in the Web client.

Context is layered for cache stability:

1. a process-stable Mobile system section containing only invariant environment rules and stable boot facts;
2. a once-per-Session bootstrap snapshot;
3. append-only environment deltas or on-demand capability queries for runtime changes.

The Session bootstrap may contain only:
- Primary Project;
- Attached Projects;
- current-turn project mentions;
- scoped access mode (RO/RW);
- stable IDs and resolved paths.

Do not permanently list all known Projects in the system prompt.

Do not place volatile values such as current time, battery/CPU state, ADB online polling, current foreground Activity, or transient approval state in the stable system section.

“Injected once” means the Mobile section is registered and rendered from one process-stable snapshot. The complete system prompt may still be present in every model request; its Mobile text must remain byte-identical across turns. Dynamic capability changes must not rewrite that prefix.

Canonical execution domains are Linux Runtime (PRoot development), Android Local (App-UID recovery), ADB Shell (shell UID when actually connected), and optional Android Root. DSH must query current capability state rather than infer availability from the static prompt.

See ADR 0006 for prompt/KV-cache invariants and the shared `MobileEnvironmentContextProvider` contract.

## Session persistence

- Native DSH session event log is the authoritative session history.
- Mobile may maintain searchable/indexable metadata, but not a competing message history.
- Native fork/resume/recovery/subagent capabilities should be surfaced rather than reimplemented.

## Plugin compatibility

- Preserve DSH Host plugins.
- Preserve DSH Browser plugins when Web APIs and platform dependencies are available.
- Provide optional Mobile Extensions for native Android surfaces.
- Do not require Mobile adaptation for ordinary compatible DSH plugins.

## Upgrade policy

For every DSH upgrade:
1. stage in inactive runtime slot;
2. run native DSH smoke tests without Mobile patches;
3. run Mobile compatibility tests;
4. run representative plugin tests;
5. migrate only after health checks;
6. retain rollback slot.

Any source patch against official DSH must have:
- upstream version/range;
- exact reason/reproduction;
- smallest possible diff;
- owner/test;
- removal condition.
