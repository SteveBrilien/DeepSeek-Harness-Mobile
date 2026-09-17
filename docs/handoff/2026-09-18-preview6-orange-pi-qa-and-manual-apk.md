# 2026-09-18 | Preview.6 Orange Pi QA / 手动测试包验收记录

## 范围和交付性质

用户同意暂缓 Android 无线 ADB 配对，优先在 Orange Pi aarch64 的隔离测试环境推进需求并在验证后提供 APK。因此本轮只制作 **0.4.0-preview.6-dev / versionCode 26 手动测试候选**，不将其声明为 G0–G7 需求全部交付、OriginOS 真实兼容验收通过或自动 OTA。`release/update.json` 保持旧版本，不通过 MCP 安装、覆盖、卸载、force-stop 或清理手机应用；旧 22022 SSH 隧道、手机救援隧道配置和签名私钥保持不变。

## 本轮源码及理由

沿用已有 Preview.6 工作树的 Android WebView 文件选择源 sheet（拍照 / 相册 / 文件）、FileProvider 私有相机缓存、多选 ClipData 处理及 UTF-8 文本编辑的 SHA-256 并发保护/原文件快照；修补 `RecoveryVault.status()` 和 `NativeFileManager.browserRoot()` 在 SDK30+ 平台权限查询异常时安全降级为未授权，防止错误扩权及 Robolectric fixture 非预期异常。额外修复 `AttachmentPickerPolicy`：网页仅接纳 PNG/WebP 时不提供 JPEG 拍照；相册选择器根据网页 accept 指定单一 MIME 或 `EXTRA_MIME_TYPES`，不可接受图片则拒绝相册入口；取消/结束时清空 accept 状态。新增 Robolectric SDK30 验证 JPEG/PNG、混合 accept、非图片及读授权。只更改 App 项目，不改上游 DSH/官方 Settings CSS，不引入读全部媒体的新授权。

## 可审计门禁（主机测试，不是手机真机）

- `android_unit_test` job `task-android_unit_test-42ae3df405b4463ba0fe` succeeded / exit 0；JUnit XML：app 32、core/recovery 12、core/runtime-android 15，总 59 tests、0 failures、0 errors、0 skipped，包含四项 Android11 文本编辑集成测试。
- `runtime_alpine_e2e` job `task-runtime_alpine_e2e-e89a0fe13628425084d0` succeeded / exit 0；完整新 Alpine/aarch64 rootfs、内置/在线 fallback DSH、原生 node-pty PTY、Web 认证与移动 UI 资源校验通过。
- Node 24.18.1 JS syntax、mobile UI policy、WebView compat policy、`git diff --check` 均 PASS。
- `android_lint` job `task-android_lint-11c7e64091c147fb8410` succeeded / exit 0；lint `0 errors, 23 warnings`，警告未清零，特别是 WebView 的版本适配/renderer 相关警告需要在真机证据下评审；不把警告直接认定为实际崩溃。
- `android_debug` job `task-android_debug-679ecba2493a4e5898be` succeeded / exit 0；AGP output metadata `applicationId=com.stevebrilien.dshmobile`, `versionCode=26`, `versionName=0.4.0-preview.6-dev`。APK ZIP 完整、核心 UI 和 WebView compat 资产 SHA256 与项目源码相同；dirty tree candidate SHA256 `e69877e06981cc244df7077c5fa5c9224de34790bf8d780919b7e7ce6edae238`、88,355,472 bytes。
- `android_signing_verify` job `task-android_signing_verify-bf4c79d4d9e04c208202` succeeded / exit 0；签名证书指纹与项目原证书相同（不导出私钥）。正式源冻结和 clean-build、再次 SHA 对照、远端下载验证需要独立完成后追记。

## 明确保留的阻断／未实现事项

- 没有连接真机 ADB；小主机沙箱没有 `/dev/kvm`、Android emulator 或 `qemu-system-aarch64`；Robolectric/Alpine/Chromium 测试无法代表 vivo OriginOS Android11 WebView151 的真实行为。
- 26 秒启动、抽屉闪影/惯性和跟手手势、顶部汉堡/右栏在各种会话内、键盘回收跳动、系统导航条意外出现、设置页面顶部留白、相机返回和多选 1/2/3/5 文件的 **服务端最终送达** 都没有在此轮真机量测；不可宣称修复。以前独立 Chromium 的官方 DSH Settings 几何与右栏测试只适用于当时对应源码和 Chromium。
- 跨授权项目目录/SAF、完整批量文件事务、Trash 原位置恢复、系统级完整备份和逐类恢复、端到端 PTY/ADB 终端权限等 G4–G6 大范围需求仍须 ADR、迁移/回滚与设备验收；当前候选不能等价为功能全部完结。
- Pi 侧 22023 救援 SSH 与 ADB 25555 曾验证在线，但本轮前检查时两端已掉线；断线恢复/OriginOS 长时保活与 APK 更新前后连续性未验收。绝不自动安装 APK 或干扰既有 22022 隧道。
- MCP artifact HTTP server 目前未运行，`publish_artifact` 返回 `FILE_SERVER_NOT_CONFIGURED`；若通过受控 Git 仓库 `release/` 手动渠道发布，必须新增唯一文件名、先冻结源、clean-build、签名及下载后的 SHA-256 复核，保留已有 APK/旧 OTA JSON。任何未经远端证实的 URL 不算已交付。

## 使用说明

本 APK 如发布，仅给项目所有者手动覆盖安装与反馈。不能卸载旧版或清除数据；安装器签名冲突/降级冲突需停止，不得以卸载或删数据修复。安全发布和手机更新仍为两个独立门禁。


## 2026-09-18 01:55 +08｜冻结、clean-build 与本地手动包

- 源代码与上述测试记录由 local commit `a55b15ce98fcf742fe9d5f6cd92e75a481423501` 固定，提交前后无未授权变动；构建时 `git_dirty=false`。
- `dshm_preview6_clean_build_temp` 任务 `task-dshm_preview6_clean_build_temp-9337364438cd44cfb122` succeeded / exit 0，实际运行 `:app:clean :app:assembleDebug`，build successful；清理后的 APK 88,355,472 bytes、SHA256 仍为 `e69877e06981cc244df7077c5fa5c9224de34790bf8d780919b7e7ce6edae238`，与 dirty candidate 完全一致。构建重用已受控验证的缓存与依赖，并非重新下载所有工具链。
- 构建后 `android_signing_verify` 任务 `task-android_signing_verify-6f7640d0b48a46f1a76a` succeeded；包标识、versionCode=26、versionName=0.4.0-preview.6-dev、ZIP完整性及三项内置 Web 资源 SHA 均再次符合。
- 在 `release/DeepSeek-Harness-Mobile-0.4.0-preview.6-dev.apk` 用原子独占创建方式保留该包，`release/SHA256SUMS` 只追加独立新条目，整包 `sha256sum -c` PASS；既有 release APK 和 `release/update.json` 未动。
- MCP artifact HTTP server 仍未配置；尝试发布 `artifact-e74755ad3e1149ef9ca07c4bacf970cf` 之前需确认发布服务就绪，否则通过受控 Git 发行文件提交，并验证远端 HTTP 200、大小和哈希。普通项目 sandbox 对 SSH GitHub 严格主机密钥校验失败，不得使用 StrictHostKeyChecking=no 或假设已经上传。只有受控 Git API 的实际推送及公开文件校验成功方可声明可下载。


## 2026-09-18 01:58 +08｜受控发布验收（手动测试文件，不是 OTA）

- `release/` APK 与 SHA256SUMS 由 local commit `e850d348593d3093a1489a9002652e30795a4f10` 固定；随后通过 Project `git_push` 的非强制 main 推送成功，未使用禁用主机密钥检查等绕过方式。
- 通过公开 HTTPS URL 完整重新下载 `https://raw.githubusercontent.com/SteveBrilien/DeepSeek-Harness-Mobile/main/release/DeepSeek-Harness-Mobile-0.4.0-preview.6-dev.apk`：HTTP 200，实得 88,355,472 bytes，SHA256 严格匹配 `e69877e06981cc244df7077c5fa5c9224de34790bf8d780919b7e7ce6edae238`。这是已核实的独立手动测试版直链，不表示任何 G0–G7 真机门禁通过。
- `release/update.json` 未更改；GitHub 对约84.26 MiB 单文件提示超过推荐的 50MB 但在本次受控推送中接受。未来建议使用 Release assets 或 Artifact server 而不是不断将大 APK 提交进仓库历史。
- 重要：手机救援端口 22023 当前掉线且自动重连未通过，禁止本会话调用 ADB 安装/覆盖、停进程或清除数据。用户自行安装前应先恢复并复测救援链接、核验兼容签名和数据备份，不允许通过卸载解决覆盖安装问题。
