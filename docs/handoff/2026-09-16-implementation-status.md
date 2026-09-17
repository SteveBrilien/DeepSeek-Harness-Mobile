# 2026-09-16｜Preview.4-dev 分阶段实现与真实验收状态

当前总状态（2026-09-17）：**已按用户要求提供独立手动测试 APK，仍未达到 G1/G2 完成条件，也未发布 OTA**。本轮用户已明确授权依据 `2026-09-16-device-ui-issue-inventory.md` 与 `2026-09-16-ui-remediation-and-acceptance-plan.md` 开始实施，但要求未明确事项及时报告，不允许擅改架构/风险策略。原方案末尾“当时仅授权文档”是历史事实，本文件记录新的实施授权。G0 原始状态见 `2026-09-16-implementation-g0-baseline.md`。

## 实际改动及状态

| 阶段/问题 | 本轮实际交付 | 状态与不应宣称的结论 |
|---|---|---|
| G0 | 记录 HEAD/Preview.3/Preview.2 OTA 分离、原有 dirty/untracked、ADR/权限/真机可用性；明确开发候选身份 | 主机记录已完成；真实 Vivo 数据快照/ADB 基线未取得，不声称 G0 全设备门禁完成 |
| G1 / WEB-01/03 | `dsh-client-ui-mobile/lib/client.js` 现以 `min(80vw,360px)`、至少 48px 遮罩区展示非全屏抽屉；遮罩调用官方 `ctx.layout.toggleSidebar()`；菜单按钮改为三横线、尝试挂入具名 DSH 会话标题行并占位，未找到标题行则保留原启动位置；加入弹窗遮罩交互保护、桌面断点隐藏 | **代码完成但仅静态门禁通过、真机与带登录会话的 Chromium DOM/React 验收待办**。没有实现跟手左右滑；右栏入口尚未定位；标题 React 重排和弹层 z-index 有潜在兼容风险，不可宣布 G1 完成 |
| G1 / WEB-05/06/07/08 | 没有改官方 DSH 设置布局、IME/原生导航栏/26s 启动链；保留 Web root compat 不变 | **未修复、待可脱敏真机 IME/insets/DOM 帧证据**，不根据截图猜测键盘跳动根因 |
| G2 / ATT-01 | `ChatScreen.kt` 在网页请求 `MODE_OPEN_MULTIPLE` 时显式设置 chooser `EXTRA_ALLOW_MULTIPLE` 与授权读取 flag；回调仅记录 resultCode、ClipData 个数、URI 个数与是否存在 callback 等匿名数据 | **诊断/桥接补充，不是“多文件已修复”**；须真机选取 1/2/3/5 件并核对每项 ready 和服务端最终收到的文件。未证实 `parseResult` 或 DSH 后端为根因，不打印原 URI、文件名、内容或 token |
| G3 | 暂不重做官方附件 UI；拍照/相册/文件 sheet、最近预览涉及系统授权与文件选择链，应待 G2 闭环和权限设计确认 | 未开始 |
| G4 | 旧 Project Registry 稳定 ID、DSH Session primary cwd 仍保留；未重写文件根校验/权限或 Trash schema | **未开始**。跨项目外部目录访问与 SAF、Trash 恢复元数据、批量事务若改变持久合同，先新 ADR/迁移/回滚评审；禁止直接删 `requireInsideBrowserRoot` |
| G5 | 不伪造“完整恢复成功”；设置/运行时重组尚未修改 | 未开始。现有 backup create/verify/export 不能包装成完整恢复流程；真实恢复必须预检/快照/隔离/验证/回滚 |
| G6 / TER-02 | Auto 模式中 `pm/am/dumpsys/settings/input/cmd/svc`（含 `/system/bin/` 直呼）不再以 App UID 假冒 ADB：未连接则显示不可用并保留命令；Android Local 仍明确 App UID；新增纯路由单测 `TerminalRoutingTest.kt` | 此安全冲突已修正且路由单元通过；不是已实现 ADB provider/完整交互式 PTY，也不宣称 shell 脚本深度静态识别 |
| G7 | 本地 `0.4.0-preview.4-dev` / code24、Cordis `0.4.2-dshm.1`、`mobile-v6` marker；第三方 notices 记录本地差分与 hashes；`release/update.json` 未改 | **本地脏工作区测试候选，非可推广发行版**，禁止自动公开 APK/OTA |

## 截至目前的测试证据（状态须以最终 Job terminal result 为准）

- Node24 JavaScript syntax：PASS；`scripts/test-mobile-ui-policy.mjs`：PASS（旧 width100%/禁止 backdrop 断言被新的实际合同断言替换，而非删除）；`scripts/test-webview-compat-policy.mjs`：PASS（仅修复测试 fixture 对 Node24 只读 navigator 全局的设置/恢复）；`git diff --check`：PASS。
- `android_unit_test`：首轮 PASS (`task-android_unit_test-a17cb47d134f490990c8`)；新增终端路由后复跑 PASS (`task-android_unit_test-d23dc719d62c47d79d1f`, 161 tasks)。
- `android_lint`：PASS (`task-android_lint-38d465c834c7455f9bce`, 289 tasks)。
- `android_debug`：PASS (`task-android_debug-d495587bd2a4419aa756`, dirty workspace candidate; not clean-release reproducibility)。
- `android_signing_verify`：PASS (`task-android_signing_verify-70f08d59e47e46c6a9dc`)，证书维持旧指纹。
- `runtime_alpine_e2e` 首轮 `task-runtime_alpine_e2e-b60fb3adad5c427dbadf` **FAIL/exit3**：前半段 embedded/online packages、兼容策略、profile reconciliation、native module passed；后半段在线 fallback DSH Web token exchange 在 120 × 250ms 内未得到 token。任务诊断笼统分类 SOURCE_COMPILE 与最后实际阶段不一致，不能写为 PASS；不公开日志里的 token URL。隔离复跑 `task-runtime_alpine_e2e-e94c3e6f8ef9497abf37` 在本文件创建时进行中，结果待追记。
- `adb_devices`：没有已连接手机，无法完成 Vivo/OriginOS 原生 picker、多选最终发送、IME/正文弹跳、系统三键/截图、抽屉手势、冷/热启动 3 轮与覆盖安装数据保留检查。

## 需要证据或设计确认才能动的阻断点

1. Rightbar 恢复：必须获得真实“已打开会话”的 DSH header/Rightbar 控件 DOM、ARIA 与 bounding rect，对应回归截图；不能凭 icon/数组位置插一个伪按钮。当前 13083 临时 DSH Web 服务无运行，旧 Chromium 会话仅可看到缓存/断连页面。
2. IME 弹跳：需要同一单调时间轴的 Android IME/systemBars/nav frame/WebView rect/Web visualViewport/Composer/scrollTop + 真机帧，确定第一处几何跃变；不得无证据修改 Insets 或 Web 滚动。
3. 文件多选：需要匿名 `chooser clipCount / returnedCount`、DSH ready 数量与实际发送文件数，以定位 native/DSH 分界；否则不能决定是否需要上游 DSH patch。
4. G3 最近媒体：若 Android11 系统能力/现有 URI grants 无法稳定提供最近预览，应降级三入口而不是增加全盘媒体权限；拍照 FileProvider 必须独立安全评审。
5. G4 跨受权项目根、SAF、Trash 恢复原位置、批量复制中断与 G5 分类别恢复属于数据/权限合同变更，必须先 ADR、升级/降级路径、真实失败演练及用户审核，不能作为本 UI 切片的隐式副作用。

## 回退与交接

原有 Preview.3 的手动测试 APK/签名与 `release/update.json`（Preview.2）保持原状；此候选未推 Git、未改公开下载、未安装设备、未删 Runtime/DSH/Projects/Recovery Vault/SSH/分析证据。Git 原有变更与新候选变更混在同一工作区，正式 clean-release 前必须按文件分离提交、保持可定位源 commit 并运行全套设备门禁。此文档仅记录实际状态，不能替代两份完整方案与接受的 ADR。

## 22:30 +08 续作验收增量（不覆盖以上历史状态）

- 上轮隔离 E2E 复跑 `task-runtime_alpine_e2e-e94c3e6f8ef9497abf37` 最终 PASS；此前首轮 FAIL 保留。本次 CSS 微修复后再跑 `task-runtime_alpine_e2e-8fe8777e879f48c8a19a`，终态 **PASS / exit 0**，包括受控 Web auth 与移动 UI asset 验证。
- 用 `runtime_presentation_debug_current` 的新临时 DSH Web 测试进程和 Chromium 360×708 完成真实授权访问：官方 Internal Testing Notice 的 Continue 在本次成功保存并消失，API key 提示选择 Configure later。先前“确认无法保存”不应泛化为稳定复现的产品缺陷；持久化触发原因尚未定位，未改官方 onboarding、未伪造用户确认。
- 发现原遮罩 button 虽只在抽屉右侧可见，但 bounding rect 覆盖 360×708；按其几何中心点击可能命中抽屉下方。因此仅在 Cordis UI CSS 将遮罩左边界限于 `min(80vw,360px,calc(100% - 48px))`（源码保留空格格式），不另造开关状态。新增 mobile-ui-policy 遮罩几何断言；Node24 JS syntax、mobile-ui-policy、webview-compat-policy、`git diff --check` 均 PASS。
- 在可清除的隔离浏览器测试根同步相同源码后重新载入：左抽屉实测约 288px / 360px；遮罩 `x=288,width=72,height=708`，点击遮罩后抽屉返回 `x=-288`。这仅证明 Home 上的非全屏抽屉与点击关闭，非真机或完整 G1。没有修改设备上的生产 profile。
- 现有 Android unit/Lint 仍沿用此前 PASS；本次修复后 `android_debug` 重建 job `task-android_debug-1bc30fe9ef38434a96f5` PASS，`android_signing_verify` job `task-android_signing_verify-3e9c210a0aba47baac4c` PASS、指纹不变。dirty-workspace APK SHA256 `c602d109f1c6137276841bdd2b81867bb50b1287892c5eb5a9c4fd6380e87dd7`、88316312 bytes；**非正式发行，不可用此结果冒充 clean build**。
- 13083 临时服务已主动取消、确认端口关闭；ADB `devices=[]`。浏览器可选择临时 workspace，但尚未取得真实会话 header/rightbar DOM。G1 的右栏入口、跟手手势、IME、官方设置完整交互，G2 的多选真正上传/发送仍阻断；G3–G5 未开始，G4/G5 数据/权限合同变更仍须 ADR/迁移/回退审核。
- 本次没有 push、变更 `release/update.json`、清理用户数据或发布新 APK。阶段完成标准仍以原实施规范为准；无设备实测不得宣称 Preview.4 已修复用户所见弹跳/多选错误。

## 2026-09-17 用户授权手动 APK 交付（仍非 OTA）

用户要求继续推进，并将当前调整后的 APK 提供给本人安装反馈；据此仅授权 **Preview.4-dev 手动测试包交付**，并未将 G1/G2、真机回归或完整备份恢复视为通过。人工交付时仅对匹配 SHA-256 的 `versionCode=24` / `0.4.0-preview.4-dev` 包提供独立下载，`release/update.json` 继续指向 Preview.2；不修改签名、应用数据或公开 OTA 状态。用户安装必须使用覆盖安装，不能卸载/清除数据；安装器报签名/降级冲突时停止，不执行破坏性修复。

2026-09-17 检查：MCP doctor `ready=true`，但此 secondary runtime 的 artifact server `running=false`、`remote_download_ready=false`；调用 `publish_artifact` 返回 `FILE_SERVER_NOT_CONFIGURED`，不能编造 MCP 已签名下载链接。采用与 Preview.3 相同的受控仓库 `release/` 手动测试包渠道时，应先冻结源提交、clean-build、签名/哈希验证、提交独立 APK，推送后核对真实 HTTP 下载与 SHA-256；不得发布更改过或未校验的链接。ADB 仍没有设备，G1/G2 和原生 IME/Settings/Workspace 验收保持阻断。

此外，`THIRD_PARTY_NOTICES.md` 中本轮 CSS 最后一处差分后的 `lib/client.js` SHA-256 原记录仍是上一轮值，现已修正为 `5bf0b7fe80b94fd92eb95a80183013715b163246c119077f42b8f994c53fb82c`，Node24 syntax、mobile-ui-policy、webview-compat-policy、diff-check 复核 PASS。此操作只同步溯源文档，不修改此前打包的 APK 字节。

### 2026-09-17 手动测试包交付结果（16:23 UTC 之前的验证）

- 受控源提交 `668d6ae8f164b78f38efc04c132541a491193b7d`；仅提交实现/测试/文档，不提交此前保留的三类真实设备/分析截图。项目 `.git/info/exclude` **仅本地**忽略这些已保留的证据，文件全部仍在原位置。干净工作树再构建 `task-android_debug-c6e1e7b7f18c42e28c91` **PASS，记录 `dirty=false`**，与先前未提交构建得到同一 APK SHA/大小；签名验证 `task-android_signing_verify-cc0ccf191ff441058372` **PASS**，稳定证书未变。
- 固定 APK `release/DeepSeek-Harness-Mobile-0.4.0-preview.4-dev.apk` / versionCode 24 / SHA-256 `c602d109f1c6137276841bdd2b81867bb50b1287892c5eb5a9c4fd6380e87dd7` / 88,316,312 bytes；复制与本地 `release/SHA256SUMS` 全部校验通过。单独提交 APK `1bcfff3ac4d8adefd1750dfbf40afc9da73c768e`，仅通过允许的 `main` 普通 push 发布此手动测试资产，**没有修改 `release/update.json`（仍 Preview.2/code22）**。
- GitHub Raw 手动下载地址：`https://raw.githubusercontent.com/SteveBrilien/DeepSeek-Harness-Mobile/main/release/DeepSeek-Harness-Mobile-0.4.0-preview.4-dev.apk`；远端实际 HTTP 200、Content-Length=88316312；独立流式下载的 SHA-256 与上述完全一致。原 Preview.3/Preview.2 APK 未覆盖或删除。MCP artifact server 仍关闭；不使用虚构的 signed URL。
- 当前源/资源 E2E `task-runtime_alpine_e2e-8fe8777e879f48c8a19a` PASS（上一轮 CSS 最终版）；本次 clean Git build 之后 `android_unit_test` `task-android_unit_test-8a35a93a83f74a4195ba` PASS（161 tasks）、`android_lint` `task-android_lint-7b0f604b7ebd479f8639` PASS（289 tasks）；Node24 syntax/UI/WebView policy/diff-check PASS。以上都不能替代无 ADB 连接的 OriginOS 真机验收。
- 交付性质仅手动覆盖安装与采集反馈。请在不卸载、不清数据前提下核对：抽屉开关和遮罩关闭、已进入会话的标题/右栏、原生文件多选 1/2/3/5 项到实际发送、IME 收起弹跳、系统栏以及设置/工作区；保留失败和未修复项。签名或安装冲突立即停止，不建议卸载、清理持久 Runtime/DSH/项目或凭据。


## 2026-09-17 真实 Preview.4 反馈：先纠偏，不能拿构建通过作交付完成

新增真机截图与按阶段需求核对见 `2026-09-17-preview4-device-regressions-and-scope.md`，保留以上旧状态为历史记录。Preview.4 实际仍存在设置模态缩窄、右侧栏入口不可见、抽屉文字闪影/遮罩不统一、多文件仍上传失败；三入口/预览、统一工作区、终端交互与原生设置/备份恢复仍未交付。用户并未认可先前 G1/G2 或整体软件完工。

局部定位：上游 Settings 在 `sidebar.settings` slot 内渲染 fixed overlay，Preview.4 抽屉 transform/will-change 使它受限到 288px 侧栏。源码已在未发布 Preview.5-dev/code25、UI 0.4.2-dshm.2 改为 fixed drawer 的 `left` 动画（未修改上游 DSH Settings CSS / WebView root-only compat）。隔离 Chromium 360×708 对照 Settings panel 宽 268→341 CSS px、320×708 新面板宽 301px；打开/关闭抽屉可操作；320px 内容仍有狭窄说明行，完整 Settings 体验和 OriginOS 实机仍未通过。Node24 syntax、mobile-ui-policy（新增防 transform containing-block 负断言）、webview-compat-policy 与 diff-check PASS。不能把未采样的闪影/速度改善写 PASS。

具体执行顺序、权限/数据 ADR 门禁及未实现项由新文档逐项列明；不能把无真机、无最终附件送达和无恢复演练的方案或单测当功能交付。新候选没有替换旧 release APK/更新清单，也没有安装在用户手机上。

### 2026-09-17 自动化测试终态及设备阻断

- 当前未发布源的 `runtime_alpine_e2e`：`task-runtime_alpine_e2e-f4f4ecb8b916447cb273` **PASS / exit0**（embedded/online fallback、Web auth 与 mobile UI asset/version 同步）。
- `android_unit_test`：`task-android_unit_test-29cab7a566cd4044a0a2` **PASS / exit0**（161 Gradle tasks）；`android_lint`：`task-android_lint-c45a4bcfbcfa48229ddc` **PASS / exit0**（289 Gradle tasks）。Node24 syntax、mobile UI policy、WebView compat policy、diff check PASS。
- 独立 Chromium 中正式验证的是 Settings modal 几何（360宽：268→341；320宽：301）和抽屉遮罩关闭；**没有验证**所有顶部标签/授权文案的排版通过，更没有证明 OriginOS 动画、闪影、右栏入口、多附件最终送达或其他 G3–G6 功能。
- 最新 `adb_devices` 返回 `devices=[]`；没有安装、清理、读取手机个人数据。原 Preview.4 APK、签名与 OTA 清单均未改；Preview.5-dev 仍只是代码候选，未产出/提供新版 APK。

### 2026-09-17 follow-up WEB-02/ATT-01 限定证据

见 `2026-09-17-preview4-device-regressions-and-scope.md` 末尾 follow-up。对照冻结官方 DSH 布局与右侧插件，修复右栏全屏虽展示而桌面 track 仍 collapsed 时 UI plugin 隐藏整列的条件错误；加官方 `[data-sidebar-right-panel="fullscreen"][data-sidebar-right-open]` 早期 reveal，防止依赖延后的 fullscreen report 产生两段滑动。真实已选 Session 的 320/360/390 CSS px Chromium 完整右栏开/关及菜单入口宽度通过；DSH Settings modal 在同 Session 宽 341（360 viewport）。浏览器合成 3 TXT 与 3 PNG 各作为一批进入 DSH 原生 input，三项全部显示、TXT 终止上传且发送键启用、PNG 缩略图齐全，但 Android `ClipData` 与真正发送仍无数据，原生图片选择 sheet G3/工作区 G4/设置 G5/终端 G6 未实施。每项仍有明确阻断，不将浏览器上传算用户 bug 修复。此 follow-up 源码改动后的全量 E2E/Unit/Lint 若未重新跑完，必须单独注明，不借用先前 PASS。

### 2026-09-17 follow-up 后测试终态（以此为本轮最新，不更改早期历史）

Rightbar 全屏/原生 open marker 逻辑最终版后 Alpine E2E `task-runtime_alpine_e2e-e970db2688224076b974` PASS；Android Unit `task-android_unit_test-b098920e0ee44f74a160` PASS；Android Lint `task-android_lint-5996ee1eac6b410aa043` PASS；本地 Debug APK 构建 `task-android_debug-2dab3457f65544d3aaca` PASS；既有证书签名 `task-android_signing_verify-1dc441258c454e498334` PASS。内含候选插件的 APK 与源码 SHA 精确一致，APK versionCode25 / Preview.5-dev，仅在 `app/build/outputs/apk/debug/app-debug.apk`，未公开发布；详情哈希与所有功能阻断以 2026-09-17 回归文档的“本轮源码测试完成”节为准。ADB 仍无设备。G2 Android 多文件最终发送、G3 预选择、G4–G6 App 原生页、手势/键盘/动画实机仍不可宣称完成。
