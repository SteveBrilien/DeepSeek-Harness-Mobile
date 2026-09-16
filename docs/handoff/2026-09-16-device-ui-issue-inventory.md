# 2026-09-16｜Preview.3 真机 UI / 功能问题清单（事实与待证假设）

状态：**用户反馈与只读源码核对完成；缺陷未修复，尚未进行新一轮真机/自动化验证**。配套执行方案：[`2026-09-16-ui-remediation-and-acceptance-plan.md`](./2026-09-16-ui-remediation-and-acceptance-plan.md)。

## 0. 阅读规则、基线与证据可信度

- 目标机：用户截图所示 Vivo / OriginOS / Android 11，手机截图宽约 691 px（对应 Web CSS 约 360 dp；截图缩放不能作为真实布局测量）；截图来自 **2026-09-16 19:40–20:24 本地时间**。截图属于用户现场反馈，当前没有对应完整触摸轨迹、运行日志、DOM/insets 采样；不得把截图视为因果证据。此前 26 s 启动为用户观测，非受控统计。
- 已下载供手工测试的 APK：`0.4.0-preview.3`，`versionCode=23`；`app/build.gradle.kts` 和 `dsh-client-ui-mobile/package.json` 分别标识 Preview.3 / `0.4.1-dshm.1`。`release/update.json` 当前仍广告 Preview.2 (`versionCode=22`)；**手动发布 APK 不等于 OTA 清单升级或 Preview.3 真机通过**。以 Git/清单/具体 APK 指纹辨别候选版，不能笼统称“最新正式版”。
- 证据分级：**O** 用户截图或明确复现陈述；**C** 已只读确认的代码行为；**H** 根因假设，须插桩验证；**D** 用户已明确的设计偏好。风险级别 P0=阻断关键功能/可靠性/数据安全，P1=主要交互回归，P2=视觉与效率。优先级仅是工程处理顺序，非已通过验收。
- **源文件真相**：Android `app/src/main/kotlin/com/stevebrilien/dshmobile/ui/{AppShell,ChatScreen,ProjectsScreen,FilesScreen,TerminalScreen,SettingsScreen,RecoveryScreen,DshConfigScreen}.kt`；文件/项目底层在 `core/recovery/.../{NativeFileManager,ProjectRegistry}.kt`；Web UI 改动在 `core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile/lib/client.js`；单独根视口兼容插件是 `dsh-webview-compat`。本文件是现状快照，不以代码路径中的 `Preview.2` 历史注释推断 Preview.3 已修复。

## 1. 缺陷与证据矩阵

| ID / 优先级 | 用户可见问题与现场证据 | 源码所能确认 | 仍需确认 / 禁止直接断言 |
|---|---|---|---|
| WEB-01 P0 | 19:40 会话标题“你好”左端被新浮动按钮盖住；标题/工具栏挤压。O | `client.js` 自建 `#dshm-mobile-nav-toggle` 使用 `position:fixed; top:10px; left:10px; z-index:340`，未给原生标题栏正式分配空间。C | 精确 DOM/header 元素与 ARIA，标题长/短、字体缩放及横屏下热区冲突。H |
| WEB-02 P0 | 右侧侧边栏的可见开关较前版丢失。O | 右栏 CSS 与开闭状态仍存在；新增开关只调用 `ctx.layout.toggleSidebar()`（左栏）。C | 原生右栏按钮是被遮挡、隐藏还是 DOM/slot 改变，需取实际 DOM/计算样式；不能声称右栏功能整体消失。H |
| WEB-03 P1 | 19:40 当前左栏全屏覆盖、不如 09-09 旧图保留主页面遮罩的窄抽屉。O/D | 移动插件 `[data-dshm-sidebar-col]` `inset:0; width:100%; max-width:none; translate3d`，全屏是当前有意 CSS 行为。C | 旧截图是体验参照而非可直接复制的源代码；验收需有遮罩、可点外部关闭且无右侧死区。 |
| WEB-04 P1 | 要求右滑打开、左滑关闭抽屉，并能跟手。D | 目前可见实现只有自建按钮的 click + DSH 原生 `toggleSidebar()` 和状态观察，未见完整抽屉触摸状态机。C | 与 Android 边缘返回、垂直滚动、右侧栏、弹层冲突必须在真机测试；“没有手势”仅指该插件实现。 |
| WEB-05 P1 | DSH **原生 Web 设置弹窗**顶部分隔/关闭行留白、分类末项被切；并非要求重做 DSH 主题 UI。19:40 截图。O/D | `client.js` 对 settings nav、header、options、overlay 有局部 CSS 覆盖。C | 需 DOM 几何与滚动容器归属；不得误把 App 原生设置当 DSH 设置。 |
| WEB-06 P0 | 发消息/收键盘后正文与 Composer 上下弹跳，底栏可能短时收放。19:40 文本页及多附件页。O | `AppShell.kt` 将底部预留设为 `max(56dp, WindowInsets.ime.getBottom())`，同时 `AnimatedVisibility(!imeVisible)` 为原生底栏加进入/退出动画；Manifest `adjustResize`。C | IME insets、Compose remeasure、WebView visualViewport、DSH scroll anchor 任何一者都可能相关；“底栏动画是根因”仅为 H。 |
| WEB-07 P1 | 20:16、20:24 若干截图系统三键导航与 App 底栏并列/覆盖；截图/系统 UI 后有恢复异常。O | `MainActivity` 使用 immersive sticky 与焦点/系统 UI 回调尝试重新隐藏；原生底栏固定 56dp。C | 第三方系统选取器由系统管理；不可承诺永不显示系统导航，亦不得为隐藏它禁用正常返回。 |
| WEB-08 P1 | 用户此前观察冷启动约 26s、抽屉卡顿与页面切换突变。O | Preview.3 加步骤 `durationMs`、有条件 warm reuse 和局部动画，主机侧测试曾通过。C | 速度改善没有真机冷/热分段对照；不可把“加了日志/动画”当性能修复。 |
| ATT-01 P0 | 20:08 系统文件选取器已选 3 项；返回聊天出现 3 个附件位置，其中一个空白；用户反馈一次多选 2 个及以上不能上传，逐个选可以。O | `ChatScreen.kt` `onShowFileChooser` 使用 `fileChooserParams?.createIntent()`；仅在 fallback `ACTION_OPEN_DOCUMENT` 上加 `EXTRA_ALLOW_MULTIPLE=true`；结果直接 `FileChooserParams.parseResult(resultCode,data)` 回给 WebView，缺乏逐项数量/权限/加载阶段诊断。C | **尚不知**是原始 `multiple`/chooser intent、`ClipData`、URI grant、WebView FileList、DSH 并发处理还是发送门禁出错；空白缩略图不等于该 URI 必然失败。H |
| ATT-02 P1 | 希望像 20:10 参照图：拍照/相册/文件三个入口、可横滑最近照片及已选附件预览。D | 当前原生层只有系统 chooser bridge，Web 原生附件卡片由 DSH 拥有。C | 近期照片浏览需 Android 授权与 SDK/厂商兼容性调查；不能把设计示意当已实现。 |
| WKS-01 P1 | 20:16–20:17 项目页与文件页是两个切换页；项目卡片列绝对路径/内部 ID，项目登记后无法直接浏览该项目目录。O | `AppShell` `WorkspaceSection.Projects/Files`；`ProjectsScreen` 列表登记，`FilesScreen` 独立 `fileManager.browserRoot()`。C | 项目路径可能在浏览器全局根外，直接复用现有 `list()` 会被路径校验拒绝。 |
| WKS-02 P1 | 文件页路径卡、重复“新建”、两行工具面板、长期存储 Banner 占据文件列表；操作尚未选中就展示。O | `FilesHeader` 和 `FileActions` 都含新建；常驻路径面板、权限 Banner 和七项操作。C | 单一列表 + 上下文操作是用户当前明确方向；不改变项目元数据语义。D |
| WKS-03 P0 | 基础文件管理/恢复不闭环：期待树形层级、面包屑、多选、预览/编辑、复制/移动/删除/恢复。D | `NativeFileManager` 有 list/parent/create/readText/saveText/rename/copy/move/deleteToTrash；UI 仅 `selectedPath: String?`（单选），文本编辑在 92% 高对话框，未见回收站选择/还原 UI；`browserRoot` 按共享权限或 Vault 选一个根。C | `copy/move` 单项方法不等于批量事务；`deleteToTrash()` 不等于“可恢复到原位置”；跨根/SAF 权限、软链接、命名冲突需方案。 |
| SET-01 P1 | 20:23 App 原生设置首页信息结构不清，“只保留真正不同的任务入口”说明文案多余，“外观”似可点实则不可点。O | `SettingsScreen` 中外观 `SettingsRow(... trailing=null)` 无 `onClick`，与 DSH 设置权属重叠；配置编辑、运行时、备份、更新、高级都聚合到一个设置索引。C | DSH 原生主题/模型/插件不是 App 应再造的设置；两个“设置”入口必须名称/跳转清楚。 |
| SET-02 P0 | 20:24 备份只有状态与横向按钮，未形成可选择备份→预检→预览→恢复的完整流程。O | `RecoveryScreen` 可校验、快照、导出、校验最近备份，但没有对用户开放完整的恢复选择/预览页面；底层 `RecoveryBackupManager` 有部分恢复方法。C | “跨卸载保留=是”“Manifest 已发现”都不是所有类别可完整恢复的证据；不得承诺已有完整恢复。 |
| SET-03 P1 | 20:24 运行时操作横向溢出，正常 HEALTHY 状态却突出安装/修复，健康卡片冗余且中英文混杂。O | `RecoveryScreen` 以 `horizontalScroll` 排启动/停止/修复/回滚/复制含 token 链接/浏览器验证；组件一项一卡。C | 需严格区分“已发命令/处理中/健康已验证”；未知不等于失败；修改后不得丢失现有高级工具。 |
| SET-04 P1 | “高级维护”混合引导、配置、原生组件状态与大块 Runtime/WebView 日志。O | `RecoveryView.ADVANCED` 同时呈现引导、日志、WebView 诊断、健康状态与其它操作；日志是在设置页里直接渲染文本。C | 日志必须限长脱敏；DSH 浏览器 token 链接仅能主动敏感操作，不得被一般诊断导出。 |
| SET-05 P1 | 二级设置页面标题、返回、危险操作、成功提示样式不统一。O | `SettingsScreen` 使用 `detail enum` 替换内容；`RecoveryScreen` 既当管理又当 Update/Backup/Advanced，横向滚动操作共用局部 busy/message。C | 真正的“备份完成/启动就绪/更新已安装”需依各自状态机判断；不能仅 `dispatch` 成功就宣告任务完成。 |
| TER-01 P1 | 20:24 终端顶部模式/绝对路径占宽，底部仍为单行命令输入；用户要求更好布局及键盘交互。O | `TerminalScreen` 以命令记录 `LazyColumn` + `OutlinedTextField(singleLine=true)` 呈现，每条命令调用 `executeShell` / native shell；不是长期交互式 PTY 前台会话。C | 产品需先定“命令控制台”还是 PTY 模拟器，不能只换皮宣称支持 vim/top 等交互命令。 |
| TER-02 P0 | 终端自动路由/权限描述与已接受 ADR 0007 有潜在实质冲突。C | `chooseDomain` 当前把 `pm/am/dumpsys/settings/input/cmd/svc/getprop/setprop` 等选向 `TerminalDomain.ANDROID`，实现执行的是 App-UID `NativeRecoveryShell`，而 ADR 0007 明确要求需 shell 权限的命令走实际可用 ADB，否则报告不可用。 | 须按命令、有效 UID 和能力验证，不得把 Android Local 冒充 ADB Shell；未获授权不得试图提升权限。 |

## 2. 根因待验证清单（优先做证据采集）

1. 抽屉/左右按钮：DOM header 与原生控件语义、bbox、z-index/overflow、切换状态；360/390 CSS px、14/16px 字体、多层弹窗。区分 WEB-01/02/03 根因；不要直接“把所有按钮 fixed”。
2. IME 跳跃：每帧 `imeBottom`、底栏可见与测量高度、WebView native rect、`visualViewport.height/offsetTop`、scrollTop、Composer rect；按进入/退出时间关联，先复现再锁定修复点，所有日志截断及脱敏。
3. 附件：受控无敏感测试文件 1/2/3/5 项，混合 JPG/TXT/PNG，记录 input `multiple/accept`、chooser mode、实际 `clipData` 数量/授权、回调数量、DSH 阶段状态与发送结果；禁止记录文件内容、URI 查询参数或私人路径。
4. 文件/恢复：通过现有授权根、外部项目根、目录改名/移动、删除与多文件失败模拟验证底层能力；独立验证 Runtime 不可用时 Native File/Recovery 仍可用。
5. 运行时：审核服务命令接受/执行/健康就绪三阶段及 A/B 先决条件；安全模式、备份实际恢复链路需有错误注入及回滚演练。

## 3. 边界与相关文档（本清单不改变既有架构）

- 以 `docs/REQUIREMENTS_BASELINE.md`、`docs/ARCHITECTURE_V0_2.md`、`docs/DEVELOPMENT_RULES.md`、`docs/DATA_RECOVERY.md`、`docs/DSH_COMPATIBILITY.md` 为更高层约束；ADR 0001/0002/0006/0007/0008/0010/0011 生效，0009 已被后续 baseline 阶段取代。`docs/UI_BASELINE.md` 的旧项目/文件二选一属于**被这次用户反馈更新的界面方向**，项目实体与 Session 模型并未废除。
- `docs/handoff/2026-09-16-ui-motion-and-startup-preview3.md` 是 Preview.3 既有变更/主机侧检查事实，不能当真机通过声明；本文件补充 Preview.3 安装后的回归及新需求。
- 截图无需包含真实账号/文件内容即可复现，证据上传前要裁剪/脱敏。本文涉及的所有“建议”均为**待执行设计**；源代码/测试/APK/发布清单本轮没有更改。
