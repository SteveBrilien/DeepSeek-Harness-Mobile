# Architecture V0.1

## 1. Layer model

```text
Android App
├─ Native Recovery Core
│  ├─ File Manager
│  ├─ Text Editor
│  ├─ Recovery Terminal
│  ├─ Log Viewer
│  ├─ Backup / Restore
│  ├─ Runtime Repair
│  └─ Updater Bootstrap
│
├─ Mobile Application Layer
│  ├─ Projects
│  ├─ Session metadata/index
│  ├─ Mention resolver (@Project/@File/@Session)
│  ├─ Plugin manager bridge
│  ├─ Privilege gateway
│  └─ Remote runtime registry
│
├─ DSH Presentation
│  └─ Official DSH Web Client
│     └─ mobile compatibility plugin/patches only
│
├─ Local Runtime
│  ├─ Linux userspace (initial target: PRoot-based)
│  ├─ Node.js
│  ├─ DeepSeek Harness
│  ├─ Git/Python/toolchain
│  └─ DSH plugins
│
├─ Privilege Providers
│  ├─ StandardProvider
│  ├─ LocalAdbProvider (preferred non-root enhanced path)
│  ├─ ShizukuProvider (optional fallback)
│  └─ RootProvider (optional UID 0 path)
│
└─ Persistent Data
   ├─ Projects
   ├─ Session recovery vault
   ├─ Attachments
   ├─ Project metadata
   ├─ Plugin state
   ├─ Backups
   └─ Recovery metadata
```

## 2. Dependency direction

Dependencies must point inward toward stable contracts, never from Recovery Core toward DSH runtime implementation.

```text
Features -> Application contracts -> Core models
DSH adapters -> DSH contracts
Privilege providers -> Privilege contracts
Runtime providers -> Runtime contracts

Recovery Core !-> DSH
Recovery Core !-> Node
Recovery Core !-> Debian/PRoot
```

## 3. Project / Workspace / Session model

Mobile `Project` is not DSH `Workspace`.

- Project↔Session is many-to-many.
- A Session has exactly one Primary Project.
- Primary Project path maps to native DSH session `cwd` / Workspace identity.
- A Session may attach multiple additional Projects.
- Attached Projects do not mutate native DSH primary workspace identity.
- `@Project` resolves a stable project ID to current metadata/path; names are display-only.

Conceptual model:

```text
Project A ─┐
Project B ─┼─ Session X
Project C ─┘      │
                  └─ Primary Project A
                       └─ native DSH cwd = Project A path
```

## 4. DSH integration boundary

The app hosts the official DSH Web Client as the Chat surface. Do not reimplement chat rendering unless required for a documented compatibility or recovery reason.

Integration responsibilities:

- lifecycle: start/stop/health-check local DSH runtime;
- transport: local HTTP/WebSocket endpoint;
- project context plugin: expose Primary/Attached/mentioned project context;
- mention bridge: extend UI resolver to support `@Project` while preserving native `@file`/`@session` behavior;
- native bridge: controlled APIs for file picker, Android share, privilege requests, updater/recovery status;
- mobile compatibility layer: viewport, safe area, keyboard, touch and narrow-screen fixes.

## 5. Native Recovery Core

The Recovery Core is the app's survival layer. It must start when local runtime is missing or corrupted.

It owns:

- persistent data discovery;
- runtime health checks;
- A/B runtime slot selection;
- log access;
- backup and restore;
- native file operations;
- native text editing;
- recovery shell;
- package/runtime update bootstrap;
- plugin safe-mode selection.

## 6. Runtime slots

```text
runtime/
├─ slot-a/
├─ slot-b/
└─ active-slot metadata
```

Update sequence:

1. download/stage inactive slot;
2. verify integrity/signature;
3. install/prepare;
4. run offline checks;
5. atomically activate;
6. run health check;
7. keep old slot until success policy permits cleanup;
8. rollback automatically on failed health check.

## 7. Privilege model

All enhanced Android operations use a provider abstraction.

```text
PrivilegeGateway
├─ StandardProvider      app UID
├─ LocalAdbProvider      shell UID 2000
├─ ShizukuProvider       optional shell/root service
└─ RootProvider          UID 0
```

Callers request capabilities, not implementations. Example capabilities: package query, activity launch, settings read, privileged shell, file access class, process inspection.

High-risk writes require explicit policy checks/audit. Root filesystem write mode is disabled by default.

## 8. File and terminal surfaces

Files and Terminal are peers to Chat, not accessories.

File Manager requirements:
- browse trees/breadcrumbs;
- create/delete/copy/cut/paste/move/rename;
- multi-select;
- file preview;
- text/code edit and save;
- search/sort/hidden files;
- Trash/version recovery for important data;
- Open in Terminal / Open in Files linking.

Terminal profiles:
- Runtime Terminal;
- Android/ADB Terminal;
- Recovery Terminal;
- Root Terminal when available.

## 9. Plugin layers

```text
Plugin package
├─ DSH Host Plugin                official ecosystem
├─ DSH Browser Plugin             official web UI ecosystem
└─ Mobile Extension (optional)    Android-native surface/capabilities
```

Normal DSH plugins must not require a Mobile rewrite unless they depend on unavailable platform APIs. Mobile extensions are additive.

## 10. Remote runtimes

Remote execution (OrangePi/Azure/future nodes) is optional and must not be required for local app startup. A future Runtime abstraction should allow the same Project/Session UI to target Phone or a remote node without collapsing their filesystems into one namespace.

## 11. Initial module plan

```text
:app
:core:model
:core:storage
:core:logging
:core:recovery
:core:update
:core:runtime-api
:core:privilege-api
:core:dsh-api
:core:plugin-api

:runtime:local
:runtime:proot
:dsh:bridge
:dsh:webhost

:privilege:standard
:privilege:local-adb
:privilege:shizuku
:privilege:root

:feature:chat
:feature:projects
:feature:files
:feature:editor
:feature:terminal
:feature:plugins
:feature:backup
:feature:diagnostics
:feature:settings
```

This module plan is intentionally interface-first; implementations may evolve without changing the core contracts.
