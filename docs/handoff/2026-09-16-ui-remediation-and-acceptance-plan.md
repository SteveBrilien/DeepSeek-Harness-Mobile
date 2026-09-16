# 2026-09-16｜移动端 UI / 功能整改实施规范与验收计划

状态：**供项目所有者执行的实施方案；当前尚未实现、构建或发布**。问题与证据 ID：[`2026-09-16-device-ui-issue-inventory.md`](./2026-09-16-device-ui-issue-inventory.md)。本规范是既有架构下的产品/UI 细化；不能越权替代已接受 ADR、数据保护规则或 DSH 官方语义。

## 1. 实施总原则与不可突破的边界

1. **保留官方 DSH Web Client**：Home 的会话、消息、右侧栏、插件、模型、主题、官方 Settings、附件状态/发送均由 DSH 拥有。只在版本化 `dsh-client-ui-mobile` Cordis 浏览器插件中作可复现、可撤销的窄屏适配；不得在 `ChatScreen`/Compose 中用 `evaluateJavascript` 修改 DSH DOM/CSS，不得覆写 DSH API、伪造附件状态、复制一套模型/插件设置。参考 `DSH_COMPATIBILITY.md`、ADR 0008/0010/0011；注意 ADR 0009 已由后续基线阶段取代，旧版文字不再是当前实施依据。
2. **隔离 WebView 根视口兼容层**：`@dsh-mobile/dsh-webview-compat` 仅修 `html/body/#root` 的尺寸契约；不把侧栏/弹窗/IME/设置视觉补丁塞进这个包。UI 插件禁止接管 root viewport/`100dvh` 规则；失败时能够禁用 UI 适配回到“官方 DSH + 最小兼容”进行差分验证。
3. **Android Native 层负责 OS 生命周期**：Compose/AppShell 管系统 Insets、IME、原生四项底栏、原生设置、文件、终端、Recovery；Web Host 负责唯一长驻 WebView、`onShowFileChooser`、`content://` 授权、导航/renderer 生命周期和受限诊断。任何跨层动作须走清晰契约，不能两个层各动画一遍/各保存一份状态。
4. **数据不可损毁**：项目真实目录、DSH event/session、附件、插件、凭据与 Recovery Vault 是非一次性数据。禁止以清除数据、卸载、删 `persistent/dsh-home`、删原始备份、切换新签名或仅恢复 Runtime slot 来处理 UI 故障。删除默认入 Trash；覆盖前快照；迁移先预检/备份/验证，失败回滚。`DATA_RECOVERY.md` 与 ADR 0002 优先。
5. **不扩大权限以换取便利**：无 Root 核心可用，默认 App UID；用户选文件用系统授权 `content://`，保留 WebView `allowFileAccess=false` 与本地 loopback/鉴权边界；相册/最近照片先走系统 Photo Picker/SAF 可用能力，未授权不做整个媒体库扫描；拍照必须显式用户动作和受限、可撤销 URI 授权，复用或审查受控 FileProvider，不把 update 专用 authority 当通用共享接口未经审核复用。不得为了新设计默认索取 MANAGE_EXTERNAL_STORAGE 或依赖 Shizuku/ADB。
6. **状态与命名准确**：命令已派发≠任务已完成≠健康检查通过；“已跨卸载保留”≠所有备份完整；“Android Local”≠ADB shell UID≠Root；“项目登记”≠物理目录复制；浏览器“附件预览”≠文件已上传/已发送。UI 必须按事实显示 pending/success/failure/unknown。
7. **版本和候选准入**：当前已有 Preview.3 手动测试 APK (`versionCode=23`)；公开 `release/update.json` 仍指 Preview.2。后续每个改动独立可回滚、递增版本/managed profile marker、验证稳定签名与原数据覆盖安装；未经所有门禁及用户批准，不改公开 update manifest、不宣告正式发布或已解决 26s。

## 2. 优先级、责任边界与完成定义

| 执行阶段 | 对应缺陷 | 主责与禁止捷径 | 阶段完成证据 |
|---|---|---|---|
| G0 基线冻结/数据安全 | 全部 | 仅记录 Preview.3 指纹、设备版本、用户数据与旧版截图；备份/脱敏；不要先重构 | baseline 截图、受控日志、可回退 Git commit/旧包、恢复路径确认 |
| G1 关键 Web 导航 + IME | WEB-01/02/03/04/05/06/07 | Cordis UI 插件处理 DSH 页面，Compose 只处理 OS/原生 bottom；不能靠固定按钮叠加 | 标题/左右栏/设置/返回/IME/系统 UI 真机矩阵通过 |
| G2 附件可靠性 | ATT-01 | Web Host chooser contract + DSH 原生附件协议；先证实 fault boundary | 1/2/3/5 件混合文件均收到、预览可取消且真正发送成功 |
| G3 附件选择 UX | ATT-02 | 原生选择 sheet 仅是入口，遵守 DSH `accept`/`multiple`，依系统媒体权限；不重做 DSH 上传器 | 拍照/相册/文件、横向预览、取消/拒绝授权/旋转稳定 |
| G4 工作区整合 | WKS-01/02/03 | Compose 文件浏览统一入口，`ProjectRegistry` 仍是独立元数据，`NativeFileManager` / 授权根负责文件 | 目录/项目跳转、选中操作、回收站、安全边界、离线恢复全部通过 |
| G5 设置/备份/运行时 | SET-01..05 | 原生设置 + Recovery/Runtime owner 拆分；不移动真正状态源 | 每项只一个入口，备份实际可选可恢复，操作完成可验证 |
| G6 终端语义/优化 | TER-01/02 | ADR 0007 执行域与 Native Recovery Core；不能 UI 假装 PTY/ADB | UID/模式/命令安全路由、无 Runtime 自救及键盘场景通过 |
| G7 全量回归/候选 | 全部 + WEB-08 | 按门禁发布，不删原数据、不更改签名 | clean-build/test/真机证据、SHA、交接更新、用户确认 |

P0 的 G1/G2 与 G5 数据安全阻断正式发布；G4/G6 可以独立切片交付但不能拖着基础文件/权限违约上线。每个阶段独立提交、每个阶段可单独关闭插件/恢复原生页面，不做一次性全量 CSS 重写。

## 3. G1：DSH 导航、抽屉、设置与键盘

### 3.1 标题栏与右栏入口（WEB-01/02）

- 首先抓 360×708 和 390×844 的真实 DSH header DOM/ARIA、bounding rect、CSS overflow/z-index；使用**原生 DSH 左/右侧栏开关语义**（`ctx.layout` 可用 API、slot 或可靠事件），不要直接以图标名字/硬编码 DOM child index 猜功能；若某 API 不支持，记录兼容性差异并保留原生控件作为退路。
- 左按钮成为 header flex/grid 的布局成员或通过官方 slot 注入；`36/44dp` 合理触摸目标，标题获明确 `min-width:0; overflow:ellipsis` 与可用空间。禁止再在 `top:10px,left:10px,z-index:340` 创建脱离标题流的覆盖控件；不同 DSH 版本选择器不可用时**降级为原生按钮**，而非透明遮挡层。
- 保留且验证右侧 DSH 栏入口/可关闭按钮，左右各自对应功能；同一位置不能同时渲染两个“左侧”入口；右栏的开闭状态不应让左按钮覆盖标题。长标题、插件入口、字号 14/16/系统大字、缩放/旋转逐一检查。

### 3.2 非全屏左抽屉与手势（WEB-03/04）

- 以旧参照图为体验目标：手机竖屏推荐抽屉约 `75–82vw` 且右侧至少保留可见/可点击遮罩区（建议最小 48dp；具体值由 320/360/390dp 实机测试敲定），不强制 100vw。遮罩属于独立可点区域，非前版 32px 空白死区；抽屉内容独立滚动且右侧主体不重新排版。
- 打开/关闭只有一个状态源：DSH 官方 `sidebar-collapsed`/layout 语义；插件仅将状态映射到 transform 与 mask。正常状态只对 transform/opacity 动画，动画期间真实点击/焦点有策略，结束移除 `will-change` 或按性能证据保留；尊重 `prefers-reduced-motion`。
- 手势状态机：从合适的应用内可用左边缘右滑打开（与系统返回边缘判定协调），打开后抽屉内左滑关闭；水平距离阈值、角度锁和速度阈值要区分会话列表垂直滚动、浏览器横滑附件及系统返回。手指移动时跟手 transform，取消/多指/弹层打开要回滚。点击遮罩、显式关闭按钮、Android 返回均关闭当前抽屉；右栏/设置更高层弹窗按 stack 优先返回，不把返回事件直接传给 Activity finish。
- `WEB-05` 仅优化 DSH **原生**设置弹窗的 mobile nav/header/scroll 容器（标题 + X 同行、分类条完整可水平触达、内容紧贴、关闭按钮不单占大行），保留原主题卡片、控件和逻辑；不借机重做原生设置/模型/插件页面。

### 3.3 IME / 三键导航空间所有权（WEB-06/07）

- 先做**可脱敏、仅 debug**诊断：同一个 monotonic 时间序列采集 Android `WindowInsets.ime`、systemBars、底栏测量/visible、WebView 矩形、Web `visualViewport`、Composer rect、聊天 scrollTop。录入 IME 进入/退出 10 次视频或帧采样；定位第一帧跳动责任层。
- 收敛到唯一有效内容底部边界：IME 展开时底栏不能参与内容高度/二次补白；底栏切换不能与 `max(bottomNavHeight, imeBottom)` 形成两个独立几何动画。**具体改法由上述证据决定**；优先让底栏视觉淡出不改变 WebView 已算好的布局边界，保持 WebView 实例与滚动锚点；不要全局固定滚动位置或注入 `scrollToBottom` 硬修。
- immersive 恢复应以 WindowInsetsController/Android 系统契约为依据，避免 focus/1.2s 回调在 IME 动画中反复设置 flags 导致新一轮 resize。系统三键导航、通知栏、截图/系统文件选择器属于 Android 受控 UI，允许按系统手势短暂出现，不得覆盖系统 chooser 或承诺永久隐藏。
- 验收：多行正文、空输入、已附 3 图、发送后、旋转/返回 Home、系统栏短显后，页面不二次跳动、末尾文本不遮挡、关闭键盘不误切到其他 native tab；单帧数据标注误差允许设计评审，不能只说“手感好”。

## 4. G2/G3：多文件上传闭环与选择面板

### 4.1 先定界，再修故障（ATT-01）

- 复现集合：单张 JPG、两张 JPG、三张 JPG、JPG+TXT、五个小文件、同名不同目录、第三个坏/不可读 URI、用户取消、Activity 恢复、离线/服务端失败；所有测试使用专用非私人样本。
- Web 层读取 `input[type=file]` 的 `accept`/`multiple` 与 DSH 的单次附件/发送状态；Native 层仅当网页请求允许多选时向 chooser 传正确 `EXTRA_ALLOW_MULTIPLE`/mode，并按 `FileChooserParams` 与系统 SDK 兼容处理 `ClipData`/`data`。**不可直接断言 `parseResult` 不能多选**；核对 `createIntent()` 返回的 mode、extras、flags 和实际 callback。
- 结果桥：保证每个 `Uri` 有有效 read grant（必要时使用 Android 标准 grant 流程）；对缺失/重复/不可读逐项检查且不读取/记录私密内容；WebView `ValueCallback<Array<Uri>>` **每个 chooser 请求恰好回调一次**（含取消/重入/销毁）；重入先取消旧请求，处理异常，不能把旧 ActivityResult 误投新 callback。日志只记匿名序号、数量、MIME 类别、长度是否可读、错误码和时间；禁止原始 URI/权限 token/文件名/内容进入一般日志。
- 若 native 2/3 个 URI 均返回，而 DSH 只处理 1 个，则在官方 DSH Web/插件兼容层调查上传队列与附件状态，不自行伪造 `FileList`、私自调用未公开 upload endpoint 或将多选简单拆成隐式重复 DOM 上传以掩盖 bug。失败逐文件反馈并保留其他就绪附件，明确发送按钮启用门槛。
- 功能完成标准：**选得上 + 每项可见 + 每项 ready/失败可区分 + 可单项移除 + 真正发送成功/服务端读取到全部文件 + 回到原会话无丢附件**；单纯 chooser 返回或出现 N 个空位不算成功。

### 4.2 三入口 UX（ATT-02，不阻塞 ATT-01 修复）

- 从 DSH 原生附件按钮走受控桥展示原生轻量底部 sheet：`拍照 / 相册 / 文件`。取消 sheet 返回原会话，不改变已输入文字或现有附件。保留官方 input `accept` / `multiple` 能力约束；不把任何来源的文件强行作为图片上传。
- 最近媒体预览：**可选增强**。优先依 Android 11 可用的系统媒体选择器/SAF 已授权 URI，明确授权范围与持久化规则；若无法稳定/合规取得最近缩略图，降级为三入口，不申请额外整库权限。拍照通过用户主动调用相机、受限 URI 临时文件/回收；取消清理临时文件，不写到不受控公共目录。
- 已选附件横向 filmstrip 要求最大高度受约束、可滑动、显示图片可放大、文档显示名称/大小、移除按钮有触摸面积；未知/预览失败明确占位和重试，不显示无解释白卡。预览只是 UI，不替代 DSH 上传成功/发送状态。

## 5. G4：工作区统一为文件夹浏览器（保留项目数据模型）

- 取消 native `项目 / 文件` 常驻二选一导航；保持底部四入口 `首页 / 工作区 / 终端 / 设置`。进入工作区显示目录入口、项目快捷收藏/最近位置、当前位置面包屑、文件列表。项目是**带稳定 ID/Session 关系的目录入口**，点击后导航到真实被授权的目录；可以单独“登记/取消登记项目”，**取消登记仅删除元数据**，物理删除必须单独风险确认。任何 `Project ↔ Session`、Primary `cwd` 和 `@Project` 规则继续服从 ADR 0001 / DSH_COMPATIBILITY，绝不能把项目变成 DSH 原生 Workspace 或改写 Session。
- 页面 hierarchy：紧凑标题行（搜索/新建/更多）→ 可折叠根与项目快捷入口 → 面包屑（横向可滚/省略中间目录；可点击）→ 列表。**隐藏平时无关控件**：未选择时不放置“复制/移动/删除”工具墙；单击目录进入、文件按类型打开；长按进入多选，选中后出现 contextual action bar；剪贴板非空时目标目录提供粘贴。消息用 Snackbar/状态行按实际完成结果短显，失败保留操作状态。路径/内部 ID 在属性详情展示，不作列表主体；权限/banner 只在异常或关键风险时出现。
- 基本操作：新建文件/文件夹、目录返回、排序/搜索/隐藏文件开关、重命名、复制/剪切/粘贴/移动、批量选择/批量处理、分享/导入（可授权时）、文本/代码编辑保存、类型预览、Trash/恢复原位或新位置、从文件到终端 cwd/从终端 cwd 到文件。首版竖屏单栏，不复制 MT 双栏；横屏双栏为独立后续任务。不可开放看起来可点但未实现的功能。
- **真实访问权模型**：现有 `NativeFileManager.browserRoot()` 只允许共享根或 Vault 根，`ProjectRegistry.registerFolder()` 可登记其它已可访问目录。统一 UI **不能仅用旧 browserRoot 根校验来推断项目都可打开**，也不能为体验把 `requireInsideBrowserRoot()` 删除。设计每次导航的 `AuthorizedLocation`（文件系统受控根/SAF tree URI、grant 生命周期、显示名、stable project ID 可选）、每个操作在同一受权根内校验 canonical containment、防 symlink 逃逸、跨根复制须双端可读写且按能力选择流/DocumentFile 路径；权限失效可重新授权、只读则禁用写操作。权限架构变更另写 ADR 并征得确认，保留 `targetSdk=28` 当前运行时原因（ADR 0005），不能为了 UI 更新 SDK/权限策略。
- 操作可靠性：批量分项进度/取消与逐项结果；重名冲突 ask rename/skip/replace（replace 先快照）；目录不能移动到自身/子目录；中断/ENOSPC/权限撤销不能无声产生半成品或删除唯一原文件。当前 `FileClipboard` 单 source、`selectedPath` 单值、`uniqueTarget()` 自动重命名和 `deleteToTrash()` 不保存原位置的能力缺口要有显式新契约。Trash manifest 需记原始受权位置/删除时间/类型，恢复时若原授权丢失或原路径重名则让用户选择新位置；Vault 内真正数据不应因误清 Trash 无法恢复。编辑器由占屏 92% dialog 改手机全页，退出未保存确认，写前版本/mtime/内容冲突检查，原子写、失败保留原件/快照，且不破坏 Native Core 与 Runtime 解耦。

## 6. G5：App 设置、备份恢复、Runtime 与诊断重组

### 6.1 Settings 首页结构（SET-01/05）

保留原生 Compose 的中性卡片、蓝色选中态、分组行，删除营销/自述副标题。首页只作为**功能路由和必要状态**：`应用（关于/更新）`、`数据（备份恢复/存储与权限）`、`运行环境（Runtime）`、`开发者工具（配置文件/诊断日志/恢复工具）`。不重复提供 DSH 的外观、模型、插件操作；若需要去 DSH 原生设置，做带真实跳转的明确入口，禁止“看似可点击却不响应”的外观行。二级页共用左返回/标题/操作反馈/危险操作提示；Android 返回先收 sheet/dialog 再返回一级，再切 tab。

### 6.2 备份与恢复（SET-02，发布阻断）

- 首页显示实际数据覆盖类别、备份目的地、最近一次**已验证成功**时间/大小/完整性、加密与跨卸载条件；`Vault exists`、manifest exists 不代表完整/新鲜。常用唯一主动作“创建备份”，历史备份列表可查看详情；导出另入口；导出诊断移到诊断页，安全模式移至恢复工具。权限未授权时显示明确范围/风险，不能向用户保证卸载后永不丢失。
- 恢复必须是闭环 `发现→列出可用备份→逐项完整性/版本/解密能力预检→类别/文件预览→冲突策略（保留双方/新副本优先）→先对当前数据做安全 checkpoint→明确确认→隔离 staged restore→验证→原子切换或 rollback→审计结果`。按单文件/项目/DSH 会话/配置/插件分别能力判定；底层 `restorePersistentDshHomeFromLatest()` 等是**部分实现**，不能直接包装成“恢复全部数据”按钮。重要恢复默认 fork/copy，不覆写唯一 damaged evidence。敏感 SSH/key 必须解锁、独立恢复凭据（不能只依赖卸载后失效的 Keystore），不得把明文放诊断或云盘。
- 必须支持失败、低存储、schema 不兼容、权限失效、中断恢复再启动的安全提示及可回退记录。远程镜像是可选增强，不是该设备自救先决条件。

### 6.3 Runtime 控制（SET-03）

- 顶部只显示当前可验证状态（未安装/已停止/启动中/已就绪/需修复）、版本与上次检查；A/B slot、详细启动耗时下沉详情。主按钮跟随状态：未安装→安装；停止→启动；就绪→重启/管理；错误→诊断/修复。停止、重装、回滚入“更多操作”并给出影响说明/确认；横向滚动不能藏关键风险按钮。
- 服务 action dispatch accepted、process started、HTTP/web ready/health verified 分段展示，只有最终检查通过才标“就绪”。合并健康项为紧凑中文状态列表，`UNKNOWN` 写“未检测”，按需展开 stderr/原始诊断；独立界定 DSH plugins 的健康信息由谁提供，不伪报。
- 保留 A/B verified rollback、Runtime 重装不碰持久数据的既有设计；启动耗时单独在诊断显示冷/热测试条件及区间，26s 只能在同机同条件实测比较。

### 6.4 配置、诊断、维护、更新（SET-04/05）

- `settings.yaml` 属开发者/高级配置；保留现有 AtomicFile 保存但补未保存离开提醒、配置语法与业务验证、应用/生效条件提示、预更改快照及失败恢复；不要把完整配置及 token 拿来做截图/log。
- Runtime 与 WebView 日志进独立有界只读查看器（时间戳、筛选、复制/导出、脱敏），不在普通设置长列表渲染数千行。含 DSH token 的浏览器链接是显式敏感操作，单独确认复制/外部打开、短期可用，不并入默认分享诊断文件；不能假设日志脱敏覆盖剪贴板/截图。
- 高级恢复工具包含引导重跑、安全模式和独立诊断导出；应用更新保持 AppUpdateManager 的 hash/签名/系统安装器/用户确认，Runtime/插件更新与 APK 更新区分。状态型操作必须异步收敛到结果，不仅弹“已请求”。

## 7. G6：终端页面与执行域

- 延续 ADR 0007 的**一个终端，多执行域**，UI 初始目标是“逐条执行命令的 native 控制台”，不能宣称已经具备完整 PTY；若未来要交互式 vim/top/ssh/ctrl-c/窗口 resize，请单独研发评审 PTY transport/生命周期/流控制及测试，不通过更换图标假装完成。
- 压缩标题与路径栏、长路径单行省略但可复制/展开；输出区为主可用面积；历史固定命令在显式入口，模式/有效执行身份在每条记录可查看；输入栏固定于 IME 上且不与底栏重叠。
- **修正实质安全冲突**：当前 auto-router 把 `pm/am/dumpsys/settings/input/cmd/svc` 选到 App-UID Android Local，违反 ADR 0007 对 shell-UID 命令的边界。先分 allowlist 与真实所需能力，Linux 使用 PRoot，Android Local 仅 App UID 能实际执行的命令；需要 ADB shell UID 而 provider 未连接则明确“ADB 不可用”，禁止降级冒充或静默执行失败。禁止自动 Root；Shizuku/无线 ADB 是可选且需实际在线+授权的独立 capability。保留无 Runtime/Node/DSH 时 native 故障终端可用（ADR 0002）。

## 8. 文件所有权、文档协调及需要新增 ADR 的情形

| 既有约束文件 | 本轮如何协调 | 若产生冲突的处理 |
|---|---|---|
| `docs/REQUIREMENTS_BASELINE.md` | 完整文件操作、多选/恢复、Project many-to-many、非 Root、自救、持久性均保留 | 不能把本计划作为降级 P0 功能的理由 |
| `docs/UI_BASELINE.md` | 视觉/四底栏保持；旧“工作区 项目/文件 switch”按用户新明确要求改为 unified explorer 的 UI 修订；项目仍是独立数据实体 | 只修正展示约定，不能宣称更改 ADR 0001；见文件内 2026-09-16 addendum |
| `docs/ARCHITECTURE_V0_2.md`、ADR 0002 | Web Host、Runtime、Native UI、Recovery 所有权不变；Settings 的 Runtime/Recovery/Diagnostics/Update 拆分是真实目标 | 如欲改变模块依赖必须写新 ADR |
| `docs/DSH_COMPATIBILITY.md`、ADR 0001/0006 | 不造第二份 Session，Project ID 稳定、主 cwd/附加项目与一次 snapshot 不变 | Session/API/prompt 行为变化单独走 ADR 和 contract tests |
| ADR 0007 | 终端 Auto 以实际 UID/权限为准；不可把 App UID 说成 ADB | 若改变执行域语义必须先新 ADR |
| ADR 0008/0010/0011 与 Preview.3 handoff | 官方 DSH + compat root-only + scoped UI Cordis；逐条恢复基线，不大范围 rewrite | 选择器/插件版本变化须更新 provenance/marker/测试，必要时退回 compat-only |
| `docs/DATA_RECOVERY.md` / `docs/DEVELOPMENT_RULES.md` / `docs/UPDATE_RECOVERY.md` | 恢复资产、原子、SecretRedactor、测试、A/B 与签名坚持不变 | 新的多根文件访问、Trash manifest schema、恢复事务等写 ADR + migration + rollback |
| `docs/NEXT_ACTIONS.md` / `docs/HANDOFF.md` | 记录 Preview.3 手动测试及本评估为待实现方案；历史 Preview.2 证据保留为历史 | 不覆盖原测试结论、不虚报新 APK、不可无确认推广 update.json |

计划的主要代码触点：`AppShell.kt`（Native tabs/insets）、`ChatScreen.kt` + Web Host（chooser only）、Cordis `dsh-client-ui-mobile/lib/client.js`（Web layout）、`SettingsScreen.kt`/`RecoveryScreen.kt`/`DshConfigScreen.kt`（native IA）、`ProjectsScreen.kt`/`FilesScreen.kt` + `ProjectRegistry.kt`/`NativeFileManager.kt`（unified explorer）、`TerminalScreen.kt` + 执行域 contract、`MainActivity.kt`（system bars）。检查已有能力和 API contract 后再决定新文件/模块，切勿直接删除旧数据源。

## 9. 分阶段测试矩阵与阻断门禁

- **每切片静态与单元**：`git diff --check`、版本/manifest/notice 一致性、JS syntax、`scripts/test-mobile-ui-policy.mjs`、`scripts/test-webview-compat-policy.mjs`、`android_unit_test`、`android_lint`；对 Project ID/授权根/URI result callbacks/IME state/restore transaction/terminal routing 写针对性单元或 Compose 测试。注意**现有 mobile-ui-policy 断言 `width:100%` 且禁止 `dshm-mobile-nav-backdrop`，与新窄抽屉正面冲突**：先以“非全屏 + 可点击遮罩 + 无死区 + state semantic”替换此旧测试契约，并同步实际交互测试；不能简单删掉全部断言或让 CI 仍为旧视觉背书。
- **浏览器基线**：固定 DSH `0.1.5-rc.2`、受控 Web profile，先官方+compat、再启 mobile UI，分别在 320/360×708/390×844 及横屏测初载、sidebar 反复开闭、rightbar、Settings、theme/Tokyo、插件/模型、会话/scroll/composer、`@Project` 不回归；DOM 结构/ARIA/控制台/网络与截图四种证据。`runtime_alpine_e2e` 须隔离临时目录/并行作业；此前有 npm `Exit handler never called!` 与并发删除污染的失败，不得把所有重跑写成绿灯。
- **真实 OriginOS 门禁**：覆盖安装，不卸载/清理；抽屉 20 次 + 长标题 + 右栏/弹窗，IME 收起/展开各 10 次（含 3 附件），三键导航/截图/系统 chooser、返回优先级；多选 1/2/3/5 项直到服务端附件处理成功；相机/相册拒绝授权、取消/轮换 Activity；文件根/项目外部根/SAF 授权失效/多选/冲突/Trash 恢复/低存储/Runtime 缺失；设置所有入口/禁用控件/备份恢复安全流程；终端无 ADB/无 Runtime 和权限命令。记录系统/Build/版本、脱敏时序及实际操作步骤。
- **性能与视觉**：真实设备冷/热启动各至少 3 次分阶段记录（不与旧用户单次 26s 直接归因）；抽屉触摸跟手、帧延迟与设置滚动在测试机实际观察；浅/深/东京夜色、字体放大、320–390dp、小屏与横屏、TalkBack/触摸区域测试；`prefers-reduced-motion` 行为可达。画面不出现白条/空洞/标题覆盖/双导航/失去右栏入口。
- **Recovery 阻断**：实际单文件与项目恢复、会话/插件分类恢复能力按支持范围明示；覆盖冲突、损坏 ZIP、无权限、模拟中断/重启与密钥恢复演练。若某类别仅部分支持，则不展示一键完整恢复。个人密钥不可进入测试 zip/普通日志。
- **发行**：clean source commit 后 `android_debug` 与 reproducibility `dirty=false`、签名证书保持旧身份、APK SHA/size、manual cover-install 数据不丢、`release/update.json` 版本/URL/checksum 仅在通过发布门禁并获用户明确确认后同步。若失败，回退到可验证旧 APK/插件 profile；不回滚用户已经更新的会话数据到旧格式，不尝试不可逆删除。

## 10. 执行交付物与完成报告模板

每个阶段必须提交：①变更 scope + 对应 issue ID ②原因证据/取舍 ③修改文件与代码级 boundary ④新增测试及原基线回归结果（PASS/FAIL/SKIPPED 原样）⑤真机视频或脱敏日志 ⑥数据/权限/签名检查 ⑦可用回退 commit 与实际手工测试 APK checksum ⑧尚未解决事项。每项单独标记 `未开始/实施中/待真机/通过/阻断`，不可把方案文件状态写作代码完成。

**用户本轮仅授权整理文档**：此计划不授权修改源码、删除数据、签名变更、运行安装/更新流程、推送远端或升级公开清单；后续由项目所有者按以上阶段执行、验证并决定候选版发布。
