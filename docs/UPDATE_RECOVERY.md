# Update and Self-Repair V0.1

## Layers

```text
L0 Android Native Bootstrap / Recovery Core   -> APK update only
L1 Runtime Manager / bridges                  -> component update where safe
L2 Linux rootfs / Node / DSH                   -> A/B runtime slots
L3 DSH plugins / Mobile extensions             -> hot update with rollback
L4 Projects / Sessions / user data             -> never deleted by component update
```

## APK update

The app must support in-app update discovery/download/verification. Installation may use normal Android package installation flow and, when explicitly enabled/available, an enhanced provider such as Local ADB. Never depend exclusively on a privileged update path.

Before APK activation:
- verify package signing identity/expected release signature;
- verify downloaded artifact integrity;
- write update journal;
- ensure persistent data/recovery metadata is flushed;
- retain compatibility information for rollback/recovery.

## Runtime update

Runtime components must use A/B staging.

```text
active=A
stage B -> verify -> prepare -> offline tests -> activate B -> live health -> commit
                                              \-> fail -> reactivate A
```

Never update the only working runtime directory in place.

## Plugin update

- stage package;
- verify;
- compatibility preflight;
- preserve previous package/config snapshot;
- activate;
- start health observation;
- disable/rollback on crash loop.

## Recovery Center

Native UI must expose at least:
- overall health;
- Android core health;
- storage/persistent-vault health;
- runtime slot state;
- DSH/Node health;
- privilege provider health;
- plugin health;
- backup freshness;
- recent failure reason.

Actions:
- restart DSH;
- stop/kill stuck runtime;
- rebuild runtime;
- rollback runtime;
- start without third-party plugins;
- repair/reinstall DSH component;
- verify persistent data;
- open Recovery Terminal;
- open logs;
- export diagnostics.

## Recovery Terminal

Recovery Terminal must not use the managed DSH runtime as its shell implementation. It must remain usable when DSH, Node, Linux userspace or PRoot is broken.

It should provide bounded native rescue commands/APIs for:
- file inspection/manipulation;
- process/runtime inspection;
- checksums;
- log inspection;
- backup export/import;
- runtime slot repair;
- ADB pairing/provider diagnostics where feasible.

## Failure journal

Persist an append-only/recoverable journal for update/repair operations containing:
- operation ID;
- component/version;
- source/target slot;
- validation results;
- activation state;
- rollback state;
- timestamps;
- sanitized error details.

A failed updater must leave enough information for Recovery Core to decide which known-good slot to boot.
