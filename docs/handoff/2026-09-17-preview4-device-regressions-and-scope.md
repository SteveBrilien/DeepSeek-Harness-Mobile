# 2026-09-17｜Preview.4-dev 真实反馈、前序需求复核与纠偏清单

本文件在 `2026-09-16-device-ui-issue-inventory.md`、`2026-09-16-ui-remediation-and-acceptance-plan.md`、`implementation-status.md` 基础上增补；不覆盖旧的测试事实/ADR，不把本文件当成实现。输入为所有者 00:42–00:44 六张 Android 真机截图、明确反馈、前几轮需求、现有源码与隔离 Chromium 回归。截图未复制到代码仓库，避免带入会话/私人文件。Preview.4-dev/code24 已作为**手动测试包**发布，但 G1–G6 从未被标记为完整通过；之前交付声明强调编译/签名而没有用显眼的功能清单说明大量未实现项，属于交付沟通缺陷。公开 `release/update.json` 仍 Preview.2。

## 观察、根因证据与不允许虚报的状态

| ID | 2026-09-17 实机观察 | 工程定位、当前状态、验收要求 |
|---|---|---|
| WEB-03 | 左抽屉宽度达到期望方向，展开瞬间文字边缘闪影/灰影 | 宽度 ~80vw 已观察到；闪影仅为现象，`transform`/合成文字栅格是待验证假设。独立测 20 次真实设备帧时间、深浅主题、字体，不以静态宽度测试充当动画通过。 |
| WEB-05 / 新回归 | 点击左栏「设置」后整个 DSH 原生 Settings 跟随侧栏缩为窄面板，权限说明逐字折行，分类标签横向溢出 | **已定位结构性原因**：上游 Settings 通过 `sidebar.settings` slot 渲染于 Sidebar 内，官方 overlay 自身是 `position:fixed; inset:0`；Preview.4 在侧栏列加 `transform` 和 `will-change:transform`，使其成为 fixed 后代的新 containing block。360×708 Chromium 修复前 Settings bounding width=268px、侧栏 width=288px；将侧栏移动方式局部换为 fixed left（不变动 Settings 官方 CSS、没有转移 React DOM）后 width=341px，320×708 width=301px。静态/隔离 Chromium 通过**仅此几何项**，OriginOS、顶部留白、窄屏行排版和导航所有标签仍待验收。不得把 settings 全部列为修好。 |
| WEB-02 | 进入真实会话后右侧栏开关仍不可见 | 上游 `@deepseek-ai/dsh-client-ui-sidebar-right` 的 ExpandButton 注册在 `conversation.session.header.corner`，具 `data-sidebar-right-expand`、ARIA「Open right sidebar」；本地 UI 插件仅处理右栏列的 CSS，没有恢复入口/可达性。必须在**真实已选中会话**测按钮存在、显示/边界与点击、右栏展开后关闭；无会话 Hero 页不应伪造右栏按钮。不能硬造指向错误 ctx.layout 操作的图标。P0 未修。 |
| WEB-04 | 侧栏和右侧外露暗区手感/阴影与设置及上下文菜单不统一，未实现左右滑动开合 | 当前抽屉 `#dshm-mobile-drawer-backdrop` 只绘制侧栏右边 20% 面积且使用自定 rgba 黑色；上游 Settings 使用 `--dsw-alias-bg-mask-1` 加独立 backdrop blur；会话菜单又是独立浮层。明确状态层级/遮罩 token、内容 hover 规则、触控拦截优先级，别只换 opacity。按需求实现左缘右滑/侧栏内左滑且跟手，避让系统 Back/垂直滚动/附件/右侧栏；当前未做。 |
| WEB-01/06/07/08 | 汉堡标题遮挡在此前部分改善；IME 收回正文跳跃/系统导航复现与 26s 启动未提供这轮实测 | 标题行前一轮只在已识别会话 header 时插入；动态重挂仍需长标题/右栏/返回测试。IME 第一处几何跃变未采样，禁止继续乱改 Insets。系统三键导航归 Android 所有，不能承诺永久不可见；启动分段需同机冷/热各三轮。 |
| ATT-01 | 选中两个及以上文件仍不能全部上传；先前第三个空卡 | 代码只对 `FileChooserParams.MODE_OPEN_MULTIPLE` 设置 chooser multiple extra，并记录 ClipData/URI count；**不是修复完成**。先用专用非私人 1/2/3/5 项样本形成 `input[multiple] → Android Intent/ClipData → callback → DSH ready/error → send → 服务端确认` 全链。数据边界未证实前不擅拆 DOM 上传、不伪造 FileList、不给全媒体库授权。 |
| ATT-02 | 没有「拍照 / 相册 / 文件」三入口和横滑缩略图/已选预览 | **未开始**；先确保多选可靠，再做系统授权 sheet，近期图片仅在 URI 授权合法时显示；Android 11 不稳定时降级三入口。已选横向预览支持放大/删除/文档信息，失败不能留无解释白块。 |
| WKS-01/02/03 | 工作区仍「项目 / 文件」两页，重复新建和操作墙，未按文件夹层级浏览项目、无完整编辑/回收恢复 | `AppShell.WorkspaceSection`、`ProjectsScreen`、`FilesScreen` 两个路由仍原样。目标是**一个目录层级文件管理器 + 项目快捷入口**，四底栏不变；Project stable ID、Primary/Attached 与原生 DSH Workspace/Session 保持独立。目录/文件新建、查看编辑保存、重命名、复制/移动/批量、删除到 Trash 与恢复都需实际操作闭环，不能把底层单项 API 等同 UI 完成。外部根/SAF/跨根/Trash manifest 先 ADR、权限预检、迁移/回滚审核，不删除 `requireInsideBrowserRoot`。 |
| TER-01/02 | 终端布局、操作、键盘仍旧版 | 仅修复 Auto 把 shell-UID 命令误交 App UID 的路由安全问题；`OutlinedTextField(singleLine=true)` 和命令记录模式未变，不是完整 PTY。继续整合有效域提示、紧凑布局、历史入口、IME、自救；ADB/Shizuku 为可选真实授权能力，离线拒绝。 |
| SET-01..05 | 原生 App 设置页以及其 Runtime、备份、配置、诊断子页仍旧版 | 只有 DSH Web Settings 几何补丁，不代表 Compose App 设置已重构。目标：一级应用/数据/运行环境/开发者工具四组，唯一可执行入口；去掉不可点击的「外观」假按钮，保持官方 DSH 主题/模型/插件归属。Runtime 区分 accepted/started/healthy；备份必须可发现、校验、预览、选择、冲突处理、checkpoint、stage、验证/回滚，不能将“已发现 Vault”宣称能完整恢复；配置/日志要脱敏和原子保存；不删除原有高级工具。 |

## 本轮实际改动及验证范围（待版本/发行门禁）

- 只在 `dsh-client-ui-mobile/lib/client.js` 将抽屉列 `transform` / `will-change: transform` 改为固定定位 `left` 动画，避免上游 `sidebar.settings` fixed modal 被限制在侧栏中；加 `scripts/test-mobile-ui-policy.mjs` 负断言禁止再为侧栏建立 transform containing block。没有改 WebView root compat、官方 DSH 包/Settings CSS、Android 数据或用户 profile。
- 改动后 Node24 `--check`、mobile-ui-policy、webview-compat-policy、`git diff --check` 通过。独立浏览器旧/新 360×708 Settings 宽度由 268→341 CSS px；320×708 新宽 301px；抽屉仍 288/256px，右侧遮罩可点关闭。320px 宽时权限说明仍显拥挤，不能标记 Settings 体验通过。Chromium 不是 Vivo/OriginOS 证明。
- 这次**没有**多文件故障完成证据、没有右侧栏真实会话 DOM 的验证、没有 IME 的同时间轴诊断。以上均是 P0 余项。不得打包发布/升级 OTA，仅凭局部 CSS 修复推动新版本。

## 执行顺序（不是功能降级）

1. 先守住已回归的 Settings viewport、抽屉遮罩和标题区；获取真实 session header/rightbar DOM、处理右栏与抽屉层级/手势，做 320/360/390、长标题、菜单、Settings 并发测试。IME 与系统栏必须真机采样后改。
2. 多附件故障闭环最高优先：native 与 DSH 边界数量+逐项 ready+真正发送，出错时保留其他附件。完成后独立设计/实现三入口和预览。
3. 工作区统一文件浏览器从**现有受控根内的无损导航/新建/查看**开始；跨项目根授权、批量事务、Trash/恢复先 ADR/迁移评审。不能为了改 UI 改坏真实文件、Session 或权限。
4. 原生 App 设置 IA 与终端逐页实施：保留 Runtime/Recovery 安全功能，拆导航与状态/操作，真实备份恢复不完成就清楚标为部分支持。
5. 每阶段独立证据、测试与回退。合并发布前 Android Unit/Lint、E2E、浏览器、真机 1/2/3/5 附件、IME、覆盖安装数据保留、签名/版本一致性全验，获所有者确认再改 `release/update.json`。

**未授权/禁止**：Root、静默申请整盘媒体权限、卸载清数据、删除 Runtime/Vault/Projects/会话/SSH/旧 APK、改变签名或 targetSdk、私自将 DSH 官方 Web CSS 整包重写、把失败项写 PASS。当前所有个人截图保留在会话，不入 Git。

### 2026-09-17 自动化测试终态及设备阻断

- 当前未发布源的 `runtime_alpine_e2e`：`task-runtime_alpine_e2e-f4f4ecb8b916447cb273` **PASS / exit0**（embedded/online fallback、Web auth 与 mobile UI asset/version 同步）。
- `android_unit_test`：`task-android_unit_test-29cab7a566cd4044a0a2` **PASS / exit0**（161 Gradle tasks）；`android_lint`：`task-android_lint-c45a4bcfbcfa48229ddc` **PASS / exit0**（289 Gradle tasks）。Node24 syntax、mobile UI policy、WebView compat policy、diff check PASS。
- 独立 Chromium 中正式验证的是 Settings modal 几何（360宽：268→341；320宽：301）和抽屉遮罩关闭；**没有验证**所有顶部标签/授权文案的排版通过，更没有证明 OriginOS 动画、闪影、右栏入口、多附件最终送达或其他 G3–G6 功能。
- 最新 `adb_devices` 返回 `devices=[]`；没有安装、清理、读取手机个人数据。原 Preview.4 APK、签名与 OTA 清单均未改；Preview.5-dev 仍只是代码候选，未产出/提供新版 APK。

## 2026-09-17 follow-up / WEB-02 真实 Session 与 ATT-01 边界验证（后续更新，覆盖上方“尚无浏览器证据”的历史时间点）

### WEB-02：右栏为何明明有开关却打不开

已逐行核查 `@deepseek-ai/dsh-client-ui-layout` 与 `@deepseek-ai/dsh-client-ui-sidebar-right` 的当前冻结依赖：`computeColumns(360, ..., rightbar)` 在手机屏宽分配的右侧**桌面布局列宽为 0**，故 frame 仍标记 `data-rightbar-collapsed`；右栏真正打开时由 native sidebar-right `RightbarSeat` 报告 fullscreen，pane 使用 `[data-sidebar-right-panel="fullscreen"][data-sidebar-right-open]`。旧 UI CSS **错误地以“有桌面布局列”作为右栏可见的唯一条件**；右栏即使由官方 ExpandButton 成功设置为 open，移动端外层仍位于屏幕外。并且官方 `data-rightbar-fullscreen` 报告会等待打开过渡完成，因此仅检查该标志会让适配层出现串行的双重滑动/延迟。修复只在版本化 mobile Cordis UI 插件中增加 fullscreen/原生 open marker 对应 CSS，`RIGHTBAR_ATTR` 同步也观察 native open marker；不重造按钮、不调未公开 API、不修改官方 JS/CSS。

隔离 E2E fixture（独立 `dsh-home`、无私人账号、无正式用户文件）实测 **在真实已创建且已选中的 Session**：

- 360×708：官方 header 右开关 `Open right sidebar` bounding x=320,w=28；点击后 Files 右栏全屏 x=0,w=360，可见 `Collapse right sidebar` x=326；点击关闭后官方开关恢复且右栏退到 x=360。左汉堡 x=20,w=36，与标题/更多/右栏入口分离；左抽屉 x=0,w≈288、遮罩 x=288,w=72。左侧抽屉内 `Settings` 点击后原生设置对话框 x=10,w=341，关闭正常，没有再次限制在抽屉内。
- 320×708：原生右开关 x=280,w=28，Files 全屏右栏 x=0,w=320、关闭开关 x=286；关闭后恢复原开关。320 上长标题被省略，不能据此证明所有字号及多语种可用。
- 390×844：原生右开关 x=350,w=28，Files 全屏右栏 x=0,w=390、关闭开关 x=356；关闭后恢复原开关。随后针对“右栏本体动画完毕后才报告 fullscreen”添加对官方 `[data-sidebar-right-panel="fullscreen"][data-sidebar-right-open]` 的早期 reveal CSS + observer 条件，同样在该真实 Session 上重新打开，最终 x=0,w=390；仅有快照与状态证明，**尚无真机帧级数据/严格打开耗时比较**。browser console 查询没有日志错误。

以上是**Chromium 交互几何通过、而非 OriginOS 真机完成**；保留右栏与左栏/Settings 并发状态、手势、TalkBack、暗区/动画闪影/键盘相关门禁。官方右栏仅在真实 Session header slot 渲染；不应为了空白 Hero 页图标数量而伪造该入口。

### ATT-01：非私人合成附件在浏览器端的分界结果

检查冻结 DSH Web：`input[type=file][multiple]` 为原生设计，`onPickFiles()` 将整个 `FileList` 转成数组交给 `intakeFiles`；非图片经默认上限 2 worker 的上传队列排队，图片先生成本地 object URL。默认图片单消息个数上限在冻结包配置是 20，不能将手机“两张以上失败”凭猜测归因于上限。已使用仓库 `tests/fixtures/` 的三个 26B 人工 TXT、三个 32×32 人工 PNG，在隔离 Chromium 的 **同一次选择** 分别上传：三条 TXT 文件 tile 全部显示名字、大小，第三条初始 Uploading 后变为不再上传且 Send 启用；三张 PNG 均显示非空缩略图和独立移除控件；原生 DSH 的已选附件本来就有水平 filmstrip。浏览器工具还报告每批接收 3 个 fixture。该证据**只证实 Chromium → DSH composer/queue 的边界在该 fixture 可工作**，不证实 Android OriginOS `Intent → ClipData → WebView file input` 的可靠性，也不证实端到端模型发送。隔离 fixture 没有模型凭据，之前创建 Session 的文字测试返回 `MISSING_CREDENTIAL`；不得导入用户真实模型密钥或把这次记为“实际发送通过”。

故下一条 P0 必须在用户授权的真机或等价可控 Android 11 WebView 环境记录：一次选 2/3/5 文件的系统选择数量、`ClipData`、`parseResult` 返回数量、Web input 文件数量、单项 `ready/error`、最终服务端回执。诊断只含数量/匿名状态，不采集具体私人路径/名字/凭据。G3 **三入口预选择图片预览尚未实现**；不能将这次 DSH 原生“已选横滑预览”误报为用户所要的相机/相册/文件三入口完成。

此轮源码更改仅为之前 Settings modal 修复 + 上述 Rightbar 全屏状态适配 + 静态断言。已重跑 Node24 syntax / mobile-ui-policy / webview-compat-policy / git diff check PASS；当前该补丁之后的完整 `runtime_alpine_e2e`、Android Unit/Lint 结果必须另记录，不能借用上文早先测试作为本轮最终结论。非私人样本纳入 `tests/fixtures/` 便于复现；没有更动用户 profile/OTA/签名/公开 APK。

## 2026-09-17 本轮源码测试完成 / Preview.5-dev 仍未发布

以上 Rightbar early-reveal 是本轮最后一处应用代码修改；以下任务均在其之后从完整当前源码启动，而非复用旧包结果：

| 验证 | Job / 结果 | 证明范围 |
|---|---|---|
| Alpine Runtime E2E | `task-runtime_alpine_e2e-e970db2688224076b974`，succeeded/returncode0，`runtime-alpine-e2e: PASS dsh=0.1.5-rc.2` | embedded/offline path、online fallback、Web auth、UI 资产版本一致性，非 Android 视觉证明 |
| Android Unit | `task-android_unit_test-b098920e0ee44f74a160`，succeeded/returncode0，161 Gradle tasks | Android 单元测试，非 real WebView picker/IME |
| Android Lint | `task-android_lint-5996ee1eac6b410aa043`，succeeded/returncode0，289 Gradle tasks | 静态规则，非 UX 证明 |
| APK 本地打包 | `task-android_debug-2dab3457f65544d3aaca`，succeeded/returncode0，172 Gradle tasks | `app/build/outputs/apk/debug/app-debug.apk`：`versionCode=25`、`0.4.0-preview.5-dev`；Android 产物内部 `assets/runtime/dsh-client-ui-mobile/lib/client.js` 的 SHA-256 与仓库本轮源码均为 `585f0678db94136640bda2c2b8071c377e218db3c278414677cdf3616c6e4806`，`package.json` 同样逐字 hash 一致。APK 本体 SHA-256=`e7e8c929f3724886f83454b3e5ded4aaf34cc4124ec3a4026cf1639532e26e78`。该 APK 仅本地生成，绝不误称真机验收或正式发布。 |
| 签名 | `task-android_signing_verify-1dc441258c454e498334`，succeeded/returncode0 | 稳定证书指纹符合仓库既有值，不更换用户覆盖安装签名。 |
| Node24/Policy | `node --check`、`test-mobile-ui-policy.mjs`、`test-webview-compat-policy.mjs`、`git diff --check`：PASS | Scoped mobile CSS/JS/旧 compat 静态门禁；真实帧时序仍待设备。 |
| 真机 / OriginOS | ADB 查询：`no devices/emulators found` | **BLOCKED**：无真机截图/布局几何/上传回执/IME 证据，不能签发用户版。 |

此轮测试只在隔离 profile 中临时创建了一段 disposable Session，以及六个人工文件；无个人文件/私钥/模型密钥落入仓库。浏览器已关闭且专用调试 server Job 已取消，E2E 随后独立重跑以避免共用目录冲突。之前 Preview.4 已交付的 APK SHA-256=`c602d109f1c6137276841bdd2b81867bb50b1287892c5eb5a9c4fd6380e87dd7`、`release/update.json` Preview.2 保留原状。尚未 commit/push/公开上传本地 Preview.5。版本单调递增只代表区分构建，不代表所有 G 阶段验收完成。


## 2026-09-17 09:29 起续接：新功能/安全切片（此节覆盖旧文档的未开始描述，但不覆盖验收门禁）

当前修改在未发布 Preview.5-dev/code25 **工作树**进行，没有动正式包、OTA、用户数据或官方 DSH 包。

- **ATT-01 Android 多选桥修正**：新增 `AndroidFileChooserResult.resolve()`：仅 RESULT_OK 返回；MODE_OPEN_MULTIPLE 合并 `ClipData`、`Intent.data` 和 WebView `parseResult`，按首次顺序去重；MODE_OPEN 最多一项。单 ActivityResult launcher 未完成时拒绝后续重入并取消后者 callback，旧 chooser 原位保留；取消和 Composable 销毁重置 callback；日志仅计数。6 项 API30 Robolectric 合成 Intent 单测此前 PASS。**未得到 Vivo 系统文件提供方实际 2/3/5 文件回调、URI 可读性、DSH ready 与真正发送/服务端回执，不可写已修好。**
- **SET-01 UI 首切片**：Compose 设置首页按应用 / 数据 / 运行环境 / 开发者工具四组重排，移除无法点击的外观伪入口和首屏耗时堆叠；配置入口移至开发者工具，所有原有二级入口/Recovery 动作仍可到达。备份与恢复页面用 `FlowRow` 替代挤在单行的 6 颗水平滚动按钮，并将诊断导出/安全模式移入高级维护，明确告知“目前只能创建、导出、校验，选择并真正恢复尚未开放”，不伪称恢复已交付。
- **WKS-01 第一切片**：四个主导航不变，工作区默认目录层级浏览；项目元数据以文件根中的虚拟快捷入口显示，`管理项目`二级页沿用旧新建/登记/取消登记业务；项目跳转先通过 `NativeFileManager.list` canonical 授权检查，非授权项目明确拒绝而非扩大 browserRoot。创建文件/目录统一为单一新建入口，复制/移动/粘贴/重命名/删除只在选择或剪贴板有内容时出现；纠正路径前缀比较，项目项放入虚拟化 `LazyColumn` 避免大量项目一次性组合。**尚不是跨根 SAF 管理、批量文件管理、Trash manifest/恢复完成。**
- **WKS-02 交互/保护**：移动粘贴只有成功后清空剪贴板；在操作期间拒绝重复粘贴，失败保留待粘贴文件。文本编辑异步保存：失败保留编辑内容和错误提示、成功才关闭，未保存退出需确认；底层删除/移动/改名阻止浏览根、Vault 根、Trash 根；复制/移动目录到自身或子树拒绝；递归操作预检 symlink 避免越出受权根和无限循环；复制先写随机 staging 目录并成功后改名提交，失败清掉未完成 staging 但保留来源；文本保存禁用原先 rename 失败后直接 truncate 原文件的破坏性 fallback。新增 `FileOperationSafetyTest` 三组回归覆盖自复制、核心目录、递归 symlink。**不代表已有 Trash 原位恢复能力。**
- **PREVIEW 第一切片**：原生文件浏览器新增 JPG/PNG/WebP/GIF/BMP **只读图片预览**，后台解码限制源文件最大 25 MiB 和最大边 1600px，FileManager 原有受控根检查不变，失败显示明确错误；不向 WebView 暴露 file URI，切换/关闭忽略迟到结果。非图片仍使用原 UTF-8 编辑器（PDF/Office/视频、聊天附件选择前的图册 sheet、横滑预选择尚未实现）。

仍未关闭：抽屉实机文字闪影、左右滑开合/遮罩统一、IME 重排、系统三键重现、26s 启动、三入口、聊天附件真机发送、文件批量/Trash 恢复、完整设置恢复、终端全规格，以及 Vivo 真机完整可用性。**新原生修改之后必须重新通过 Android Unit + Lint + APK build/signature；Web runtime E2E/Chromium 证明范围同前，绝不将旧 job 成绩直接贴到新源码上。**


### 2026-09-17 09:29 起续接：最终本地测试结果 / 发布阻断（以此为最新）

| Gate | 结果（从续接源码运行，不借旧测试） | 限定说明 |
|---|---|---|
| Android Unit | `task-android_unit_test-411db7f51fd840cda9be` succeeded/exit0；161 Gradle tasks；新增 FileOperationSafetyTest 3/3、AndroidFileChooserResultTest 6/6、NativePreviewTypeTest 1/1 均无失败 | 代码/纯函数回归；没有真实 ContentProvider 和 UI image preview 动画 |
| Android Lint | `task-android_lint-c60133587bbd4493a258` succeeded/exit0；289 Gradle tasks | 静态检查，并非触摸、IME、备份恢复证明 |
| Runtime Alpine E2E | 并行时 `task-runtime_alpine_e2e-de99d42e6f3143ff8ad0` 在 token 链接等待 30s 超时退出3，**记一次确实失败**；Lint 结束后单独运行 `task-runtime_alpine_e2e-aaa47cec43f54774bf03` succeeded/exit0、完整 `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`，embedded/online fallback + Web token/cookie authentication | 初次失败目前仅定位到等待 server token 链接超时，不能断言必定由并发引起；后续可靠性需要重复时序采样和必要时单独修超时逻辑，不隐藏失败 |
| APK | `task-android_debug-fa9253ee5672401bad47` succeeded/exit0；172 Gradle tasks；`app/build/outputs/apk/debug/app-debug.apk`，88,788,621 bytes，SHA-256 `146f55405147c4b4980bfc373139973ce2c4fd62fbdcada2e5d71feee13aa1b5`，`versionCode25`、`0.4.0-preview.5-dev` | 是小主机**本地 Debug 候选包**，不是用户版发布或真机通过。APK 内 5 个 managed mobile UI 文件与当前源码逐字节相同（包括 client.js SHA-256 `585f0678db94136640bda2c2b8071c377e218db3c278414677cdf3616c6e4806`） |
| 签名 | `task-android_signing_verify-737076a4dc104203b111` succeeded/exit0，既有 SHA-256 签名证书匹配 | 未改签名、min/target SDK |
| 静态 UI/版本门禁 | Node24 JS syntax、mobile UI policy、WebView compat policy、git diff --check 均 PASS；`release/update.json` 仍 Preview.2/code22 | 未改公开升级地址/Manifest/旧 APK |
| Android 11 Vivo 实机 | 最新 `adb_devices` 返回 `devices=[]` | **BLOCKED**。没有多附件真正发送回执、OriginOS 动画/设置截图、覆盖安装的数据校验；不能以构建或隔离 Chromium 代替 |

本次可确定的“已实现”仅为局部代码与上列测试范围；未完成完整三入口、聊天发送回执、抽屉闪影/手势、文件批量/Trash 恢复与跨授权根支持、全格式预览、终端重构、全量备份恢复，以及真机性能/键盘验收。请不要把 `previewImage()`（仅原生文件管理器的受限只读图片）写成聊天附件选择之前的相册预览，也不要把只有备份导出/校验写成恢复成功。
