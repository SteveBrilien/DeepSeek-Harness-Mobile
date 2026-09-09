# Development Rules V0.1

## 1. Technology baseline

- Android: Kotlin.
- Native UI: Jetpack Compose unless a platform component requires Views.
- Chat: hosted official DSH Web Client.
- Persistence for Mobile metadata: Room/SQLite or equivalent transactional Android store.
- DSH session content: native DSH persistence remains authoritative.
- Runtime process supervision: Android native service/process manager, not a DSH plugin.
- Cross-layer communication: typed interfaces/RPC; avoid ad-hoc file polling when a stable protocol is practical.

## 2. Code organization

- Feature modules may depend on core contracts, never directly on unrelated feature implementations.
- Recovery modules must not depend on runtime or DSH implementation modules.
- Privilege callers depend on `PrivilegeGateway`, never call `su`, Shizuku or ADB transport directly.
- Runtime callers depend on `RuntimeManager`, never manipulate rootfs slots directly.
- DSH-specific behavior stays under DSH adapter modules/plugins.

## 3. State ownership

Every state value must have one documented owner.

Examples:
- DSH event/message history -> DSH persistence.
- Mobile Project registry -> Mobile metadata store.
- Project source files -> real filesystem path.
- runtime slot health/active state -> Runtime Manager.
- plugin enablement -> DSH profile + Mobile extension metadata when needed.
- privilege capability state -> Privilege Gateway/provider.

Do not duplicate mutable truth across stores without a reconciliation strategy.

## 4. Persisted schemas

Any persisted record must include:
- stable ID;
- schema/version marker where format may evolve;
- timestamps where recovery ordering matters;
- migration path;
- corruption/error handling behavior.

Schema migrations must be:
1. preflighted;
2. backed up;
3. transactional/atomic where possible;
4. rollback-capable or recovery-copy based;
5. tested against at least the previous supported release.

## 5. Destructive operations

- Prefer Trash over permanent delete for Projects, Sessions, important config and user files.
- Before destructive migration, create a recovery snapshot.
- Never reuse a backup path as a working directory.
- Never silently delete unknown files from user-selected directories.
- Root/system writes require an explicit risk gate and audit record.

## 6. Process execution

Every managed process must expose:
- process identity;
- start reason;
- stdout/stderr capture with bounded rotation;
- exit code/signal;
- start/stop timestamps;
- health state;
- retry/backoff state.

Avoid infinite restart loops. After repeated failures, enter Safe Mode/Recovery state.

## 7. Shell execution

Define shell contexts explicitly:
- `runtime`: local Linux userspace;
- `android`: Local ADB/Shizuku shell provider;
- `recovery`: native app-owned rescue execution surface;
- `root`: explicit privileged provider.

The UI and logs must show which context executed a command.

## 8. DSH web compatibility

- No cosmetic fork of official DSH Chat.
- Mobile CSS/JS patches must have a documented reproduction case.
- Prefer plugin/slot extension over source patch.
- Keep compatibility patches isolated and version-gated.
- A DSH upgrade must be testable with Mobile compatibility layer disabled.

## 9. Error handling

Errors shown to the user should answer:
- what component failed;
- whether user data is at risk;
- whether the failure is recoverable;
- next safe action;
- where logs are stored.

`TOOL_OUTCOME_UNKNOWN` is not session corruption. Surface a recovery action that verifies external state before retrying side-effecting operations.

## 10. Logging

Use structured logs with component, severity, timestamp, correlation/session IDs when applicable.

Never log:
- API keys/tokens;
- passwords;
- full authorization headers;
- private key material;
- secret file contents.

Provide user-exportable diagnostic bundles with an explicit secret-redaction pass.

## 11. Testing priorities

P0 automated/manual tests:
- Native Recovery Core with runtime directory missing;
- broken Node/DSH start and repair;
- A/B update rollback;
- app process kill during active session;
- storage permission loss/regrant;
- project path move/rename with stable project ID;
- session with multiple attached projects;
- plugin crash and Safe Mode;
- Local ADB unavailable/revoked;
- upgrade preserving persistent data.

## 12. Documentation

Any new subsystem requires:
- purpose;
- owner module;
- persistent state;
- failure modes;
- recovery behavior;
- compatibility assumptions;
- security/privilege implications;
- tests.

Architecture-changing decisions require an ADR under `docs/adr/`.
