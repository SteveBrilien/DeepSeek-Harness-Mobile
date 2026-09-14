# DeepSeek Harness Mobile Architecture V0.2

Status: migration in progress for the 0.3.0 alpha line.

## 1. Why V0.2

The first implementation proved that the phone can host a local DSH runtime, the official DSH Web Client, native files/projects/terminal/recovery surfaces, and A/B runtime installation. It also exposed an architectural problem: presentation code currently owns too much orchestration.

`ChatScreen` currently performs runtime inventory checks, service dispatch, readiness polling, WebView creation/configuration, browser diagnostics, navigation policy, renderer recovery and DSH presentation. Settings similarly routes runtime management, recovery, update and diagnostics through one screen. This makes failures hard to classify and allows lifecycle/recomposition details to leak into runtime behavior.

V0.2 separates the control plane from the presentation plane and makes logs/cache state bounded, versioned and diagnosable.

## 2. Top-level model

```text
Android process
├─ App Kernel
│  ├─ Runtime Supervisor          desired state / startup state machine
│  ├─ Presentation Registry      DSH profile/plugin generation
│  └─ Diagnostics Registry       bounded diagnostic streams
│
├─ Native UI
│  ├─ Home                       observes supervisor; hosts presentation only
│  ├─ Workspace                  projects + files
│  ├─ Terminal                   execution surfaces
│  └─ Settings
│     ├─ Runtime
│     ├─ Recovery
│     ├─ Diagnostics
│     └─ Update
│
├─ Web Host
│  ├─ one retained WebView
│  ├─ origin/navigation policy
│  ├─ renderer lifecycle
│  ├─ presentation-version cache coherency
│  └─ read-only health evidence
│
├─ DSH Presentation Plane
│  ├─ official DSH Web Client (active baseline)
│  ├─ WebView compatibility plugin (active, root viewport only)
│  ├─ mobile context plugin (active, non-visual)
│  └─ DSH/Cordis mobile UI plugin (dormant until baseline passes)
│
└─ Runtime Control Plane
   ├─ RuntimeForegroundService
   ├─ RuntimeControlPlane
   ├─ AndroidRuntimeManager
   ├─ A/B Runtime slots
   └─ DSH process
```

## 3. Hard ownership boundaries

### App Kernel / Runtime Supervisor

Owns the app-process view of desired runtime state. It is idempotent: multiple UI observers cannot trigger multiple startup operations. It converts runtime/service details into a small observable state machine:

```text
Idle -> Inspecting -> Starting/Updating -> Ready(endpoint) -> Failed
```

The supervisor may dispatch the foreground service and poll the control plane. Compose screens may request `ensureStarted()` or `retry()`, but do not implement the startup loop themselves.

### Runtime Control Plane

Owns install/start/stop/rollback, runtime slots, local endpoint readiness and process health. It must not depend on Compose or WebView.

### Web Host

Owns exactly one retained WebView for the shell lifetime. It configures WebView once, enforces local-origin navigation policy, handles renderer death and authentication failures, and manages browser-cache coherency when the DSH mobile presentation generation changes.

It does **not** rewrite DSH layout DOM/CSS. Normal presentation mutations belong to the DSH/Cordis browser plugin.

### DSH Presentation Plane

Owns DSH document layout and DSH-native interactions. During the baseline phase the official DSH Web Client runs without the mobile UI plugin. True-device raw testing proved that Android WebView still collapses `html/body/#root` to zero height, so a separate WebView-compat package owns only that root viewport contract. WebView/Runtime correctness can therefore be proven independently of responsive UI overrides. After the baseline passes, mobile drawer/composer/settings adaptations return only through a versioned DSH/Cordis browser plugin. Android may collect read-only health evidence but must not become a second CSS implementation.

## 4. Presentation descriptor and cache coherency

A Web launch is not just a URL. The control plane exposes a descriptor:

```text
DshPresentationDescriptor
├─ launchUrl          ephemeral authenticated local URL
└─ generation         managed mobile presentation generation
```

The Web Host remembers the last generation it rendered. The generation includes the active presentation mode (currently `dsh-webview-compat-v1`) as well as managed package versions. When that generation changes after an APK/profile/plugin update, it clears WebView resource cache once before loading the new descriptor. Cookies/storage remain independent. This prevents a new on-disk Cordis plugin from being paired with stale cached browser JavaScript.

## 5. Home startup contract

Home must never silently become a blank black surface.

```text
Supervisor Inspecting/Starting -> native progress surface
Supervisor Failed              -> native error/retry surface
Supervisor Ready               -> Web Host
Web renderer fatal             -> supervisor/presentation error surface
```

A loaded HTML document is not sufficient evidence of a healthy presentation. Debug builds retain read-only page health probes; production uses runtime endpoint health plus WebView main-frame/renderer failures. A presentation watchdog may surface a native recovery affordance but must not mutate DSH DOM.

## 6. Logging policy

Logs are diagnostic evidence, not an append-only user timeline.

- install/start telemetry: current operation only; a standalone runtime start begins a new operation log;
- DSH process log: rotating bounded files while the process is running, not only at process start;
- WebView diagnostics: rotating bounded files, verbose probes only in debug builds;
- Settings diagnostics reads bounded tails on demand;
- UI must not render an ever-growing complete log in the normal Runtime management page;
- clear/export actions operate on explicit diagnostic stores.

Default retention target for alpha:

```text
install/start telemetry  <= 256 KiB current operation
DSH process log          <= 2 MiB current + 2 MiB previous
WebView diagnostics      <= 1 MiB current + 1 MiB previous
```

## 7. Settings information architecture

The existing single Recovery screen is decomposed conceptually into four destinations:

- Runtime: installed version, state, start/stop/reinstall/rollback;
- Recovery: backup vault, permissions, restore and repair;
- Diagnostics: health checks, bounded Runtime/WebView logs, clear/export;
- Update: application/runtime update status.

Migration may keep shared implementation components temporarily, but navigation and ownership must follow these boundaries.

## 8. Dependency rule

```text
Compose feature -> app-level coordinator/contracts
Web Host -> runtime presentation descriptor + Android WebView
Runtime Supervisor -> RuntimeControlPlane + RuntimeForegroundService
Runtime implementation !-> UI/WebView
Recovery core !-> DSH/WebView
DSH browser plugin !-> Android Compose internals
```

## 9. Migration slices

### Slice A — reliability foundation
- introduce Runtime Supervisor outside `ChatScreen`;
- add versioned presentation descriptor and one-shot WebView cache invalidation;
- bound/reset runtime logs;
- keep existing visible UI behavior.

### Slice B — presentation extraction
- move retained WebView/client/navigation/diagnostics out of `ChatScreen` into Web Host;
- make Home a small state renderer;
- add presentation-ready watchdog and evidence.

### Slice C — Settings split
- separate Runtime, Recovery, Diagnostics and Update destinations;
- remove full diagnostic logs from normal Runtime page;
- add clear/export actions.

### Slice D — module convergence
- move generic bounded logging into `:core:logging`;
- move app state contracts into stable application modules if justified by size/tests;
- add device/browser contract tests for each boundary.

## 10. Non-goals

V0.2 does not reimplement DSH chat natively, does not replace the official DSH Web Client, and does not make remote infrastructure required for app startup. The purpose is to make the existing local DSH architecture deterministic, observable and replaceable at its boundaries.
