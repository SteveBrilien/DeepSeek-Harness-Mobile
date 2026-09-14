# Checkpoint 1 Handoff — Candidate Identity

Date: 2026-09-14
Status: **Checkpoint 1 complete after final gates / stop before Checkpoint 2**
Baseline local checkpoint: `7412ed67c81b84a0b9be880a1414a432bc03686e`
Branch: `main`
Push status: **do not push**

Related documents:

- `docs/debug/2026-09-14-webview-rendering-debug.md`
- `docs/research/2026-09-14-webview-presentation-technical-route.md`

## 1. Scope actually completed

Checkpoint 1 implemented **candidate identity and fail-closed process reuse only**. It did not implement presentation readiness, WebView geometry, viewport repair, WebView instrumentation, virtual Android infrastructure, OEM acceptance, or mobile responsive UI.

Implemented behavior:

- Android desired presentation generation is content-addressed rather than semantic-version-only.
- Generation inputs include the pinned DSH seed SHA-256, pinned Web profile seed SHA-256, profile mode, active mobile-context asset-tree hash, active WebView-compat asset-tree hash, and presentation/handshake schema identity.
- Managed active plugin trees are checked by content, so same-version content drift invalidates active presentation reconciliation.
- Dormant `dsh-client-ui-mobile` is still integrity-repaired but does not participate in the active presentation generation and does not invalidate a healthy active presentation by itself.
- Runtime disk state records the desired generation associated with a successfully launched DSH Web process and clears that identity when the runtime slot changes or presentation ownership is explicitly stopped/invalidated.
- The in-process DSH process registry records the desired generation owned by the current live process.
- `RuntimeSupervisor` no longer treats an HTTP-ready endpoint as sufficient to bypass runtime/profile reconciliation.
- `AndroidRuntimeManager.start()` reconciles the APK-owned presentation contract before deciding whether an existing endpoint/process is reusable.
- Reuse requires all of the following: endpoint reachable, launch URL available, active profile already current before reconciliation, profile reconciled after reconciliation, live process generation equals desired generation, and persisted process generation equals desired generation.
- If active presentation bytes/marker were stale before reconciliation, the old DSH process is not reused even when reconciliation repairs the disk state successfully.
- Launch-token search is scoped to the current spawn's log offset rather than blindly accepting any historical token in the log tail.
- Unknown/unowned endpoints are rejected fail-closed rather than assumed current.

## 2. Review correction made before final gates

Final code review found one CP1 coherence defect in the first implementation: active managed files could be repaired by reconciliation before the reuse decision, after which the old already-running process could incorrectly look reusable even though it had loaded stale bytes.

The implementation was corrected so that `profileCurrentBeforeReconcile` is a mandatory reuse condition. After that production-logic change, all previous green test results were discarded and the complete CP1 gate sequence was rerun from the final source state.

## 3. Final automated gates

All task IDs below correspond to the final CP1 source state after the review correction.

- Android Unit Test: **PASS**
  Job: `task-android_unit_test-70a28435fbf54574bb86`
  Result: `BUILD SUCCESSFUL in 2m 4s`, 161 actionable tasks.

- Android Debug Build: **PASS**
  Job: `task-android_debug-41a5c16ca85946b4bce4`
  Result: `BUILD SUCCESSFUL in 49s`, 172 actionable tasks.
  Artifact record: `artifact-cf532a953e534bc4bae58623c5474d3f`.

- Android Lint: **PASS**
  Job: `task-android_lint-13d9b271c0f94f6793a7`
  Result: `BUILD SUCCESSFUL in 3m 4s`, 289 actionable tasks.
  Lint artifact: `artifact-b8a5530c5e1f437eab565649745d96e0`.

- Clean Alpine Runtime E2E: **PASS**
  Job: `task-runtime_alpine_e2e-2702d84edd274e18846c`
  Result: `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`
  Elapsed: 178.979s.
  This covered embedded DSH/profile fast path, authenticated Web startup, online fallback, bundled `node-pty`, PTY behavior, source-build fallback, old-profile reconciliation, and final authenticated frontend HTTP contract.

## 4. Exact-current-profile Chromium composition sanity gate

This gate was run only after the final Runtime E2E passed. It is **not** WebView evidence.

Environment:

- Fresh non-persistent Chromium session.
- Viewport: `360x708`.
- DSH process started from the exact current clean E2E root/profile on `127.0.0.1:13083`.
- Browser session had no persisted state.

Observed composition:

- DSH page rendered and reached the `Internal Testing Notice` surface.
- Main application shell was visible behind the notice.
- Application combo revision: `rev=a77cf275f366`.
- Combo explicitly included `@dsh-mobile/dsh-webview-compat/client.js`.
- Combo did **not** include dormant `dsh-client-ui-mobile/client.js`.
- Browser console contained no errors in the final authenticated fresh session.

Interpretation:

**PASS as a composition sanity gate only.** It proves the exact current profile can be served and rendered by Chromium with the intended active/dormant plugin composition. It does not prove Android System WebView execution, plugin effect activation in the Android `; wv)` branch, viewport geometry, root visibility, or presentation readiness.

## 5. Final diff / code review

- Tracked `git diff --check`: **PASS**.
- The three new untracked Kotlin files were additionally checked independently for `git diff --no-index --check` output and CR characters: **PASS**.
  - `PresentationCandidateIdentity.kt`
  - `PresentationCandidateIdentityTest.kt`
  - `RuntimeStateStoreTest.kt`
- Final code review found no remaining CP1-blocking defect after the pre-reconcile-current correction described above.

Review notes that remain outside this checkpoint:

- Launch-token discovery remains log-based. The current implementation scopes discovery to the current spawn offset and rotates before spawn; further hardening against an unusual second log rotation during the short startup window can be considered later, but it is not treated as a CP1 candidate-identity blocker.
- `PresentationReady` is still absent by design at this checkpoint. HTTP/process generation correctness must not be interpreted as rendered-page correctness.

## 6. Known Limitation — intentional fail-safe, not crash recovery

**Important:** if the Android app process is killed while an older DSH child process remains alive and continues to occupy port 3080, a new app process has lost the in-memory process ownership record. The current implementation therefore reports/rejects an **unowned endpoint** and will neither adopt that process nor kill it automatically.

This is an intentional fail-safe decision for Checkpoint 1. It prevents an unknown/stale process from being silently treated as the current candidate and prevents blindly spawning a competing process onto the same port.

It is **not** a completed crash-recovery solution. A later lifecycle design may add a durable, authenticated process-ownership/recovery mechanism, but that work is not part of CP1.

## 7. Explicitly not started

The following items remain untouched and must not be inferred from the CP1 green gates:

- Checkpoint 2 WebMessage readiness/observability: **NOT STARTED**.
- Real Android WebView geometry handshake: **NOT STARTED**.
- `#root = 0` smallest measured fix: **NOT STARTED**.
- Espresso-Web/CDP instrumentation: **NOT STARTED**.
- Cuttlefish/KVM environment: **NOT STARTED**.
- Vivo physical-device release gate for this architecture: **NOT STARTED**.
- Mobile responsive UI plugin redesign/reactivation: **NOT STARTED**.
- New APK publication/release: **NOT STARTED / release remains frozen**.

## 8. Required stop point

After committing this CP1 change set locally, **stop**. Do not automatically enter Checkpoint 2.

The next implementation session, when explicitly requested, should begin from the research document's Checkpoint 2 and add readiness transport/observability without changing viewport repair behavior. The first goal there is to reproduce the current Android WebView failure and classify it with execution/geometry evidence, not to apply another CSS fix.
