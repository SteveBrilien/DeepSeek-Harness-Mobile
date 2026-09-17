> 2026-09-17 09:29 continuation verified: current unpublished Preview.5-dev/code25 worktree PASSED Android Unit (new 3 file-safety + 6 chooser + 1 image-extension tests), Lint, APK build, stable signing and package/assets byte-for-byte integrity. One concurrent Alpine E2E **FAILED** waiting 30s for web token, isolated sequential retry PASSED all embedded/online/auth tests; root cause of flaky first run not established. ADB still has zero devices. APK SHA-256 `146f55405147c4b4980bfc373139973ce2c4fd62fbdcada2e5d71feee13aa1b5` local only; release/update.json remains Preview.2. Review final section in `handoff/2026-09-17-preview4-device-regressions-and-scope.md`. Unmet G1–G6 features/device gates remain explicitly open.

> 2026-09-17 09:29+08 local continuation: an unpublished Preview.5-dev worktree now includes Android ClipData/parseResult reconciliation with 6 API30 chooser tests, App settings/recovery IA first pass, single hierarchy workspace with authorized project shortcuts, native image read-only preview, and guarded file operations (no recursive self-copy, no root deletion, staged copy, original-preserving save). See `handoff/2026-09-17-preview4-device-regressions-and-scope.md` latest section. **No true Android multi-file/send proof, no three-entry chooser or full backup restore; device acceptance remains blocked. New final build/test/signature results must be recorded separately.**

> Latest verification 2026-09-17: local-only Preview.5-dev/code25 APK now **built and signed** (NOT published); final scoped Rightbar/Settings source passed runtime Alpine E2E, Android Unit/Lint, clean debug packaging, stable signing and packaged plugin byte-for-byte SHA comparison. No connected ADB device, no private upload or true send, no real-device IME evidence. See handoff/2026-09-17-preview4-device-regressions-and-scope.md final testing section. Do not publish APK or OTA as completed G1–G6.

> Follow-up evidence 2026-09-17: Preview.5-dev/code25 remains UNRELEASED. In an isolated, genuine Session at 320/360/390 CSS px, official right-sidebar button/fullscreen Files panel/close cycle and native Settings viewport geometry work with the narrowly scoped mobile UI fix. DSH browser accepted a batch of 3 synthetic TXT and 3 synthetic PNG with TXT upload completion and PNG thumbnails; this does NOT prove Android multi-select or send (the fixture has no LLM credential). See `handoff/2026-09-17-preview4-device-regressions-and-scope.md` for detailed facts/remaining gates. G2 native chooser, G3 camera/gallery/file picker, G4 unified files, G5 App Settings/Recovery, G6 terminal and real-device IME remain open. No new public APK/OTA.

> 2026-09-17 Preview.4 owner feedback amendment: See `handoff/2026-09-17-preview4-device-regressions-and-scope.md`. The published Preview.4 manual APK regresses native DSH Settings width; G1/G2/G3/G4/G5/G6 are NOT completed. An isolated unpublished Preview.5-dev code25 / mobile UI 0.4.2-dshm.2 candidate removes the sidebar transform containing block. Chromium 360/320 viewport modal width is recovered; device validation and remaining user requirements are still outstanding. Do not confuse the previously published Preview.4 APK with this unbuilt, unreleased patch; do not promote OTA or install over device data without gates. Prioritize rightbar, file multi-selection true-send and user-visible workflows, and keep data/permission contracts intact.

# Immediate Next Actions

## 2026-09-17 更新：Preview.4-dev 手动测试包（不是 OTA）

用户明确要求交付当前整改候选 APK；`release/DeepSeek-Harness-Mobile-0.4.0-preview.4-dev.apk` 已经独立公开为**手动覆盖安装测试包**，SHA256=`c602d109f1c6137276841bdd2b81867bb50b1287892c5eb5a9c4fd6380e87dd7`。`release/update.json` 仍保留 Preview.2，不应把以下 2026-09-16 历史“仅文档”描述当作当下状态。详情及具体测试 job 见 `handoff/2026-09-16-implementation-status.md`。

当前优先让用户安装反馈 G1 抽屉/会话标题/右栏、G2 1/2/3/5 件真实附件发送、IME/system bars；Vivo ADB 仍离线，未验收的功能必须保持未验收。之后独立处理项目文件层级 UI、设置/Recovery（后者必须先做授权根/迁移/回滚评审，不得隐式改变用户数据合同）。

Last updated: 2026-09-16 (after Preview.3 owner feedback / documentation handoff).
Current **manual-test APK**: `0.4.0-preview.3` (`versionCode=23`), candidate source commits `1039491` / `eaa26e4`; user has installed and reported regressions. **Public in-app update manifest remains Preview.2 (`versionCode=22`)**. The Preview.2 source freeze `4f11518e5ff2e4e45fb1d71877f2dc772a86730a` is historical, not the Preview.3 freeze. Target DSH generation: `0.1.5-rc.2` from `config/dsh-runtime.properties`.

## Active handoff — execute the documented remediation slices, not a blind UI rewrite

Owner feedback at 19:40–20:24 on Preview.3 identifies blocked multi-file upload, overlapping conversation title, lost visible right sidebar toggle, IME/body bounce, non-preferred fullscreen drawer, system-navigation overlap, and major Native Workspace/Settings/Recovery/Terminal information-architecture problems. Current work is **documentation only**; fixes have not been implemented or verified.

1. Read [issue inventory](handoff/2026-09-16-device-ui-issue-inventory.md) (source/observation/hypothesis separated) and [remediation/acceptance plan](handoff/2026-09-16-ui-remediation-and-acceptance-plan.md) (ownership, data/permission boundaries, slices G0–G7 and gate matrix). `UI_BASELINE.md` v0.3 amends **planned UI**: one directory-oriented Workspace UI with Project shortcuts, without modifying ADR 0001 Project/DSH Session distinction.
2. G0 acquire sanitized device evidence and preserve user data; G1 fix navigation/IME regressions within Cordis UI + Android OS owner; G2 reproduce and repair 2/3/5-file upload through chooser→DSH **send success**; G3 optional user-authorized camera/gallery/files sheet; G4 unified native file manager (authorization-root / recoverable multi-file operations); G5 functional backup/restore and Settings IA/Runtime state reporting; G6 terminal effective execution-domain compliance with ADR 0007; G7 gate and release.
3. Do not mark any slice complete because these two docs exist. Stage independent patches and tests with owner approval. Keep official DSH Settings CSS and underlying DSH semantics; only fix responsive geometry. Do not touch `release/update.json`, change signing, clear app data or announce a new APK without real gate results and owner approval.

## Historical P0 — Vivo/OriginOS Preview.2 cover-install acceptance

The following section records the earlier Preview.2 gate, not the current Preview.3 outcome. Preview.2 was to be cover-installed without clearing app data. Its Android-only paths were the document chooser returning `content://`, IME/Composer geometry, and immersive system navigation. Review also included the old narrower sidebar dismissal strip, right-side DSH surfaces, Native Settings task split, in-app `settings.yaml` editor, Tokyo Night, DSH font-size stepper and cold/warm timing.

Do not delete Runtime slots, `persistent/dsh-home`, Projects, credentials, SSH identity or Recovery Vault state just to create a clean visual test.

## Historical automated validation — exact Preview.2 source only

For the exact Preview.2 runtime/UI source on Orange Pi ARM64: `android_unit_test` PASS; `android_lint` PASS; `mobile_context_contract` PASS; `runtime_alpine_e2e` PASS including authenticated DSH Web and old-profile reconciliation; fresh non-persistent MCP Chromium PASS at `360x708`; the real font-size stepper changed `14 -> 15 px` and back; a 24-byte TXT attachment passed through DSH's hidden file input and reached ready state; clean-commit `android_debug` PASS with `dirty=false`; stable signing PASS; the packaged release APK is byte-identical to the clean build and all release SHA-256 entries verify. The single captured browser 401 came from a deliberate initial unauthenticated root request. **None of those observations verifies Preview.3 multi-file upload, OriginOS IME or drawer performance.**

Preview.3 host-side Unit/Lint/Debug/signing/static gates have separately been observed PASS, with prior Alpine E2E PASS and later reruns failing from npm/parallel temp cleanup; see `handoff/2026-09-16-ui-motion-and-startup-preview3.md` and the issue inventory for exact limitations.

## P0 — Preserve user state and security invariants

A/B Runtime slots are rebuildable; persistent DSH/user state is not. DSH Web remains loopback-only by default. Keep `file://` access disabled for Web upload; only user-authorized content URIs should cross the native chooser boundary. Never include DSH token URLs, API keys, SSH material or `settings.yaml` secrets in ordinary bug reports. Project metadata is separate from real files; unregister never means physical delete. A Vault existing does not prove verified restore.

For architecture/invariants see `docs/HANDOFF.md`, `docs/ARCHITECTURE_V0_2.md`, `docs/REQUIREMENTS_BASELINE.md`, `docs/DATA_RECOVERY.md` and accepted ADRs; Runtime history is in `docs/INSTALLATION_RUNTIME_RESEARCH.md`.
