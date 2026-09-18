# DSH Mobile｜工作区、抽屉、应用 UI 与插件式附件技术实施规范

> 版本：技术方案 V1.0（2026-09-18）；状态：**调研与设计完成，待技术门禁、实现及设备验收**。本次只写文档，不改功能代码、不构建/安装 APK、不改变 SSH/ADB、签名、发布源、授权或用户数据。对应需求：`2026-09-18-owner-ui-and-attachment-plugin-reconciliation.md`。所有数值阈值如未注明“现有行为”，均为**拟议初值**，须基线及真机验证，而非已经过用户确认的指标。

## 0. 结论先行、版本与证据分级

架构路线：**官方 DSH 会话/输入/附件状态不复制；Cordis 浏览器插件提供聊天内联入口与抽屉适配；Android 原生 Web Host 只管理系统 picker/临时授权/生命周期；Compose 管四项底栏、工作区、设置和 Recovery；Native Recovery Core 持有文件事务和恢复事务。** WebView 根视口仍专属 `dsh-webview-compat`。严格保留无 Root、原签名、原数据和独立 Termux SSH 救援。

证据等级：`F`＝已直接阅读此 Git HEAD 的代码/本地包，`E`＝已核对外部官方规范但运行环境未实测，`P`＝拟议实现接口/策略，`B`＝阻断实施的未决点。`PASS` 仅指明确列出的测试，不能扩展成 OriginOS UI 实测。现场基线 Git `ae638e4`，先前已改过工作区/备份首屏并通过现有测试；手机截图所示 Preview.6 旧版 APK 并没有这些新源码改动。旧文档 `2026-09-16-ui-remediation-and-acceptance-plan.md` 中 G3 提到的“原生轻量底部 sheet”已被本轮用户的新明确要求**替代为 composer 内联插件 UI**，但原生 picker/授权职责仍保留。ADR 0011 描述的是当时 compat-only 阶段，当前 `MobilePluginProfileCoordinator.kt` 实际明确启用四个 APK-managed bundles，须以现态为准。

| 来源 | 已查得事实及适用范围 | 后续需要补证 |
|---|---|---|
| `app/.../ui/ChatScreen.kt`、`AndroidFileChooserResult.kt`、`AttachmentPickerPolicy.kt`（F） | Preview.6 `onShowFileChooser` 接收 mode/accept/multiple 后弹原生 `ModalBottomSheet`；Native 汇总 `ClipData`/`data`/`parseResult` 去重，camera 使用单独 FileProvider；WebView 禁 `file://` 访问、允许 `content://` | 实际 Web 页面中究竟哪个 input/API 引发 callback、DSH 是否完整接收及发送 2/3/5 项 |
| `core/runtime-android/.../dsh-client-ui-mobile/lib/client.js`（F） | v0.4.2-dshm.2；宽 `min(80vw,360px)`、最小留 48px 遮罩；当前左抽屉只用 `left` 过渡，无触摸跟手；标题无 header 时回退 fixed top-left；右栏有 fullscreen 适配 | OriginOS 真正的 Header/Settings/抽屉尺寸、20 次交互和帧记录 |
| `MobilePluginProfileCoordinator.kt`（F） | profile mode `dsh-mobile-ui-v1`，managed context 0.2.2、compat 0.1.2、mobile UI 0.4.2-dshm.2、Tokyo Night 0.2.2-dshm.1；desired presentation generation 由 seed SHA、mode 和四包资源 hash 构成，reconcile 保留无关 profile 项 | 新独立插件必须纳入同等级 manifest/seed/hash/rollback 和“安全模式”验证，不能只复制 JS |
| 本地 `.mcp/tmp/dsh-npm-install-host/node_modules/@deepseek-ai/`（F，但只是本地安装副本） | `dsh-client-ui-conversation`、`ui-attachment`、`ui-layout` 的本地包均报 0.1.2-rc.1；下文类型与实现均来自该副本 | **不能把该 npm 安装副本自动等同运行中手机 Web bundle**：提取实际 DSH web 的锁文件/打包资源、页面 bundle 与 slot registry 的指纹并比对 |
| `Windows/dsh-mobile-app/plugin-mobile-ui/client/index.js`（F，历史参考） | 曾有 composer 文档流中的三块来源面板和横向预览，但通过 `textarea` / `DataTransfer` / 人工 ClipboardEvent(paste) 桥接旧编辑器 | 只参考结构和交互，不移植人工粘贴；当前输入是 Lexical |
| `Windows/dsh-plugin-workbench`（F，橙派独立插件） | Host Node FS 接口与 Browser 客户端分离，目录单层懒加载、路径围栏、MIME 限制和会话作用域 | 复用分层、边界与测试思路，绝不能把 Linux Node FS 代码当 Android 的 App UID/SAF 权限 |
| 外部 DSH 官方 Slots/Web Client 文档（E） | slots 声明和 `ctx.slots.inject` 生命周期、单占和列表 cardinality、Host→Remote→Client→Slot 数据流有明确规范 | master 文档可能先于/后于本机冻结包；以实际版本声明为先，升级另开兼容 ADR |

### 0.1 不变量与明确不做

1. 不 Fork 官方 Chat DOM、Lexical/Session 记录；不重建一套附件 registry/消息存储；不把 Web CSS 与 root viewport 修复混在一起。
2. 不使用 synthetic ClipboardEvent、伪造 `FileList`、手工调用未公开上传 URL、从 WebView 读取任意本机文件、无条件 `addJavascriptInterface` 或不受限 `evaluateJavascript`。无公开 intake 时先补受控扩展契约/版本兼容，不做“看起来传了”的假功能。
3. 不靠卸载清数据、重签 APK、关闭 host key 校验/SSH 隔离、扩大 `MANAGE_EXTERNAL_STORAGE`/Root/ADB 权限修 UI。现有 targetSdk 28 有本地可执行 Runtime 的 ADR 0005 约束；不能顺手提升而使 PRoot 无法运行。
4. 不把 `Vault manifestExists` 当备份完整，不把内部局部 restore 函数当全量安全恢复；不把选择器 `returnedCount=N` 当模型已经处理 N 项。

## 1. 需求追踪与负责人

| ID | 最小可观察结果 | 权威状态/技术负责人 | 发布阻断点 |
|---|---|---|---|
| ATT-1 | 聊天输入区内三来源并排、无 ModalBottomSheet、展开/收起不遮历史 | Browser Cordis 插件（呈现）；Native 只选择 | 正式插件插槽与 intake API 兼容 |
| ATT-2 | 用户授权的近照横向预选（可用时）、已选附件水平栏、放大/移除、失败标记 | 图片：DSH Attachment slot；可选近照：授权媒体提供者 | Android 11 权限、DSH 单占槽不冲突 |
| ATT-3 | 1/2/3/5 张图片真实发送且失败保留；文件入口只宣称实际支持的 MIME | DSH input/session + Native picker；Host 服务端 ACK | 端到端发送、URI 验证、多选限额 |
| DR-1 | 非全屏抽屉、真实遮罩、汉堡与标题同排、保留右栏与 Settings | `ctx.layout` + UI 插件 | Settings fixed containing block、Hero fallback |
| DR-2 | 打开/关闭跟手、滚动/边缘系统返回/附件横滑不误触、Back 层级正确 | UI 插件交互状态 + Native Back 协议 | 真机帧与手势冲突 |
| UI-1 | 标题/状态/操作轻量、浅深/东京夜色一致、IME 收回不弹、系统栏按系统契约运行 | Compose theme/insets + DSH theme/visualViewport | geometry 时序、可访问性 |
| WKS-1 | 单入口目录树、项目快捷、可点面包屑、搜索排序/隐藏、批选上下文栏 | FilesScreen/ProjectRegistry/NativeFileManager | 已登记目录权限差异、数据安全 |
| WKS-2 | 授权根、跨根复制、编辑 SHA 冲突、Trash 有原路径可恢复、终端 cwd 可信互跳 | 新 Native 文件抽象/事务；独立 ADR | SAF grant/重名/断电/符号链接 |
| REC-1 | 真实备份验证状态、分类/备份预览、恢复前 checkpoint、恢复失败可回退 | Native Recovery Core；Compose 仅路由 | 完整事务及密钥恢复演练 |
| PERF-1 | 冷/热启动与抽屉/切页测量，拒绝无证据“26s 已解决” | Runtime diagnostics + WebView/Frame timeline | 同机同条件、脱敏证据 |

## 2. 模块划分、依赖与回退路径

```
Android Activity / AppShell (四导航、系统栏/IME、唯一长驻 WebView)
  ├─ Native FilesScreen / Projects shortcut / Terminal / Settings / RecoveryScreen
  │    └─ Native Recovery Core: AuthorizedLocation, FileOpJournal, TrashIndex,
  │       ProjectRegistry, BackupCatalog, RestorePlan, RestoreExecutor (新契约，待实施)
  └─ DshWebHost: WebChromeClient + ActivityResult + URI permit + page lifecycle
       ↕ 仅 main-frame + 精确 origin 的有限 WebMessage（若确需原生来源提示）
       DSH Web Client (官方 Session、Lexical 输入、模型/附件限制、Host admission)
          ├─ @dsh-mobile/dsh-webview-compat（只能 html/body/#root 几何）
          ├─ dsh-client-ui-mobile（drawer/header/settings 显示适配）
          ├─ @dsh-mobile/dsh-attachment-intake-ui [拟议的新可关闭 Browser plugin]
          └─ 官方 ui-conversation + ui-attachment（唯一图片草稿与消息附件）
```

**源码触点**：`ChatScreen.kt` / `AttachmentPickerPolicy.kt` / `AndroidFileChooserResult.kt` / `DshPresentationBridge.kt` / `AppShell.kt` / `MainActivity.kt`；浏览器 `dsh-client-ui-mobile/{package.json,lib/client.js,cordis.patch.yml}`；新增附件插件的 Host/Browser entry 与版本化 package/bundle；`MobilePluginProfileCoordinator.kt` profile reconcile/generation/asset hash；`FilesScreen.kt`/`ProjectsScreen.kt` + `NativeFileManager.kt`/`FileOperationSafety.kt`/`ProjectRegistry.kt`；`SettingsScreen.kt`/`RecoveryScreen.kt` + `RecoveryVault.kt`/`RecoveryBackupManager.kt`/`NativeRecoveryController.kt`。实际文件列表须在 P0 兼容性验证后固定，未在本轮新增代码文件。

插件安装契约：Browser package `dsh.client.platform='web'`，使用已有 DSH `cordis.patch.yml` 加入显式 bundle；`/client` 只暴露 `apply/inject` 所需导出；`ctx.slots.inject` 等待 owner slot 的声明而不是靠加载顺序；退出 effect 解除注册、监听、URL/临时 DOM/样式。新包必须跟随 seed 与 existing-profile 两条路径、版本锁、marker SHA 和 A/B rollback；验证插件禁用时 official+compat 仍可发送/显示。**不要把可能破坏用户自装插件的 profile 全量重写**。

## 3. 附件插件：先验证冻结 API，再实施；禁止猜测入口

### 3.1 已核对的官方槽位边界

本地 `ui-conversation/lib/types/client/contract/slots.d.ts`：`conversation.input.left` 是 session/list，适合左工具行触发；`conversation.input.dock` session/list，位于输入卡上方；`conversation.composer.dock` session/list，位于卡下方；`conversation.input.overlay` session/list 在卡内悬浮；`conversation.input.attachments` 则是 session-maybe/**single**，当前官方 `ui-attachment` 已注册此槽。其 owner 提供 `attachments`、`canAcceptDrop`、`onAddImages(files)`、`onRemoveImage(id)`、`dropLimits`，官方组件已有水平图片栏、逐项移除、灯箱和拖放。**新来源组件注册新的 list-id；不第二次注册同名单占 slot、不自造预览与发送 registry**。图像预览由官方附件栏提供，必要的卡内重新排版只能通过有 owner 授权的 child/wrapper extension point 实施，禁止依赖 CSS-module hash 猜位置。

`IConversation` 公共接口只有跨插件允许的方法，`InputActions.addImages` 接收的是已注册 `DraftAttachmentId[]`，并不接收原生 `File[]`。Concrete `ConversationController.createDraftImages(files)` 虽可见，但 `IConversation` 未公开，且直接串起两者会绕过 `InputBar` 中模型 MIME、数量、单项字节、总字节的完整校验。Lexical 粘贴的 `intakeFiles` 和 InputBar `addImages(files)` 是该 package 内部入口。**当前只核查到了本地安装副本，没有证明设备上存在公开可被第三方直接调用的 `admitFiles(files)`；P0 不通过不得开工插件发送桥**。

### 3.2 P0 冻结资源调查/实验（只在下一实施轮进行）

1. 记下 `RuntimePins` 的 DSH seed / web-profile SHA、profile `pnpm-lock.yaml`、实际 Android 已安装包 versionCode/签名、设备 WebView 提供者及 `@deepseek-ai/*` 在运行页面 registry 的完整版本；对 `dsh-0.1.5-rc.2` 与 `0.1.2-rc.1` 本地安装副本逐文件指纹比对，不将 GitHub master README 视为本机 ABI。
2. 运行官方+compat-only fixture，记录实际所有 `input[type=file]`、`accept`、`multiple`、DOM/ARIA、调用方堆栈（仅调试样本）以及 `onShowFileChooser` 是否发生；查全量 `@deepseek-ai/dsh-web-app` / Slot 注册，不限于 conversation、attachment 和 chat。若冻结版本没有可用 file input/入口，明确记为“原 DSH 尚无此能力”，而不是把 Preview.6 native sheet 误认为官方已有发送实现。
3. 在隔离 fixture 测 1/2/3/5 个样本的端到端 Host Session echo、附件 ID、文件数及失败行为，记录提交可用性与兼容上限；先修真实数据链，再装视觉插件。
4. 用类型检查和 registry 负例确认 `input.left`/`input.dock` 的生命周期、同名单占槽注册冲突及 session 切换效果；明确最后一张参考图的“最近照片”与“已选预览”两层是否都可用。

### 3.3 技术方案取舍（决议/淘汰条件）

| 路线 | 判断 | 条件/原因 |
|---|---|---|
| A：冻结版本已有正式 public file intake 服务 | **优先** | `ctx` service/slot owner 公开 `File[] → 受限 admission → 官方草稿` 且遵守 Session、模型/数量/总字节；新插件只呈现三入口并调用它，Native picker 接原生输入 |
| B：新增版本化、最小官方输入扩展点 | **A 不成立时的正式路线，需单独兼容 ADR/严格评审** | 输入 owner 提供 `requestFiles()` 或 `admitFiles(files)`，与现有 `InputBar.intakeImages` 共用验证器、错误文本及事务，不复制内部检查，不新增第二 attachment slot；若改官方源码必须记录 pin SHA、diff、移除/上游提交条件 |
| C：从 concrete `createDraftImages` 调用 `addImages(ids)` | **禁止作为正式方案** | IConversation 未承诺该方法可跨插件调用，且易跳过 InputBar admission/模型限制 |
| D：DataTransfer 造 FileList + synthetic paste/自动调用私有上传接口 | **淘汰** | 依赖旧 textarea、受 Lexical/浏览器可信事件及版本变化影响，真实发出和错误回滚不可控 |
| E：Native 自己发送文件/重复显示消息 | **淘汰** | 复制 DSH Session/附件权威状态、易丢历史与鉴权/缓存语义 |

拟议公共契约（P，仅为 API 审查草案，具体 shape 以 frozen upstream API 审查为准）：`AttachmentIntake.request({sessionId, accept, multiple, origin:'camera'|'gallery'|'document'}) → {kind:'accepted', draftIds[]}|{kind:'cancelled'}|{kind:'rejected', code, itemIndices[]}`；`AttachmentIntake.admit(files, {sessionGeneration})` 必须由官方输入 owner 执行 MIME/大小/个数/total/model 权限和真实文件 sniff 与草稿注册的**同一事务**，并返回能在该 Session `useInput`/官方附件槽观察到的 ID。`requested→chosen→admitted` 是三个不同状态；“已发送”必须等待 DSH Host admission 与 Session 观察，不能仅凭 Browser promise 成功。

### 3.4 来源 UI 与图四信息层级

- 输入工具行独立、可关闭的附件按钮注册 `conversation.input.left`（唯一新 `id`）；点击使 panel 以 `conversation.input.dock` 或经 owner 明确授权的卡内 child 承载，**随输入区正常布局展开，不使用 Native `ModalBottomSheet`、body fixed overlay 或独立 Android 全屏选择页作为三入口面板**。布局优先：可用且明确授权的最近图片水平预选栏（高度拟 64–76dp；无权限时不虚构空白缩略图）→ 已选官方图片栏（不复制）→ 三个并排相同高度的「拍照」「相册」「文件」（320dp 宽仍有清晰文字与可点区域），可键盘/屏幕阅读器访问；内联 panel 展开必须与 IME/阅读位置配合，不强制 `scrollToBottom`。
- **最近照片≠已选照片**：参考图上方是最近媒体，官方 ui-attachment 已提供的是已选草稿；若必须展示未经选择的最近媒体，另设可关闭的 Native consent-scoped media provider，授权范围、顺序、分页、低清缩略图/缓存、权限撤销须由用户明确授予。系统 Photo Picker 本身不是可在 App 中嵌入的任意最近图库；未获得适用访问权时只展示已选栏与系统 picker 入口，不暗中扫描 `MediaStore`，不默认请求整库权限。这是与参考图达到完全一致的**单独待确认能力门槛**。
- 文件只允许模型/DSH 已支持的类型。若冻结版本只承认 image/png/jpeg/webp/gif，则「文件」可通过系统 DocumentsUI 选择符合 accept 的**图片文件**，在说明中明确“目前仅支持图片”；PDF/TXT 的文件管理/`@file` 引用不等于二进制附件发送。扩展普通文件需定义官方 durable attachment ref、mime/大小、序列化、Host admission、历史展示及模型/工具消费，独立里程碑，不以名字和本地卡片冒充支持。

### 3.5 Native 选择器协议与权限边界

现有 `WebChromeClient.onShowFileChooser` 由 WebView 文件 input/JS 所请求；保留 mode (`OPEN`, `OPEN_MULTIPLE`, `OPEN_FOLDER`, `SAVE`) 和 `accept/capture`，非 OPEN 直接按官方 Intent contract；OPEN 不得放大 `multiple`。本轮仅设计将三入口的“来源选择”前移到 Browser 插件，Native 负责 **physical OS picker launch**，移除原生 ModalBottomSheet；具体源提示需要 P0 首先证明 Web 插件的官方 `requestFiles()` 能逐源发出对应浏览器输入事件。若由 Native `onShowFileChooser` 按来源分流，提议有限的 `SourceHint`（见下），**不可假设简单的 `input.click()` 与异步 WebMessage 必然按顺序抵达 Native**。

安全的消息时序草案：打开内联面板先向已经装在 WebView 上的受限 `WebViewCompat.addWebMessageListener` 请求 `ARM_SOURCE`；Native 在精确 main-frame origin、受限 scheme/port、documentGeneration/SessionGeneration、源枚举、短 TTL、唯一 requestId 校验后回 `ARM_ACK`；组件收到 ACK 后才启用来源物理点击，点击发起官方 input file chooser；`onShowFileChooser` **只消费相同 doc/session 的一枚 pre-arm hint**，一律以 FileChooserParams 的 mode/accept/multiple 为最高限制。ACK 超时/跳页/换 Session/第二请求 → hint 失效并走默认安全文件 picker 或取消，绝不猜来源。若浏览器 user-activation 在 ACK 过程丢失，ACK 只发生在来源按钮的**前一步**，最终 input.click 必须在下一次真实用户点击的同步处理器内；此时序仍需 Chromium/WebView 实验证明，失败则回退默认系统 picker 而非模拟粘贴。`ARM_SOURCE` 只传 source/非敏感 ID，不传 URI、路径或字节；不接受 iframe/跨域消息，不向任意插件提供文件读取 RPC。

Native chooser 状态机（`ChooserRequest` 数据类为拟议）：

```
IDLE → ARMED → AWAITING_WEBVIEW_REQUEST → PICKER_LAUNCHED
    → RESULT_RECEIVED → URI_VALIDATED → CALLBACK_DELIVERED → IDLE
                ↘ CANCELLED/LAUNCH_FAILED/DISPOSED → CALLBACK_NULL_ONCE → IDLE
```

每一请求绑定 `requestId`, `webViewIdentity`, `documentGeneration`, `sessionId`(如有), `mode`, `accept[]`, `multiple`, `sourceHint`, `callbackOnce`, `cameraOutputUri`。只有 Android 主线程操作 Callback/ActivityResult；重入**拒绝新请求并回新回调 null**，不能替换旧 callback。导航、WebView dispose、生命周期中断清理 pending 且一次性 null；相机失败回收自己拥有的空临时文件，仍在写入中的不抢删，过期由本目录定界清理；重建 Activity 与 provider 回调要区分“原请求可重连”或安全取消，不将旧返回 URI 送到新 Session。已选择的 URI 临时 grant 随系统契约，不默认 `takePersistableUriPermission`，只有 ACTION_OPEN_DOCUMENT/TREE 且系统明确给 persistable flag、确有长期用途时持久化。

选择器防御：把外部 Intent `data/clipData/parseResult` 视为**不可信**；只接受特定任务允许的 `content://` 与本次自有 camera FileProvider URI，拒绝 `file://`、`javascript:`、未知 scheme 与指向 APP PRIVATE/自有凭据 authority 的返回；核对系统 grant / provider 可读能力、mode 单/多、去重顺序、MIME/签名（必要时有限文件头嗅探）、独立字节和总体上限、流式读取、单项错误反馈，不能仅 `ContentResolver.getType()` 宣称安全。第三方 ContentProvider 未给大小可能未知，不在 UI 主线程全量读；检查自己授权的 camera output 与任意第三方回传的 self-provider 区分；无访问权且拒绝后保留已有 DSH 草稿。日志仅记录匿名 request/计数、阶段、错误码、时长，不能打印 `content://`、文件名、API token、正文或图片数据。

`Photo Picker` 首选 AndroidX `PickVisualMedia` / `PickMultipleVisualMedia(max)`（设备可用时）；Android 11 支持情况由系统/OEM 模块决定，最终 SAF `ACTION_OPEN_DOCUMENT` 回退不保证数量上限，**接收后再次核限**。现用 `ACTION_GET_CONTENT` 只能作为兼容基线，是否迁移按目标 OriginOS 能力验证；相机用 `ACTION_IMAGE_CAPTURE` + 私有 FileProvider `EXTRA_OUTPUT`/临时读写 grant，结果 Intent 可以为 null，检查文件实际生成。文件走系统 DocumentsUI/原 `createIntent`，保留原 `accept`。原生选择器不是在 Web page 自绘完整系统目录授权页。

### 3.6 真实发送、失败与恢复状态

每一幅图的草稿状态由 DSH 持有，显示 `selected → validating → ready/unsupported/failed`；送出后 `submitting → admission accepted → Session echo/reference observed`，不能以选中数、URI 数或 HTTP 请求成功一项单独宣称发送完成。`MODEL_DOES_NOT_SUPPORT_IMAGES`/TOO_MANY/IMAGE_TOO_LARGE/TYPE_MISMATCH 按官方 Host code 显示，不覆盖文本草稿，单项失败可移除/重试已授权仍有效的项；网络 TRANS­PORT 与 API AUTH 错误分别说明，避免把截图中的 API 密钥失效诊断为 picker bug。发送时固定 sessionGeneration，切 Session 不串发；同一 request/submit 唯一 ID，超时或 outcome unknown 时查 DSH event/echo 再允许重试，防止重复消息。过程成功需有 **服务端观察到的附件 ID/文件内容 hash（只用测试样本）及 Session 可再次加载**。

## 4. Drawer/Header/Settings：DOM 适配必须让 DSH 控制状态

### 4.1 状态和分层

权威开关调用 `ctx.layout.toggleSidebar()`、`openDetails()/closeDetails()`；官方 layout state/ARIA 观测为输入，插件只维护短暂 `dragFraction`，不设置第二套长期 `isDrawerOpen`。独立 UI state：`closed`, `opening`, `open`, `closing`, `dragging-open`, `dragging-close`, `suspended-by-modal`。当外部 Session/sidebar state、屏宽 breakpoint、Settings/rightbar 发生变化时取消临时动画，以官方状态复位。

左栏保持当前 `width=min(80vw,360px,100%-48px)` 的非全屏设定（75–82%为设计目标而非所有屏宽强制同一比值）；真正遮罩只覆盖**露出的右侧**，点击/ESC/Back 应关闭且不漏击底下对话，手指右滑打开、左滑关闭是下一步功能，当前源码仅有按钮与背景点击，不能写作已存在。DOM z-index 清单（拟议）主内容 < 右栏/遮罩 < 左栏 < 插件开关 < 官方 Settings/Modal/光箱；不能凭任意“999999”覆盖所有官方层。Focus trap/aria-expanded/aria-controls 应与当前界面同步，打开抽屉聚焦首个有效项、关闭返回触发按钮（输入法焦点例外）。

当前 sidebar fixed DOM 的祖先里包含官方 fixed Settings modal：`transform`, `filter`, `will-change: transform` 等可能为 fixed 子孙新建 containing block。**切勿在 sidebar 或其任何 modal 祖先用 transform 做跟手**；当前用 `left` 过渡是功能性隔离但可引发重排。拟议跟手以 drawer `left` 与单个 CSS 变量/RAF 更新（或将官方 Settings modal portal 至 `document.body` 并取得上游授权后才能尝试 transform），只写可控 style，测 `Layout`/FPS 及性能；不可为了“GPU 加速”重新引入模态宽度缩窄。

### 4.2 GestureArbiter（拟议阈值必须设备校准）

触控起点在真实应用内容内且不落 Android 系统保留手势区或有 `touch-action` 管辖的横向 filmstrip、输入编辑区、侧栏水平子列表；closed 时左侧可见按钮是零手势依赖的保底入口，edge gesture 仅在系统导航安全区域触发；open 时在 drawer 内可开始关闭。`pointerdown` 记录 id/x/y/time，`pointermove` 超过拟议 12 CSS px 且 `abs(dx)≥1.35*abs(dy)` 锁水平，否则交给纵向滚动/原生系统；锁后 `preventDefault`/capture（只在允许的可取消 pointer 路径），按可用宽度计算 `dragFraction` 限定 [0,1]；pointerup 若 fraction 或同向速度达到拟议阈值则 commit 一次 `ctx.layout.toggleSidebar()`，否则弹回；pointercancel/多指/卸载/屏幕旋转/弹层出现立即 cancel。`pointer-events` 在可点遮罩/抽屉内精确切换；对系统返回不全屏设置 gesture exclusion，也不抢浅边缘右滑。与附件横滑冲突先按 `event.composedPath()` 排除 rail 与可横向滚动祖先，TalkBack/reduced-motion 则按钮可完整覆盖所有功能。

### 4.3 标题栏与页面级退路

按正式 `conversation.session.header.corner` / actions 等现有槽位及官方 layout APIs 核查能否原生注册开关，**优先官方 slot 级注入，不要凭第一个 DIV 子元素定位标题**。现有 `dockNavInSessionHeader()` 只在有 header 时安全，但 Hero/Settings fallback 是 `position:fixed;top:10px;left:10px`，必须拆成 Hero 实际 in-flow 导航容器；若缺少可靠的 header/hero slot，写上游微扩展 ADR 或仅保留官方导航，不用固定遮挡按钮。右侧栏其实际打开标记 `data-sidebar-right-panel=fullscreen`/`data-sidebar-right-open` 先于 `data-rightbar-fullscreen`；避免动画二次开始；状态由官方 owner 提供时迁至官方 store 而非永久追逐 DOM 属性。长标题必须 `min-width:0`、省略、原有控件可达，原生 DSH Settings 只修滚动布局与标题 X 同行，不夺走设置数据/主题 owner。

### 4.4 Android Back 优先级

拟定 UI 栈：系统文件选择器由 Android ActivityResult 自己处理；Web 内 lightbox/正式 modal/Settings → Web transient menu/附件内联面板 → 右栏（按官方语义）→ 左抽屉 → Web 页面历史 → Native Home/tab/Activity。与现有 `WEBVIEW_BACK_HANDLER`（通过 JS 合成 Escape/查按钮）逐项对照；优先增加插件受控 `handleBack(): handled` 或官方 slot/bail event，异步有 requestId/超时只会回落，不能双重 goBack 或双重 close。Accessibility 返回与硬键等价；返回时不因新 overlay 卸载整个 WebView。

## 5. IME、系统导航、切页与性能：先定位后选动画

当前 `AppShell.kt` 在 Compose composition 中读 `WindowInsets.ime.getBottom(density)` 并以 `max(BottomNavigationHeight, imeDp)` 计算 padding。Android 官方指出 inset 值在 composition 后、layout 前更新，直接在 composition 读取**可能慢一帧**；因此这是需验证的 IME 跳动假设，不是已确证根因。拟议按设备同一 monotonic clock 采样 `WindowInsets.ime`/navigationBars、底栏 visible+height、WebView rect、网页 `visualViewport.height/offsetTop`、`#root` 和 composer boundingRect、scrollTop、frame presentation；记录硬键/软键键盘展开关闭至少各十次，附 0/1/3 图及焦点切换。定位 double padding 后在 Native layout 阶段改用 `imePadding/windowInsetsPadding` 或等价受控 inset，确保 Native 只扣一次、Web compat 只定 root，Browser 不另叠固定 bottom 补丁；底栏可 fade 隐藏但不重复驱动高度。UI 动画不碰 Session/scroll anchor；已有 `AnimatedContent` 仅原生 tab，Web 内变更由 UI plugin 掌管，选一位 owner。

系统导航隐藏采用 Android `WindowInsetsControllerCompat` 沉浸式及 transient bar 契约，通知栏、截图、系统授权/PhotoPicker 调出时可以暂时显露，**不能保证永久不可见，也不能强行覆盖系统选择器**；跟随 Activity window-focus/IME 合理恢复，避免定时器循环反复 hide 导致 resize。保留三键导航/无障碍返回；首次安装/系统返回边缘与 Drawer 手势分别验收。

26 秒启动只是一次用户观测，不作为比较分母。采集 `Activity created / initial-frame TTID / vault-discovery / Runtime ready / token-HTTP ready / WebView page-start / DOM + handshake root measured / first interactive composer TTFD` 分段同一条件记录（冷、温、热 ≥3 次），使用 `reportFullyDrawn` 仅在真实可交互时，比较每段分位/中位而非关掉认证或 cache 验证“优化”。WebView 实例保持挂载、用户 Session/草稿/scroll anchor 原样；通用大 MutationObserver、反复 DOM 测量、无界 setTimeout/RAF 优先列入性能嫌疑而非盲目新增动画。

视觉 token：基于 DSH theme token/`LocalDshColors` 派生，不 hardcode 白字白底。拟议 App 一级页标题 22–26sp、说明 12–14sp/必要时省略、正文 14–16sp、列表 52–64dp、44–48dp 交互热区、间距 4/8/12/16dp；这不是最终验收数据，应经 320/360/390dp、fontScale1.0/1.3/1.5、浅色/深色/Tokyo 夜色、横屏和 TalkBack 实测确认。Home 官方 DSH 聊天布局最大限度原样，不能再用另一个统一 Compose 页面替代。

## 6. 工作区：数据模型与文件事务是 UI 的前提

### 6.1 单界面信息架构

顶栏 `工作区 + 新建 + 更多`（当前已有精简版）→ 相对可点面包屑 `根目录 / … / 当前` → 折叠的项目快捷行（仅根/用户主动展开）→ 懒加载单层目录列表，图标/名称/必要的大小时间；完整绝对路径进入属性详情，非 debug 用户看不到机内实现路径。单点目录进入、图片原生只读预览、文本代码全屏编辑；未知二进制提供属性/受控系统打开，无自动执行。长按进入 selection，选择模式下一次点击选/取消，toolbar 根据选中能力显示复制/移动/重命名（单项）/Trash；剪贴板非空且目标有写权限才可粘贴；系统 Back 先退出选择/编辑未存确认再离开工作区。当前只有 `selectedPath:String?`，**不等于多选已完成**。

拟议 `ExplorerState`：`location:AuthorizedLocation`, `breadcrumbs[]`, `selection:Set<EntryKey>`, `clipboard:{sources[],mode,sourceGeneration}`, `sort`, `showHidden`, `searchQuery`, `loading/error`, `viewGeneration`；切授权根/项目、权限撤销时失效的 selection/clipboard 不得落到新目录。Project shortcut displayName + stable ProjectId，仅导航，不把 Project 元数据当目录副本；取消登记与删除实体分按钮、分确认。`ProjectRegistry.registerFolder` 可以登记 browserRoot 之外真实目录，但 `NativeFileManager.list` 会正确拒绝越权：下一实施轮设计新授权抽象，不可以去掉围栏来让它“看起来能打开”。

### 6.2 多根权限（独立 ADR 才能动代码）

建议 `AuthorizedLocation = SandboxRoot(rootCanonical, grantId)` 或 `DocumentTree(treeUri, persistedGrantId, providerCapabilities)`，条目 `EntryKey=(grantId,relativeCanonicalPath|documentId, versionToken)`；UI 只拿 displayName / capability flags，Native resolver 按每次读写的根/提供者校验权限。文件系统根仍 `canonicalFile` containment、拒绝 symlink/特殊文件并在实际打开/提交前复核；若高风险 TOCTOU 用受控 fd/no-follow 方案或在无法证明原子时拒绝，不能仅在列目录时检查一次。SAF `ACTION_OPEN_DOCUMENT_TREE` 是用户授权**具体树**，Android 11 禁止选择内部卷/受限 SD 根、Download 树等，Provider 可能只读/拒重命名；以 `DocumentsContract` capability+grant 为事实，Uri/documentId 不能当真实绝对路径传给 PRoot。需要 PRoot cwd 的 SAF 项目必须另做 user-approved 导入/镜像到受控文件路径及双向同步/冲突策略，不能传 `content://` 伪装 POSIX cwd。`MANAGE_EXTERNAL_STORAGE` 如已有是当前用户主动授予能力，新增功能不能强制扩大权限，敏感 Android/data/obb 仍服从平台约束。

### 6.3 多项操作计划/事务

新 `FileOperationPlan`（拟议）含 `operationId`, `sourceEntries[]`, `sourceVersion[]`, `targetGrant`, `conflictPolicy`, `estimatedBytes`, `preflight`, `stagingPaths`, `perItemState`。状态 `planned→preflighted→staged→verified→committing→succeeded / partial / failed / cancelled`；每项 `accepted/rejected/skipped/committed` 可解释。复制：检查两端权限/空间/同名、拒目录到子目录、递归 symlink 检查，逐项限额与取消点、临时文件同目录流式复制 + flush/fsync/校验 hash + 原子 rename（Provider 不保证原子则标非原子并生成恢复副本），只有 commit 后更新 UI。移动：同卷 rename 可一阶段；跨卷 copy+verify 后才删原件，删除失败**不得谎称移动成功**，报告保留双方并提供人工复核；运行中失败不自动 `deleteRecursively` 未归属路径。覆盖/并发冲突默认保留双方，用户选择覆盖前 snapshot、写入 expected SHA/version，不能以当前 `uniqueTarget` 静默重命名充当全部冲突策略。

Trash 目前只有时间前缀文件，没有原路径元数据。拟议 `TrashRecord{trashId, originalGrantId, originalRelativePath, entryKind, deletedAt, size, sha256?, movedObjectRef, schemaVersion, moveState}`，manifest 和数据 staged+commit 保持一致，先落 journal 再 rename/copy，对孤儿和 dangling manifest 做只读扫描及恢复提示。恢复默认原位，已存在则 `保留双方/另选位置/取消`，原 grant 撤销则只能重新授权或选可写目标；根/Vault/.Trash 自身不可当删除源，历史条目迁移不自动猜其原始目录，旧记录只允许“恢复到用户选择的新目录”。重装卸载后 Vault 需真实跨卸载且备份经校验，否则 UI 不承诺能找回 Trash。

文本编辑复用 `NativeFileManager.readText(max 2MiB)` UTF8 与 `saveText(expectedSha256)`，全屏页面维护 original SHA、dirty、编码/换行、授权 generation；保存前 snapshot、流式临时写/`fd.sync`/提交前再比 SHA、冲突时对比/另存副本，Back 必须提醒。图片 `BitmapFactory` 解码已经有限额，后续缩略缓存须基于版本 hash/路径授权并控制内存；PDF 文档若未有安全 viewer 不自动让 WebView 打开任意 `file://`。

### 6.4 文件与终端双向跳转

Terminal command identity 是 Linux PRoot / Android App UID / 实际授权 ADB Shell 等独立域；从文件页到 Terminal 传结构化 `FileLocation+domainHint`，只有真实可进入的 POSIX 目录才产生 `cwd`；从 Terminal cwd 到文件页先定位映射的授权根再解析。SAF root、远程 cwd、ADB shell 的不可映射位置明确“需要导入/授予目录”，不能偷偷退回 `/storage/emulated/0` 或把 Root 能力默认为当前权限。`@Project` 始终使用 stable ID、主 Project 映射官方 DSH cwd、附加项目不改 Workspace。

## 7. Settings / Recovery：先实现真实数据契约，再展示完成状态

Settings 一级只保留 App、Data、Runtime、Developer 四组，DSH 原生模型/主题/插件设置继续由官方页面提供，Native 不重建假的切换器。二级头部同一 compact header + 返回，Backup 首屏只显示备份最近一次**验证结果**、跨卸载/秘密数据范围的真实状态和主要动作，大小、路径、manifest、原始日志折叠；现有已精简 `RecoveryScreen` 只是 UI 步骤，未完成恢复能力。

已核查 `RecoveryBackupManager`：`createCheckpoint`/`createPortableExport` 要求持久 Vault，ZIP 每文件 SHA/size、schemaVersion=1，写 `.partial` 并 verify 后发布；`verifyLatest` 按两个目录 mtime 选择最新。`restorePersistentDshHomeFromLatest()` **确实存在**，但仅对 `AppPrivate/dsh-home/` 范围按 manifest 写单文件 `AtomicFile`，不在 `RecoveryAction`/UI 暴露、无用户选备份/冲突预览/整批 staging+rollback，也跳过敏感路径；不可把它直接绑定「一键恢复」。`RecoveryVault.verify()` 仅确认 layout/manifest 基础结构，不等于 `verifyArchive()` 的每文件 hash。`Vault discovered / Manifest exists / backup verified / restore completed` 必须分别显示。

拟议分层：

- `BackupCatalog.list/inspect(backupId)` 返回路径标识/类型/创建时刻/schema、hash 验证时间/数据分类统计、秘钥加密类型、是否完整及错误码，不能返回私钥明文；备份 ID 用 manifest fingerprint，不用“最新文件路径”做易变选择。
- `RestorePlanner.plan(backupId,categories,destination,conflicts)` 仅读 source ZIP、manifest 与当前文件版本，生成逐项 preview/冲突/空间需求、授予能力、当前数据 checkpoint 需求、不可恢复类别；绝不边预览边覆盖。
- `RestoreExecutor.execute(planId,verifiedBackupSha)` 在 Runtime 停稳后执行 `checkpoint-current→verify checkpoint→stage to separate directory→entry-level verify→compat migration→health check→atomic switch per domain/prepare rollback→commit`。全局一致性跨 Android 文件、ZIP 与 SAF Provider **无法天然保证**，必须 journal 记录已提交项并将完成表述为“按域原子+全局可逆补偿”；不可假设一个 rename 覆盖所有数据。
- journal `DISCOVERED/VERIFIED/PLANNED/CHECKPOINTED/STAGING/VERIFYING/COMMITTING/COMPLETED/ROLLING_BACK/ROLLED_BACK/NEEDS_MANUAL_RECONCILIATION`，重启优先处理未完成 journal，只读呈现并提供从旧 checkpoint 复原。普通导入/导出不携带明文 SSH 私钥/API key，凭据只有独立加密 vault + 跨卸载解锁/恢复密钥演练；Android Keystore 卸载后密钥可能失效，不能假设“加密文件还在＝能够解密”。
- ZIP 不可信：拒绝绝对/`..`/反斜杠变体、规范化重复名/大小写或 Unicode 碰撞、符号链接及 Zip Slip、声明与实际 size 不符、zip bomb（单文件/总解压字节、文件数、嵌套层级、压缩比预算）、checksum 错、schema 过新、空间不足；读取必须有上限，不能 `readBytes()` 整个任意大 entry。预检能力不足时禁止执行并允许原 ZIP 原样保存和重新授权。

恢复 UI 状态 `notConfigured / discovered / verifying / verified / invalid / restoreAvailable / restoring / partial / completed / needsAttention` 只由 Controller/Journal 结果驱动，当前不支持的单文件/项目/会话/插件/凭据分类清楚显示不可选，不给假按钮。用户原始会话 event log 为事实来源，损坏恢复创建 fork/copy 不盲重放 side effect 工具调用。

## 8. 测试设计、追踪证据与准入门禁

| 层 | 具体测试/负例 | 判定 |
|---|---|---|
| Source/ABI | 冻结 pin SHA、真实 Client slot、`ctx.slots.inject` 装卸、list 唯一 ID、single slot 不占用、原生 input 来源、compat-only 对照、插件 Safe Mode | 任一类型错或与 upstream pin 不符即阻断 ATT；master 成功不能替代 pin |
| Android chooser 单元/Robolectric API30 | 文件 1/2/3/5、单选过滤、多选 ClipData/data/parse union、取消、无 grant、重复/恶意 URI、第三方返回自有 FileProvider、超额/未知 MIME、Camera 无结果/空文件、Activity recreate、重入/跳页 callback 恰一次 | 无权限越界、无跨会话串回调，选中数和回调数一致 |
| Browser/DSH integration | text+图片 1/2/3/5、同名不同 URI、类型不支持、超数量/单项/总体积、网络断线/模型 AUTH、移除、再次进入 Session、上游 Editor 功能/上传 queue | 每项 admission/Session echo 可证；失败草稿保留，发送不重复 |
| Drawer/Settings browser | 官方+compat 与加 mobile/plugin A/B，320/360/390dp、长标题、Hero 空会话、右栏 fullscreen、Settings fixed 宽度、插件菜单/灯箱、暗色/东京夜色、reduced-motion、组件卸载重装 | 真遮罩可点、右栏可用、无 fixed 陷阱与 duplicated toggle |
| Device OriginOS WebView151 | Drawer 连续 20 次（轻扫、慢拖、取消、纵滚、横图冲突）；IME 展开/收起各 10 次；三键导航/截图/系统 picker；选 1/2/3/5 真正发送；字体 1.0/1.3/1.5 | 帧与几何记录、真实 Server 结果和脱敏截图，不能仅 Chromium pass |
| Native Files | 项目外浏览根拒绝、SAF grant 撤销、symlink/TOCTOU、批选多项、目录自包含、跨卷半失败、ENOSPC、相同名称、文本 SHA 变动、Trash orphan/原位冲突/无原 grant、Terminal cwd 不匹配 | 原文件不丢，不越权，逐项结果准确；无法证明原子时要求可复原 |
| Recovery | 损坏 ZIP/manifest/hash、zip bomb、schema 过新、权限被收回、空间不足、运行时忙、断电模拟 checkpoint/stage/commit、Rollback、加密凭据独立恢复、旧数据升级 | 仅验证并恢复成功类别可显示完成，失败无抹除唯一副本 |
| Performance | 同机冷/温/热各 ≥3 次，TTID/TTFD 分段，抽屉帧时间/输入滚动/内存峰值、长目录+大图源 | 按新旧同配置记录差异和回归，不用用户单次 26s 宣称改进 |

门禁顺序必须记录每条结果：G0 Git/签名/旧 APK SHA/非破坏备份/救援 SSH 复连与用户授权→P0 ABI/API/附件实际来源调查→针对性 Node24 type/syntax/slot policy、Android11 unit/Robolectric→`android_lint`→`runtime_alpine_e2e`→exact-current-profile Chromium composition gate（official+compat 为对照）、`git diff --check`、review→受控设备 OriginOS ADB/SSH 下真实 UI+发送、覆盖安装备份与数据复核→本地 commit/发布候选。现存上一轮 `59` 单测、`0 errors 23 warnings`、Alpine E2E PASS 只证明上一轮源码对应范围；本轮无新代码、不运行这些门禁。用户未授权发布时不动 `release/update.json`，无清数据/卸载、无擅改外部 Git remote。

设备控制现实：`2026-09-18-live-phone-diagnostic-baseline.md` 记录过 `22023` 救援通道一度 OK 后又掉线，当前实时连通性仍须重新验证，旧日志不能作今天手机在线证明；`adb devices` 曾为空/配对受限，不能把普通 Termux shell 视为已授权 ADB。**后续任何 APK 安装先实际重建/复测独立 Termux→Azure→橙派救援、备份、签名一致性与 Android 11 真机通道**，否则只做静态设计与 Browser 可控测试。

## 9. 切片、依赖、降级与决策点

| 里程碑 | 先决条件和交付 | 必须可独立回退 |
|---|---|---|
| D0 架构冻结 | 需求-ID/截图目标、实际 profile 和公开 ABI 指纹、测试夹具、强约束 ADR 草案 | 不改任何代码/设备，文档评审 |
| D1 ATT intake 探针 | 证明标准 input / Host admission 和一处受控 public intake 契约，先过 1/2/3/5 闭环 | 新插件 disable→官方输入无损；失败阻断 D2 |
| D2 ATT 展示插件 | Browser list slot 三入口/已选 official rail/可授权最近图、Android chooser thin bridge，remove native ModalBottomSheet；版本 hash/seed/profile 双路 | 插件禁用与 Native 默认 picker 退路，草稿/Session 不丢 |
| D3 DR/Header | 官方状态、非全屏真实遮罩、header/Hero 可靠占位、手势、Settings/rightbar 优先级；先保留无手势按钮路径 | 单个 UI 插件禁用回官方+compat；不改 viewport |
| D4 Insets/性能 | 有责任层证据后最小改 Native padding 或 Browser 监听，分层 TTID/TTFD 和帧优化 | 独立特性开关；新优化回退后 WebView/Runtime 保留 |
| D5 Workspace explorer | 紧凑视图→真正 multiselect→授权根 ADR/SAF→可取消批处理→Trash manifest/restore→Terminal cwd | 文件 schema 升级需 migration + checkpoint + journal，UI 回退不回滚用户文件 |
| D6 Settings/Recovery | 数据分类/验证 catalog→read-only plan→事务及故障注入→启用明确支持范围的恢复 UI | 旧备份可读、兼容迁移与原备份永不覆盖 |
| D7 候选与发布 | 全门禁、真机持续救援、安全回退、版本递增/证书同一、用户验收 | 候选只本地，OTA 必须另获确认 |

需要明确确认但不阻断 D0 文档的事项：①参考图顶部是“最近图库”还是“已选图栏+最近图库都需要”？默认两层目标、无授权时仅已选栏；②“文件”是否要求 PDF/TXT 真的随提示词传给模型？默认先图片文档选择器，普通文件另立官方协议；③项目是否需要设备任意外部目录和 SAF 文件夹作为 **可直接运行的 PRoot cwd**？默认授权浏览与运行目录分离；④抽屉左边缘与 Android 系统返回冲突时优先系统返回，汉堡永远可用；⑤完整恢复的第一期资产范围，默认只暴露实际验证可恢复的类别。以上选择都是本技术方案的可测试假设，非已获得最终产品确认。

## 10. 官方外部规范与本机参考（2026-09-18 调研）

**官方外部文档，供实施者按版本校验，不代表设备已验证**：

1. DeepSeek Harness：[Web Client Slots](https://deepseek-harness.github.io/deepseek-harness/en/reference/subsystems/slots)；[Web Client architecture](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/subsystems/web-client.md)。依据：slot cardinality、`inject` 生命周期、Client/Host 状态所有权；master 只做概念基线。
2. Android：[WebChromeClient.FileChooserParams](https://developer.android.com/reference/android/webkit/WebChromeClient.FileChooserParams)、[WebChromeClient.onShowFileChooser](https://developer.android.com/reference/android/webkit/WebChromeClient)。明确用户选回 URI 不可信、区分 mode/capture、相机结果单独处理。
3. Android：[Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker) 与 [PickMultipleVisualMedia](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.PickMultipleVisualMedia)。系统/OEM/SAF 分级回退；SAF 回退不强制多选最大数量。
4. Android：[Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)。Android 11 tree restrictions、用户指定树、可持久 URI grant 的条件和 provider capabilities。
5. Android：[WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat)、[WebMessageListener](https://developer.android.com/reference/androidx/webkit/WebViewCompat.WebMessageListener)、[WebView unsafe file inclusion](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion)。限定 origin/main frame 与返回 URI 核验，不扩大 file scheme。
6. Android：[Compose window insets](https://developer.android.com/develop/ui/compose/system/insets-ui)、[immersive system bars](https://developer.android.com/develop/ui/views/layout/immersive)、[app startup TTID/TTFD](https://developer.android.com/topic/performance/vitals/launch-time)。IME 同帧布局、系统 transient bars 与量化启动。
7. MDN：[Containing block](https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Display/Containing_block)：fixed 子孙的 transform/filter/will-change containing-block 问题；不能凭“transform 总更快”选择抽屉动画。

**本机参考文件索引**：`docs/UI_BASELINE.md`、`docs/REQUIREMENTS_BASELINE.md`、`docs/DSH_COMPATIBILITY.md`、`docs/DATA_RECOVERY.md`、`docs/DEVELOPMENT_RULES.md`、`docs/adr/0001-*.md`/`0005-*.md`/`0007-*.md`/`0008-*.md`/`0011-*.md`、`docs/handoff/2026-09-16-ui-remediation-and-acceptance-plan.md`、`docs/handoff/2026-09-18-owner-ui-and-attachment-plugin-reconciliation.md`；实际浏览器类型是本地 `.mcp/tmp/dsh-npm-install-host/node_modules/@deepseek-ai/dsh-client-ui-conversation/lib/types/client/contract/{slots,input}.d.ts` 与 `ui-attachment/lib/client.js`，**本地缓存是证据而非依赖/发布源**；原生实现触点详见本文件第 2 节。

## 11. 本文交付与实施限制

已完成：源文件/插件/API/存储与备份逐项核对、外部官方规范交叉验证、架构和失败恢复边界、接口草案、拒绝路径、矩阵、切片、待确认条件。**未完成且本轮不实施**：新增 Cordis plugin、官方 intake 扩展、Android chooser 修改、抽屉手势/UI 修复、SAF/Trash/restore schema、新单测/真机测试、编译、APK/OTA/设备操作。下一轮应先完成 P0 获取真实运行 bundle/ABI 与服务端发送证据，再据结果修订本稿中的 `P` 接口；不能把技术方案状态写成代码完成。
