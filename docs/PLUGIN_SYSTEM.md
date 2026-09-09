# Plugin System V0.1

## Objective

Preserve the DSH plugin ecosystem and add optional Android-native extension points without creating an incompatible replacement ecosystem.

## Plugin shapes

```text
Plugin package
├─ DSH Host plugin           existing ecosystem
├─ DSH Browser plugin        existing web UI ecosystem
└─ Mobile Extension          optional Android addition
```

A normal DSH plugin should install and run unchanged when its runtime/platform dependencies are satisfied.

## Mobile Extension responsibilities

Optional native additions may provide:
- app-wide native overlay/widget;
- Android-specific settings surface;
- notification integration;
- sensor/device metrics;
- native file picker/share integration;
- privilege capability adapters;
- background task status surfaces.

The extension should share data/core logic with Host plugin where possible instead of duplicating business logic.

## Example: resource monitor

```text
ResourceMonitor Core
├─ LinuxMetricsProvider      OrangePi/Linux
├─ AndroidMetricsProvider    phone-native metrics
├─ DSH Web overlay           Chat surface
└─ Android native overlay    Files/Terminal/Projects/etc.
```

## Isolation

- Third-party plugin failure must not crash Native Recovery Core.
- Track crash loops per plugin/profile.
- Offer Safe Mode: start DSH without third-party plugins.
- Allow single-plugin disable/repair/update/rollback.
- Plugin logs must be separable from core app logs.

## Compatibility metadata

Maintain metadata for:
- plugin version;
- DSH version/range;
- host platform/architecture requirements;
- required commands/packages;
- browser APIs;
- Mobile Extension version/API level;
- requested privilege capabilities.

Do not block plugins only because they lack Mobile-specific metadata; treat unknown Mobile extension support separately from ordinary DSH compatibility.

## Install sources

Planned sources:
- local package/file;
- normal DSH/npm-compatible source where supported;
- import/sync manifest from OrangePi;
- repository URL in later phases.

Every install/update requires integrity/version metadata and must be rollback-aware.

## OrangePi sync

Sync is package-aware, not blind directory copying.

Compare:
- package identity/version;
- DSH compatibility;
- architecture/platform dependencies;
- Mobile Extension availability;
- configuration migration requirements.

Allow user to import a compatible subset.

## Remote plugins

Future design must not assume all plugin execution is local. A plugin/tool provider may target Phone, OrangePi or another registered Runtime. Execution target must be explicit in metadata/UI where ambiguity could cause side effects.
