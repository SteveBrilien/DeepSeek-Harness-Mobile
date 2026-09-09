# Data, Backup and Recovery V0.2

## 1. Design goal

Environment rebuild must mean rebuilding software, not rebuilding the owner's identity, plugins, projects or long-lived context.

Any user-owned asset that cannot be reliably regenerated from an authoritative external source is a **Persistent Recovery Asset** and must have a recoverable copy outside disposable runtime state.

## 2. Data classes

### Disposable / reproducible
- APK binaries;
- caches;
- downloaded packages after verified install;
- Debian/PRoot/Node/DSH runtime slots when reproducible;
- build output that can be regenerated;
- package-manager caches.

### Persistent Recovery Assets
- project files and project registry metadata;
- native DSH session history and Mobile session metadata;
- attachments and attachment indexes;
- SSH public/private identities;
- known/trusted-host metadata and user-managed SSH configuration;
- remote-node/pairing metadata that is safe and meaningful to restore;
- user-authored/local plugin source;
- uncommitted local plugin modifications;
- plugin manifests, version locks, configuration, enabled state and compatible persistent state;
- user preferences that materially affect workflow;
- backup policy and backup-target metadata;
- runtime reconstruction manifest/lock;
- recovery metadata and schema migration state.

Persistent Recovery Assets must never have their only recoverable copy inside app-specific storage, an active runtime slot, cache, or a single mutable database without an external recoverable representation.

## 3. Unified Recovery Vault

Use one versioned Recovery Vault instead of unrelated backup systems for sessions, SSH, plugins and settings.

Conceptual layout:

```text
DeepSeekHarness/
├── Projects/
├── Sessions/
├── Plugins/
│   ├── Installed/
│   ├── Local/
│   └── State/
├── Recovery/
│   ├── manifest.json
│   ├── Identity/
│   │   ├── ssh/
│   │   ├── trusted-hosts/
│   │   └── device-pairings/
│   ├── Config/
│   │   ├── app/
│   │   ├── dsh/
│   │   ├── plugins/
│   │   └── projects/
│   ├── Secrets/
│   │   └── encrypted-vault/
│   ├── Plugins/
│   │   ├── local-source/
│   │   ├── working-tree-snapshots/
│   │   ├── package-manifests/
│   │   └── version-locks/
│   ├── Sessions/
│   │   └── snapshots/
│   ├── Runtime/
│   │   └── runtime.lock
│   └── Snapshots/
└── Exports/
```

This is a conceptual contract, not a permanently fixed physical layout. Android storage constraints may change the physical paths, but the logical asset classes and recovery guarantees must remain stable.

## 4. Recovery manifest

`Recovery/manifest.json` is the discovery/index entry point. It must be versioned and integrity-checkable.

It should identify, without embedding plaintext secrets:
- manifest schema version;
- vault identity and creation/update timestamps;
- application/data schema versions;
- project IDs and recoverable locations;
- session snapshot/index inventory;
- attachment inventory metadata;
- SSH identity public metadata and encrypted-secret references;
- local plugin identities, source snapshots, dirty/uncommitted state and plugin config schema versions;
- runtime reconstruction manifest reference;
- backup sources/targets and latest known successful backup timestamps;
- checksum/integrity metadata for recoverable objects;
- migration state and compatibility requirements.

Display names and physical paths are not stable identifiers. Use stable IDs and resolve the current path/name dynamically.

## 5. Recovery Discovery

First install after reinstall, detection of an empty local state, explicit `Find existing data`, or a recovery-mode startup must run **Recovery Discovery**.

Discovery flow:

```text
App start / empty state / reinstall
            ↓
scan authorized/known Recovery Vault locations
            ↓
find manifest candidates
            ↓
validate schema + integrity + compatibility
            ↓
show recoverable asset inventory
            ↓
build non-destructive recovery plan
            ↓
restore/relink selected assets
            ↓
rebuild reproducible runtime components
            ↓
health check
```

Automatic detection is encouraged. Automatic destructive overwrite is forbidden.

## 6. Recovery layers

```text
L0  native live persistence
L1  local checkpoint/snapshot
L2  persistent on-phone Recovery Vault
L3  trusted remote incremental mirror
L4  optional manual/archive destinations
```

Git is project source control, not a replacement for session, plugin-working-tree, identity or configuration backup.

## 7. Secrets and identities

Secrets are a separate recovery class.

Examples:
- SSH private keys;
- API credentials/tokens;
- plugin credentials;
- sensitive remote authentication material.

Rules:
- encrypt secrets at rest;
- never store plaintext private keys in shared storage;
- normal diagnostic/session exports exclude secrets by default;
- backup/export UI must clearly state whether encrypted secrets are included;
- never write secrets into logs, project metadata, screenshots or ordinary manifests.

### Cross-uninstall recovery requirement

Android Keystore may wrap/unlock a vault key for convenient local use, but it must not be the only decryption root for persistent backups because uninstall can invalidate/remove app-scoped keys.

Use a dual-path design conceptually equivalent to:

```text
Vault Master Key
├── Device Wrapper
│   └── Android Keystore
└── Recovery Wrapper
    └── recovery password/key or another independently recoverable secret
```

After reinstall, the Recovery Wrapper must be sufficient to unlock the encrypted vault and establish a new device-local wrapper.

Recovery credentials themselves must not be silently uploaded or logged.

## 8. Local plugin preservation

User-authored/local plugins are first-class Persistent Recovery Assets.

For each local plugin, preserve enough information to recover work that has not reached an external repository:
- stable plugin ID;
- source snapshot or equivalent recoverable working tree;
- manifest/package metadata;
- dependency/version lock information where practical;
- plugin config and config schema version;
- enabled/disabled state;
- declared permissions/capabilities;
- Git remote/branch/commit if present;
- dirty/uncommitted state;
- snapshot of uncommitted changes when needed;
- checksums and timestamps.

A Git clone is not considered complete recovery if the original working tree contained uncommitted changes.

Installed third-party plugin binaries that can be reliably fetched may be reconstructed rather than copied, but their version lock/config/state still need recovery metadata.

## 9. Plugin config migration

Plugin backup metadata must record plugin version and configuration schema version.

On restore:
1. resolve a compatible plugin version;
2. inspect backed-up config schema version;
3. run explicit migration steps when required;
4. preserve the original config before migration;
5. health-check the plugin;
6. rollback/disable safely on failure.

Do not blindly inject an old config into an incompatible new plugin.

## 10. Filesystem policy

Default project root may live under a persistent shared location, but a Project may reference another authorized real path.

Important file operations should support where practical:
- Trash;
- version history/snapshot before overwrite;
- restore to a new copy;
- export.

Root/system paths remain read-only by default.

## 11. Session recovery

- Native DSH event persistence remains the source of truth for session content.
- Preserve the original damaged session/event source.
- Recover from the last valid committed boundary into a new recovery session/fork.
- Do not mutate the only damaged evidence during repair.
- `TOOL_OUTCOME_UNKNOWN` requires external-state verification, not blind replay for side-effecting operations.

Session backups should preserve native event data plus Mobile metadata, project references, lineage and attachment references without duplicating full project trees into every session backup.

## 12. Runtime reconstruction

Runtime binaries are normally rebuilt from a versioned reconstruction manifest rather than restored as an opaque old rootfs.

`runtime.lock` should describe enough state to reconstruct a compatible environment, such as:
- runtime/base distribution version;
- Node/Python/DSH versions;
- required runtime packages;
- plugin version locks;
- relevant compatibility/schema versions.

Runtime reinstall flow:
1. stop runtime;
2. snapshot persistent metadata/config;
3. prepare a clean inactive runtime slot;
4. install/reconstruct required runtime components;
5. restore/migrate compatible configuration and plugin state;
6. relink persistent projects/session vault;
7. health-check;
8. activate atomically or rollback.

Do not make a byte-for-byte copy of a potentially corrupted runtime the only recovery method.

## 13. Restore policy by risk

Recovery Discovery may restore safe metadata automatically or in batches when configured.

Require explicit user confirmation/unlock for high-risk classes such as:
- private keys and credentials;
- privileged/root configuration;
- ADB/device trust requiring reauthorization;
- dangerous plugin permissions;
- overwrite of newer local data;
- system-level settings.

The restore plan must be inspectable and auditable.

## 14. Remote mirror

Design target for a trusted remote mirror:
- incremental;
- integrity-checked;
- encrypted where needed;
- versioned;
- does not silently propagate deletion as the only copy;
- exposes last successful backup time and lag;
- can restore a single asset, file, plugin, project, session or full Mobile metadata set.

Remote mirroring is an additional recovery layer, not a reason to weaken on-device recovery guarantees.

## 15. Required recovery tests

At minimum test:
- APK reinstall followed by automatic Recovery Discovery;
- runtime deletion/rebuild while projects/sessions remain recoverable;
- encrypted secret-vault restore after uninstall/reinstall using the independent recovery path;
- local plugin restore including uncommitted modifications;
- plugin config schema migration and rollback;
- single-file restore;
- session valid-prefix recovery;
- deletion/Trash restore;
- corrupted manifest/object detection;
- interrupted recovery operation followed by safe resume/rollback.
