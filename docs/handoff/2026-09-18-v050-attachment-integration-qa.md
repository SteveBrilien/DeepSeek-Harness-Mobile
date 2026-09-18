# DSH Mobile 0.5.0-preview.1-dev｜附件、抽屉与启动集成 QA（2026-09-18）

状态：**分阶段实现并实测；尚不满足六项完整发布门禁；不是 Release Note / 不是 APK 下载承诺。** 基线 HEAD `e2e8aad`；当前改动在本地待提交。只涉及本轮用户收敛的六项目标，不增列已确认的工作区/备份 UI，不要求 SSH 救援。

## 冻结运行时和入口取证

- 这轮必须以真正的嵌入种子 `dsh=0.1.5-rc.2` 为准，不使用旧的 `.mcp/tmp/dsh-npm-install-host` 的 0.1.2 npm 临时副本。实际解包的 `dsh-client-ui-conversation/lib/client.js` 的官方 file input 为 `<input type="file" multiple>`，`onPickFiles` 提取 `File[]` 并调用内部 `intakeFiles`，执行模型可用性/图片数量/单张体积/消息总体积检查，进而进入 owner 的 `addFiles`；官方图片/文件 rail 是单独注册的 **single** 槽位 `conversation.input.attachments`，官方提供 `ComposerAttachmentsOwnerProps.onAddFiles` 但仅作为其被渲染时的属性，不是对其他插件开放的独立 intake Service。旧版模拟粘贴方案严禁移植。
- 实现：新增独立 Cordis Browser 插件 `@dsh-mobile/dsh-mobile-attachment-sources`，只注册 `conversation.input.left` 与 `conversation.input.dock` 两个可组合 list slot。用户直接点击相机/相册/文件时同步触发**官方真实 input** `.click()`，仅在一次选择期间调整标准 `accept/capture/multiple`。`change/cancel` 或插件卸载后恢复原有属性。没有自建附件存储、第二条 upload queue、手造 FileList/粘贴事件、任意 `file://` 访问，Native 只提供系统 chooser 与有限的相机 cache FileProvider。选完多 URI 合并去重，只接收 `content://`；Native 无双重来源 modal。
- `dsh-client-ui-mobile` 包随抽屉修订为 `0.4.3-dshm.1`；新增附件插件包 `0.1.0-dshm.1`，两者由 `MobilePluginProfileCoordinator` 复制进用户现存 profile 且加入 managed presentation hash，保留非管理的用户 bundle。App 本身 `versionName=0.5.0-preview.1-dev/versionCode=27`，旧 release APK、`release/update.json`、runtime pin 未改。

## 橙派自动测试及结果（按当前代码阶段）

| 门禁 | 证据 | 结果与限定 |
|---|---|---|
| Android API 30 Robolectric / 单元 | `task-android_unit_test-20df6b6ed8b743088e42`，app 34、recovery 12、runtime-android 16 | 62/62 PASS，先前 png-only 摄像头用例错误已修为 ALBUM，避免 JPEG 越 MIME 约束。 |
| Lint | `task-android_lint-504e935bcc464f12a142`，`app/build/reports/lint-results-debug.txt` | Gradle PASS，0 errors / 23 warnings；历史警告尚未逐项消除。 |
| Runtime Alpine E2E | `task-runtime_alpine_e2e-a1829744115846db97df` | **最终 SVG 附件插件源码**复跑 PASS DSH=0.1.5-rc.2，含 embedded fast path、旧 profile 在线迁移、token/auth 与 managed plugin；旧 job `task-runtime_alpine_e2e-306fe8e148bf47df9a64` 仅保留历史对照。 |
| Node24 Cordis unit | `.mcp/tools/node-v24.18.1-linux-arm64/bin/node scripts/test-mobile-attachment-sources.mjs` | PASS 2 list slots、3 source click、属性改/还原、插件停用、丢失真实 input，不取代官方单附件 rail。最终 SVG icons type 校验也 PASS；本次 09:46 UTC 再次运行 Node24 两份脚本及 `test-mobile-ui-policy.mjs`，均 PASS。 |
| Node24 抽屉契约 | 同 Node24 `scripts/test-mobile-drawer-gesture.mjs` | PASS open/close、纵向、系统边缘、横向附件 rail、modal、cancel、unload；实际手指/帧未证明。 |
| Source | `git diff --check` | PASS，无空白/补丁问题。 |

## exact-current-profile Chromium 行为证据（390×780，非 Android WebView）

- 使用 `runtime_presentation_debug_current` 独立 TaskProfile 启动隔离运行实例 `127.0.0.1:13083`，仅测试沙箱中的临时 DSH_HOME，不用个人账户登录，不存 API 密钥。测试完成后只关闭该 browser session/测试 job，不触碰 Android 或旧隧道。
- 第一次真实加载曾暴露旧 fixture 缓存引起的双入口：同时显示官方回形针和插件 `+`，证据 `browser-f9265e19cf9a4027/screenshot-1789702563262.png`。修复插件 CSS 后刷新 fixture、重启仅测试 runtime，在第二次实际渲染中双入口消失：`browser-af38419355c14053/screenshot-1789702767073.png`。三入口按等宽显示在 composer 周围正常文档流，没有原生 `ModalBottomSheet`。该截图还是 Emoji/tofu icon，后续已在源码替换成内联 SVG，须做**最终源码字节对应截图**方可标最终版外观 PASS。
- 五份固定、无隐私的 8×8 PNG 文件放在忽略目录 `.mcp/tmp/mobile-attachment-fixtures/fixture-[1-5].png`。通过**官方真实** `[data-composer-card] input[type="file"]`：先放 1 张，官方 `Pending attachments` 有缩略图；点缩略图官方原图灯箱打开/关闭；移除图片后同次放 2 张有 2 条；再放第 3 张有 3 条；再放第 4+5 张合成 5 条，rail 出现横向导航。截图 `browser-af38419355c14053/screenshot-1789702908317.png`。未向浏览器注入用户私有图片。
- 输入纯测试文本并点击 **Send message**，Session 首条用户消息显示这 5 张图片和文本；随后官方 provider 返回明确 `MISSING_CREDENTIAL`，**不能宣称模型回复成功**。刷新页面、加载同一 Session 后再次看到 5 张消息图片和文本，图片均可异步解析显示：证明浏览器侧 input→DSH owner admission→用户消息→持久 Session→重新加载的这组链路；但**不包括 Android 系统返回 content URI/grant**，也没有分别测试单张/双张/三张各自独立发消息收到 Host 端回执。
- 无法由此认定 Vivo 真机、相机/相册/文件系统 intent、照片权限撤销、20 次手势、冷/温/热启动、真正的 DeepSeek API 模型输出已经 PASS。

## 与用户参考图的明确差距、后续门禁

1. 当前只有**已选官方图片 rail**，尚未实现用户授权后的 `MediaStore` 最近图片横向预选、点击单张直接入列。最近图与已选草稿不能混为一谈；实现前必须设计受控 `RecentMediaRepository` 与 `MediaBridgeV1`（固定 origin/main frame、document+session+permission epoch、受限 thumbnail/读取、撤销清理），并获得正式可审计 owner `onAddFiles` 接入点；不得绕过 single slot 或模拟 paste/change。
2. Native ActivityResult callback 合并、只接收 content URI、相机私有 FileProvider 的 API30 单测通过，但 OEM `ClipData` 多图、Android 11 权限/Activity 销毁、相机 JPEG/PhotoPicker/SAF fallback 仍属 DEVICE_NOT_RUN。
3. 抽屉/切页已有源码和脚本单测，Vivo 20 次、系统 back、设置 modal、WebView 151 帧/几何仍 DEVICE_NOT_RUN；startup 删除固定 520ms 等待不是启动 p50 的量化改善，缺三组冷温热 metrics。
4. 未具完整检查前不发布 APK、覆盖安装、更新 OTA 或宣称全部完成。用户不需要 SSH 救援，本轮亦未操作它。


## 09:50 UTC 最终源码门禁复核（本轮续跑追加）

- 以保留在工作树中的实际当前 SVG 版本复跑 `runtime_alpine_e2e` job `task-runtime_alpine_e2e-a1829744115846db97df`：**succeeded / exit 0 / `managed-attachment-source-plugin-ok` / `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`**。同时 `mobile_context_contract` job `task-mobile_context_contract-29cc317795b541b3ac13` succeeded / exit 0。
- Android 现阶段最后一次 Kotlin+Robolectric job `task-android_unit_test-20df6b6ed8b743088e42`：app 34 + recovery 12 + runtime-android 16，62/62 通过；Lint job `task-android_lint-504e935bcc464f12a142`：succeeded，0 errors / 23 warnings；无声称这些测试覆盖 Android 11 的真实 provider 行为。Node24 三份附件/手势/UI-policy 契约当前源码再次通过；`git diff --check` PASS。
- Chromium 浏览器曾有本次冻结 DSH Session 五图进入草稿、提交消息、reload 后重载五图的实际行为证据，**但最终 SVG 修改后的截图仍未得到**；这次独立浏览器导航返回 `BROWSER_ACTION_FAILED`，故不以旧截图充当最终外观门禁。测试用 DSH `/healthz` 和原生浏览器可访问性是不同层次。此失败不影响 Node/Alpine/Android 单元的已有 PASS，仍阻止端到端外观 PASS。
- 尚缺 MediaStore 获授权近期图库 + 正式 `onAddFiles(File[])` API（当前只能通过官方系统选择器入列）、Vivo 1/2/3/5 组图实际 Provider→Host 回执、抽屉真实 20 次及冷/温/热各 3 次启动 p50；上述任何项目都不可写成已完成。不读取私人图库、不请求用户 API key，SSH 救援不是门禁。


## 09:52 UTC APK 可构建性检查（非交付）

- 本轮最新源码 `android_debug` job `task-android_debug-2e0e92c6c33f4471b57e` **succeeded/exit 0**（172 Gradle tasks）；`android_signing_verify` job `task-android_signing_verify-622c961c5e2f4f9f9c67` **succeeded/exit 0**，证书 SHA-256 与历史证书相同，未更换密钥。
- 本地 `app/build/outputs/apk/debug/app-debug.apk` 为 **88,466,017 bytes**，SHA-256 `ec13be6128a4825a1ac8c3b73a56ebdf0ae52de6cff109dfb53a6f54421ace2f`；ZIP 确认含 `assets/runtime/dsh-mobile-attachment-sources/{package.json,cordis.patch.yml,lib/client.js}` 与新版 `dsh-client-ui-mobile`。`app/build.gradle.kts` versionCode=27、versionName=0.5.0-preview.1-dev。此包是未发布的当前开发候选；没有公开 HTTPS 地址、没有覆盖安装、没有设备或更新清单修改。
- **发布结论仍 BLOCKED**：MediaStore 最近预选与一次直接入列正式接口、设备 Android 11 picker grant / 五组独立 Host 回执、抽屉和动效实机验收、启动冷/温/热测量；浏览器最终 SVG 外观还需重取截图。因此禁止发旧 Preview.6 链接冒充新版，不触动 `release/update.json`。
