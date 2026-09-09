# Confirmed Requirements Baseline V0.1

Date: 2026-09-07

This document records requirements already confirmed with the owner. Do not reopen them without a concrete implementation conflict or new owner request.

## Usage model

- Personal-use Android application; no need to optimize product positioning for a general audience.
- Phone must be able to run a local DeepSeek Harness environment and execute real local development commands.
- OrangePi/Azure are optional development, backup and remote-runtime nodes, not prerequisites for local app startup.

## Chat / DSH

- Chat should preserve official DSH UI/behavior to the maximum extent possible.
- Only fix actual mobile rendering/interaction bugs in the base Web Client.
- Nonessential UI/behavior changes should be plugins/extensions.
- Preserve native DSH Session/Workspace semantics.

## Projects

- Mobile Project is distinct from native DSH Workspace.
- Project↔Session is many-to-many.
- Each Session has one Primary Project mapped to native DSH `cwd`/Workspace.
- A Session may attach/reference multiple additional Projects.
- Multiple Sessions may maintain the same Project.
- Project may reference an existing authorized directory; it is not forced under one app-created folder.
- Input composer supports `@Project` alongside native `@file`/`@session` concepts.
- `@Project` stores/resolves a stable project ID, not a path/name string.
- DSH prompt/context must explain the Mobile Project layer and dynamically expose active project context.

## Files

Native File Manager is a P0 surface and supports at least:
- directory tree/breadcrumb browsing;
- create file/folder;
- rename;
- copy/cut/paste/move;
- delete and restore path for important data;
- multi-select;
- file preview;
- text/code editing and save;
- search/sort/hidden files;
- open directory in Terminal and open terminal cwd in Files.

File Manager and editor must remain usable if DSH/Node/Linux runtime fails.

## Terminal / self-repair

The app must expose terminal functionality and specifically a Native Recovery Terminal that does not depend on DSH/Node/Debian/PRoot.

Terminal contexts:
- Runtime;
- Android/ADB;
- Recovery;
- Root when available.

Recovery Center/Terminal must support diagnosis, logs, component restart/rebuild/rollback, Safe Mode and backup/restore operations.

## Privilege

- Core app must work without Root.
- Root is optional advanced capability.
- Preferred non-root enhanced path: embedded Local Wireless ADB provider.
- Do not implement modern ADB cryptography/protocol from scratch when mature/AOSP-compatible implementations can be reused.
- Shizuku remains optional fallback/compatibility provider, not mandatory dependency.
- Root/Shizuku/Local ADB/standard operations are abstracted through `PrivilegeGateway`.
- Root/system filesystem writes are disabled/read-only by default until explicitly enabled.

## Runtime and updates

- App supports in-app update discovery/update flow.
- Runtime components and plugins must be repairable independently where feasible.
- Runtime/DSH component update uses verified A/B staging, health check and rollback.
- Native Recovery Core is part of the APK/bootstrap survival layer.
- Runtime reinstall must not delete Projects/Sessions/user data.

## Data durability

Hard rule: any non-reproducible data must not exist only in disposable app-specific storage.

Persistent/recoverable data includes:
- Projects;
- DSH Sessions;
- attachments;
- Project/Session relationship metadata;
- plugin/config state;
- recovery metadata.

Support multiple recovery layers including on-phone persistent recovery copies and OrangePi backup/mirror.

Destructive recovery should preserve originals and recover via copy/fork/snapshot.

Session/File deletion should be recoverable (Trash/version history) by default for important data.

## Sessions

- Native DSH Session event log remains the source of truth for session content.
- Mobile does not create a competing chat-message history store.
- Surface native DSH fork/resume/recovery/subagent semantics rather than reimplementing them.
- `TOOL_OUTCOME_UNKNOWN` is recoverable execution uncertainty, not automatically session corruption.
- Actual damaged sessions should be preserved and recovered into a new copy/fork from the last valid boundary where possible.
- Session lineage/fork tree should be visualizable.

## Plugins

- Preserve official DSH/Cordis Host and Browser plugin compatibility where platform dependencies allow.
- Existing DSH UI plugins should run inside the DSH Chat/Web Client where compatible.
- Provide an optional Mobile Extension layer for Android-native surfaces, app-wide overlays, device metrics, notifications and Android capabilities.
- A resource-monitor plugin may share one core/data provider while exposing both DSH Web overlay and Android-native overlay.
- Plugin failure must be isolated; provide per-plugin health and Safe Mode.
- Future OrangePi↔Phone plugin-set comparison/sync should be package/version/compatibility aware, not blind directory copying.
