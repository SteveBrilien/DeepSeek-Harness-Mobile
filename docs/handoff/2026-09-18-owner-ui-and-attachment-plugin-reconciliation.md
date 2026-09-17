# 2026-09-18｜所有者 UI 反馈复核、现状差异和插件式附件交付契约

状态：**已对照旧对话反馈、现有 UI/需求文档、当前 Preview.6 源码、橙派旧版移动 UI 插件、工作台插件与冻结 DSH 官方槽位类型；本文件是整改依据，不等于源码修复、真机验收或新版 APK。** 不将包含私人会话/路径的用户截图复制进 Git。

## 1. 本轮四张截图的证据与误解纠正

- 图一 Preview.6 会话顶部显示左汉堡、右栏控件，底部弹出 Compose `ModalBottomSheet`，正文被遮盖，照片/相册两枚小胶囊与全宽「文件」堆叠；原生 picker callback 工作与否不能从截图判断；另有 `API 密钥无效`/`Connection error` 这两个模型请求错误，不得混入附件/UI 根因。
- 图二「备份与恢复」超大标题、重复副标题、巨型边框面板、内部绝对路径、永久状态说明与五个等重动作；页面下半空白。「Manifest 已发现」只说明文件存在；当前没有可选备份→预检→预览→恢复完整 UI，不得以视觉改写假装恢复可用。
- 图三「工作区」巨大标题/副标题、内部绝对路径占两行，管理项目、新建、上一级、根目录、刷新按钮换行；项目快捷入口单独大卡，所有文件/文件夹都有「选择」副按钮；仍为单选。文件列表本身被抢占首屏空间。
- 图四目标参照是 **composer 内嵌横向可滑动媒体预选/已选预览 + 同一区域拍照/相册/文件三入口并排**，不覆盖对话；不是图一的 Android 系统样式底部模态弹窗。最近媒体只能显示已获授权的图片；无访问许可时显示已选照片并保留调用系统 Photo Picker/SAF 的入口，不静默遍历整库。

## 2. 已明确确认过而当前未实现/验收的要求（优先级与范围）

- 视觉基线 `docs/UI_BASELINE.md`：官方 DSH 的黑白灰信息层级/少量蓝色选中态、精简标题和薄分隔，不堆叠大卡片/冗余说明/硬编码内部路径。原生固定四项导航：首页/工作区/终端/设置；DSH Chat 主界面保留官方行为，Web UI 增强走 Cordis Browser Plugin，Android OS picker、权限、长期数据与 Native Recovery 走原生层。
- 左抽屉：约 75–82% 手机宽，右侧保留**可点击的真实遮罩**，不留空白死带、文字闪影或跳帧。原生汉堡必须参与会话标题行布局、不能使用固定箭头遮挡；首页 Hero/Settings 等没有 header 的场景也不能退回会遮挡的 fixed top-left。官方右侧栏原生展开/关闭按钮始终依真实 Session 语义保留。右滑打开/左滑关闭跟手，点击遮罩/Android Back 关闭并避让纵向滚动、系统返回和附件横滑。尊重 reduced-motion；原生 Settings 是 sidebar 子节点的 fixed dialog，**不能再在其祖先使用 transform/will-change**，否则 modal 宽度缩成侧栏。
- IME 收起弹跳、系统三键导航截图后显露、启动约26秒、切页突变都未获得 OriginOS 通过证据；先同时间轴 Insets/WebView/Composer 几何与帧证据，再收敛动画所有权，不能用更多 CSS/计时器掩盖。
- 工作区：一个 **文件夹层级浏览器 + 注册 Project 的目录快捷入口**，不设常驻「项目/文件」双页面，Project stable ID/Session 关系仍与官方 DSH Workspace 分开。紧凑工具栏只放必要按钮/搜索/更多；可点面包屑、目录单击进入、文件依类型打开，长按进入多选、上下文操作随选中出现，剪贴板有内容时才显示粘贴；路径/内部 ID 去详情，权限异常才弹警告。支持批量安全文件操作、搜索排序隐藏文件、内联图片/文档预览、占屏文本编辑、Trash 原位/改址恢复、终端 cwd 双向跳转；项目跨授权根需单独 ADR/SAF grant 与逐端 canonical/symlink 审核，不能通过移除根校验假修。
- 设置：主页应用/数据/运行环境/开发者工具四组，单一真正可用入口；页面副标题/操作墙/水平挤压按钮和不可点假设置都删或下沉。备份首页突出真实已验证状态与主操作，历史与导出放二级，只有完整 `发现→校验→选择预览→冲突策略→checkpoint→staging→验证→回滚` 后才能声称可恢复；当前只支持创建/导出/校验须准确展示。
- 附件必须先跑通 1/2/3/5 项 `Android chooser → content grant → callback → DSH draft preview/ready/error → 真正发送和服务端回执`，失败项单独反馈且原草稿不丢。浏览器模拟器数量通过不等于真机完成。图片横滑、点击放大、逐项移除；非图片在官方 DSH 未提供完整兼容附件槽位前，不得伪造“已成功上传”。

## 3. 本次实际代码对照：偏离的位置

- `app/.../ui/ChatScreen.kt` 在 `onShowFileChooser()` 内设置 `showAttachmentSources=true`，并在 `DshWebClient` 底部绘制 `ModalBottomSheet`：这是弹层偏差的直接实现位置。保留必要的 Native `WebChromeClient`, FileProvider, user-granted `content://`, accept/multiple 和一次 callback 处理；把**源控件外观与交互**迁到 Cordis Browser Plugin，Native 不再拥有独立一套附件 UI。
- `app/.../ui/FilesScreen.kt` `FilesHeader()` 的 `DshPageHeader(displaySmall)` + 永久绝对路径 + `FlowRow` 四动作就是图三大头部；`FileRow()` 永远展示「选择」按钮，`selectedPath:String?` 仅单选。`RecoveryScreen.kt` 的 BACKUP 分支是巨型 `DshPanel`、路径、多个平级操作/文字墙，正是图二。原生组件与信息架构应按 `docs/handoff/2026-09-16-ui-remediation-and-acceptance-plan.md` 分阶段调整，不删除底层能力与数据。
- 当前 `dsh-client-ui-mobile/lib/client.js` 仍有 `top:10px,left:10px,position:fixed` 的 header 识别失败兜底；其 drawer 宽 80vw + 外侧 backdrop 方向正确，但 `left` 动画尚无触摸跟手/OriginOS 帧证据。已有右栏 fullscreen open marker 的 CSS 仅有 Chromium 成功证据。根尺寸问题只属于 `dsh-webview-compat`，不可把通用 UI 改到兼容插件里。

## 4. 橙派本地插件与冻结官方接口的证据

- `Windows/dsh-mobile-app/plugin-mobile-ui` v0.6.0（独立旧工程，**参考而非可以直接复制**）`client/index.js` 的 `ensureUploadButton`、`buildAttachSheet`、`setAttachSheetOpen` 和 `data-dshm-attach-sheet` CSS：把三个并排 tile 放入 `[data-composer-seat]` 正常文档流、留出缩略图栏，避免模态弹窗遮蔽；它实现过本轮想要的视觉结构。旧实现依赖 `textarea[data-phase]` + `DataTransfer`/人工 `ClipboardEvent('paste')`，并可能重新压缩图片。这是旧版私有兼容路径，新冻结官方 Editor 已用 Lexical contenteditable；**禁止不验证就原样复制旧入口或模拟粘贴当正式协议**。
- `Windows/dsh-plugin-workbench` v0.1.0 的 `package.json` / `src/{fs-read,fs-write,media,paths,trust}` / `client/src/{api,dom,state,style}.js` 示范分离 Cordis Host/Browser、受控按 Session 的 file API、MIME 白名单与 `nosniff`；**不能直接把宿主 Linux 文件接口移到 Android 任意路径**。
- 冻结 DSH `@deepseek-ai/dsh-client-ui-conversation` 的 `lib/types/client/contract/slots.d.ts` 实际公开 `conversation.input.left` (list)、`conversation.composer.dock` (list, composer 下方)、`conversation.input.dock` (list, composer 上方)、`conversation.input.attachments` (single, 所有者提供 `onAddImages`, `attachments`, `onRemoveImage`)；官方 `@deepseek-ai/dsh-client-ui-attachment` 已填充单一附件 slot，用 64px 水平草稿**图片**栏、逐张移除及灯箱。因此来源入口可试验插件列表槽位，但不能另建一套图像附件状态；单一附件槽不能与官方实现双重注册，扩展应寻找官方可组合 wrapper/新公共 source-intake 契约。
- 官方 `InputActions.addImages(ids)` 要求已登记的 `DraftAttachmentId`，**不能直接传 File**；`ConversationController.createDraftImages(files)` 在类实现类型上公开但 `IConversation` 公共对外接口没有列它，且直接登记绕过 InputBar 当前 count/单项/总尺寸等完整校验。因此暂不将两个方法直接串联当作正式 intake。`InputBar` 私有 `addImages(files)` 和 Lexical paste 处理也不属于跨插件公开契约。上游附件 README 明言只呈现图片，非图片文档尚未有统一的官方卡片/历史渲染。必须明确梳理文件类型与授权，不能把本地预览=官方发送成功。
- `dsh-client-ui-mobile` 的 `package.json` 声明 Web plugin + `cordis.patch.yml` 插入 `ui-mobile`，`lib/index.js` Host 半身为空、`lib/client.js` 是 `__ModuleLoader__.load` Browser 半身；新增附件模块应走独立可关闭的 Browser plugin 或版本化现有 plugin 的独立效果/样式块，官方 UI/Runtime/Session 主代码不作分叉。移动 Native 拾取只提供由用户主动选择的 URI，需定义可信源与一次请求/一次回调及生命周期；不能向网页开放任意 `file://`、任意 JS bridge 或私有路径。

## 5. 执行路线（按独立切片，不能把文档写成 PASS）

1. **UI 基线和插件协议**：冻结上版可回退 commit/截图，拆 `ATT` 插件方案。验证当前 DSH 冻结客户端的 slot 注册、可组合的正式 intake 入口、Android 11 WebChrome chooser 边界；明确各 source 的 `accept`/multiple、单回调/拒绝/取消与 React 草稿状态桥。若正式 plugin API 缺失，应新增受控、版本化 DSH 插件契约/上游扩展点，先写 adapter contract tests，绝不靠模拟粘贴掩盖。先实现无权限越界的三入口 inline composer，照片预选行先只用用户已经授权的媒体或官方选中项，后续如需最近相册要单独用户授权。视觉可在 Browser fixture 验，发送必须真机/等价 Android provider 验。
2. **Web 抽屉和 Header**：不要新增固定悬浮箭头；可靠 title row/hero dock、右栏原控件和 modal stack；修遮罩 token，保留无 transform 的 sidebar modal 祖先，手势按单状态源/角度锁和可取消的跟手状态机实现。各场景 320/360/390 宽、长标题、设置弹窗、右栏、横滑预览与 reduced-motion 逐项验。
3. **Native 工作区重排**：先不改文件/项目数据 schema，压缩字号/留白/toolbar，以 breadcrumbs+列表替换常驻 path/action wall，项目快捷项与普通目录视觉一致，长按多选/上下文 toolbar 与恢复操作按能力分层。完整多根/Trash transaction 先另 ADR+迁移、故障注入、可回滚。
4. **Native 设置和备份重排**：索引简洁，备份首屏验证信息/明确主次动作，绝对路径/Manifest/类别进详情，真正 restore 不存在则不可点、不暗示可用。
5. **门禁**：Node 24 syntax/policy、固定 profile Chromium 官方+compat 与新插件对照、Android 11 Robolectric、Runtime Alpine E2E、Lint、签名/源冻结 clean build，OriginOS 实机 1/2/3/5 真正发送、抽屉20次/IME10次、覆盖安装数据和救援隧道重连通过后才能给新候选 APK；不能以 59 个旧单测或 Preview.6 已发布替代。不要卸载清数据、改 SSH/ADB/密钥、改 `release/update.json` 或宣称已修复。

## 交付状态

本轮已完成：历史需求复核、4图定位、旧版插件和官方 slot API 静态审查、整改路线记录；已开始原生 UI 的无数据迁移切片，`FilesScreen.kt` 将常驻新建/上级/根/刷新/管理项目收敛为新建+更多、单行相对路径及需要时的完整路径详情，文件行单击打开、长按选中（**仍是单项选择，不是假称批量**）；`RecoveryScreen.kt` 备份卡保留主操作创建快照/导出备份，路径、Manifest、刷新及校验移动到明确可展开的更多操作，仍显著声明完整恢复未开放；共享 `DshPageHeader` 将巨大 displaySmall 缩成 headlineSmall、副标题缩成 bodySmall。业务动作保持原有 controller、canonical 授权与 backup 语义，不改存储/签名/OTA。

改动后 `android_unit_test` job `task-android_unit_test-62e62d227c5d4395b605` succeeded / exit 0 / Kotlin compile PASS；这证明代码编译和既有单测，并不证明真实 360px/OriginOS 版面好看或新插件完成。本轮全部既有单测结果：app 32/32、core/recovery 12/12、core/runtime-android 15/15，失败 0；`android_lint` job `task-android_lint-fe9069fb21874ee79b37` succeeded/exit 0，报告 `0 errors, 23 warnings`；`runtime_alpine_e2e` job `task-runtime_alpine_e2e-ef131dd19310471fb595` succeeded/exit 0，末行 `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`。后者包含 WebView compat 静态 policy 与 Web token auth，但**并非冻结 profile 的完整 UI Chromium composition gate，也不能代替 OriginOS 实机视觉/真实附件发送验收**。因此本轮只入库阶段性源码和测试文档，不构建或发布新版 APK。

本轮未完成：按新视觉实现附件 Cordis 插件（仍保留 Preview.6 旧 modal chooser；不得给本轮旧 APK 起新名字）、抽屉跟手/闪影、工作区真正多选及 Trash 恢复、备份真正恢复、实机发送或动画验收、新 APK。具体实现需逐切片记录源码/测试/屏幕对比，而不是单纯重新打包旧 UI。
