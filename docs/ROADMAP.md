# Roadmap

## V0.1 — Architecture scaffold

Goal: freeze boundaries before implementation.

- [x] Agent maintenance contract
- [x] Architecture baseline
- [x] DSH compatibility rules
- [x] Development rules
- [x] Data/recovery policy
- [x] Plugin architecture
- [x] Update/self-repair policy
- [ ] Android build bootstrap
- [ ] Module skeleton
- [ ] CI/build script

## V0.2 — Native shell of the app

Goal: APK opens and Native Recovery Core works without DSH.

- App navigation: Chat / Projects / Files / Terminal / More
- Recovery Center placeholder
- Native file manager MVP
- Text editor MVP
- Recovery Terminal MVP
- structured logging
- persistent app metadata store

Exit criterion: intentionally remove local DSH runtime and confirm Files/Editor/Recovery still work.

## V0.3 — Local runtime

Goal: launch real local Linux userspace + Node + DSH on arm64 Android.

- runtime slot manager
- rootfs install/verify
- PRoot/native process supervisor
- Node runtime
- DSH install/start/health
- local HTTP/WebSocket endpoint
- official DSH Web Client in Chat
- mobile compatibility layer

## V0.4 — Project context

- Project registry
- import existing directory
- create project
- Git detection metadata
- Primary/Attached project relations
- Project↔Session many-to-many metadata
- `@Project` mention resolver
- DSH project-context plugin
- Files/Terminal/Chat project linking

## V0.5 — Privilege gateway

- StandardProvider
- Local Wireless ADB pairing/discovery/reconnect
- structured Android capability bridge
- Android/ADB Terminal
- optional Shizuku adapter
- RootProvider API + read-only system defaults

## V0.6 — Session durability

- DSH-native persistence integration
- Mobile session index/search
- Fork/tree UI
- Trash
- recovery from valid prefix/copy
- tool-outcome-unknown recovery UI
- attachments/reference backup metadata

## V0.7 — Plugins

- DSH plugin manager integration
- plugin compatibility diagnostics
- Safe Mode
- Mobile Extension API v0
- sample resource-monitor extension
- OrangePi plugin-set comparison/import

## V0.8 — Updates and backup

- in-app update checker/downloader/verifier
- APK install flow
- A/B runtime update
- plugin rollback
- on-phone recovery vault
- OrangePi incremental mirror
- restore single file/project/session

## V0.9 — Remote runtimes

- Runtime registry
- OrangePi target
- Azure target
- explicit execution target UI
- remote project/session/tool surfaces

## V1.0 — Personal daily-driver baseline

- stability burn-in
- OEM/Android compatibility matrix
- migration tests
- backup restore drill
- release signing and rollback drill
- maintenance runbook

## Deferred / iterative decisions

These are intentionally not blocking V0.1:

1. exact plugin marketplace/install UX;
2. model/provider credential UX;
3. notification and long-running background execution policy per Android version;
4. exact persistent vault physical layout and database mirroring strategy;
5. final APK release channel/signing automation;
6. OEM-specific compatibility workarounds;
7. remote-runtime transport protocol details;
8. final UI gestures, tablet/foldable polish and visual theming.

Decide these through ADRs as real usage/testing reveals constraints.
