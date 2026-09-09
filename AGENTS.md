# DeepSeek Harness Mobile — Agent Maintenance Contract

This file is the highest-priority repository-local maintenance contract for all future agents and contributors.

## 1. Core intent

DeepSeek Harness Mobile is a personal Android host for running DeepSeek Harness locally on the phone, with optional privileged and remote runtimes. Do not optimize for app-store generality at the expense of the owner's workflow.

## 2. Non-negotiable architecture constraints

1. **DSH compatibility first.** Keep the official DSH Web Client and native DSH Workspace/Session semantics intact whenever possible. Mobile-specific chat changes must be limited to Android/mobile compatibility fixes or implemented as plugins/extensions.
2. **Project is a Mobile-layer concept.** `Project` must not replace or redefine DSH Workspace. Project↔Session is many-to-many. Each Session has exactly one Primary Project mapped to native DSH `cwd`/Workspace and may have multiple Attached Projects.
3. **Native Recovery Core must survive runtime failure.** File Manager, Text Editor, Recovery Terminal, Log Viewer, Backup/Restore, Runtime Repair and Updater must not depend on DSH, Node, Debian or PRoot being healthy.
4. **No irreplaceable data only in app-specific storage.** APK, caches and runtime binaries are disposable. Projects, sessions, attachments, project metadata, plugin state and recovery metadata require a persistent recoverable copy outside disposable runtime state.
5. **Session event log remains the source of truth.** Never create a second independent chat-message database that competes with native DSH persistence. Mobile metadata may index sessions but must not redefine their content.
6. **Destructive recovery is forbidden by default.** Preserve originals and recover via copy/fork/snapshot. File/session deletion should be recoverable through Trash or version history where practical.
7. **A/B runtime updates.** DSH/runtime/plugin component updates must use verification, health checks, atomic activation and rollback. Never destructively overwrite the only working runtime in place.
8. **Privilege abstraction.** Privileged operations go through `PrivilegeProvider`: Standard, Local ADB, optional Shizuku, optional Root. DSH must not directly depend on one privilege mechanism.
9. **Local Wireless ADB is the preferred non-root enhanced provider.** Shizuku is a fallback/compatibility provider, not a mandatory dependency.
10. **Root system paths are read-only by default.** Explicit user action is required before write access to high-risk system paths.
11. **Plugins must fail isolated.** A third-party plugin failure must not prevent Android Native Recovery Core from starting. Safe Mode must exist.
12. **Official DSH plugin ecosystem stays usable.** Standard DSH Host/Browser plugins should work unchanged when their platform dependencies are satisfied. Mobile-native extensions are optional additions, not a replacement ecosystem.
13. **`@Project` is a stable reference.** Display names are not identifiers. Persist UUID-like project references and resolve current path/name dynamically.
14. **Primary Workspace is stable.** Shell `cd` into an attached project must not mutate the native DSH Session primary `cwd`/Workspace identity.
15. **Recovery Terminal must be independent.** It must still open when DSH/Node/Debian/PRoot are broken.
16. **Persistent Recovery Assets are broader than sessions.** SSH identities, trusted-host metadata, user-authored/local plugin source including uncommitted changes, plugin configuration/state, Project/Session metadata, attachment indexes, material user settings, backup policy, remote-node metadata and runtime reconstruction manifests must have a recoverable persistent copy outside disposable runtime state.
17. **Use one versioned Recovery Vault.** Recovery assets must be discoverable through a versioned manifest with integrity metadata. First install after reinstall, or detection of an empty local state, must run Recovery Discovery instead of assuming a new environment.
18. **Secrets must survive uninstall safely.** Private SSH keys, API credentials and equivalent secrets must be encrypted at rest. Android Keystore may be used as a device-local convenience wrapper, but must not be the only decryption root for recoverable backups; a separate recovery key/password or equivalent cross-install recovery mechanism is required.
19. **Rebuild binaries, restore state.** Reproducible runtime binaries, caches and downloaded packages should normally be reconstructed from a versioned runtime manifest rather than copied back as the only recovery strategy.

## 3. Coding rules

- Kotlin is the primary Android language. Prefer Jetpack Compose for native UI.
- Keep platform/native recovery modules dependency-light and below all runtime/DSH layers.
- Keep DSH integration behind explicit adapters/interfaces; do not spread DSH implementation details across feature modules.
- No hidden destructive migration. Every schema/runtime migration needs preflight, backup and rollback behavior.
- Persisted formats require version fields and migration tests.
- Every privileged command must report provider, command/action, exit/result and audit timestamp.
- Every component update must have integrity verification metadata.
- No secrets in repository, logs, crash reports, session exports or screenshots.
- Recovery manifests must be versioned, integrity-checkable and source-agnostic. Detection may be automatic; destructive overwrite or privileged/secret restoration must remain explicit and auditable.
- Prefer structured APIs to parsing shell stdout when both are available.
- Do not fork official DSH Web UI for cosmetic preference changes.
- Keep repository guidance source-agnostic. Do not cite unrelated historical projects, old project paths, or previous implementations as design authority. Migrate only the abstract requirement, interface, verified constraint, or reusable mechanism needed by this repository.

## 4. Required quality gates

Before release or migration:

- App cold start with DSH runtime absent.
- Recovery Core start with intentionally broken runtime.
- Session restore after forced process kill.
- Update rollback after injected failed health check.
- Project/session relation migration test.
- Plugin Safe Mode test.
- File delete/restore test.
- Android reboot and Local ADB reconnect behavior test where supported.
- Upgrade from previous signed APK without deleting persistent user data.
- Reinstall recovery-discovery test with a pre-existing Recovery Vault.
- Secret-vault recovery test proving that uninstall/reinstall does not make the encrypted backup permanently undecryptable.

## 5. Change policy

If a future change conflicts with one of the non-negotiable constraints above, create an ADR explaining the conflict, alternatives and migration/rollback plan before implementation. Do not silently weaken these constraints.
